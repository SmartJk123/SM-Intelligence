# SM-Intelligence

Financial intelligence platform for individual and business users.

## Web development

The Angular application lives in `web/`. The Figma/React prototype is maintained separately as a design reference.

Requirements: Node.js 22.19 or a compatible Angular 21 runtime, and npm.

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

This checkout starts with the Angular web scaffold. Product screens and backend integration are not implemented yet.

The brief calls for a public information website and a separate authenticated application experience, supporting Individual and Business profiles. Planned modules include Dashboard, Accounts, Transactions, Cash Flow, Budgets, Investments, Analysis, Reports, Notifications, and Profile & Settings.

SM-Intelligence monitors financial activity; it does not execute payments, transfers, or investment purchases. Backend technology and API contracts are to be agreed with the team.

## Layout

- `web/src/app/`: Angular components and routes.
- `web/src/styles.scss`: global styles.
- `web/public/`: static assets.

Use the repository's existing branch conventions when contributing. Dependencies and build output are ignored by Git; commit source and the npm lockfile.
