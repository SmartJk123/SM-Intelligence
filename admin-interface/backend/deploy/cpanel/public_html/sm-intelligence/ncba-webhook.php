<?php
/**
 * NCBA account level push notification endpoint, for cPanel hosting.
 *
 * A PHP port of NcbaWebhookController and NcbaSignatureVerifier in the
 * bank integration service, for hosting that cannot run Java. The public
 * address stays /api/v1/webhooks/ncba through the .htaccess rewrite next to
 * this file.
 *
 * NCBA posts an XML body and reads a value back out of the Result element. Any
 * value containing the string OK counts as delivered, and anything else is
 * requeued on their side, so a refused notification is answered with HTTP 200
 * and a FAIL result rather than an error status.
 *
 * GET /api/v1/webhooks/ncba/export lets the SmartMoney bank integration
 * service pull what was stored here, so a notification reaches the customer's
 * dashboard. It needs the export token (see ncba_export).
 *
 * Credentials and the database connection are read from smi-private/ncba-config.php,
 * which lives outside the web root and is found by walking up from this file.
 *
 * Runs on PHP 7.4 and later.
 */

declare(strict_types=1);

const NCBA_ACCEPTED = 'OK';
const NCBA_DUPLICATE = 'OK: Duplicate Notification';

/**
 * A notification is a few hundred bytes. Anything far larger is refused
 * before it is parsed, so an oversized body cannot be used to exhaust the host.
 */
const NCBA_MAX_BODY_BYTES = 524288;

ini_set('display_errors', '0');

$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

if ($method === 'GET' && isset($_GET['export'])) {
    ncba_export();
    exit;
}
if ($method === 'GET' || $method === 'HEAD') {
    ncba_probe();
    exit;
}
if ($method !== 'POST') {
    http_response_code(405);
    header('Allow: GET, HEAD, POST');
    exit;
}

ncba_receive();

// --------------------------------------------------------------- endpoint

function ncba_probe(): void
{
    // Names the failing step, never a value, so a setup problem can be fixed
    // without opening the server error log.
    if (ncba_config() === null) {
        $status = 'not ready: smi-private/ncba-config.php was not found, or is not valid PHP';
    } elseif (ncba_database() === null) {
        $status = 'not ready: the MySQL login in ncba-config.php failed (db_dsn, db_user or db_password)';
    } else {
        $status = 'ready';
    }
    header('Content-Type: text/plain; charset=UTF-8');
    header('Cache-Control: no-store');
    echo "SmartMoney Intelligence notification endpoint\n";
    echo "Provider : NCBA Bank Kenya\n";
    echo "Status   : " . $status . "\n\n";
    echo "POST account level push notifications to this URL as XML.\n";
    echo "A GET returns this message so the address can be verified.\n\n";
    echo 'Checked at ' . gmdate('Y-m-d\TH:i:s\Z') . "\n";
}

function ncba_receive(): void
{
    $raw = file_get_contents('php://input', false, null, 0, NCBA_MAX_BODY_BYTES + 1);
    $raw = $raw === false ? '' : $raw;
    if (strlen($raw) > NCBA_MAX_BODY_BYTES) {
        ncba_log('Refused an NCBA notification larger than ' . NCBA_MAX_BODY_BYTES . ' bytes');
        ncba_answer('FAIL: The notification body is larger than this endpoint accepts');
    }

    $config = ncba_config();
    $db = ncba_database();
    if ($config === null || $db === null) {
        // Answered as FAIL so NCBA requeues it and nothing is lost.
        ncba_answer('FAIL: The endpoint is not ready to store notifications');
    }

    $notification = ncba_parse($raw);
    [$accepted, $signatureValid, $reason] = ncba_verify($notification, $config);
    if (!$accepted) {
        ncba_log('Refused an NCBA notification: ' . $reason);
        ncba_answer('FAIL: ' . $reason);
    }

    $reference = $notification['TransID'] !== '' ? $notification['TransID'] : null;
    $externalId = $reference ?? 'PAYLOAD-' . hash('sha256', $raw);

    try {
        ncba_store($db, $notification, $externalId, $signatureValid, $raw);
    } catch (PDOException $error) {
        // SQLSTATE 23000 is the unique key on trans_id: the same TransID was
        // already stored, so the first copy is kept and this one is confirmed.
        if ($error->getCode() === '23000') {
            ncba_log('NCBA notification ' . $externalId . ' was already stored, answered as a duplicate');
            ncba_answer(NCBA_DUPLICATE);
        }
        ncba_log('Could not store an NCBA notification: ' . $error->getMessage());
        ncba_answer('FAIL: The notification could not be stored, please retry');
    }

    ncba_log('Accepted NCBA notification ' . ncba_describe($notification));
    ncba_answer(NCBA_ACCEPTED);
}

