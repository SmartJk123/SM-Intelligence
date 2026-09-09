# SM-Intelligence customer frontend

Angular customer frontend using the Azure design. Main flow: Landing -> Registration -> Account setup -> Dashboard. Login returns unfinished users to setup and completed users to the dashboard, using the server-provided setupCompleted flag. Light mode is the default; appearance controls belong in customer Settings.

## Development

Use Node 24.15+ and npm 11+ (see `.mise.toml`).

```sh
npm ci
npm start
npm run build
npm test -- --watch=false
```

Routes: `/`, `/login`, `/register`, `/setup`, `/dashboard`. The dashboard is currently a destination screen; financial modules are not implemented. Hosting must serve `index.html` for frontend routes and route `/api/*` to the backend.

Setup includes bank logo choices, account name and number, debit/credit selection, opening balance or credit outstanding, and an effective date. KES is fixed. Confirmation masks the number and the form clears it after a successful save. Frontend state is in memory; server sessions are restored through the API on reload.

## Backend integration

Read [API-CONTRACT.md](API-CONTRACT.md). The frontend calls a proposed same-origin cookie-session API; the backend is not included in this repository. Successful registration/login and saving require that implementation. Failed requests display errors, never fabricated success. Tests mock API responses to verify the flow. No demo bypass is exposed in customer navigation.

The React project in Downloads remains the separate design reference.

