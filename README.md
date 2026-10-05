# SM-Intelligence

Angular customer frontend and Spring Boot services for financial monitoring.

The customer flow is landing → registration/login → mandatory account onboarding → dashboard. Users must save at least one real manual account snapshot before entering the workspace. Live bank syncing remains a future integration. The frontend does not generate sample users, accounts or financial activity.

Invoice capture is also available: upload a photo/PDF, review extracted details and save an invoice-backed pending transaction. These records and original files persist in the transactions database, independently of bank balances. Start the transactions service as described in [backend setup](backend/README.md#invoices-and-pending-activity). See [frontend invoice capture](web/README.md#invoice-capture) for mobile Wi-Fi testing.

## Run locally

Every Java service needs JDK 25 and is started from the repository root, one terminal each. Maven does
not read `.env.local` files, so load a service's file into the terminal first (see
[backend setup](backend/README.md)).

| Service | Port | Start | Needed for |
| --- | --- | --- | --- |
| PostgreSQL | 5432 | local PostgreSQL service with `smi_identity`, `smi_accounts`, `smi_transactions` | everything |
| identity-service | 8081 | `./backend/mvn.ps1 -pl identity-service spring-boot:run` | sign in, users, password reset |
| api-gateway | 8080 | `./backend/mvn.ps1 -pl api-gateway spring-boot:run` | everything the browsers call |
| accounts-service | 8082 | `$env:DB_URL = 'jdbc:postgresql://localhost:5432/smi_accounts'` then `./backend/mvn.ps1 -pl accounts-service spring-boot:run` | customer accounts and balances |
| transactions-service | 8083 | `$env:DB_URL = 'jdbc:postgresql://localhost:5432/smi_transactions'` then `./backend/mvn.ps1 -pl transactions-service spring-boot:run` | customer transactions |
| bank-integration-service | 8090 | `./backend/mvn.ps1 -pl bank-integration-service spring-boot:run` | bank webhooks, admin bank screens |
| web | 4200 | `npm start` in `web/` | customer app |
| admin-interface | 4300 | `npm start -- --port 4300` in `admin-interface/` | admin portal |

Start bank-integration-service exactly as shown. Its H2 database is a folder relative to where it starts,
and this command uses `backend/bank-integration-service/data`, which holds the real data. Starting it
from anywhere else opens an empty or older database, and transactions appear to vanish.

With Docker (`backend/docker-compose.yml`) the accounts and transactions databases are on 5433 and 5434 instead, and the `DB_URL` lines are not needed.

Environment variables must be set in the terminal running each service; the root `.env.example` is a
reference, not an automatically loaded file.

## Features at a glance

- **Customer app:** sign up, sign in, "Forgot password?" by email (SMTP settings in
  [backend setup](backend/README.md#forgot-password-emails)), account onboarding, dashboard, invoices.
- **One account number, one user:** a bank account number can be registered by only one user, whether
  the customer enters it or an admin links it.
- **Admin portal:** real sign in for `ADMIN_EMAILS` accounts, user management, bank integrations.
- **Bank notifications in production:** NCBA and KCB post to PHP receivers on cPanel
  (`https://sm-intelligence.globalsmartspaces.com`), and bank-integration-service imports them every
  minute. See [cPanel deployment](admin-interface/backend/deploy/cpanel/README.md).

## Project layout

- `web/src/app/`: Angular UI with authentication, account onboarding, persisted account snapshots and invoice capture. Other finance modules show a not-connected state.
- `web/tools/auth-server.mjs`: loopback development adapter for the identity API. It keeps JWTs server-side and issues an HttpOnly session cookie.
- `backend/`: Maven reactor with gateway and nine domain services. Runtime schemas are in each service's Flyway migration directory; see [schema map](erd.md).
- `bruno-collections/`: direct backend API examples.

Account endpoints verify the signed-in owner through identity. The dashboard displays persisted manual account snapshots; bank-ledger transactions remain unconnected. Other domain services remain future integration work. Financial monitoring does not execute bank payments or purchases.
