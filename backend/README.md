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
