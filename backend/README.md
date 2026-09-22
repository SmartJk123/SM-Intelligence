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
./backend/mvn.ps1 -pl identity-service spring-boot:run
```

Identity listens on 8081 and runs its Flyway migration against PostgreSQL. Keep the signing key stable across restarts if existing tokens should remain valid; do not commit it. Production startup now requires JWT_SECRET rather than falling back to a public source-code secret.

In a second terminal:

```powershell
./backend/mvn.ps1 -pl api-gateway spring-boot:run
```

The gateway listens on 8080 and forwards /api/auth/** to identity. Its configuration uses the Spring Cloud Gateway Server Web MVC property namespace. The account and transaction services remain unconnected pending ownership/authentication work.

Run `npm start` in `web/` and open http://localhost:4200. Registered users are stored in PostgreSQL; sessions in the local adapter end when it restarts.

Environment overrides: identity accepts DB_URL, DB_USER, DB_PASSWORD, PORT, JWT_SECRET and JWT_EXPIRATION_MS. Gateway accepts PORT and IDENTITY_SERVICE_URL. Set values separately in the shell for each process; .env files are not automatically imported by Maven.

## Ports on a development machine

| Service | Port | Notes |
| --- | --- | --- |
| api-gateway | 8080 | Only forwards `/api/auth/**` today. Opening `http://localhost:8080/` in a browser shows a 404 page, which is normal. |
| identity-service | 8081 | Needs PostgreSQL and `JWT_SECRET`. |
| bank-integration-service | 8090 | Receives KCB, NCBA and Stanbic notifications. Keep it off 8080. |
| web (Angular) | 4200 | See [web/README.md](../web/README.md). |

## Windows notes (no Docker, low memory, JDK 21)

- **PowerShell splits `-D` options at the dot.** Quote them: `mvn "-Djava.version=21" spring-boot:run -pl identity-service`. Without the quotes Maven receives `.version=21` as a separate word and fails with "Unknown lifecycle phase".
- **`release version 25 not supported`.** Maven is running on JDK 21 while the parent pom asks for 25. Either point `JAVA_HOME` at JDK 25 or override with `"-Djava.version=21"`.
- **`insufficient memory` or `paging file is too small`.** Windows has no free commit space for another JVM. Enable a system managed page file (System Properties, Advanced, Performance Settings, Advanced, Virtual memory), close unused editors, and start each service with a small heap: `"-Dspring-boot.run.jvmArguments=-Xms64m -Xmx256m -XX:+UseSerialGC"`.
- **Local PostgreSQL instead of Docker.** The identity service defaults to user `admin`, password `secretpassword`, database `smi_identity` on port 5432. Create them once as the `postgres` superuser:
  ```sql
  CREATE USER admin WITH PASSWORD 'secretpassword';
  CREATE DATABASE smi_identity OWNER admin;
  ```
  Use `psql` from the PostgreSQL install folder (for example `C:\Program Files\PostgreSQL\18\bin\psql.exe`) if it is not on PATH. These defaults are for local development only.
- **`FATAL: password authentication failed` or `role "admin" does not exist`.** The database login above has not been created yet.

## Bank integration service

Run it from `backend/`, with the settings loaded into the shell first because Maven does not read `.env.local`:

```powershell
cd backend\bank-integration-service
Get-Content .env.local | ForEach-Object { if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { Set-Item "Env:$($matches[1])" $matches[2].Trim() } }
cd ..
mvn "-Djava.version=21" spring-boot:run -pl bank-integration-service
```

- Each bank has its own switch, default `SANDBOX`: `KCB_ENV`, `STANBIC_ENV`, `NCBA_ENV` (use `PRODUCTION` for a live account). There is no Equity connector yet.
- KCB holds one environment at a time, with one `KCB_CLIENT_KEY` and `KCB_CLIENT_SECRET`. The secret is never stored in the repository.
- `PUBLIC_BASE_URL` is the bare public address with no path, for example `https://sm-intelligence.globalsmartspace.com`. The service adds `/api/v1/webhooks/<bank>` itself, so a value that already contains the path produces a doubled address in the connection test.
- Notification addresses to give the banks: `<PUBLIC_BASE_URL>/api/v1/webhooks/kcb`, `/ncba` and `/stanbic`.
- `curl http://localhost:8090/actuator/health` should report `UP`.

### NCBA credentials

NCBA is push only. The secret key, username and password are values you choose and send to NCBA in writing, and every notification carries them back. They are kept in `NCBA_SECRET_KEY`, `NCBA_USERNAME` and `NCBA_PASSWORD`.

- Use letters and digits only, as the NCBA guide calls these alphanumeric strings. The secret key should be 16 characters or more.
- Choose them once and do not change them after NCBA has them, or notifications are refused until NCBA is updated.
- The service checks `User` and `Password` against these values, and recomputes `HashVal` as base64 of the lowercase hexadecimal SHA-256 of the secret key followed by TransType, TransID, TransTime, TransAmount, AccountNr, Narrative, PhoneNr, CustomerName and Status.
- A repeated `TransID` is answered `OK: Duplicate Notification` and stored once.

## Organisation invites (Admin-Interface branch, not merged into main yet)

`identity-service` on the `Admin-Interface` branch adds `GET/POST /api/organizations` and `GET/POST /api/organizations/{id}/members`, routed through the gateway. Creating an organisation saves it without inviting anyone. Inviting a member creates their account with a temporary password and emails the credentials.

- Mail is configured with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` and `FRONTEND_LOGIN_URL`. With no `MAIL_HOST` the request still succeeds and the response reports `emailSent: false`.
- The temporary password is only ever placed in the email, never in an API response.
- These endpoints are not authenticated yet, because the admin interface has no real login against the backend. They must be locked down before a public deployment.
