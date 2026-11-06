# NCBA Endpoint on cPanel

The live NCBA notification endpoint, and the address to give NCBA:

```text
https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/ncba
```

The cPanel hosting cannot run the Java bank integration service, so this folder holds a PHP port of
`NcbaWebhookController` and `NcbaSignatureVerifier`. It checks the same things, answers with the same
SOAP envelope, and stores each notification in a MySQL table. The rules it follows are in
[NCBA integration setup](../../../../docs/ncba-integration-setup.md), sections 3 and 4.

## Layout on the server

`globalsmartspaces.com` hosts several modules, so each keeps its files in its own folder under
`public_html`, and its secrets in its own folder outside it. This folder mirrors the server exactly:
upload each file to the same path.

```text
/home/<account>/
├── public_html/                    main site, globalsmartspaces.com
│   ├── .htaccess                   forwards /api/v1/webhooks/ncba into sm-intelligence/ (merge, do not replace)
│   ├── index.html, erp/, ...       the other modules, untouched
│   └── sm-intelligence/            the SmartMoney Intelligence module
│       ├── .htaccess
│       └── ncba-webhook.php
└── smi-private/                    SmartMoney Intelligence secrets, never inside public_html
    └── ncba-config.php             from smi-private/ncba-config.sample.php, filled in on the server
```

Each file in this folder goes to the same path on the server, with three exceptions:

- `public_html/.htaccess`: if the server already has one, paste the block between `BEGIN` and `END`
  at the top of it instead of replacing it.
- `smi-private/ncba-config.sample.php` is renamed to `smi-private/ncba-config.php` and filled in.
- `schema.sql` is not uploaded. Run it once in phpMyAdmin.

The endpoint looks for `smi-private/ncba-config.php` in the folders above its own, so it is found
from `public_html/sm-intelligence` without any setting. When the subdomain
`sm-intelligence.globalsmartspaces.com` resolves (it has no DNS record yet), set its document root
to `public_html/sm-intelligence` in cPanel, Domains, and the same files answer on
`https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/ncba` as well.

Hidden files: `.htaccess` starts with a dot. In File Explorer turn on View, Show, Hidden items; in
cPanel File Manager turn on Settings, Show Hidden Files.

## Steps

