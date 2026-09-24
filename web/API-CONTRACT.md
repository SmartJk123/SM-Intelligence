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

Accounts, bank transactions and aggregate workspace contracts are not connected. The adapter returns 501 for unsupported authenticated requests. Invoice-backed pending activity is supported below; bank-ledger integration still needs explicit mapping and ownership enforcement.

## Invoice-backed pending activity

- `GET /api/invoices?page=0`: signed-in owner's invoices, newest first, 50 per page. Response is an array containing `id`, `vendor`, `invoiceNumber`, `amount`, `currency`, `invoiceDate`, `dueDate`, `filename`, `createdAt`, `status: "PENDING"`, and `source: "INVOICE"`.
- `POST /api/invoices`: multipart form with `file`, `vendor`, `amount`, `currency`, `invoiceDate` (ISO date), optional `invoiceNumber` and `dueDate`. Returns 201 with the saved record. Requires a reviewed positive amount and JPEG/PNG/PDF file up to 10 MB. Same document bytes for the same owner return 409.
- `GET /api/invoices/:id/document`: authenticated attachment download. Other users' documents return 404.

The adapter forwards its server-held bearer token to the gateway; the transactions service verifies it with identity. The invoice owner is derived from the verified profile. These pending expected expenses are stored separately from posted bank transactions and do not affect balances. No bank payment, reconciliation or cancellation is implemented in this milestone. Browser extraction is best-effort and must be reviewed; it never creates a record before Save.

`INVOICE_API_URL` optionally overrides the adapter's invoice upstream; by default it uses `IDENTITY_API_URL` (the gateway). When pointing identity directly to port 8081, set `INVOICE_API_URL=http://localhost:8083` or keep both behind the gateway.
