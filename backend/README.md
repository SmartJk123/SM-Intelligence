# Backend development

Requirements: JDK 25. Maven is provided by the official Apache wrapper (Maven 3.9.11, wrapper 3.3.4); no global Maven installation is required.

On this machine, a checksum-verified Temurin JDK 25 is installed under the ignored `../.tools/jdk25/` directory. `mvn.ps1` uses it when JAVA_HOME is unset. Other developers can install JDK 25 and set JAVA_HOME.

## Build and test

From the repository root on Windows:

```powershell
./backend/mvn.ps1 --version
./backend/mvn.ps1 -B -ntp test
```

On Linux/macOS, set JAVA_HOME to JDK 25, then run `sh backend/mvnw -f backend/pom.xml test`. Wrapper downloads require network access on the first invocation. The test profiles use isolated H2 databases with Flyway disabled; they do not validate PostgreSQL migrations.

## Run registration/login against PostgreSQL

Start the identity database:

```powershell
docker compose -f backend/docker-compose.yml up -d postgres-identity
```

In the identity terminal, generate a private local signing key and start the service:

```powershell
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:DB_URL = 'jdbc:postgresql://localhost:55432/smi_identity'
./backend/mvn.ps1 -pl identity-service spring-boot:run
```

Identity listens on 8081 and runs its Flyway migration against PostgreSQL. Keep the signing key stable across restarts if existing tokens should remain valid; do not commit it. Production startup now requires JWT_SECRET rather than falling back to a public source-code secret.

In a second terminal:

```powershell
./backend/mvn.ps1 -pl api-gateway spring-boot:run
```

