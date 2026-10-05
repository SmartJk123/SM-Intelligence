<?php
/**
 * Equity Bank (Jenga) Instant Payment Notification endpoint, for cPanel hosting.
 *
 * A PHP port of EquityWebhookController in the bank integration service, for
 * hosting that cannot run Java. The public address is /api/v1/webhooks/equity
 * through the .htaccess rewrites.
 *
 * Jenga posts a JSON body for successful and failed payments alike, and
 * authenticates with Basic Auth: the username and password registered with
 * the callback URL on Jenga HQ. Both kinds are stored; the bank integration
 * service credits only successful ones. Source:
 * https://developer.jengahq.io/guides/jenga-pgw/instant-payment-notifications
 *
 * GET /api/v1/webhooks/equity/export lets the SmartMoney bank integration
 * service pull what was stored here (see equity_export).
 *
 * Settings and the database connection come from smi-private/equity-config.php,
 * outside the web root. Runs on PHP 7.4 and later with pdo_mysql.
 */

declare(strict_types=1);

const EQUITY_MAX_BODY_BYTES = 65536;

ini_set('display_errors', '0');

$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

if ($method === 'GET' && isset($_GET['export'])) {
    equity_export();
    exit;
}
if ($method === 'GET' || $method === 'HEAD') {
    equity_probe();
    exit;
}
if ($method !== 'POST') {
    http_response_code(405);
    header('Allow: GET, HEAD, POST');
    exit;
}

equity_receive();

// --------------------------------------------------------------- endpoint

function equity_probe(): void
{
    $config = equity_config();
    if ($config === null) {
        $status = 'not ready: smi-private/equity-config.php was not found, or is not valid PHP';
    } elseif ($config['signature_verification'] && ($config['ipn_username'] === '' || $config['ipn_password'] === '')) {
        $status = 'not ready: ipn_username and ipn_password in equity-config.php must match the callback on Jenga HQ';
    } elseif (equity_database() === null) {
        $status = 'not ready: the MySQL login in equity-config.php failed (db_dsn, db_user or db_password)';
    } else {
        $status = 'ready';
    }
    header('Content-Type: text/plain; charset=UTF-8');
    header('Cache-Control: no-store');
    echo "SmartMoney Intelligence notification endpoint\n";
    echo "Provider : Equity Bank Kenya (Jenga)\n";
    echo "Status   : " . $status . "\n\n";
    echo "POST Instant Payment Notifications to this URL as JSON, with Basic Auth.\n";
    echo "A GET returns this message so the address can be verified.\n\n";
    echo 'Checked at ' . gmdate('Y-m-d\TH:i:s\Z') . "\n";
}

function equity_receive(): void
{
    $raw = file_get_contents('php://input', false, null, 0, EQUITY_MAX_BODY_BYTES + 1);
    $raw = $raw === false ? '' : $raw;
    if (strlen($raw) > EQUITY_MAX_BODY_BYTES) {
        equity_answer(413, ['status' => 'rejected', 'reason' => 'Notification body too large']);
    }

    $config = equity_config();
    $db = equity_database();
    if ($config === null || $db === null) {
        // Not acknowledged, so Jenga can retry and nothing is lost.
        equity_answer(503, ['status' => 'rejected', 'reason' => 'Endpoint not ready to store notifications']);
    }

    $authenticated = null;
    if ($config['signature_verification']) {
        $authenticated = equity_authenticated($config);
        if (!$authenticated) {
            equity_log('Refused an Equity notification: Basic Auth credentials missing or wrong');
            header('WWW-Authenticate: Basic realm="SmartMoney"');
            equity_answer(401, ['status' => 'rejected', 'reason' => 'Invalid or missing Basic Auth credentials']);
        }
    }

    $notification = json_decode($raw, true);
    if (!is_array($notification)) {
        equity_log('Refused an Equity notification whose body is not JSON');
        equity_answer(400, ['status' => 'rejected', 'reason' => 'Body is not valid JSON']);
    }

    $transaction = equity_section($notification, 'transaction');
    $bank = equity_section($notification, 'bank');
    $reference = equity_text($transaction, 'reference');
    if ($reference === '') {
        $reference = equity_text($bank, 'reference');
    }
    $externalId = $reference !== '' ? $reference : 'PAYLOAD-' . hash('sha256', $raw);

    try {
        equity_store($db, $notification, $externalId, $authenticated, $raw);
    } catch (PDOException $error) {
        // SQLSTATE 23000 is the unique key on reference: already stored.
        if ($error->getCode() === '23000') {
            equity_log('Equity notification ' . $externalId . ' was already stored, acknowledged as a duplicate');
            equity_answer(200, ['status' => 'received', 'reference' => $externalId, 'duplicate' => true]);
        }
        equity_log('Could not store an Equity notification: ' . $error->getMessage());
        equity_answer(500, ['status' => 'rejected', 'reason' => 'Notification could not be stored, please retry']);
    }

    equity_log('Accepted Equity notification reference=' . $externalId
        . ' amount=' . equity_text($transaction, 'amount') . ' status=' . equity_text($transaction, 'status'));
    equity_answer(200, ['status' => 'received', 'reference' => $externalId]);
}

