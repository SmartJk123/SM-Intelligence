# SM-Intelligence

Angular customer frontend and Spring Boot services for financial monitoring.

The current connected workflow is registration, login, session restoration, profile display and logout. Financial service integration is the next team milestone. No sample users, temporary accounts or financial records are created by the frontend.

## Run locally

1. Start PostgreSQL for identity: `docker compose -f backend/docker-compose.yml up -d postgres-identity`.
2. In a PowerShell terminal, set `JWT_SECRET` to a private random value of at least 32 bytes, then run `./backend/mvn.ps1 -pl identity-service spring-boot:run`.
3. In another terminal, run `./backend/mvn.ps1 -pl api-gateway spring-boot:run`.
4. In `web/`, run `npm ci` and `npm start`, then open http://localhost:4200.

See [backend setup](backend/README.md) for Java/Maven requirements and [frontend setup](web/README.md) for API configuration. Environment variables must be set in the terminal running each service; the root `.env.example` is a reference, not an automatically loaded file.

## Project layout

- `web/src/app/`: Angular UI. Finance page designs remain available for the next integration step, but active finance routes show a not-connected state.
- `web/tools/auth-server.mjs`: loopback development adapter for the identity API. It keeps JWTs server-side and issues an HttpOnly session cookie.
- `backend/`: Maven reactor with gateway and nine domain services. Runtime schemas are in each service's Flyway migration directory; see [schema map](erd.md).
- `bruno-collections/`: direct backend API examples.

The account and transaction endpoints are not exposed through the gateway or frontend in this milestone. They still need authenticated ownership enforcement before customer use. Other domain services remain future integration work. Financial monitoring does not execute bank payments or purchases.
