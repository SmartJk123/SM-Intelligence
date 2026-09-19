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

Accounts, transactions and aggregate workspace contracts are deliberately not connected in this step. The adapter returns 501 for unsupported authenticated requests, never a fake success or temporary record. The preserved UI designs and backend DTOs will need explicit mapping and ownership enforcement in the next milestone.