/**
 * Stored notifications for the SmartMoney bank integration service to import.
 *
 *   GET /api/v1/webhooks/equity/export?after=<id>&limit=<1..200>
 *   X-SMI-Export-Token: <export_token from equity-config.php>
 *
 * Same shape as the NCBA and KCB exports. With no export_token configured it answers 404.
 */
function equity_export(): void
{
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');

    $config = equity_config();
    $expected = $config === null ? '' : (string) $config['export_token'];
    if (strlen($expected) < 32) {
        http_response_code(404);
        echo json_encode(['error' => 'Not found']);
        return;
    }
    $provided = (string) ($_SERVER['HTTP_X_SMI_EXPORT_TOKEN'] ?? '');
    if (!hash_equals($expected, $provided)) {
        equity_log('Refused an export request with a missing or wrong token');
        http_response_code(401);
        echo json_encode(['error' => 'Unauthorized']);
        return;
    }
    $db = equity_database();
    if ($db === null) {
        http_response_code(503);
        echo json_encode(['error' => 'Storage unavailable']);
        return;
    }

    $after = max(0, (int) ($_GET['after'] ?? 0));
    $limit = min(200, max(1, (int) ($_GET['limit'] ?? 100)));
    $statement = $db->prepare(
        'SELECT id, reference, signature_valid, raw_body, received_at
         FROM equity_notifications WHERE id > :after ORDER BY id ASC LIMIT ' . $limit
    );
    $statement->execute([':after' => $after]);
    $items = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $items[] = [
            'id' => (int) $row['id'],
            'transId' => $row['reference'],
            'signatureValid' => $row['signature_valid'] === null ? null : (bool) $row['signature_valid'],
            'rawBody' => $row['raw_body'],
            'receivedAt' => $row['received_at'],
        ];
    }
    echo json_encode(['items' => $items], JSON_UNESCAPED_SLASHES);
}

/** Writes a JSON answer and ends the request. */
function equity_answer(int $httpStatus, array $body): void
{
    http_response_code($httpStatus);
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');
    echo json_encode($body, JSON_UNESCAPED_SLASHES);
    exit;
}

// ---------------------------------------------------------- configuration

