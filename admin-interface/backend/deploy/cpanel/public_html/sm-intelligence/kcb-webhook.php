<?php
/**
 * KCB instant payment notification (IPN) endpoint, for cPanel hosting.
 *
 * A PHP port of KcbWebhookController and KcbSignatureVerifier in the bank
 * integration service, for hosting that cannot run Java. The public address is
 * /api/v1/webhooks/kcb through the .htaccess rewrites.
 *
 * KCB posts a JSON body after crediting the account, with a Signature header:
 * a base64 SHA256withRSA signature of the body made with KCB's private key and
 * checked here with KCB's public key. The acknowledgement body is fixed by the
 * KCB IPN specification:
 *
 *     {"transactionID": "...", "statusCode": "0", "statusMessage": "..."}
 *
 * GET /api/v1/webhooks/kcb/export lets the SmartMoney bank integration service
 * pull what was stored here, so a payment reaches the customer's dashboard and
 * the admin interface. It needs the export token (see kcb_export).
 *
 * Settings and the database connection come from smi-private/kcb-config.php,
 * which lives outside the web root and is found by walking up from this file.
 *
 * Runs on PHP 7.4 and later, with the openssl and pdo_mysql extensions.
 */

declare(strict_types=1);

/** A notification is well under 2 KB. Anything far larger is refused before it is parsed. */
const KCB_MAX_BODY_BYTES = 65536;

ini_set('display_errors', '0');

$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

if ($method === 'GET' && isset($_GET['export'])) {
    kcb_export();
    exit;
}
if ($method === 'GET' || $method === 'HEAD') {
    kcb_probe();
    exit;
}
if ($method !== 'POST') {
    http_response_code(405);
    header('Allow: GET, HEAD, POST');
    exit;
}

kcb_receive();

// --------------------------------------------------------------- endpoint

function kcb_probe(): void
{
    // Names the failing step, never a value, so a setup problem can be fixed
    // without opening the server error log.
    $config = kcb_config();
    if ($config === null) {
        $status = 'not ready: smi-private/kcb-config.php was not found, or is not valid PHP';
    } elseif (!extension_loaded('openssl')) {
        $status = 'not ready: the PHP openssl extension is not enabled';
    } elseif ($config['signature_verification'] && kcb_public_key($config) === null) {
        $status = 'not ready: public_key in kcb-config.php is missing or is not a valid KCB public key';
    } elseif (kcb_database() === null) {
        $status = 'not ready: the MySQL login in kcb-config.php failed (db_dsn, db_user or db_password)';
    } else {
        $status = 'ready';
    }
    header('Content-Type: text/plain; charset=UTF-8');
    header('Cache-Control: no-store');
    echo "SmartMoney Intelligence notification endpoint\n";
    echo "Provider : KCB Bank Kenya\n";
    echo "Status   : " . $status . "\n\n";
    echo "POST instant payment notifications to this URL as JSON, signed in the Signature header.\n";
    echo "A GET returns this message so the address can be verified.\n\n";
    echo 'Checked at ' . gmdate('Y-m-d\TH:i:s\Z') . "\n";
}

