# Authentication API contract

## Browser to local adapter

The Angular development proxy forwards `/api/**` to the loopback adapter. The browser uses an HttpOnly, SameSite=Lax session cookie; JWTs remain inside the adapter.

| Method | Path | Request / response |
| --- | --- | --- |
| POST | /api/auth/register | `{name,email,phone,password,kind}` where kind is individual or organization; returns `{user}` with 201 |
| POST | /api/auth/login | `{email,password}`; returns `{user}` with 200 |
| GET | /api/auth/session | Revalidates the stored JWT through backend /me; returns `{user}` or 401 |
| POST | /api/auth/logout | Clears the local session cookie and server-side token |

User shape: `{id,name,email,kind,setupCompleted:false}`. The id is the identity database UUID, never a generated local user id. The frontend derives setup completion from an authenticated GET /api/accounts response; the identity-only flag does not grant workspace access.

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

This only shows real data: a user with no `Account`/`Transaction` rows sees zeros, not sample data. Accounts are created today via `POST /api/accounts` (see Persisted account onboarding, below) — nothing yet creates a `Transaction` automatically from a live bank webhook. That linkage (matching an incoming KCB/NCBA/Stanbic notification to a specific customer's own account) does not exist anywhere in the codebase yet and is a separate, larger piece of work: `bank-integration-service`'s stored transactions have no `user_id`/`account_id` column today.

Invoice-backed pending activity is supported below; bank-ledger integration still needs explicit mapping and ownership enforcement. Every other authenticated route not listed anywhere in this document still returns 501, never a fake success or temporary record.

**Known gap**: `accounts-service` and `transactions-service` have no authentication of their own today — anything that can reach them directly (bypassing this adapter) can read or write any `userId`'s accounts/transactions. This predates the dashboard endpoint above but matters much more now that real money flows through it; it should be closed before this goes near production.

## Invoice-backed pending activity

- `GET /api/invoices?page=0`: signed-in owner's invoices, newest first, 50 per page. Response is an array containing `id`, `vendor`, `invoiceNumber`, `amount`, `currency`, `invoiceDate`, `dueDate`, `filename`, `createdAt`, `status: "PENDING"`, and `source: "INVOICE"`.
- `POST /api/invoices`: multipart form with `file`, `vendor`, `amount`, `currency`, `invoiceDate` (ISO date), optional `invoiceNumber` and `dueDate`. Returns 201 with the saved record. Requires a reviewed positive amount and JPEG/PNG/PDF file up to 10 MB. Same document bytes for the same owner return 409.
- `GET /api/invoices/:id/document`: authenticated attachment download. Other users' documents return 404.

The adapter forwards its server-held bearer token to the gateway; the transactions service verifies it with identity. The invoice owner is derived from the verified profile. These pending expected expenses are stored separately from posted bank transactions and do not affect balances. No bank payment, reconciliation or cancellation is implemented in this milestone. Browser extraction is best-effort and must be reviewed; it never creates a record before Save.

`INVOICE_API_URL` optionally overrides the adapter's invoice upstream; by default it uses `IDENTITY_API_URL` (the gateway). When pointing identity directly to port 8081, set `INVOICE_API_URL=http://localhost:8083` or keep both behind the gateway.

## Persisted account onboarding

- `GET /api/accounts`: returns the signed-in owner's accounts. The adapter strips owner and provider identifiers. Client-supplied user IDs are ignored.
- `POST /api/accounts`: `{bank,accountName,accountNumber,cardType,balance,balanceDate,currency}`. Bank is KCB/Equity/NCBA/Stanbic; type is debit/credit; currency is KES; balance is nonnegative with up to two decimals; date cannot be in the future. Returns 201 with the persisted account.
- The adapter forwards creation to `/api/accounts/manual` using its server-held bearer token. Accounts validates the token with identity and derives ownership from `/api/auth/me`.
- An ACTIVE account is required for workspace routes. Lookup failures keep the user on setup with a retry action. Manual accounts remain DISCONNECTED from bank syncing. Opening credit balances populate creditOutstanding, never availableBalance.
- Account numbers are not stored in full: the service stores the last four digits and a stable owner-scoped SHA-256 fingerprint for duplicate detection. Duplicate accounts return 409. The effective date is stored as lastUpdated.
- Legacy account read/update/delete routes also require a bearer token and enforce ownership. `ACCOUNTS_API_URL` defaults to the gateway and can point directly to accounts for local development.
