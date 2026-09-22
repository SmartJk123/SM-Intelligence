# Customer frontend: authentication milestone

Registration and login connect to the repository's identity service. The old hosted-sample URL, sample authentication mode, seeded personas and in-memory financial API have been removed.

## Start

Use the Node engine declared in `package.json` and npm 11+. Start the backend as described in [backend/README.md](../backend/README.md), then:

```powershell
cd C:\Users\admin\SM-Intelligence\web
npm ci
npm start
```

Open http://localhost:4200. The Angular server uses port 4200 and the authentication adapter uses loopback port 4301. Stop both with Ctrl+C. `WEB_PORT` and `AUTH_ADAPTER_PORT` can select other ports; an occupied port is reported instead of silently changing it.

`IDENTITY_API_URL` defaults to `http://localhost:8080` (the gateway). To target identity directly, set it to `http://localhost:8081` before running `npm start`. Remote URLs require HTTPS. No old hosted backend is selected implicitly.

## Current behavior

- Registration sends the correct identity fields, including Individual/Organization, and signs the user in from the returned JWT after verifying it through `/api/auth/me`.
- Login and page refresh load the actual backend profile and stable user UUID. Passwords and JWTs are never returned to browser code by the adapter.
- Logout deletes the adapter session and clears its HttpOnly cookie. JWTs expire in the backend; there is no backend refresh-token or global revocation endpoint yet.
- The identity database owns user records. Restarting the adapter requires signing in again, but does not delete registered backend users.
- The Financial Overview page (`web/src/app/dashboard.ts`) reads real balances and transactions from accounts-service/transactions-service through `GET /api/dashboard` (see [API-CONTRACT.md](API-CONTRACT.md)). A signed-up user with no `Account`/`Transaction` rows yet sees zeros, not sample data — those rows are not created automatically from a live bank notification today. Budgets and investments still display 'Not connected yet'. No temporary records are created. Profile is read-only; appearance remains a device preference.

This adapter is for loopback development. Production requires a deployed session layer, HTTPS/Secure cookies and persistent session management; a static frontend build by itself does not implement the adapter endpoints. See [API-CONTRACT.md](API-CONTRACT.md).

## Checks

```powershell
npm run test:auth
npm test -- --watch=false
npm run build
```
