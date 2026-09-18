# SM-Intelligence customer frontend

Angular customer frontend using the Azure design. Main flow: Landing -> Registration -> automatic sign-in -> Dashboard. Returning users sign in to the Dashboard; accounts can be added from the workspace. The setup route remains available for onboarding. Light mode is the default; appearance controls belong in customer Settings.

## Development

Use Node matching the package engines (`^24.15.0 || >=26.0.0`) and npm 11+ (see `.mise.toml` for the pinned version).

```sh
npm ci
npm start
npm run build
npm test -- --watch=false
```

Public routes: `/`, `/login`, `/register`. The `/setup` route requires a session and unfinished setup. The signed-in Dashboard contains Overview (`/dashboard`, also `/overview`), Accounts, Transactions, Cash Flow, Budgets, Investments, Analysis, Reports, Notifications, and Profile & Settings. Workspace routes require a session and remain accessible before account setup is completed. Hosting must serve `index.html` for frontend routes and route `/api/*` to the backend.

Setup includes bank logo choices, account name and number, debit/credit selection, opening balance or credit outstanding, and an effective date. KES is fixed. Confirmation masks the number and the form clears it after a successful save. Frontend state is in memory; server sessions are restored through the API on reload.

## Backend integration

Read [API-CONTRACT.md](API-CONTRACT.md). The frontend calls a proposed same-origin cookie-session API; the production HTTP implementation is not yet available in the backend starter. For development, `npm start` uses hosted authentication and a local financial API adapter; see [SAMPLE-AUTH.md](SAMPLE-AUTH.md). Production registration/login and saving require the HTTP implementation. Failed requests display errors, never fabricated success. Tests mock API responses to verify the flow. No demo bypass is exposed in customer navigation.

The React project in Downloads remains the separate design reference.

## Local development without hosted authentication

Run `npm run start:mock` from `web` (stop any other server using port 4200 first), then open http://localhost:4200. This starts Angular with a proxy and an in-memory API on loopback port 4301. Register a fictional user to start; no sample users or financial records are seeded by the launcher. Users, sessions, and financial records reset when this process restarts.

Normal `npm start` uses hosted authentication with local in-memory financial records. Mock code lives under `tools/` and is not imported into the production app. See [MOCK-USERS.md](MOCK-USERS.md) for explicit test fixtures.

`npm run test:mock` checks financial calculations and isolation. `npm run test:workflow` checks the hosted-auth adapter with stubbed authentication responses. Neither command verifies a live hosted backend.

## Dashboard pages

The entire signed-in area is called **Dashboard**; its first page is **Overview**. The Angular pages preserve the prototype's grouped sidebar and mobile navigation, with updated Azure surfaces. Profile & Settings offers a persistent light/dark preference. The separate React prototype is the design reference, not the server to start for this app.

- Accounts: list balances, add an account, and open its activity.
- Transactions: date/account/search/status/category filters; add and edit manual entries.
- Cash Flow: posted deposit movements, date bins, and exact figures.
- Budgets: create, edit, and delete category allocations with warning thresholds.
- Investments: create, edit, and delete monitoring records; unknown valuations remain unavailable.
- Analysis: category composition and actionable balance/budget/maturity alerts.
- Reports: scoped transaction, account, and cash-flow CSV exports with a preview.
- Notifications: inspect alerts and mark them read.
- Profile & Settings: update display name, organization, alert preferences, and appearance; view development change history.

Mock records are isolated by signed-in user and held in memory. They do not change bank balances. Ledger entries do not automatically update account snapshots. Transfers, linked refunds, historical reconciliation, investment execution and production identity/security remain backend work. These pages do not claim to implement those capabilities.

### Port already in use

Stop the old `npm start` or `npm run start:mock` with Ctrl+C in its terminal. The mock launcher checks both IPv4 and IPv6 for port 4200 and checks API port 4301 before starting; it exits instead of silently choosing another port. Identify a leftover process with:

```powershell
Get-NetTCPConnection -State Listen -LocalPort 4200,4301 | Select-Object LocalAddress,LocalPort,OwningProcess
Get-CimInstance Win32_Process -Filter "ProcessId = <the displayed PID>" | Select-Object CommandLine
```

Only stop a process after confirming it belongs to this project's old development server. Then run `npm run start:mock` again. The command starts both services; do not also run `npm start` in a second terminal. Browser sessions for `localhost` and `127.0.0.1` are separate, so consistently use the printed localhost URL.

For isolated verification, `MOCK_WEB_PORT` and `MOCK_API_PORT` can select different ports. `proxy.mock.cjs` uses the configured API port. Defaults remain 4200 and 4301.

### Visual data and appearance

Appearance uses an explicit Light/Dark selector with the active mode highlighted. Cash-flow charts support income/outflow comparison and selecting a period for exact values. Analysis includes an interactive category ring with percentages and exact KES amounts. Numeric tables remain available.

The SmartMoney palette guide informs semantic colors: green for income, red for outflows/losses, amber for review, blue for information and grey for inactive states. Dark-mode tokens use lighter text shades for contrast. Category colors identify categories rather than financial success/failure. Striped outflow bars, directional labels and numeric values avoid relying on color alone.
