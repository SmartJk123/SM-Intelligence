---
name: sm-intelligence
description: Work in the SmartMoney Intelligence repository, an Angular admin interface for Kenyan bank integrations backed by a Spring Boot service. Use when changing the admin interface, the bank integration service, bank credentials or webhook handling, the Bruno collections, or the project documentation.
---

# SmartMoney Intelligence

An operator platform for Kenyan business banking. The admin interface reports the
health of each bank integration and the notifications the platform has received.
The customer facing app and the domain microservices are separate products in the
same repository.

## Which branch work goes on

This is deliberate. Putting a change on the wrong branch is the most common way
work is lost here.

| What you are changing | Branch |
| --- | --- |
| The Angular admin interface: `admin-interface/src`, `public`, its build, its README | `Admin-Interface` |
| The bank integration service: `backend/bank-integration-service`, bank credentials, webhook handling, the gateway routes, `render.yaml`, the Docker builds | `feature/bank-integrations` |

Anything that crosses the two lands on both branches in the same working session
and is announced rather than merged silently:

- `admin-interface/src/app/core/` mirrors the service's request and response
  shapes, field for field.
- The webhook and admin paths have to match on both sides.
- `backend/api-gateway/src/main/resources/application.yml` routes
  `/api/v1/webhooks/**`, `/api/v1/admin/bank-integrations/**` and
  `/api/v1/admin/stats/**` to port 8090. A new admin path has to be added there
  too, or it works directly and 404s through the gateway.

## Layout

| Path | Contents |
| --- | --- |
| `admin-interface/` | Angular 21 admin interface, Tailwind v4, standalone components and signals |
| `backend/bank-integration-service/` | The Spring Boot service the admin interface reads and the banks post to |
| `backend/` | Maven reactor: gateway, identity, accounts and the other domain services |
| `web/` | The customer facing Angular app, owned by the other developer |
| `bruno-collections/` | API examples for the domain services |
| `database_schema/`, `erd.md` | Schema map |

## Running it

The admin interface needs one service, on port 8090. Nothing else has to run.

```powershell
# the service. Reads .env.local, prints the credential state, listens on 8090
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\backend\bank-integration-service"
.\run-local.ps1

# the interface, in another window
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface"
npm start
```

`admin-interface/src/app/core/api.config.ts` points at
`http://localhost:8090/api/v1`. The service allows any `localhost` origin through
CORS, so the browser can call it directly. Ports 8080 to 8089 belong to the
customer platform and are not needed for the integration screens.

## Testing the banks

There is one service for all banks, not one port per bank.

```powershell
# the addresses a bank posts to. A GET returns a readable banner
Invoke-WebRequest http://localhost:8090/api/v1/webhooks/kcb
Invoke-WebRequest http://localhost:8090/api/v1/webhooks/ncba
Invoke-WebRequest http://localhost:8090/api/v1/webhooks/stanbic
# equity answers 404 by design: no connector is implemented

# what the interface reads, per bank: health, credentials, settings, address
Invoke-RestMethod http://localhost:8090/api/v1/admin/bank-integrations

# the four step probe behind the Test connection button
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{}' `
  -Uri http://localhost:8090/api/v1/admin/bank-integrations/ncba/test
```

Expected state on a fresh database, and what each line means:

| Bank | Probe result | Meaning |
| --- | --- | --- |
| NCBA | passes, or fails at Notification address | Fails only when the public address does not resolve |
| KCB | fails at Token request | No key and secret generated in BUNI yet |
| Stanbic | fails at Token request | `STANBIC_TOKEN_URL` is empty, so the request 404s |
| Equity | fails at Connector | There is no Equity connector to call |

`apiStatus` comes from the last connection test and is stored in the
`bank_connection_test` table, so it survives a restart. `webhookStatus` comes from
when a notification last arrived: HEALTHY under 30 minutes, WARNING under 2
hours, ERROR beyond that, PENDING when nothing has ever arrived.

## Credentials

Everything lives in `backend/bank-integration-service/.env.local`, which git
ignores, so it never travels between branches or machines. The committed template
is `.env.example` beside it, and `set-credentials.ps1` writes the file without a
byte order mark. Never print a value from it, and never commit it.

`keys/` holds the local KCB rehearsal key pair and is ignored for the same reason.

`tools/new-ncba-letter.ps1` builds the NCBA request letter from the file. It
writes outside the repository by default, because the letter carries the secret
key.

## Facts worth not rediscovering

- The domain is `globalsmartspaces.com`, plural. `globalsmartspace.com` does not
  exist, and neither does any record for
  `sm-intelligence.globalsmartspaces.com` yet.
- The service uses an H2 file database locally. A deployed instance must run the
  `postgres` profile, because a container filesystem is ephemeral and would lose
  every notification on each deploy.
- `render.yaml` has no service for the bank integration unless one has been
  added. Its `DB_URL` wiring for the other services passes a `postgres://` URI
  where a JDBC URL is expected, which is worth checking before trusting a deploy.
- `backend/pom.xml` targets release 21 and passes
  `-Djdk.attach.allowAttachSelf=true` to surefire. Without the flags every mocked
  test fails with "Could not self-attach to current VM using external process".
- The gateway on this branch does not route the service, because it moved to the
  microservices and the interface calls 8090 directly.