function kcb_receive(): void
{
    $raw = file_get_contents('php://input', false, null, 0, KCB_MAX_BODY_BYTES + 1);
    $raw = $raw === false ? '' : $raw;
    if (strlen($raw) > KCB_MAX_BODY_BYTES) {
        kcb_log('Refused a KCB notification larger than ' . KCB_MAX_BODY_BYTES . ' bytes');
        kcb_answer(413, null, '413', 'Notification body too large');
    }

    $config = kcb_config();
    $db = kcb_database();
    if ($config === null || $db === null) {
        // Not acknowledged, so KCB can retry and nothing is lost.
        kcb_answer(503, null, '503', 'Endpoint not ready to store notifications');
    }

    $signatureValid = null;
    if ($config['signature_verification']) {
        [$signatureValid, $reason] = kcb_verify($raw, $config);
        if (!$signatureValid) {
            kcb_log('Refused a KCB notification: ' . $reason);
            kcb_answer(401, null, '401', 'Invalid or unconfigured signature');
        }
    }

    $notification = json_decode($raw, true);
    if (!is_array($notification)) {
        kcb_log('Refused a KCB notification whose body is not JSON');
        kcb_answer(400, null, '400', 'Body is not valid JSON');
    }

    $reference = kcb_text($notification, 'transactionReference');
    $externalId = $reference !== '' ? $reference : 'PAYLOAD-' . hash('sha256', $raw);

    try {
        kcb_store($db, $notification, $externalId, $signatureValid, $raw);
    } catch (PDOException $error) {
        // SQLSTATE 23000 is the unique key on transaction_reference: the same
        // payment was already stored, so the first copy is kept and confirmed.
        if ($error->getCode() === '23000') {
            kcb_log('KCB notification ' . $externalId . ' was already stored, acknowledged as a duplicate');
            kcb_answer(200, $externalId, '0', 'Duplicate notification received');
        }
        kcb_log('Could not store a KCB notification: ' . $error->getMessage());
        kcb_answer(500, $externalId, '500', 'Notification could not be stored, please retry');
    }

    kcb_log('Accepted KCB notification ' . kcb_describe($notification));
    kcb_answer(200, $externalId, '0', 'Notification received successfully');
}

/**
 * Stored notifications for the SmartMoney bank integration service to import.
 *
 *   GET /api/v1/webhooks/kcb/export?after=<id>&limit=<1..200>
 *   X-SMI-Export-Token: <export_token from kcb-config.php>
 *
 * Same shape as the NCBA export. With no export_token configured it answers 404.
 */
function kcb_export(): void
{
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');

    $config = kcb_config();
    $expected = $config === null ? '' : (string) $config['export_token'];
    if (strlen($expected) < 32) {
        http_response_code(404);
        echo json_encode(['error' => 'Not found']);
        return;
    }
    $provided = (string) ($_SERVER['HTTP_X_SMI_EXPORT_TOKEN'] ?? '');
    if (!hash_equals($expected, $provided)) {
        kcb_log('Refused an export request with a missing or wrong token');
        http_response_code(401);
        echo json_encode(['error' => 'Unauthorized']);
        return;
    }
    $db = kcb_database();
    if ($db === null) {
        http_response_code(503);
        echo json_encode(['error' => 'Storage unavailable']);
        return;
    }

    $after = max(0, (int) ($_GET['after'] ?? 0));
    $limit = min(200, max(1, (int) ($_GET['limit'] ?? 100)));
    $statement = $db->prepare(
        'SELECT id, transaction_reference, signature_valid, raw_body, received_at
         FROM kcb_notifications WHERE id > :after ORDER BY id ASC LIMIT ' . $limit
    );
    $statement->execute([':after' => $after]);
    $items = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $items[] = [
            'id' => (int) $row['id'],
            'transId' => $row['transaction_reference'],
            'signatureValid' => $row['signature_valid'] === null ? null : (bool) $row['signature_valid'],
            'rawBody' => $row['raw_body'],
            'receivedAt' => $row['received_at'],
        ];
    }
    echo json_encode(['items' => $items], JSON_UNESCAPED_SLASHES);
}

/** Writes the acknowledgement the KCB IPN specification defines, and ends the request. */
function kcb_answer(int $httpStatus, ?string $transactionId, string $statusCode, string $statusMessage): void
{
    http_response_code($httpStatus);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');
    echo json_encode([
        'transactionID' => $transactionId,
        'statusCode' => $statusCode,
        'statusMessage' => $statusMessage,
    ], JSON_UNESCAPED_SLASHES);
    exit;
}

// ---------------------------------------------------------- configuration

