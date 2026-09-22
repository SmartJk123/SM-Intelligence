# Customer frontend: authentication milestone

Registration and login connect to the repository's identity service. The old hosted-sample URL, sample authentication mode, seeded personas and in-memory financial API have been removed.

## Start

Use the Node engine declared in `package.json` (Node 24.15 or newer, because the app uses Angular 22) and npm 11+. Check with `node -v`, and switch with `nvm use 24.15.0` or `fnm use 24.15.0` if you have an older version. Start the backend as described in [backend/README.md](../backend/README.md), then:

```powershell
cd <path to your clone>\web
npm ci          # or npm install if you have no lock file yet
npm start
```

If `npm start` fails with `Cannot find module ...@angular\cli\bin\ng.js` or "Could not find the '@angular/build:dev-server' builder", the packages are not installed yet: run `npm ci` (or `npm install`) first.

### Troubleshooting installs on Windows

- **`EBADENGINE Unsupported engine` for every Angular package.** Node is too old. Node 24.14 is not enough, it must be 24.15 or newer. With nvm-windows: `nvm install 24.15.0`, then `nvm use 24.15.0` (run it in an administrator PowerShell if it reports a permissions error), then confirm with `node -v` in a new window.
- **`npm warn cleanup ... EPERM ... rmdir` and `ECONNRESET`.** A download dropped and npm could not remove the half-installed folder because an editor or antivirus was holding it. Close other terminals and editors, delete `web\node_modules`, and run `npm ci` again. If the network is unreliable, raise the retries first: `npm config set fetch-retries 5` and `npm config set fetch-retry-maxtimeout 120000`.
- **`Port 4200 is already in use`.** The admin interface (`admin-interface`, `npm start`) also uses 4200. Stop it, or run this app on another port: `$env:WEB_PORT = "4300"; npm start`. Check what owns a port with `Get-NetTCPConnection -State Listen -LocalPort 4200`.

Open http://localhost:4200. The Angular server uses port 4200 and the authentication adapter uses loopback port 4301. Stop both with Ctrl+C. `WEB_PORT` and `AUTH_ADAPTER_PORT` can select other ports; an occupied port is reported instead of silently changing it.

`IDENTITY_API_URL` defaults to `http://localhost:8080` (the gateway). To target identity directly, set it to `http://localhost:8081` before running `npm start`. Remote URLs require HTTPS. No old hosted backend is selected implicitly.

## Current behavior

- Registration sends the correct identity fields, including Individual/Organization, and signs the user in from the returned JWT after verifying it through `/api/auth/me`.
- Login and page refresh load the actual backend profile and stable user UUID. Passwords and JWTs are never returned to browser code by the adapter.
- Logout deletes the adapter session and clears its HttpOnly cookie. JWTs expire in the backend; there is no backend refresh-token or global revocation endpoint yet.
- The identity database owns user records. Restarting the adapter requires signing in again, but does not delete registered backend users.
- Finance pages display 'Not connected yet'. No temporary accounts, balances, transactions, budgets, or investments are created. Profile is read-only; appearance remains a device preference.

This adapter is for loopback development. Production requires a deployed session layer, HTTPS/Secure cookies and persistent session management; a static frontend build by itself does not implement the adapter endpoints. See [API-CONTRACT.md](API-CONTRACT.md).

## Checks

```powershell
npm run test:auth
npm test -- --watch=false
npm run build
```
