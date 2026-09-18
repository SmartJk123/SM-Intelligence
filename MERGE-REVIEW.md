# Merge review: Customer-facing-interface

Reviewed merge `0e5deeb` (first parent `7a444db`, merged main `eb81ff0`). The merge brings in the Maven multi-module backend, nine service-owned database migrations, identity domain/service logic, Docker infrastructure, and Render configuration. It makes no changes under `web/`.

## Cleanup applied

- Removed nine duplicate SQL files under `database_schema/` after checking their hashes against the corresponding service migrations. The runtime migration files are unchanged. Replaced duplicate SQL with a pointer to those authoritative files.
- Removed seven tracked `.idea` files. The existing root ignore rule already excludes future IDE metadata.
- Removed unused Angular scaffold `web/src/app/app.html` and `app.css`. `App` uses its inline template and global styles; neither removed file is referenced by source or build configuration.
- Replaced the obsolete ERD with current service ownership and runtime schema links. Removed broken machine-specific links, incorrect cross-service foreign-key claims, and outdated placeholder-table descriptions.
- Updated root/frontend setup documentation to reflect Angular 22, package engine requirements, hosted authentication, and the launcher's empty initial data. Preserved test fixtures and the development adapter.
- Added `test` to the Makefile's `.PHONY` targets.

## Remaining integration findings

1. **The new backend is not a replacement for the development adapter yet.** There are no HTTP controllers implementing the frontend contract, and the gateway configuration contains no routes. `web/tools/mock-api.mjs` still uses the separately hosted authentication backend. Removing that adapter now would break development login and financial workflows. See [API contract](web/API-CONTRACT.md).
2. **Render database topology conflicts with local service isolation.** All nine services reference `smi-postgres` in `render.yaml`. Each service packages its own V1 migration and uses the default Flyway history configuration, while Compose provisions separate databases. Resolve database or schema isolation before deploying these services together; no infrastructure changes were made during cleanup.
3. **Identity authorization remains a scaffold.** `SecurityConfig` ends with `anyRequest().permitAll()` and disables CSRF with stateless sessions. Define the intended session/token model and authorization before exposing customer-data endpoints. The frontend contract describes cookie sessions, so this requires an integration decision.
4. **Backend tests do not validate PostgreSQL migrations.** The test profiles disable Flyway and use H2 with Hibernate `create-drop`. A PostgreSQL migration test is still needed in the backend workflow.

## Verification in C:\Users\admin\SM-Intelligence

- Existing calculation/model tests: 10 passed.
- Existing hosted-auth workflow test with stubbed authentication: 1 passed; this does not contact the live authentication service.
- Angular production build: passed.
- Angular unit tests: 21 passed across 3 test files.
- Git whitespace check: passed.
- Backend tests were not run: this environment has JDK 21, while the project requires JDK 25, and Maven is unavailable.

Changes are local to C:\Users\admin\SM-Intelligence. Nothing has been pushed or deployed.