/** @return array|null the settings from smi-private/kcb-config.php */
function kcb_config(): ?array
{
    static $loaded = false;
    static $config = null;
    if ($loaded) {
        return $config;
    }
    $loaded = true;

    $path = getenv('SMI_KCB_CONFIG') ?: null;
    if ($path === null) {
        // Start above the web root, so a config file inside it is never used.
        $directory = dirname(__DIR__);
        for ($level = 0; $level < 5 && $path === null; $level++) {
            $candidate = $directory . DIRECTORY_SEPARATOR . 'smi-private' . DIRECTORY_SEPARATOR . 'kcb-config.php';
            if (is_file($candidate)) {
                $path = $candidate;
            }
            $parent = dirname($directory);
            if ($parent === $directory) {
                break;
            }
            $directory = $parent;
        }
    }
    if ($path === null || !is_file($path)) {
        kcb_log('smi-private/kcb-config.php was not found above ' . __DIR__);
        return null;
    }

    $values = require $path;
    if (!is_array($values)) {
        kcb_log('kcb-config.php does not return an array');
        return null;
    }
    $config = $values + [
        'public_key' => '',
        'signature_verification' => true,
        'signature_header' => 'Signature',
        'export_token' => '',
        'db_dsn' => '',
        'db_user' => '',
        'db_password' => '',
    ];
    return $config;
}

