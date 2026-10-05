<?php
/**
 * Equity (Jenga) IPN endpoint settings for cPanel.
 *
 * Copy this file to equity-config.php in the smi-private folder in the cPanel
 * home directory, next to public_html and never inside it:
 *
 *     /home/<cpanel-user>/smi-private/equity-config.php
 *
 * Fill it in on the server and set its permissions to 600. equity-config.php
 * is ignored by git, and its values must never be committed, pasted into a
 * chat or emailed.
 */

return [
    // The Basic Auth username and password you register with the callback URL
    // on Jenga HQ. Jenga sends them with every Instant Payment Notification.
    'ipn_username' => '',
    'ipn_password' => '',

    // Leave on. Off stores notifications without checking the credentials.
    'signature_verification' => true,

    // Lets the SmartMoney bank integration service import stored notifications
    // from /api/v1/webhooks/equity/export. At least 32 random characters, and
    // the same value as EQUITY_CPANEL_EXPORT_TOKEN on that service.
    'export_token' => '',

    // The same MySQL database and user as ncba-config.php can be used.
    'db_dsn' => 'mysql:host=localhost;dbname=PREFIX_smi;charset=utf8mb4',
    'db_user' => 'PREFIX_smi_ncba',
    'db_password' => '',
];