/**
 * Stored notifications for the SmartMoney bank integration service to import.
 *
 *   GET /api/v1/webhooks/ncba/export?after=<id>&limit=<1..200>
 *   X-SMI-Export-Token: <export_token from ncba-config.php>
 *
 * Answers the rows with an id above `after`, oldest first. A custom header is
 * used because cPanel's Apache commonly strips Authorization before PHP sees
 * it. With no export_token configured the export does not exist (404).
 */
function ncba_export(): void
{
    header('Content-Type: application/json; charset=UTF-8');
    header('Cache-Control: no-store');

    $config = ncba_config();
    $expected = $config === null ? '' : (string) $config['export_token'];
    if (strlen($expected) < 32) {
        http_response_code(404);
        echo json_encode(['error' => 'Not found']);
        return;
    }
    $provided = (string) ($_SERVER['HTTP_X_SMI_EXPORT_TOKEN'] ?? '');
    if (!hash_equals($expected, $provided)) {
        ncba_log('Refused an export request with a missing or wrong token');
        http_response_code(401);
        echo json_encode(['error' => 'Unauthorized']);
        return;
    }
    $db = ncba_database();
    if ($db === null) {
        http_response_code(503);
        echo json_encode(['error' => 'Storage unavailable']);
        return;
    }

    $after = max(0, (int) ($_GET['after'] ?? 0));
    $limit = min(200, max(1, (int) ($_GET['limit'] ?? 100)));
    $statement = $db->prepare(
        'SELECT id, trans_id, signature_valid, raw_body, received_at
         FROM ncba_notifications WHERE id > :after ORDER BY id ASC LIMIT ' . $limit
    );
    $statement->execute([':after' => $after]);
    $items = [];
    foreach ($statement->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $items[] = [
            'id' => (int) $row['id'],
            'transId' => $row['trans_id'],
            'signatureValid' => $row['signature_valid'] === null ? null : (bool) $row['signature_valid'],
            'rawBody' => $row['raw_body'],
            'receivedAt' => $row['received_at'],
        ];
    }
    echo json_encode(['items' => $items], JSON_UNESCAPED_SLASHES);
}

