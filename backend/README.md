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

KCB instant payment notifications carry no credit or debit field. They are sent only after an account
is credited, so they are recorded as credits. The sender's name and mobile number, and the payment
time, come from `customerName`, `customerMobileNumber` and `timestamp`.

A payment reaches a customer only when its account is linked to them in the admin portal, by bank
and account number (KCB names the account in `creditAccountIdentifier`).

### Webhook base address

Every webhook URL shown in the admin portal is the base address followed by `/api/v1/webhooks/<bank>`.
It starts as `PUBLIC_BASE_URL`, and an address saved from Settings, then Bank Integration Settings, is
stored in `platform_setting` and applied at every start.

### One account number per user

accounts-service refuses a bank and account number already held by another user, whether it was
entered by the customer (stored as a fingerprint) or linked by an admin (stored as the number).
`AccountService.findHolder` checks both forms, including fingerprints saved before 5 October 2026.