function kcb_database(): ?PDO
{
    static $tried = false;
    static $db = null;
    if ($tried) {
        return $db;
    }
    $tried = true;

    $config = kcb_config();
    if ($config === null || $config['db_dsn'] === '') {
        return null;
    }
    try {
        $db = new PDO($config['db_dsn'], $config['db_user'], $config['db_password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    } catch (PDOException $error) {
        kcb_log('Could not connect to the notification database: ' . $error->getMessage());
        $db = null;
    }
    return $db;
}

// ----------------------------------------------------------- verification

/**
 * KCB's public key, from a PEM block, or a bare base64 X.509 key as BUNI
 * sometimes shares it, which is wrapped into a PEM block here.
 *
 * @return resource|OpenSSLAsymmetricKey|null
 */
function kcb_public_key(array $config)
{
    $value = trim((string) $config['public_key']);
    if ($value === '' || !function_exists('openssl_pkey_get_public')) {
        return null;
    }
    if (strpos($value, '-----BEGIN') === false) {
        $value = "-----BEGIN PUBLIC KEY-----\n"
            . chunk_split(preg_replace('/\s+/', '', $value), 64, "\n")
            . "-----END PUBLIC KEY-----\n";
    }
    $key = openssl_pkey_get_public($value);
    return $key === false ? null : $key;
}

/**
 * Checks the Signature header against the exact bytes received. Fails closed:
 * with verification on and no usable public key, every notification is refused.
 *
 * @return array{0: bool, 1: string} valid, reason when not
 */
function kcb_verify(string $raw, array $config): array
{
    $key = kcb_public_key($config);
    if ($key === null) {
        return [false, 'Signature verification is on but public_key is missing or invalid'];
    }
    $provided = trim(kcb_header((string) $config['signature_header']));
    if ($provided === '') {
        return [false, 'The ' . $config['signature_header'] . ' header is missing'];
    }
    $signature = base64_decode(preg_replace('/\s+/', '', $provided), true);
    if ($signature === false) {
        // Some gateways send URL-safe base64.
        $signature = base64_decode(strtr(preg_replace('/\s+/', '', $provided), '-_', '+/'), true);
    }
    if ($signature === false) {
        return [false, 'The signature is not base64'];
    }
    $result = openssl_verify($raw, $signature, $key, OPENSSL_ALGO_SHA256);
    if ($result !== 1) {
        return [false, 'The signature did not verify against the KCB public key'];
    }
    return [true, ''];
}

/** A request header by name, without case, however this server passes it to PHP. */
function kcb_header(string $name): string
{
    $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
    if (isset($_SERVER[$key])) {
        return (string) $_SERVER[$key];
    }
    if (function_exists('getallheaders')) {
        foreach (getallheaders() as $header => $value) {
            if (strcasecmp((string) $header, $name) === 0) {
                return (string) $value;
            }
        }
    }
    return '';
}

// ---------------------------------------------------------------- storage

function kcb_store(PDO $db, array $notification, string $externalId, ?bool $signatureValid, string $raw): void
{
    $amount = kcb_amount(kcb_text($notification, 'transactionAmount'));
    $statement = $db->prepare(
        'INSERT INTO kcb_notifications
            (transaction_reference, request_id, channel_code, timestamp_text, booked_at,
             amount, amount_text, currency, customer_reference, customer_name, customer_mobile,
             narration, credit_account, organization_short_code, till_number, balance_text,
             signature_valid, raw_body, remote_addr, received_at)
         VALUES
            (:transaction_reference, :request_id, :channel_code, :timestamp_text, :booked_at,
             :amount, :amount_text, :currency, :customer_reference, :customer_name, :customer_mobile,
             :narration, :credit_account, :organization_short_code, :till_number, :balance_text,
             :signature_valid, :raw_body, :remote_addr, :received_at)'
    );
    $statement->execute([
        ':transaction_reference' => $externalId,
        ':request_id' => kcb_text($notification, 'requestId'),
        ':channel_code' => kcb_text($notification, 'channelCode'),
        ':timestamp_text' => kcb_text($notification, 'timestamp'),
        ':booked_at' => kcb_booked_at(kcb_text($notification, 'timestamp')),
        ':amount' => $amount,
        ':amount_text' => kcb_text($notification, 'transactionAmount'),
        ':currency' => kcb_text($notification, 'currency'),
        ':customer_reference' => kcb_text($notification, 'customerReference'),
        ':customer_name' => kcb_text($notification, 'customerName'),
        ':customer_mobile' => kcb_text($notification, 'customerMobileNumber'),
        ':narration' => kcb_text($notification, 'narration'),
        ':credit_account' => kcb_text($notification, 'creditAccountIdentifier'),
        ':organization_short_code' => kcb_text($notification, 'organizationShortCode'),
        ':till_number' => kcb_text($notification, 'tillNumber'),
        ':balance_text' => kcb_text($notification, 'balance'),
        ':signature_valid' => $signatureValid === null ? null : ($signatureValid ? 1 : 0),
        // The body is stored byte for byte, so the import reads exactly what KCB signed.
        ':raw_body' => $raw,
        ':remote_addr' => $_SERVER['REMOTE_ADDR'] ?? null,
        ':received_at' => gmdate('Y-m-d H:i:s'),
    ]);
}

/** A field as trimmed text; numbers are accepted too, because JSON may carry them unquoted. */
function kcb_text(array $notification, string $field): string
{
    $value = $notification[$field] ?? '';
    return is_scalar($value) ? trim((string) $value) : '';
}

/** The amount as a plain decimal string, or null when it is not a number. */
function kcb_amount(string $text): ?string
{
    $cleaned = str_replace(',', '', $text);
    return preg_match('/^\d+(\.\d+)?$/', $cleaned) ? $cleaned : null;
}

/** timestamp is yyyyMMddHHmmss with no zone, read as East Africa Time and stored as UTC. */
function kcb_booked_at(string $value): ?string
{
    if ($value === '') {
        return null;
    }
    $eat = new DateTimeZone('+03:00');
    foreach (['YmdHis', 'Y-m-d H:i:s', 'Y-m-d\TH:i:s'] as $format) {
        $parsed = DateTimeImmutable::createFromFormat('!' . $format, $value, $eat);
        if ($parsed !== false && $parsed->format($format) === $value) {
            return $parsed->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s');
        }
    }
    return null;
}

/** Short description for a log line. */
function kcb_describe(array $notification): string
{
    return 'reference=' . kcb_text($notification, 'transactionReference')
        . ' amount=' . kcb_text($notification, 'transactionAmount')
        . ' account=' . kcb_text($notification, 'creditAccountIdentifier')
        . ' channel=' . kcb_text($notification, 'channelCode');
}

function kcb_log(string $message): void
{
    error_log('[kcb-webhook] ' . $message);
}
