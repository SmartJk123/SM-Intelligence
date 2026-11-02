# SmartMoney Intelligence: API Contract

The interface between the administrator interface, the business user interface and the Spring Boot
backend. Treat this document as the merge boundary: if both sides implement what is written here,
the merge is a contract check rather than a code negotiation.

Interface contract, version 1.1, last updated 18 September 2026, owned by Eclectics, SmartMoney
Intelligence. Status: draft, pending the business surface. Related reading:
[Stanbic integration setup](../admin-interface/docs/stanbic-integration-setup.md),
[KCB real time trial](../admin-interface/docs/kcb-real-time-trial.md) and
[NCBA integration setup](ncba-integration-setup.md).

## 1. Three surfaces

| Surface | Audience | Content rule |
| --- | --- | --- |
| `/api/v1/admin/**` | Platform staff | Operational and aggregated. No client balances, no counterparties, no raw amounts |
| `/api/v1/business/**` | One organisation | That organisation's own financial detail, scoped by its token |
| `/api/v1/webhooks/**` | Banks | Public, verified by signature or IP, never authenticated by user session |

The administrator monitors whether the platform is working. The business user sees their own money.
Those are different questions and they must not share endpoints.

## 2. Tenancy rule

1. The organisation is taken from the token claim, never from a request parameter or body.
2. Every business query filters by `organisation_id`.
3. An admin endpoint must not return an individual organisation's balances or transaction amounts.
4. A business endpoint must reject a token whose organisation does not own the requested record.

This is the rule that keeps a multi tenant platform safe. It belongs in one place in the backend, not
repeated per controller.

## 3. Admin surface

Implemented in this repository unless marked otherwise.

Bank integrations:

- `GET /api/v1/admin/bank-integrations`: health for every bank (status, latency, counts, last receipt).
- `GET /api/v1/admin/bank-integrations/{bankId}`: health for one bank.
- `GET /api/v1/admin/bank-integrations/{bankId}/credentials`: credential status, masked. Never a secret.
- `GET /api/v1/admin/bank-integrations/{bankId}/settings`: the stored settings.
- `PUT /api/v1/admin/bank-integrations/{bankId}`: save the settings.
- `POST /api/v1/admin/bank-integrations/{bankId}/test`: the four step connection test.
- `POST /api/v1/admin/bank-integrations/{bankId}/register-callback`: register the callback with the bank.

Demonstration account:

- `GET /api/v1/admin/demo/account`: the reserved account number and name.
- `GET /api/v1/admin/demo/transactions`: movements on the account, newest first.
- `GET /api/v1/admin/demo/summary`: money in, money out and the difference, all measured.
- `POST /api/v1/admin/demo/transactions`: record one movement. The body is optional; a bare POST records
  a credit.

Directory and monitoring:

- `GET /api/v1/admin/organisations`: directory only (name, type, user count, account count, status).
- `GET /api/v1/admin/users`: directory (name, email, organisation, role, status, last login).
- `GET /api/v1/admin/transactions`: operational view (reference, bank, status, processing time), with
  amounts masked.
- `GET /api/v1/admin/reconciliation`: match rate and exceptions, aggregated.
- `GET /api/v1/admin/audit-logs`: administrative activity.
- `GET /api/v1/admin/notifications`: platform alerts.
- `GET /api/v1/admin/reports/{reportId}`: a report's rows.

The organisational directory is not client financial data. Profile information about who uses the
platform is what an administrator needs in order to operate it.

### Bank notification endpoints

One endpoint per bank, all public and none authenticated by a user session. Each answers a GET probe
so the address can be confirmed before it is given to a bank. Each accepts GET and POST.

- `/api/v1/webhooks/kcb` (KCB Bank Kenya) checks a SHA256withRSA signature over the raw body, sent in
  the `Signature` header.
- `/api/v1/webhooks/stanbic` (Stanbic Bank Kenya) will check HMAC-SHA256 once Stanbic confirms the
  scheme. Until then it fails closed.
- `/api/v1/webhooks/ncba` (NCBA Bank Kenya) recomputes `HashVal` from the secret key over nine named
  fields, and checks the username and password.

