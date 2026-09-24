# SM-Intelligence

Angular customer frontend and Spring Boot services for financial monitoring.

The customer flow is landing → registration/login → mandatory account onboarding → dashboard. Users must save at least one real manual account snapshot before entering the workspace. Live bank syncing remains a future integration. The frontend does not generate sample users, accounts or financial activity.

Invoice capture is also available: upload a photo/PDF, review extracted details and save an invoice-backed pending transaction. These records and original files persist in the transactions database, independently of bank balances. Start the transactions service as described in [backend setup](backend/README.md#invoices-and-pending-activity). See [frontend invoice capture](web/README.md#invoice-capture) for mobile Wi-Fi testing.

## Run locally

1. Start PostgreSQL for identity and accounts: `docker compose -f backend/docker-compose.yml up -d postgres-identity postgres-accounts`.
2. In a PowerShell terminal, set `JWT_SECRET` to a private random value of at least 32 bytes, then run `./backend/mvn.ps1 -pl identity-service spring-boot:run`.
3. In separate terminals, run `./backend/mvn.ps1 -pl accounts-service spring-boot:run` and `./backend/mvn.ps1 -pl api-gateway spring-boot:run`.
4. In `web/`, run `npm ci` and `npm start`, then open http://localhost:4200.

See [backend setup](backend/README.md) for Java/Maven requirements and [frontend setup](web/README.md) for API configuration. Environment variables must be set in the terminal running each service; the root `.env.example` is a reference, not an automatically loaded file.

## Project layout

- `web/src/app/`: Angular UI with authentication, account onboarding, persisted account snapshots and invoice capture. Other finance modules show a not-connected state.
- `web/tools/auth-server.mjs`: loopback development adapter for the identity API. It keeps JWTs server-side and issues an HttpOnly session cookie.
- `backend/`: Maven reactor with gateway and nine domain services. Runtime schemas are in each service's Flyway migration directory; see [schema map](erd.md).
- `bruno-collections/`: direct backend API examples.

Account endpoints verify the signed-in owner through identity. The dashboard displays persisted manual account snapshots; bank-ledger transactions remain unconnected. Other domain services remain future integration work. Financial monitoring does not execute bank payments or purchases.
