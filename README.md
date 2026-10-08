# SM-Intelligence

Angular customer frontend and Spring Boot services for financial monitoring.

The customer flow is landing → registration/login → mandatory account onboarding → dashboard. Users must save at least one real manual account snapshot before entering the workspace. Live bank syncing remains a future integration. The frontend does not generate sample users, accounts or financial activity.

Invoice capture is also available: upload a photo/PDF, review extracted details and save an invoice-backed pending transaction. These records and original files persist in the transactions database, independently of bank balances. Start the transactions service as described in [backend setup](backend/README.md#invoices-and-pending-activity). See [frontend invoice capture](web/README.md#invoice-capture) for mobile Wi-Fi testing.

## Run locally

**First time:** run `.\backend\new-local-env.ps1` from the repository root. It creates every service's
`.env.local` (ignored by git) from its `.env.example`, and gives the two pairs of services that must share a
secret the same value: `JWT_SECRET` (identity-service and bank-integration-service) and
`INTERNAL_SERVICE_TOKEN` (accounts-service and bank-integration-service). Then add your own sandbox bank keys
and `ADMIN_EMAILS`. Never use or share production values.

**Quickest way:** from the repository root run `.\start-local.ps1`. It opens one window per service in the
order sign-in needs them (identity, accounts, transactions, api-gateway, bank-integration, web, admin), finds
Java 25 by itself, loads each service's `.env.local`, skips anything already running and waits for each service
to be healthy. Stop everything with `.\start-local.ps1 -Stop`. If sign-in says "Unable to sign in ... the account
service may be unavailable" or "Could not reach the identity service", the api-gateway (8080) is not running.

To start a service by hand instead: Each Java service needs JDK 25 and is started from the repository root, one terminal each. Maven does not
read `.env.local`, so load the service's file first:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
Get-Content backend/<service>/.env.local | Where-Object { $_ -match '^\s*[A-Za-z_]+\s*=' } | ForEach-Object { $k, $v = $_ -split '=', 2; Set-Item "Env:$($k.Trim())" $v.Trim() }
./backend/mvn.ps1 -pl <service> spring-boot:run
```

| Service | Port | Needed for |
| --- | --- | --- |
| PostgreSQL | 5432 | `smi_identity`, `smi_accounts`, `smi_transactions` (with Docker: 5432, 5433, 5434) |
| identity-service | 8081 | sign in, users, password reset, organisations |
| api-gateway | 8080 | everything the browsers and the mobile app call |
| accounts-service | 8082 | accounts and balances |
| transactions-service | 8083 | transactions and the activity feed |
| bank-integration-service | 8090 | bank webhooks, admin bank screens. Start it with `.\backend\bank-integration-service\run-local.ps1` |
| web | 4200 | `npm start` in `web/` |
| admin-interface | 4300 | `npm start -- --port 4300` in `admin-interface/` |

If bank payments arrive but balances do not move, `INTERNAL_SERVICE_TOKEN` differs between accounts-service and
bank-integration-service. If the admin bank screens say the service refused your sign-in, `JWT_SECRET` differs
between identity-service and bank-integration-service. Running the script again reports either mismatch.

Start bank-integration-service as shown: its H2 database is a folder relative to where it starts, and the
script uses `backend/bank-integration-service/data`.

## Features at a glance

- **Customer app:** sign up, sign in, "Forgot password?" by email (SMTP settings in
  [backend setup](backend/README.md#forgot-password-emails)), account onboarding, removing an account
  (it is unlinked from the bank first, and the number can be added again), dashboard, invoices.
- **One account number, one user:** a bank account number can be registered by only one user, whether
  the customer enters it or an admin links it.
- **Admin portal:** real sign in for `ADMIN_EMAILS` accounts; suspend, restore, edit and send a
  password reset to any user; create organisations and invite members (they get a set-password link);
  bank integrations. The bank service's admin API only accepts admin tokens (shared `JWT_SECRET`).
- **Bank notifications in production:** NCBA and KCB post to PHP receivers on cPanel
  (`https://sm-intelligence.globalsmartspaces.com`), and bank-integration-service imports them every
  minute. See [cPanel deployment](admin-interface/backend/deploy/cpanel/README.md).

## Project layout

- `web/src/app/`: Angular UI with authentication, account onboarding, persisted account snapshots and invoice capture. Other finance modules show a not-connected state.
- `web/tools/auth-server.mjs`: loopback development adapter for the identity API. It keeps JWTs server-side and issues an HttpOnly session cookie.
- `backend/`: Maven reactor with gateway and nine domain services. Runtime schemas are in each service's Flyway migration directory; see [schema map](erd.md).
- `bruno-collections/`: direct backend API examples.

Account endpoints verify the signed-in owner through identity. The dashboard displays persisted manual account snapshots; bank-ledger transactions remain unconnected. Other domain services remain future integration work. Financial monitoring does not execute bank payments or purchases.
