<?php
/**
 * KCB IPN endpoint settings for cPanel.
 *
 * Copy this file to kcb-config.php in the smi-private folder in the cPanel home
 * directory, next to public_html and never inside it:
 *
 *     /home/<cpanel-user>/smi-private/kcb-config.php
 *
 * Fill it in on the server with cPanel File Manager and set its permissions to
 * 600. kcb-config.php is ignored by git, and its values must never be
 * committed, pasted into a chat or emailed.
 */

return [
    // KCB's PRODUCTION public key, from BUNI (buni@kcbgroup.com), as the PEM
    // block including the BEGIN and END lines. The sandbox key will not verify
    // production notifications.
    'public_key' => <<<PEM
-----BEGIN PUBLIC KEY-----
-----END PUBLIC KEY-----
PEM,

    // Leave on. Off stores notifications without checking the signature.
    'signature_verification' => true,

    // The header KCB signs with, per the IPN specification.
    'signature_header' => 'Signature',

    // Lets the SmartMoney bank integration service import stored notifications
    // from /api/v1/webhooks/kcb/export. At least 32 random characters, and the
    // same value as KCB_CPANEL_EXPORT_TOKEN on that service. Empty disables it.
    'export_token' => '',

    // The same MySQL database and user as ncba-config.php can be used.
    'db_dsn' => 'mysql:host=localhost;dbname=PREFIX_smi;charset=utf8mb4',
    'db_user' => 'PREFIX_smi_ncba',
    'db_password' => '',
];
