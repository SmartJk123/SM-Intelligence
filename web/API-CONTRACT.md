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

## Proposed dashboard contract (development mock)

This is a frontend proposal for backend review, not an implemented production endpoint. The new SQL schema alone does not provide authentication or account HTTP routes.

`GET /api/dashboard?days=30` (also accepts 90) requires the session cookie. Return 401 if unauthenticated. Response shape is defined by `DashboardData` in `src/app/dashboard.ts`, with a working example generated in `tools/dashboard-model.mjs`:

- `source`: `sample` in mock mode, `live` for the real service; `currency`: `KES`.
- `user`: name and kind (`individual` or `organization`).
- `period`: inclusive ISO dates `from`, `to`, and `days`.
- `summary`: availableCashMinor, creditOutstandingMinor, moneyInMinor, moneyOutMinor, netCashFlowMinor.
- `accounts`: id, bank display name, accountName, maskedIdentifier, accountType (`DEPOSIT`/`CREDIT`), availableBalanceMinor, creditOutstandingMinor. Do not return full account numbers.
- `cashFlow`: date bins with from, to, moneyInMinor, moneyOutMinor.
- `transactions`: up to 12 most recent records in the period: id, accountId, description, category, direction (`CREDIT`/`DEBIT`), positive amountMinor, status, ISO date.
- `transactionCount`: all matching records before the 12-record limit.

Money fields use integer minor units: 100 = KES 1.00. SQL monetary columns use decimals; agree rounding for four-decimal database values before integration. Mock form input accepts at most two decimals. SQL institution codes and user kind require mapping to the frontend names/enums.

Available cash sums deposit snapshots; credit debt is separate. Cash flow includes only POSTED deposit transactions, CREDIT as money in and DEBIT as money out. Net cash flow is in minus out, not profit. Current balances are snapshots and are not calculated from the selected transaction window. Transfers are not yet reconciled across accounts; agree transfer exclusion rules before interpreting totals as external income/spending. The mock uses UTC dates and six bins; confirm the production reporting timezone with the backend team.

`POST /api/auth/logout` clears the session cookie and returns `{}`. The mock supports existing login/register/session/account setup contracts and enforces user ownership on reads. It does not implement production password security, durable storage, verification, consent auditing, bank connections or the full SQL lifecycle. Backend review is still needed for those requirements.

## Proposed dashboard page endpoints

The entire signed-in area is Dashboard; `/dashboard` renders Overview. All module routes enforce the same login/setup guards. The mock additionally implements:

- `GET /api/workspace`: user-scoped accounts, all transaction records, budgets, investments, profile, read notification IDs, and recent development activity history. See `WorkspaceData` in `src/app/workspace-api.ts` for the response type.
- `POST /api/workspace/transactions`: create or update (when `id` is present) a manual record. Fields: accountId, ISO date, description, category, CREDIT/DEBIT direction, positive integer amountMinor, and POSTED/PENDING/FAILED/REVERSED/CANCELLED status.
- `POST /api/workspace/budgets`: create/update category, allocatedMinor, start/end dates, optional accountId, and threshold percentage (1–100). Allocation must be positive.
- `POST /api/workspace/investments`: create/update name, type, principalMinor, nullable currentValueMinor, valuationDate, and optional maturityDate. A null valuation means unavailable, not zero.
- `DELETE /api/workspace/budgets/:id` and `/investments/:id`: delete an owned record.
- `POST /api/workspace/profile`: name, organization, lowBalanceMinor, budgetAlerts, balanceAlerts, maturityAlerts. Email/kind cannot be changed through this endpoint.
- `POST /api/workspace/read`: `{ids: string[]}` marks in-app alerts read for this user.

The mock enforces account ownership and rejects editing another user's transaction/budget/investment. All mutation success responses are `{ok:true}`; invalid records return 400. The existing `/api/accounts` contract creates accounts. The frontend refetches after saving and never treats an unsuccessful response as success.

Budget actuals and category composition currently sum posted debits (including credit purchases) matching the category/scope. Transfer and linked-refund classifications are not yet represented by this draft contract; add those semantics before production financial reporting. The mock's limited history is not a secure audit trail. Full record snapshots are suitable for this small development fixture; pagination and server-side aggregations need agreement for production scale.
