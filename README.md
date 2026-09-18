# SM-Intelligence

Financial intelligence platform for individual and business users.

## Web development

The Angular application lives in `web/`. The Figma/React prototype is maintained separately as a design reference.

Requirements: Node.js matching `web/package.json` (`^24.15.0 || >=26.0.0`) and npm 11+. The application uses Angular 22; `web/.mise.toml` pins the development Node version.

```powershell
cd web
npm ci
npm start
```

Open http://localhost:4200. Stop the server with Ctrl+C.

```powershell
npm run build
npm test -- --watch=false
```

## Scope

The Angular frontend includes public pages, registration, onboarding, and the customer workspace. `npm start` uses hosted authentication through a local development adapter; financial records remain in memory and reset when that adapter restarts. See [web/README.md](web/README.md) and [web/SAMPLE-AUTH.md](web/SAMPLE-AUTH.md).

Workspace pages include Overview, Accounts, Transactions, Cash Flow, Budgets, Investments, Analysis, Reports, Notifications, and Profile & Settings, with Individual and Business profiles.

SM-Intelligence monitors financial activity; it does not execute payments, transfers, or investment purchases. The Spring Boot backend contains an API gateway and nine service modules, with identity domain logic and service-owned Flyway migrations. It does not yet implement the frontend's [HTTP API contract](web/API-CONTRACT.md), so the development adapter is still required.

## Layout

- `web/src/app/`: Angular components and routes.
- `web/src/styles.css`: global styles.
- `web/public/`: static assets.
- `backend/`: Maven multi-module backend; requires JDK 25 and Maven. Run `mvn test` from this directory. `docker compose up -d` starts PostgreSQL instances and Kafka, not the Java services.
- `backend/*-service/src/main/resources/db/migration/`: migrations loaded by each service.
- `database_schema/README.md`: points to the authoritative service migrations; redundant SQL copies have been removed.
- [erd.md](erd.md): current service ownership and relationships.

Use the repository's existing branch conventions when contributing. Dependencies and build output are ignored by Git; commit source and the npm lockfile.