/** @return array|null the settings from smi-private/equity-config.php */
function equity_config(): ?array
{
    static $loaded = false;
    static $config = null;
    if ($loaded) {
        return $config;
    }
    $loaded = true;

    $path = getenv('SMI_EQUITY_CONFIG') ?: null;
    if ($path === null) {
        // Start above the web root, so a config file inside it is never used.
        $directory = dirname(__DIR__);
        for ($level = 0; $level < 5 && $path === null; $level++) {
            $candidate = $directory . DIRECTORY_SEPARATOR . 'smi-private' . DIRECTORY_SEPARATOR . 'equity-config.php';
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
        equity_log('smi-private/equity-config.php was not found above ' . __DIR__);
        return null;
    }

    $values = require $path;
    if (!is_array($values)) {
        equity_log('equity-config.php does not return an array');
        return null;
    }
    $config = $values + [
        'ipn_username' => '',
        'ipn_password' => '',
        'signature_verification' => true,
        'export_token' => '',
        'db_dsn' => '',
        'db_user' => '',
        'db_password' => '',
    ];
    return $config;
}

function equity_database(): ?PDO
{
    static $tried = false;
    static $db = null;
    if ($tried) {
        return $db;
    }
    $tried = true;

    $config = equity_config();
    if ($config === null || $config['db_dsn'] === '') {
        return null;
    }
    try {
        $db = new PDO($config['db_dsn'], $config['db_user'], $config['db_password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    } catch (PDOException $error) {
        equity_log('Could not connect to the notification database: ' . $error->getMessage());
        $db = null;
    }
    return $db;
}

// ---------------------------------------------------------- authentication

/** Compares the Basic Auth credentials with those registered on Jenga HQ, in constant time. */
function equity_authenticated(array $config): bool
{
    if ($config['ipn_username'] === '' || $config['ipn_password'] === '') {
        return false;
    }
    $expected = $config['ipn_username'] . ':' . $config['ipn_password'];
    $header = trim(equity_header('Authorization'));
    if (stripos($header, 'Basic ') === 0) {
        $decoded = base64_decode(trim(substr($header, 6)), true);
        return $decoded !== false && hash_equals($expected, $decoded);
    }
    // Some Apache setups hand Basic Auth to PHP only as PHP_AUTH_USER and PHP_AUTH_PW.
    if (isset($_SERVER['PHP_AUTH_USER'])) {
        return hash_equals($expected, $_SERVER['PHP_AUTH_USER'] . ':' . ($_SERVER['PHP_AUTH_PW'] ?? ''));
    }
    return false;
}

/** A request header by name, without case, however this server passes it to PHP. */
function equity_header(string $name): string
{
    $key = 'HTTP_' . strtoupper(str_replace('-', '_', $name));
    if (isset($_SERVER[$key])) {
        return (string) $_SERVER[$key];
    }
    if ($name === 'Authorization' && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        return (string) $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
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

function equity_store(PDO $db, array $notification, string $externalId, ?bool $authenticated, string $raw): void
{
    $customer = equity_section($notification, 'customer');
    $transaction = equity_section($notification, 'transaction');
    $bank = equity_section($notification, 'bank');
    $amount = equity_text($transaction, 'amount');
    $statement = $db->prepare(
        'INSERT INTO equity_notifications
            (reference, bank_reference, payment_mode, transaction_date, amount, currency, status, remarks,
             bill_number, customer_name, customer_mobile, account, transaction_type,
             signature_valid, raw_body, remote_addr, received_at)
         VALUES
            (:reference, :bank_reference, :payment_mode, :transaction_date, :amount, :currency, :status, :remarks,
             :bill_number, :customer_name, :customer_mobile, :account, :transaction_type,
             :signature_valid, :raw_body, :remote_addr, :received_at)'
    );
    $statement->execute([
        ':reference' => $externalId,
        ':bank_reference' => equity_text($bank, 'reference'),
        ':payment_mode' => equity_text($transaction, 'paymentMode'),
        ':transaction_date' => equity_text($transaction, 'date'),
        ':amount' => preg_match('/^\d+(\.\d+)?$/', $amount) ? $amount : null,
        ':currency' => equity_text($transaction, 'currency'),
        ':status' => equity_text($transaction, 'status'),
        ':remarks' => equity_text($transaction, 'remarks'),
        ':bill_number' => equity_text($transaction, 'billNumber'),
        ':customer_name' => equity_text($customer, 'name'),
        ':customer_mobile' => equity_text($customer, 'mobileNumber'),
        ':account' => equity_text($bank, 'account'),
        ':transaction_type' => equity_text($bank, 'transactionType'),
        ':signature_valid' => $authenticated === null ? null : ($authenticated ? 1 : 0),
        // Stored byte for byte, so the import reads exactly what Jenga sent.
        ':raw_body' => $raw,
        ':remote_addr' => $_SERVER['REMOTE_ADDR'] ?? null,
        ':received_at' => gmdate('Y-m-d H:i:s'),
    ]);
}

/** A nested object of the notification, or an empty array. */
function equity_section(array $notification, string $name): array
{
    return is_array($notification[$name] ?? null) ? $notification[$name] : [];
}

/** A field as trimmed text; numbers are accepted too, because JSON may carry them unquoted. */
function equity_text(array $values, string $field): string
{
    $value = $values[$field] ?? '';
    return is_scalar($value) ? trim((string) $value) : '';
}

function equity_log(string $message): void
{
    error_log('[equity-webhook] ' . $message);
}