`{bankId}` is `kcb`, `stanbic` or `ncba`. Whether a notification is verified is stored per bank and
is what the toggle in the admin interface edits, so the setting the operator sees is the setting the
endpoint obeys. The environment variable only supplies the value until something has been saved.

### Demonstration account

A bank sandbox cannot show a real account moving. The demonstration account exists so a
demonstration can show money arriving and money leaving, and it is the single place where an amount
appears on the admin surface. Its rules:

- The account number `1000000001` is reserved. Nothing else may use it.
- The reference prefix `DEMO-TXN-` is reserved too, but it is a label, not the selector.
- Movements are found by the `simulated` flag on the stored event, never by the reference, so a signed
  rehearsal whose reference begins the same way cannot be mistaken for a demonstration.
- There is no verification. A movement is not a delivery from a bank, so there is no signature to check.
- The signature state is `null`, which means unchecked and must never be displayed as verified.
- Delivery figures exclude it. Bank health counts and the platform totals ignore every simulated event.
- The activity list includes it, with `"simulated": true` so the interface can label it.

`POST /api/v1/admin/demo/transactions` accepts
`{"bankId":"kcb","direction":"Credit","amount":2500.00,"narration":"Supplier payment"}`. `bankId`
accepts `kcb`, `stanbic` or `ncba` and defaults to `kcb`; `direction` accepts `Credit` or `Debit`
and defaults to `Credit`; `amount` defaults to `1000.00` and has to be greater than zero and below
`100000000`. A movement that fails those rules is refused with HTTP 400. Every accepted movement
runs the same ingestion, deduplication and normalisation path a bank notification runs.

## 4. Business surface

Owner: the business user interface developer. Not implemented in this repository.

Customer facing links to each bank, which the administrator does not need, are collected in
[Consumer bank links](consumer-bank-links.md).

- `GET /api/v1/business/dashboard`: own summary (income, expenses, net flow, outstanding collections).
- `GET /api/v1/business/accounts`: own accounts with balances.
- `GET /api/v1/business/accounts/{accountId}`: one account with its balance and recent movements.
- `GET /api/v1/business/transactions`: own transactions, with full detail and amounts.
- `GET /api/v1/business/collections`: expected, received, outstanding, overdue and partially paid.
- `GET /api/v1/business/budgets`: planned against actual, with variance.
- `GET /api/v1/business/reconciliation`: own matches and exceptions.
- `GET /api/v1/business/reports/{reportId}`: own reports.

## 5. Shared conventions

- Requests and responses are `application/json`, with camelCase field names.
- Timestamps are ISO-8601 with an offset, for example `2026-09-15T09:53:21Z`.
- Money is a number plus a `currency` field, never pre-formatted text.
- Identifiers are strings, so they can grow.
- Errors use Spring's default shape: `timestamp`, `status`, `error`, `path`.
- Pages use `page`, `size`, `totalElements` and `content`.
- Authentication is `Authorization: Bearer <token>`. The backend validates the token and reads the
  organisation claim from it.

## 6. Ownership for the merge

This repository owns the bank integration module, the admin endpoints and the webhook endpoints. The
business interface developer owns the business endpoints and the business user interface. Both agree
on any contract change before either merges. The database schema has one owner, because two people
editing migrations produces conflicts.

## 7. Keeping the merge mechanical

1. One branch per person. Review before merging to `main`.
2. Protect `main` so nobody can force push over another person's work.
3. Do not edit the other person's files. If an endpoint is missing, ask for it rather than adding a
   second one.
4. Change this document first, then the code. The diff on this file is the review checklist.
5. Run the backend tests before merging. They currently cover the health list, notification receipt,
   duplicate suppression, the connection test and the landing page.

## Revision history

- **1.1, 18 September 2026.** NCBA notification endpoint added. The per bank verification setting is
  stored and enforced, so the admin toggle is the authority.
- **1.0, 15 September 2026.** Initial contract: three surfaces, tenancy rule, admin and business
  endpoints.