The gateway listens on 8080 and forwards /api/auth/**, /api/v1/admin/users/** (identity), /api/accounts/** (accounts-service) and /api/transactions/** (transactions-service). Its configuration uses the Spring Cloud Gateway Server Web MVC property namespace.

`GET /api/v1/admin/users` on identity-service lists every active user (no password hash) — this is what makes a signup on `web/` show up in `admin-interface`'s Organisations/Users pages. It has no authentication yet (see the TODO comment next to it in `SecurityConfig.java`) because admin-interface has no real admin login against this backend yet; it only reads data, but lock it down before this is public.

accounts-service and transactions-service have no Spring Security configuration at all today — anyone who can reach them directly (bypassing api-gateway/the web adapter) can read or write any `userId`'s accounts and transactions. This predates any particular feature but matters a lot once real money is involved (see `web/API-CONTRACT.md`'s `/api/dashboard` section) — prioritize closing it before production.

Run `npm start` in `web/` and open http://localhost:4200. Registered users are stored in PostgreSQL; sessions in the local adapter end when it restarts.

Environment overrides: identity accepts DB_URL, DB_USER, DB_PASSWORD, PORT, JWT_SECRET and JWT_EXPIRATION_MS. Gateway accepts PORT, IDENTITY_SERVICE_URL and APP_CORS_ALLOWED_ORIGINS (default `http://localhost:4200,http://localhost:4300`, so run admin-interface with `npm start -- --port 4300`). Set values separately in the shell for each process; .env files are not automatically imported by Maven. To load one into the current PowerShell session before `spring-boot:run`:

```powershell
Get-Content backend/identity-service/.env.local | Where-Object { $_ -match '^\s*[A-Za-z_]+\s*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "Env:$($k.Trim())" $v.Trim() }
```

### Organisations and invitations

Admins create organisations and invite members from the admin portal (`/api/organizations`, admin
only, routed by api-gateway). A new member's account gets a random password nobody is told, and the
invitation email carries a one-time link to choose their own, valid for 7 days. An existing account is
added and told by email. The emails use the same SMTP settings as password reset, and
`FRONTEND_LOGIN_URL` (default `http://localhost:4200/login`) is the sign-in page they name. If mail is
not set up, the organisation and account are still saved and the portal reports that no email went out.

Admins can also send any user a password reset email from Users, then "Send password reset email".

If email is down, `backend/identity-service/set-password.ps1 -Email <address>` sets a local
account's password directly (needs PHP and psql).

### Forgot password emails

The web sign-in page links to `/forgot-password`. identity-service then emails a one-time link to `/reset-password?token=...` that expires after 60 minutes. Only a SHA-256 hash of each token is stored (`password_reset_tokens`, migration V4). The request always answers the same way, so it never reveals whether an address has an account.

Sending needs SMTP settings in `backend/identity-service/.env.local`. Without SMTP_HOST the service still starts, but it logs a warning and sends nothing.

| Variable | Example |
| --- | --- |
| SMTP_HOST | `smtp.gmail.com` |
| SMTP_PORT | `587` (STARTTLS) or `465` (TLS) |
| SMTP_USERNAME | the sending mailbox |
| SMTP_PASSWORD | for Gmail, an app password (requires 2-Step Verification), not the account password |
| MAIL_FROM | optional; defaults to SMTP_USERNAME |
| PASSWORD_RESET_URL | optional; defaults to `http://localhost:4200/reset-password` |

Admin accounts use the same identity, so an administrator resets their password the same way from the web app.

## Invoices and pending activity

With identity and the gateway running, open an additional PowerShell terminal at the repository root:

```powershell
docker compose -f backend/docker-compose.yml up -d postgres-transactions
$env:DB_URL = 'jdbc:postgresql://localhost:5434/smi_transactions'
./backend/mvn.ps1 -pl transactions-service spring-boot:run
```

Restart the gateway to load its `/api/invoices` route. Restart `npm start` after installing the updated frontend dependencies with `npm ci`.

Invoices are persisted in the transactions database by Flyway migration V2, including the original document (maximum 10 MB). Each request validates the bearer token against identity `/api/auth/me`; owner IDs never come from the browser. Duplicate document bytes are rejected per owner. Lists return 50 records per page. Supported files are JPEG, PNG and PDF; the server checks file signatures rather than trusting MIME headers.

Invoice-backed pending activity lives in `invoices`, separate from posted bank ledger entries. It has no bank account assignment and does not affect balances. Both customer Invoices and Transactions show these pending records. Payment, reconciliation, cancellation and merging with bank activity are future work; uploading an invoice does not execute or record payment. Existing account-based transaction APIs are unchanged.

## Bank integration service

bank-integration-service (port 8090) receives bank notifications, normalises them, and delivers each
movement on a linked account to that customer's accounts-service and transactions-service records. The
admin portal's bank screens read from it.

Start it from the repository root, after loading its settings:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
Get-Content backend/bank-integration-service/.env.local | Where-Object { $_ -match '^\s*[A-Za-z_]+\s*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "Env:$($k.Trim())" $v.Trim() }
./backend/mvn.ps1 -pl bank-integration-service spring-boot:run
```

Or run `backend/bank-integration-service/run-local.ps1`, which loads `.env.local`, refuses to start a
second copy, picks JDK 25 and uses the Maven wrapper. `stop-local.ps1` stops it, `set-credentials.ps1`
fills in bank credentials without echoing them, and `tools/` has `send-kcb-notification.ps1` and
`send-ncba-notification.ps1` (signed test payments), `new-ncba-letter.ps1` (the NCBA request letter),
`reset-data.ps1` and `demo-account-flow.ps1`.

Its H2 database is `./data/smartmoney`, relative to the folder the service starts in. `spring-boot:run`
starts in the module folder, so the data is `backend/bank-integration-service/data`. An older copy in
`admin-interface/backend/data` is not used.

### Production notifications through cPanel

The production host cannot run Java, so each bank posts to a PHP receiver on cPanel
(`admin-interface/backend/deploy/cpanel`). The receiver verifies the notification, stores it in MySQL,
and offers a token protected export that this service imports every minute.

| Bank | Notification URL | Import settings in `.env.local` |
| --- | --- | --- |
| NCBA | `https://globalsmartspaces.com/api/v1/webhooks/ncba` (registered with NCBA) | `NCBA_CPANEL_EXPORT_URL`, `NCBA_CPANEL_EXPORT_TOKEN` |
| KCB | `https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/kcb` | `KCB_CPANEL_EXPORT_URL`, `KCB_CPANEL_EXPORT_TOKEN` |
| Equity | `https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/equity` | `EQUITY_CPANEL_EXPORT_URL`, `EQUITY_CPANEL_EXPORT_TOKEN` |

KCB instant payment notifications carry no credit or debit field. They are sent only after an account
is credited, so they are recorded as credits. The sender's name and mobile number, and the payment
time, come from `customerName`, `customerMobileNumber` and `timestamp`.

A payment reaches a customer only when its account is linked to them in the admin portal, by bank
and account number (KCB names the account in `creditAccountIdentifier`).

### Equity (Jenga)

The Equity connector authenticates with Jenga (`POST .../authentication/api/v3/authenticate/merchant`,
`Api-Key` header, merchant code and consumer secret) to prove the credentials, and receives Instant Payment
Notifications protected with Basic Auth.

| Variable | Meaning |
| --- | --- |
| `EQUITY_ENV` | `SANDBOX` (Jenga UAT, `uat.finserve.africa`) or `PRODUCTION` (`api.finserve.africa`) |
| `EQUITY_MERCHANT_CODE`, `EQUITY_API_KEY`, `EQUITY_CONSUMER_SECRET` | from Jenga HQ |
| `EQUITY_IPN_USERNAME`, `EQUITY_IPN_PASSWORD` | the Basic Auth pair registered with the IPN callback |
| `EQUITY_ACCOUNT_NUMBER` | the Equity account payments belong to when a notification names none |
| `EQUITY_TOKEN_URL` | optional override of the token endpoint |

Failed payments (`transaction.status` FAILED) are recorded with their reason but never credited.

### Bank health (actuator)

`GET /actuator/health/banks` has one indicator per bank (`kcb`, `ncba`, `stanbic`, `equity`), and
`/actuator/health/banks/<bank>` one bank. Details (environment, connection and notification state, token,
today's notifications, failures in the last 24 hours, last notification and last test times) are shown
only with an admin token. Anyone else sees the overall status, and the bank breakdown answers 404.

| Status | Meaning |
| --- | --- |
| `UP` | the last connection test passed, or notifications are arriving |
| `DEGRADED` | the last connection test failed, or no notification for 7 days. The service answers 200, so monitors alert rather than restart it |
| `UNKNOWN` | not tested and nothing received yet |

The indicators never call a bank, so a health probe cannot use up a bank's rate limit. Refresh them with
the admin's "Test connection".

### Webhook base address

Every webhook URL shown in the admin portal is the base address followed by `/api/v1/webhooks/<bank>`.
It starts as `PUBLIC_BASE_URL`, and an address saved from Settings, then Bank Integration Settings, is
stored in `platform_setting` and applied at every start.

### Admin API security

Everything under `/api/v1/admin` needs a token from identity-service with the `PLATFORM_ADMIN` role. The
bank service checks it with the same `JWT_SECRET` identity-service signs with, so put that value in
`backend/bank-integration-service/.env.local` too. Without it every admin call is refused, and the admin
portal says so. Bank webhooks, the info page and the health check stay open.

The one exception: a signed-in customer may `POST /api/v1/admin/account-links` for an account
accounts-service already holds for them at that bank and number (the web app does this after
onboarding). Any other customer request is refused.

`PERMIT_ALL=true` opens everything, for tests and isolated debugging only. It is off by default.

### One account number per user

accounts-service refuses a bank and account number already held by another user, whether it was
entered by the customer (stored as a fingerprint) or linked by an admin (stored as the number).
`AccountService.findHolder` checks both forms, including fingerprints saved before 5 October 2026.

## One money flow for every app

Every app reads the same money through the gateway (`http://localhost:8080` locally), so a payment that shows
on the admin portal also shows on the web dashboard and in the mobile app.

| Call (Bearer token from sign-in) | What it gives |
| --- | --- |
| `GET /api/accounts` | The user's accounts and balances |
| `GET /api/transactions/activity` | All money in and out across the user's accounts, newest first, with bank, account, sender, amount and dates |
| `GET /api/transactions/activity?since=<receivedAt>` | Only what arrived after that moment. Poll this for notifications (the web app polls every few seconds) |
| `GET /api/transactions?accountId=<id>` | One account's transactions |
| `POST /api/v1/admin/account-links` | Link a saved account so its bank's payments reach the user (the bank service accepts a customer only for their own account) |
| `DELETE /api/v1/admin/account-links/by-account/<id>` | Unlink before removing an account |

`/api/transactions/activity` asks accounts-service for the user's accounts with the user's own token, so it
can only ever return that user's money. A movement's `direction` is `CREDIT` (money in) or `DEBIT` (money out),
and `receivedAt` is when it reached SmartMoney.

**Seeing the same data as someone else.** Each machine that runs the backend has its own databases, so a
teammate's local run starts empty. To see the same accounts and payments, point the app at the same running
backend (the shared server's gateway URL) instead of running a separate copy. To produce payments locally,
use the demo account (`1000000001`) from the admin portal.

notifications-service has no code yet. Notifications come from the activity feed above.

## Deploying on Render (one shared system)

`render.yaml` at the repository root deploys everything as one system. Only **api-gateway** is public;
every other service is a private service that only the gateway and the other services can reach. All apps
use the gateway's URL, so signing in as the same user shows the same accounts and transactions on the web
app, the admin portal and the mobile app.

| Piece | How it is set up |
| --- | --- |
| Database | The team's **Supabase** PostgreSQL (`smi-database` group). Use the **session pooler, port 5432**; the transaction pooler (6543) breaks Hibernate's and Flyway's prepared statements. Each service keeps its own schema (`DB_SCHEMA`: identity, accounts, transactions, budgets), created on first start; bank-integration-service keeps `public`, where its existing data is |
| Service addresses | Each service's private `host:port` from Render (`*_SERVICE_HOSTPORT`); locally the old `*_SERVICE_URL` defaults still apply |
| Shared secrets | `JWT_SECRET` and `INTERNAL_SERVICE_TOKEN` are generated once in the `smi-shared` environment group, so the services that must agree always do |
| Other secrets | Marked `sync: false`: enter them in the Render dashboard (bank keys, export tokens, SMTP, `ADMIN_EMAILS`, `GEMINI_API_KEY`) |
| Bank data | bank-integration-service runs with `SPRING_PROFILES_ACTIVE=postgres`, so nothing is lost on redeploy |
| Live bank payments | Still arrive on cPanel; the bank service on Render imports them from the cPanel export feeds |

**Deploying:**
1. Render dashboard → New → Blueprint → this repository. Render reads `render.yaml`. The existing web service
   `bank-integration-service` (https://bank-integration-service.onrender.com) is adopted by name. Remove any
   `DB_URL` set on it by hand (it would override the blueprint and keep port 6543). Private services need a paid
   Render instance type.
2. Fill in every value it asks for (the `sync: false` ones): `DB_USER` is the Supabase pooler user
   (`postgres.<project ref>`), `DB_PASSWORD` the database password. Use the same export tokens as in cPanel's
   `smi-private/*-config.php`.
3. Set `APP_CORS_ALLOWED_ORIGINS` on api-gateway to the deployed web app and admin portal addresses.
4. Point each app at the gateway URL:
   - **Admin portal:** `gatewayUrl` in `admin-interface/src/environments/environment.production.ts`, then `npm run build`.
   - **Web app:** run its server (`web/tools`) with `IDENTITY_API_URL`, `ACCOUNTS_API_URL`, `INVOICE_API_URL` and
     `BANK_INTEGRATION_API_URL` set to the gateway URL. Deploy it on Render as well so `BUDGETS_API_URL` can use
     budgets-service's private address (budgets-service trusts the user id it is given, so it is not public).
   - **Mobile app:** `API_BASE_URL=<gateway URL>` in `mobile/smartmoney/local.properties`, then rebuild.
   - **Bruno:** the `render` environment; set its `baseUrl` to the gateway URL.
5. Move existing data once: dump each local database (`smi_identity`, `smi_accounts`, `smi_transactions`) and
   restore it into the matching schema in Supabase (identity, accounts, transactions).

**Checked locally on 2026-10-08:** identity-service, accounts-service and bank-integration-service started in
one database with separate schemas, created their schemas and tables, and reported healthy.

**Known limits:** `GET /api/transactions?accountId=` has no sign-in check yet (prefer
`/api/transactions/activity`, which has). Some mobile calls use admin-only endpoints (listing account links,
deleting a link by id, the bank-integrations list, the demo) and are refused for customers; customers should use
`/api/transactions/activity` and `DELETE /api/v1/admin/account-links/by-account/{accountId}`.