/** Writes the SOAP envelope NCBA expects and ends the request. */
function ncba_answer(string $result): void
{
    http_response_code(200);
    header('Content-Type: text/xml; charset=UTF-8');
    header('Cache-Control: no-store');
    $escaped = htmlspecialchars($result, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    echo <<<XML
<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Header/>
  <soapenv:Body>
    <NCBAPaymentNotificationResult>
      <Result>{$escaped}</Result>
    </NCBAPaymentNotificationResult>
  </soapenv:Body>
</soapenv:Envelope>

XML;
    exit;
}

// ---------------------------------------------------------- configuration

/** @return array|null the settings from smi-private/ncba-config.php */
function ncba_config(): ?array
{
    static $loaded = false;
    static $config = null;
    if ($loaded) {
        return $config;
    }
    $loaded = true;

    $path = getenv('SMI_NCBA_CONFIG') ?: null;
    if ($path === null) {
        // Start above the web root, so a config file inside it is never used.
        $directory = dirname(__DIR__);
        for ($level = 0; $level < 5 && $path === null; $level++) {
            $candidate = $directory . DIRECTORY_SEPARATOR . 'smi-private' . DIRECTORY_SEPARATOR . 'ncba-config.php';
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
        ncba_log('smi-private/ncba-config.php was not found above ' . __DIR__);
        return null;
    }

    $values = require $path;
    if (!is_array($values)) {
        ncba_log('ncba-config.php does not return an array');
        return null;
    }
    $config = $values + [
        'secret_key' => '',
        'username' => '',
        'password' => '',
        'signature_verification' => true,
        'export_token' => '',
        'db_dsn' => '',
        'db_user' => '',
        'db_password' => '',
    ];
    return $config;
}

function ncba_database(): ?PDO
{
    static $tried = false;
    static $db = null;
    if ($tried) {
        return $db;
    }
    $tried = true;

    $config = ncba_config();
    if ($config === null || $config['db_dsn'] === '') {
        return null;
    }
    try {
        $db = new PDO($config['db_dsn'], $config['db_user'], $config['db_password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    } catch (PDOException $error) {
        ncba_log('Could not connect to the notification database: ' . $error->getMessage());
        $db = null;
    }
    return $db;
}

// ---------------------------------------------------------------- parsing

/**
 * One NCBA notification, read out of the SOAP body, keyed by the names the
 * specification uses. Aliases are accepted because banks rename fields between
 * revisions, and a renamed field degrades to an empty value.
 *
 * @return array|null null when the body is not XML this endpoint can read
 */
function ncba_parse(string $xml): ?array
{
    $fields = ncba_xml_fields($xml);
    if ($fields === null) {
        return null;
    }
    $aliases = [
        'User' => ['User', 'Username', 'UserName'],
        'Password' => ['Password', 'Passwd'],
        'HashVal' => ['HashVal', 'Hash'],
        'TransType' => ['TransType', 'TransactionType', 'TranType'],
        'TransID' => ['TransID', 'TransId', 'TransactionID', 'TransactionId',
            'TransReference', 'TransactionReference'],
        'TransTime' => ['TransTime', 'TransactionTime', 'TransDate'],
        'TransAmount' => ['TransAmount', 'Amount', 'TransAmt'],
        'AccountNr' => ['AccountNr', 'AccountNumber', 'AccountNo'],
        'Narrative' => ['Narrative', 'Narration', 'Description', 'Details'],
        'PhoneNr' => ['PhoneNr', 'PhoneNumber', 'MobileNo', 'MSISDN'],
        'CustomerName' => ['CustomerName', 'PayerName', 'SenderName', 'Name'],
        'Status' => ['Status', 'TransactionStatus'],
        'FtCrNarration' => ['FtCrNarration', 'CrNarration'],
    ];
    $notification = [];
    foreach ($aliases as $field => $names) {
        $notification[$field] = ncba_first($fields, $names);
    }
    return $notification;
}

/**
 * Reads the leaf elements of the notification into a flat map, the way
 * XmlFields does in the Java service. Namespace prefixes are stripped, so
 * <User> and <ns:User> are the same field. A doctype is refused outright,
 * because a notification is untrusted input.
 */
function ncba_xml_fields(string $xml): ?array
{
    if (trim($xml) === '' || stripos($xml, '<!DOCTYPE') !== false) {
        return null;
    }
    $previous = libxml_use_internal_errors(true);
    $document = new DOMDocument();
    $parsed = $document->loadXML($xml, LIBXML_NONET);
    libxml_clear_errors();
    libxml_use_internal_errors($previous);
    if (!$parsed || $document->documentElement === null) {
        return null;
    }

    $start = null;
    foreach (['NCBAPaymentNotificationRequest', 'NCBAPaymentNotificationResult'] as $wrapper) {
        $start = ncba_find($document->documentElement, $wrapper);
        if ($start !== null) {
            break;
        }
    }
    $fields = [];
    ncba_collect($start ?? $document->documentElement, $fields);
    return $fields;
}

function ncba_local_name(string $nodeName): string
{
    $colon = strrpos($nodeName, ':');
    return $colon === false ? $nodeName : substr($nodeName, $colon + 1);
}

function ncba_find(DOMElement $element, string $wanted): ?DOMElement
{
    if (strcasecmp(ncba_local_name($element->nodeName), $wanted) === 0) {
        return $element;
    }
    foreach ($element->childNodes as $child) {
        if ($child instanceof DOMElement) {
            $found = ncba_find($child, $wanted);
            if ($found !== null) {
                return $found;
            }
        }
    }
    return null;
}

function ncba_collect(DOMElement $element, array &$fields): void
{
    foreach ($element->childNodes as $child) {
        if (!$child instanceof DOMElement) {
            continue;
        }
        $hasElementChildren = false;
        foreach ($child->childNodes as $grandchild) {
            if ($grandchild instanceof DOMElement) {
                $hasElementChildren = true;
                break;
            }
        }
        if ($hasElementChildren) {
            ncba_collect($child, $fields);
            continue;
        }
        $name = ncba_local_name($child->nodeName);
        if (!array_key_exists($name, $fields)) {
            $fields[$name] = preg_replace('/\s+/u', ' ', trim($child->textContent));
        }
    }
}

/** First non blank value under any of the names, compared without case. */
function ncba_first(array $fields, array $names): string
{
    foreach ($names as $name) {
        foreach ($fields as $key => $value) {
            if (strcasecmp((string) $key, $name) === 0 && trim($value) !== '') {
                return $value;
            }
        }
    }
    return '';
}

// ----------------------------------------------------------- verification

/**
 * The specification defines the check in two parts. The User and Password
 * elements must be the values given to NCBA, and HashVal must match the hash of
 * the secret key and nine notification fields. Verification fails closed: with
 * verification on and no secret key, every notification is refused.
 *
 * @return array{0: bool, 1: bool|null, 2: string} accepted, signature valid, reason
 */
function ncba_verify(?array $notification, array $config): array
{
    if ($notification === null) {
        return [false, null, 'The body is not XML the service can read'];
    }
    if ($config['username'] !== '' && !hash_equals($config['username'], $notification['User'])) {
        return [false, null, 'The User element does not match the configured username'];
    }
    if ($config['password'] !== '' && !hash_equals($config['password'], $notification['Password'])) {
        return [false, null, 'The Password element does not match the configured password'];
    }
    if (!$config['signature_verification']) {
        return [true, null, ''];
    }
    if ($config['secret_key'] === '') {
        return [false, null, 'Signature verification is enabled but no secret key is configured'];
    }
    if ($notification['HashVal'] === '') {
        return [false, false, 'The HashVal element is missing'];
    }

    $expected = ncba_hash($config['secret_key'], $notification);
    $provided = preg_replace('/\s+/', '', $notification['HashVal']);
    if (!hash_equals($expected, $provided)) {
        ncba_log('NCBA notification rejected: HashVal did not verify for ' . $notification['TransID']);
        return [false, false, 'HashVal did not match the values sent'];
    }
    return [true, true, ''];
}

/**
 * SHA-256 of the secret key followed by nine fields in a fixed order, written
 * as lowercase hex and then base64 encoded. The double encoding is what all
 * three NCBA code samples do, so it is reproduced exactly.
 */
function ncba_hash(string $secretKey, array $notification): string
{
    $concatenated = $secretKey
        . $notification['TransType']
        . $notification['TransID']
        . $notification['TransTime']
        . $notification['TransAmount']
        . $notification['AccountNr']
        . $notification['Narrative']
        . $notification['PhoneNr']
        . $notification['CustomerName']
        . $notification['Status'];
    return base64_encode(hash('sha256', $concatenated));
}

// ---------------------------------------------------------------- storage

function ncba_store(PDO $db, array $notification, string $externalId, ?bool $signatureValid, string $raw): void
{
    $amount = ncba_amount($notification['TransAmount']);
    $narration = $notification['Narrative'] !== '' ? $notification['Narrative'] : $notification['FtCrNarration'];

    $statement = $db->prepare(
        'INSERT INTO ncba_notifications
            (trans_id, trans_type, trans_time, booked_at, amount, direction, amount_text,
             account_nr, narrative, phone_nr, customer_name, status,
             signature_valid, raw_body, remote_addr, received_at)
         VALUES
            (:trans_id, :trans_type, :trans_time, :booked_at, :amount, :direction, :amount_text,
             :account_nr, :narrative, :phone_nr, :customer_name, :status,
             :signature_valid, :raw_body, :remote_addr, :received_at)'
    );
    $statement->execute([
        ':trans_id' => $externalId,
        ':trans_type' => $notification['TransType'],
        ':trans_time' => $notification['TransTime'],
        ':booked_at' => ncba_booked_at($notification['TransTime']),
        ':amount' => $amount === null ? null : ltrim($amount, '-'),
        ':direction' => $amount === null ? null : ($amount[0] === '-' ? 'Debit' : 'Credit'),
        ':amount_text' => $notification['TransAmount'],
        ':account_nr' => $notification['AccountNr'],
        ':narrative' => $narration === '' ? null : $narration,
        ':phone_nr' => $notification['PhoneNr'],
        ':customer_name' => $notification['CustomerName'],
        ':status' => $notification['Status'],
        ':signature_valid' => $signatureValid === null ? null : ($signatureValid ? 1 : 0),
        ':raw_body' => ncba_redact($raw),
        ':remote_addr' => $_SERVER['REMOTE_ADDR'] ?? null,
        ':received_at' => gmdate('Y-m-d H:i:s'),
    ]);
}

/** The signed amount as a plain decimal string, or null when it is not a number. */
function ncba_amount(string $text): ?string
{
    $cleaned = str_replace(',', '', trim($text));
    if ($cleaned === '' || !preg_match('/^[+-]?\d+(\.\d+)?$/', $cleaned)) {
        return null;
    }
    return ltrim($cleaned, '+');
}

/** TransTime is YYMMDDhhmm with no zone, read as East Africa Time and stored as UTC. */
function ncba_booked_at(string $value): ?string
{
    $value = trim($value);
    if ($value === '') {
        return null;
    }
    $utc = new DateTimeZone('UTC');
    try {
        if (preg_match('/(Z|[+-]\d{2}:?\d{2})$/', $value)) {
            return (new DateTimeImmutable($value))->setTimezone($utc)->format('Y-m-d H:i:s');
        }
    } catch (Exception $ignored) {
        // Not an ISO instant. Try the bank layouts below.
    }
    $eat = new DateTimeZone('+03:00');
    foreach (['ymdHi', 'ymdHis', 'YmdHi', 'YmdHis', 'Y-m-d H:i:s'] as $format) {
        $parsed = DateTimeImmutable::createFromFormat('!' . $format, $value, $eat);
        if ($parsed !== false && $parsed->format($format) === $value) {
            return $parsed->setTimezone($utc)->format('Y-m-d H:i:s');
        }
    }
    return null;
}

/** The stored copy of the body never keeps the password NCBA sends. */
function ncba_redact(string $raw): string
{
    return preg_replace(
        '#(<(?:\w+:)?(?:Password|Passwd)>)[^<]*(</(?:\w+:)?(?:Password|Passwd)>)#i',
        '$1[redacted]$2',
        $raw
    ) ?? '';
}

/** Short description for a log line. Never includes the password or the hash. */
function ncba_describe(array $notification): string
{
    return 'reference=' . $notification['TransID']
        . ' type=' . $notification['TransType']
        . ' amount=' . $notification['TransAmount']
        . ' account=' . $notification['AccountNr']
        . ' status=' . $notification['Status'];
}

function ncba_log(string $message): void
{
    error_log('[ncba-webhook] ' . $message);
}
