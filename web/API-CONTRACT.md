# Authentication API contract

## Browser to local adapter

The Angular development proxy forwards `/api/**` to the loopback adapter. The browser uses an HttpOnly, SameSite=Lax session cookie; JWTs remain inside the adapter.

| Method | Path | Request / response |
| --- | --- | --- |
| POST | /api/auth/register | `{name,email,phone,password,kind}` where kind is individual or organization; returns `{user}` with 201 |
| POST | /api/auth/login | `{email,password}`; returns `{user}` with 200 |
| GET | /api/auth/session | Revalidates the stored JWT through backend /me; returns `{user}` or 401 |
| POST | /api/auth/logout | Clears the local session cookie and server-side token |

User shape: `{id,name,email,kind,setupCompleted:false}`. The id is the identity database UUID, never a generated local user id. Setup is deferred for this milestone.

If registration succeeds but session establishment fails, the adapter returns `{registered:true}` with 201. The UI attempts login once and otherwise offers sign-in; it does not resubmit registration.

## Adapter to identity service

The configured `IDENTITY_API_URL` receives:

- POST `/api/auth/register`: `{name,emailAddress,phoneNumber,password,accountType}`, with uppercase INDIVIDUAL/ORGANIZATION.
- POST `/api/auth/login`: `{emailAddress,password}`.
- GET `/api/auth/me`: `Authorization: Bearer <token>`.

Authentication responses contain `token,userId,name,emailAddress,accountType,expiresIn`. The JWT subject is the user UUID; its expiration is a Unix timestamp. The adapter validates token metadata and asks /me to verify the signature and current user before accepting a session.

## Financial APIs

| Method | Path | Request / response |
| --- | --- | --- |
| GET | /api/dashboard?days=&bank= | Requires a session. Returns the `DashboardData` shape `web/src/app/dashboard.ts` renders: `user`, `period`, `summary`, `accounts`, `cashFlow`, `transactions`, `transactionCount`. `days` defaults to 30 (max 365). `bank` filters accounts by `institution` and is echoed back on the response. |

The adapter builds this by calling `GET {IDENTITY_API_URL}/api/accounts?userId=<session user>` and, for each account, `GET {IDENTITY_API_URL}/api/transactions?accountId=<account id>` — the same accounts-service/transactions-service the gateway already routes to, no new backend service. Balances/amounts are converted from decimal to minor units (`* 100`). Only `DEPOSIT` accounts and `POSTED` transactions count toward `summary`/`cashFlow`, matching the labels already in `dashboard.ts`. `category` is a placeholder (`counterparty`, or `'Uncategorized'`) since categories-service is not wired up.

This only shows real data: a user with no `Account`/`Transaction` rows sees zeros, not sample data. Rows are created today via `POST /api/accounts` / `POST /api/transactions` directly (e.g. seeded manually) — nothing yet creates them automatically from a live bank webhook. That linkage (matching an incoming KCB/NCBA/Stanbic notification to a specific customer's own account) does not exist anywhere in the codebase yet and is a separate, larger piece of work: `bank-integration-service`'s stored transactions have no `user_id`/`account_id` column today.

Every other authenticated route not listed above still returns 501, never a fake success or temporary record.

**Known gap**: `accounts-service` and `transactions-service` have no authentication of their own today — anything that can reach them directly (bypassing this adapter) can read or write any `userId`'s accounts/transactions. This predates the dashboard endpoint above but matters much more now that real money flows through it; it should be closed before this goes near production.
