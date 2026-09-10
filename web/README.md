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


## Local seed data

Run `npm run start:seed` instead of `npm start` to use the optional development API. Stop any existing server on port 4200 first. Open http://localhost:4200.

| Email | Password | Initial destination |
| --- | --- | --- |
| new.user@example.com | SeedPass123! | Account setup |
| returning.user@example.com | SeedPass123! | Dashboard |
| business.user@example.com | SeedPass123! | Business account setup |

Use separate private browser windows or clear this site's cookies to switch accounts. Registration also works for new test email addresses (password minimum 12 characters). Use fictional bank numbers, such as 0012345678. Saving completes setup; subsequent logins go to Dashboard. The seeded returning user has a KCB debit account with KES 45,000. Dashboard financial cards remain a future milestone.

All data is in memory and resets when the seed server restarts. This server is development-only, bound to 127.0.0.1; it is not production authentication. The seed files are not included in the Angular production bundle. Ordinary `npm start` continues to use the production API contract without seed mode.
