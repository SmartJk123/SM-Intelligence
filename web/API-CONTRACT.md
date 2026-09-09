# Account flow integration

Landing -> Register/Login -> authenticated Account setup -> saved confirmation.

The frontend now calls these same-origin endpoints. This is a proposed integration contract, not an existing backend implementation. The host must route `/api/*` to the team's backend (not the SPA HTML fallback).

- `POST /api/auth/register`: `{name,email,phone,password,kind}`. Return `{user:{id,kind}}` and establish an authenticated, HttpOnly cookie session. `kind` is `individual` or `organization`. If verification is required before session creation, add the verification route before enabling registration.
- `POST /api/auth/login`: `{email,password}`. Same session response.
- `GET /api/auth/session`: same session response; return 401 without a valid session.
- `POST /api/accounts`: `{bank,accountName,accountNumber,cardType,balance,balanceDate,currency}`. Return `{id}` only after persisting. `cardType` is `debit` or `credit`, currency is always `KES`, balance is a nonnegative amount with at most two decimal places. Debit is available cash; credit is outstanding debt, never available cash.

Backend responsibilities: authorize account ownership using the server session, validate all fields, protect cookie requests against CSRF (Angular's default same-origin XSRF cookie/header support is enabled), store sensitive fields securely, avoid credential/account-number logs, and mask account numbers in subsequent responses. Do not infer bank connectivity from account creation.

No credentials or bank numbers are stored in browser storage. The full number is cleared from the form after a successful save; confirmation retains only the last four digits. Failure keeps the form available to retry. The setup route restores a server session on reload and redirects unauthenticated visits to login. Network failures do not create a successful session or saved confirmation.

Real registration, login, and saving remain unavailable until this backend contract is implemented. Automated tests can mock the contract to verify the frontend journey.

## Setup-based routing

All auth/session responses must include `user.setupCompleted` as a boolean persisted by the backend. Registration returns false. Login and session restoration return the user's actual status. Do not derive this from login count: a user who left setup unfinished must return to setup on their next login. Missing or invalid status is rejected.

A confirmed `POST /api/accounts` save must also persist setup completion server-side. The frontend marks it complete after the confirmed save, and offers Continue to dashboard. Reloads use the server's saved flag. `/dashboard` redirects unfinished users to `/setup`; completed users visiting `/setup`, `/login`, or `/register` are redirected to `/dashboard`. Unauthenticated dashboard/setup visits go to login.

The dashboard route currently contains a minimal destination screen; financial dashboard modules are a separate milestone.