1. **Database.** cPanel, MySQL Databases, in the same cPanel account as `public_html` (the prefix
   next to "New Database" is that account's). Create the database `<prefix>_smi` and the user
   `<prefix>_smi_ncba`, add the user to the database with ALL PRIVILEGES.
2. **Table.** phpMyAdmin, select the database, SQL, paste `schema.sql`, Go.
3. **Settings.** In the home directory, next to `public_html`, create `smi-private`. Put
   `ncba-config.sample.php` there as `ncba-config.php` and fill it in: the secret key, username and
   password exactly as on the NCBA request letter (`NCBA_SECRET_KEY`, `NCBA_USERNAME`,
   `NCBA_PASSWORD` in `admin-interface/backend/.env.local`), and the database names and password
   from step 1. Set its permissions to `600`.
4. **Upload.** Create `public_html/sm-intelligence` and upload its two files. Add the block from
   `public_html/.htaccess` to the top of the site's `public_html/.htaccess`.
5. **PHP version.** cPanel, MultiPHP Manager: PHP 7.4 or later with `dom` and `pdo_mysql`, which
   cPanel enables by default.

## Proving it before NCBA sends anything

1. **Address reachable.** Open the endpoint address in a browser. Expect `Status : ready`.
   - `not ready: ...ncba-config.php was not found` means the config is missing, misnamed or not valid
     PHP. Fix `smi-private/ncba-config.php`.
   - `not ready: the MySQL login ... failed` means the database login is wrong. `error_log` in the
     folder of `ncba-webhook.php` names the user it tried. Match the database, user and password from
     step 1.
2. **Credentials and hash.** From `admin-interface\backend`, run
   `.\tools\send-ncba-notification.ps1 -Url https://globalsmartspaces.com/api/v1/webhooks/ncba -TransId SMI-LIVE-TEST-1`.
   Expect `HTTP 200 ... OK`.
3. **Duplicate handling.** Run the same command again. Expect `OK: Duplicate Notification`.
4. **Refusal.** Add `-BreakHash`. Expect `FAIL: HashVal did not match the values sent`.

The rehearsal rows land in the live table. They carry `REHEARSAL` in the stored body, so delete
them in phpMyAdmin before NCBA begins sending:

```sql
DELETE FROM ncba_notifications WHERE raw_body LIKE '%<FtCrNarration>REHEARSAL</FtCrNarration>%';
```

## Reading notifications

Every accepted notification is a row in `ncba_notifications`, visible in phpMyAdmin. The password
NCBA sends is replaced with `[redacted]` before the body is stored. Refused notifications are not
stored, only logged to the PHP error log with the reason.

## Sending notifications on to the customer dashboard

The bank integration service imports this table through a token protected export, so an NCBA
notification reaches the linked customer's web dashboard and the admin interface's NCBA tile.

1. Generate a token of 32 or more random characters, for example in PowerShell:
   `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))`
2. Put it in `export_token` in `smi-private/ncba-config.php` on the server.
3. Give the bank integration service the same value as `NCBA_CPANEL_EXPORT_TOKEN`, and
   `NCBA_CPANEL_EXPORT_URL=https://globalsmartspaces.com/api/v1/webhooks/ncba/export`.

The export answers only a request carrying the token in the `X-SMI-Export-Token` header (cPanel's
Apache commonly strips `Authorization`). With no `export_token` set it does not exist, and answers
404. It returns the stored body, which has the password redacted and nothing else secret.

## Things that would break delivery

- Changing the secret key, username or password in `ncba-config.php` after NCBA has them.
- Removing the SmartMoney block from `public_html/.htaccess`, or another module adding a rule above
  it that rewrites every request to its own index. Check the probe still answers after any change.
- An HTTP to HTTPS redirect added for POST requests. A redirected POST loses its body.
- The database living in a different cPanel account from `public_html`. Each account has its own
  MySQL, and one cannot log in to another's.

## KCB instant payment notifications (IPN)

The address to give KCB (email it to buni@kcbgroup.com for review, as the IPN specification asks):

```text
https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/kcb
```

`kcb-webhook.php` is the KCB counterpart of `ncba-webhook.php`. KCB posts JSON after crediting the
account, with a `Signature` header: a base64 SHA256withRSA signature of the body, checked against
KCB's public key. The endpoint answers with the acknowledgement body the specification fixes:

```json
{"transactionID": "FT00026252", "statusCode": "0", "statusMessage": "Notification received successfully"}
```

An accepted notification gets HTTP 200 with `statusCode` `0`. One already received (same
`transactionReference`) also gets 200 and `0`, with "Duplicate notification received". A missing or
wrong signature gets 401, and a body that is not JSON gets 400, each with the same number as
`statusCode`. If the config or database is not ready, the answer is 503, so KCB can retry.

### Files

- `public_html/sm-intelligence/kcb-webhook.php` goes to the same path on the server.
- `public_html/sm-intelligence/.htaccess` replaces the existing one (it adds the `kcb` rules).
- `smi-private/kcb-config.sample.php` becomes `smi-private/kcb-config.php`, filled in, with
  permissions `600`.
- `schema.sql` is run again in phpMyAdmin. It only creates `kcb_notifications` if it is missing.

### Steps

1. **Public key.** Ask BUNI for the **production** public key that signs IPN notifications. The sandbox key
   will not verify production notifications. Paste the PEM block into `public_key` in `kcb-config.php`.
2. **Database.** Use the same database and user as `ncba-config.php`, and run `schema.sql` again.
3. **Export token.** Generate a new 32+ character token, for example
   `[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))`, and put it
   in `export_token`.
4. **Upload** the files above, then open `https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/kcb` in a browser.
   It should show `Status   : ready`. Any `not ready:` line names the step to fix.
5. **Bank integration service.** In `backend/bank-integration-service/.env.local` set
   `KCB_CPANEL_EXPORT_URL=https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/kcb/export` and
   `KCB_CPANEL_EXPORT_TOKEN` to the same token. It then imports new KCB payments every minute.
6. **Link the account.** In the admin interface, link KCB account `1302360167` to the customer, so its
   payments show on their dashboard. KCB names it in `creditAccountIdentifier`.
7. **Email KCB.** Send the address above to buni@kcbgroup.com for review.

## Equity Bank (Jenga) Instant Payment Notifications

The address to register as the IPN callback on Jenga HQ:

```text
https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/equity
```

`equity-webhook.php` receives Jenga's JSON notifications. Jenga authenticates them with **Basic Auth**:
the username and password you register with the callback URL on Jenga HQ. It sends successful and failed
payments; both are stored, and the bank integration service credits only successful ones. Format and
authentication follow [Jenga's IPN guide](https://developer.jengahq.io/guides/jenga-pgw/instant-payment-notifications).

An accepted notification gets HTTP 200 with `{"status":"received","reference":"..."}`. One already
received (same reference) also gets 200, with `"duplicate":true` added. Missing or wrong Basic Auth
gets 401, and a body that is not JSON gets 400, both with `{"status":"rejected",...}`. If the config
or database is not ready, the answer is 503, so Jenga can retry.

Jenga does not document the reply it expects, so confirm with Equity that a 200 with this body counts as
delivered.

### Steps

1. **Upload** `public_html/sm-intelligence/equity-webhook.php`, and the updated
   `public_html/sm-intelligence/.htaccess` (it adds the `equity` rules and passes the Authorization header
   through to PHP).
2. **Config.** Copy `smi-private/equity-config.sample.php` to `smi-private/equity-config.php`, permissions
   600. Choose a username and a long random password for `ipn_username` and `ipn_password`, generate an
   `export_token` (32+ characters), and copy the database lines from `ncba-config.php`.
3. **Table.** Run `schema.sql` again in phpMyAdmin. It only adds `equity_notifications`.
4. **Check.** Open the address above. It should say `Status   : ready`.
5. **Jenga HQ.** Register the address as the IPN callback with the same username and password.
6. **Bank integration service.** In `backend/bank-integration-service/.env.local` set
   `EQUITY_CPANEL_EXPORT_URL=https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/equity/export`,
   `EQUITY_CPANEL_EXPORT_TOKEN` (same token), and the Jenga credentials listed in the
   [service README](../../../../backend/README.md#equity-jenga), then restart.
7. **Link the account.** Jenga's sample notification has no account number (`bank.account` is null), so
   payments are filed under `EQUITY_ACCOUNT_NUMBER`. Link that Equity account to its customer in admin.
