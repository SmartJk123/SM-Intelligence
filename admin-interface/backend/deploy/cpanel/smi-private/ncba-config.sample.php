<?php
/**
 * NCBA endpoint settings for cPanel.
 *
 * Copy this file to ncba-config.php in a folder named smi-private in the cPanel
 * home directory, next to public_html and never inside it:
 *
 *     /home/<cpanel-user>/smi-private/ncba-config.php
 *
 * Fill it in on the server with cPanel File Manager. ncba-config.php is ignored
 * by git, and its values must never be committed, pasted into a chat or emailed.
 *
 * The secret key, username and password must be exactly the values on the
 * request letter NCBA received. Changing any of them afterwards breaks every
 * notification until NCBA is given the new value.
 */

return [
    // The three values given to NCBA. Same as NCBA_SECRET_KEY, NCBA_USERNAME
    // and NCBA_PASSWORD in the service's .env.local.
    'secret_key' => '',
    'username' => '',
    'password' => '',

    // Leave on. Off stores notifications without checking HashVal.
    'signature_verification' => true,

    // Lets the SmartMoney bank integration service import stored notifications
    // from /api/v1/webhooks/ncba/export. At least 32 random characters, and the
    // same value as NCBA_CPANEL_EXPORT_TOKEN on that service. Empty disables it.
    'export_token' => '',

    // The MySQL database created in cPanel, MySQL Databases, in the SAME cPanel
    // account as public_html. cPanel prefixes both names with that account's
    // prefix (shown next to "New Database"), for example abcd_smi and abcd_smi_ncba.
    'db_dsn' => 'mysql:host=localhost;dbname=PREFIX_smi;charset=utf8mb4',
    'db_user' => 'PREFIX_smi_ncba',
    'db_password' => '',
];
