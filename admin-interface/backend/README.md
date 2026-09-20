# SmartMoney Intelligence API

Spring Boot backend for the SmartMoney Intelligence platform. It implements the Stanbic, KCB and
NCBA bank integrations: the OAuth token flows, the connection tests, the instant payment
notification endpoints, the signature checks, and the admin API the Angular interface reads.

## Document control

| Field | Value |
| --- | --- |
| Document type | Service README |
| Version | 0.5.0 |
| Status | Working scaffold, verified locally |
| Last updated | 18 September 2026 |
| Related documents | [KCB real time trial](../../docs/kcb-real-time-trial.md), [Stanbic integration setup](../../docs/stanbic-integration-setup.md), [NCBA integration setup](../../docs/ncba-integration-setup.md), [API contract](../../docs/api-contract.md), [Public endpoint and trial runbook](../../docs/public-endpoint-and-trial-runbook.md) |
| Prerequisites | Java 21, Maven 3.9 or later |

## Requirements

| Tool | Version used |
| --- | --- |
| Java | 21 |
| Maven | 3.9.11 |
| Spring Boot | 3.5.6 |
| Database | H2 by default, PostgreSQL through the `postgres` profile |

## Running it

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\backend"
.\run-local.ps1
```

That script loads `.env.local`, prints the credential state and the addresses to give the bank,
then starts the service on `http://localhost:8080`. Add `-Offline` if Maven should not contact a
repository. If the script is refused with a message about running scripts being disabled, allow it
for the current window:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Build a runnable jar:

```powershell
mvn -DskipTests package
java -jar target/smartmoney-api-0.1.0.jar
```

Run the tests:

```powershell
mvn test
```

Twenty one tests across four classes cover the callback reachability check, the admin API, the
Stanbic and KCB signature verifiers, and the NCBA endpoint. Six of them exercise the admin API end
to end: the health list, accepting a notification, ignoring a repeated notification, the simulated
notification path, the connection test without credentials, and the landing page. Ten drive NCBA:
the documented hash, the probe, a credit, a debit that arrives as a negative amount, a duplicate, a
wrong hash, a wrong password, a body that is not XML, and the two settings tests.

The build sets `-Djdk.attach.allowAttachSelf=true` on the test JVM in `pom.xml`. Mockito and
Byte Buddy reach the running JVM through the JDK attach API, and some Windows and CI
environments refuse that self attach. Removing the flag makes the Mockito based tests fail
with the message "Could not self-attach to current VM using external process".

The same `argLine` caps the test JVM at 1 GB of heap, 384 MB of metaspace, a 192 MB code cache and
two compiler threads. Without a cap the fork sizes itself from installed memory, and on a machine
with no paging file it dies before the first test starts. The surefire summary then reads
`Tests run: 0` and the only real explanation is in the dumpstream and in `hs_err_pidNNNN.log`:
`There is insufficient memory for the Java Runtime Environment to continue`, followed by a native
`malloc failed ... Error detail: Chunk::new`. With the caps a full run takes about 30 seconds.
Adding a paging file removes the ceiling entirely. See
[Public endpoint and trial runbook](../../docs/public-endpoint-and-trial-runbook.md).

## Exposing the service to a bank

```powershell
.\start-tunnel.ps1
```

That opens a Cloudflare tunnel to port 8080, reads the public address back, writes it to
`PUBLIC_BASE_URL` in `.env.local` and prints the addresses to hand to each bank. Every other line in
the credentials file is left as it was. Restart the API afterwards so it picks the value up.

| Command | Use |
| --- | --- |
| `.\start-tunnel.ps1` | Quick tunnel. A random address that changes on every start |
| `.\start-tunnel.ps1 -Hostname sm-intelligence.example.com` | Named tunnel. One address across restarts, so a registered callback stays valid |
| `.\start-tunnel.ps1 -PublicUrl https://example.com -NoTunnel` | Set the value only, when the tunnel is managed elsewhere |
| `.\start-tunnel.ps1 -NoTunnel` | Print what `PUBLIC_BASE_URL` holds now, and change nothing |

## Connection tests are remembered

The result of the last connection test per bank is written to the `bank_connection_test` table as
well as held in memory, and it is read back at start up. The API column on the integrations page
therefore survives a restart.

Before this the results lived only in memory, so every restart showed all four banks as never
tested. The page then reported zero connected banks and zero healthy APIs even where a test had
passed an hour earlier, which reads like a fault rather than a cold start. A stored result still
ages: the interface shows when it was taken, and a test is still the only way to confirm a bank is
reachable now.

## The demonstration account

A bank sandbox cannot show a real account moving, so the platform carries one synthetic account for
demonstrations. It is the only place on the admin surface where an amount appears, it is labelled as
simulated wherever it shows, and its movements are kept out of every bank delivery figure.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/demo/account` | The reserved account number and name |
| GET | `/api/v1/admin/demo/transactions` | Movements, newest first |
| GET | `/api/v1/admin/demo/summary` | Money in, money out, net, in total and for today |
| POST | `/api/v1/admin/demo/transactions` | Record one movement |

```powershell
# money in
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"bankId":"kcb","direction":"Credit","amount":25000,"narration":"Demo funds received"}' `
  -Uri http://localhost:8080/api/v1/admin/demo/transactions

# money out
Invoke-RestMethod -Method Post -ContentType 'application/json' -Body '{"bankId":"kcb","direction":"Debit","amount":4000,"narration":"Demo payment out"}' `
  -Uri http://localhost:8080/api/v1/admin/demo/transactions

# what moved
Invoke-RestMethod http://localhost:8080/api/v1/admin/demo/summary
```

In the admin interface this is the Transactions screen: pick a bank, set an amount, then press Money
in or Money out. Each press runs one notification through the same pipeline a bank uses, and the
screen reloads what the backend stored rather than what the browser hoped it stored.

Two reserved values hold it apart from real traffic. The account number is `1000000001` and the
reference prefix is `DEMO-TXN-`. Bank health counts and the platform statistics ignore both, so a
demonstration never inflates what a bank is reported to have delivered. The activity list does show
the movements, each carrying `"simulated": true`, which is what draws the Demo label next to it.

The list is selected by that flag, not by the reference. An earlier version filtered on the prefix
and quietly pulled in genuinely signed KCB rehearsals whose references happened to start with the
same letters, which made the demo account look busy with another bank's traffic.

## Rehearsing a KCB notification

KCB signs each notification with SHA256withRSA over the request body, in a `Signature` header. You
can rehearse that whole path without any KCB credentials:

```powershell
.\tools\send-kcb-notification.ps1 -Amount 15000 -Direction Credit -Count 3
```

The first run creates a rehearsal key pair under `keys/`, which git ignores, and prints the public
key. Run it once with `-UsePublicKey` to write that key into `.env.local`, restart the service, and
later notifications are reported as verified rather than unchecked. The script runs on Windows
PowerShell 5.1 and on PowerShell 7.

The full procedure, including the sandbox and production steps, is in
[KCB real time trial](../../docs/kcb-real-time-trial.md).

## Rehearsing an NCBA notification

NCBA pushes XML and carries no signature header. The authentication is a `HashVal` built from the
secret key and nine fields, written as lowercase hexadecimal and then base64 encoded, plus the
username and password in the body. The rehearsal script computes all of it from `.env.local`:

```powershell
.\tools\send-ncba-notification.ps1 -Amount 15000 -Direction Credit -Count 2
.\tools\send-ncba-notification.ps1 -BreakHash
.\tools\send-ncba-notification.ps1 -TransId NCBA-DUPLICATE-TEST
```

The first command is accepted with `OK`. `-BreakHash` sends a hash that cannot be right and is
refused with `FAIL: HashVal did not match the values sent`, which is the refusal path working.
Sending the same `TransId` twice is answered with `OK: Duplicate Notification`, and the second copy
is not stored.

The full procedure, including the request letter values, is in
[NCBA integration setup](../../docs/ncba-integration-setup.md).

## Clearing everything received so far

To start a trial from zero, stop the service and run:

```powershell
.\tools\reset-data.ps1
```

The H2 database is moved into `data\backup` with a timestamp in its name. It is never
deleted, so the previous contents can be restored by moving the file back.

## Configuration

Everything has a working default, so the service starts with no setup. Your credentials live in one
file, `backend/.env.local`, which git ignores. There is no second env file to manage.

Fill it in without editing it by hand:

```powershell
.\set-credentials.ps1
```

It asks for each value and writes the file. A value it does not prompt for is written back at the
end, so running it cannot drop a setting. Then start the service:

```powershell
.\run-local.ps1
```

That script prints what it loaded before starting, so a missing value is obvious immediately.

### The variables

| Variable | Where it comes from | Required |
| --- | --- | --- |
| `STANBIC_CLIENT_KEY` | Client Key on your app page in the portal | Yes |
| `STANBIC_CLIENT_SECRET` | Client Secret on the same app page | Yes |
| `STANBIC_TOKEN_URL` | Token URL on the API product page. Defaults to the sandbox value | Only if it differs from the default |
| `STANBIC_API_KEY` | The `ApiKey` field of the register request. Not issued by the self-service portal | No |
| `STANBIC_ACCOUNT_NUMBER` | Your account number, defaults to `0100013306316` | No |
| `PUBLIC_BASE_URL` | The public address the bank can reach, your tunnel while testing | Only to receive notifications |
| `KCB_CLIENT_KEY` | Sandbox or production Key generated in BUNI | Only for KCB |
| `KCB_CLIENT_SECRET` | The matching KCB Secret | Only for KCB |
| `KCB_PUBLIC_KEY` | KCB public key used to verify the `Signature` header. A PEM block, a path to a `.pem` file, or a base64 X.509 key | Only to verify KCB signatures |
| `NCBA_SECRET_KEY` | Generated here, 16 or more alphanumeric characters. Sent to NCBA and used for every `HashVal` | Only for NCBA |
| `NCBA_USERNAME` | Generated here and sent to NCBA | Only for NCBA |
| `NCBA_PASSWORD` | Generated here and sent to NCBA | Only for NCBA |
| `NCBA_ACCOUNT_NUMBER` | The account NCBA watches. The service does not need it to receive | Only for NCBA to route |
| `NCBA_SIGNATURE_VERIFICATION` | Seeds the verification switch. Defaults to `true` | No |
| `PERMIT_ALL` | `true` while developing. Set to `false` before any shared deployment | No |

### About STANBIC_API_KEY

The register request carries an `ApiKey` field, and the self-service portal does not show one. It
issues the OAuth client key and secret only.

When `STANBIC_API_KEY` is empty the service sends your Client Key in that field and reports the
source as `client key fallback`, so you can attempt registration with what you have. If Stanbic
rejects the registration, ask the Integrations Team for the value, quoting your application name
and account number. The support contact in the specification is kilele@stanbic.com.

The client key and client secret are never written to source control and are never returned to the
browser.

## Addresses to give Stanbic

These are printed on the landing page at the root address.

| Purpose | Address |
| --- | --- |
| Payment notification | `<PUBLIC_BASE_URL>/api/v1/webhooks/stanbic` |
| OAuth redirect | `<PUBLIC_BASE_URL>/oauth/stanbic/callback` |

The redirect address exists because some portals require one when registering an application.
SmartMoney authenticates server to server with the client credentials flow, so the integration
itself does not depend on a redirect.

The notification address answers a GET with a readable confirmation, so it can be verified in a
browser before it is registered.

For sandbox testing, `PUBLIC_BASE_URL` must be a public HTTPS address. Stanbic cannot reach
localhost. Run `.\start-tunnel.ps1`, which starts the tunnel, writes the address into
`PUBLIC_BASE_URL` and prints the addresses to hand over, then restart the service. A quick tunnel
address is replaced on every start, so use `.\start-tunnel.ps1 -Hostname <your hostname>` before
registering anything with the bank.

## Addresses to give KCB and NCBA

| Bank | What they need |
| --- | --- |
| KCB | The notification address `<PUBLIC_BASE_URL>/api/v1/webhooks/kcb`. The callback is set on the application in BUNI, and the Key and Secret come from the same page |
| NCBA | The notification address `<PUBLIC_BASE_URL>/api/v1/webhooks/ncba`, plus the secret key, the username, the password and the account number. See [NCBA integration setup](../../docs/ncba-integration-setup.md) |

Both addresses answer a GET with a readable confirmation. NCBA has no token endpoint and no
registration API, so nothing is called on their side and the connection test reports what this
service has been given instead.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/` | Landing page listing the addresses for the bank |
| GET | `/actuator/health` | Service health |
| GET | `/api/v1/admin/bank-integrations` | Health for every supported bank |
| GET | `/api/v1/admin/bank-integrations/{bankId}` | Health for one bank |
| GET | `/api/v1/admin/bank-integrations/{bankId}/settings` | Stored settings |
| GET | `/api/v1/admin/bank-integrations/{bankId}/credentials` | Which credentials are loaded, never a secret |
| GET | `/api/v1/admin/bank-integrations/{bankId}/webhook-url` | The notification address for that bank |
| POST | `/api/v1/admin/bank-integrations/{bankId}/test` | Run the connection test |
| POST | `/api/v1/admin/bank-integrations/{bankId}/register-callback` | Ask Stanbic to send credit and debit alerts to our endpoint |
| PUT | `/api/v1/admin/bank-integrations/{bankId}` | Save settings |
| POST | `/api/v1/admin/bank-integrations/{bankId}/simulate-notification` | Send a sample notification through the real path |
| GET | `/api/v1/admin/stats` | Real platform statistics for the dashboard |
| GET | `/api/v1/webhooks/{bankId}` | Readable confirmation that the address exists |
| POST | `/api/v1/webhooks/{bankId}` | Receive a notification from the bank |
| GET | `/oauth/stanbic/callback` | OAuth redirect target |

## Testing without Stanbic credentials

You do not need credentials to prove the pipeline works.

```powershell
# 1. Confirm the service is up. Leave this terminal running while you test.
Invoke-RestMethod http://localhost:8080/actuator/health

# 2. Send a sample notification through exactly the path the bank will use
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/admin/bank-integrations/stanbic/simulate-notification

# 3. Or post a notification shaped the way a bank would
$notification = @{ eventId = "TEST-1"; amount = 1500.00; currency = "KES" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/webhooks/stanbic -ContentType "application/json" -Body $notification

# 4. The webhook state moves to HEALTHY once a notification has been received
Invoke-RestMethod http://localhost:8080/api/v1/admin/bank-integrations/stanbic
```

Posting the same `eventId` more than once stores a single event, which is the idempotency rule the
platform depends on.

Two things to watch when running these:

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Failed to connect to localhost port 8080` | The service was not running yet, or the command was run in a terminal after the service stopped | Start the service first and leave that terminal running, then run the commands in a second terminal |
| The command returns nothing, or an error about the host | The URL was pasted with markdown link brackets around it, for example `[http://...](http://...)` | Paste the plain address only, with no brackets |

`127.0.0.1` can be used anywhere `localhost` appears if name resolution is ever a problem on your
machine.

## Connecting the Angular interface

There is nothing to switch. Every screen reads from this service, so start it and then start the
Angular development server. The Bank Integrations page shows a `Backend connected` indicator, and
when the service cannot be reached the pages say so instead of showing placeholder figures.

If the browser is on a port other than 4200, the API still accepts it: CORS is open to every
localhost port while developing. Narrow that in `SecurityConfig` before release.

## Project structure

```text
src/main/java/io/smartmoney/api
    SmartMoneyApplication.java
    ServiceInfoController.java
    config
        PlatformProperties.java
        SecurityConfig.java
    bankintegration
        BankConnector.java            one interface per bank
        BankConnectionSettings.java
        BankConnectionTest.java
        ConnectionStep.java
        IntegrationHealth.java
        PlatformStatsController.java  real statistics for the dashboard
        PlatformStatsService.java
        BankIntegrationController.java
        BankIntegrationHealthService.java
        BankIntegrationSettingsService.java
        BankIntegrationEntity.java
        WebhookEventEntity.java
        WebhookIngestionService.java
        kcb
            KcbProperties.java
            KcbTokenService.java
            KcbConnector.java
            KcbSignatureVerifier.java
            KcbWebhookController.java
        stanbic
            StanbicProperties.java
            StanbicTokenService.java
            StanbicConnector.java
            StanbicSignatureVerifier.java
            StanbicWebhookController.java
        ncba
            NcbaProperties.java
            NcbaConnector.java
            NcbaSignatureVerifier.java
            NcbaWebhookController.java
            NcbaNotification.java
        SignaturePolicy.java          whether a bank verifies its notifications
        XmlFields.java                secure XML reading for the push banks
tools
    send-kcb-notification.ps1         signed KCB notification rehearsal
    send-ncba-notification.ps1        NCBA notification rehearsal, computes the HashVal
```

## What is complete and what is not

| Area | State |
| --- | --- |
| OAuth token retrieval, caching and refresh | Implemented |
| Connection test with four steps | Implemented |
| Notification endpoint, storage and idempotency | Implemented |
| Admin API for the Angular interface | Implemented |
| Settings persistence | Implemented |
| Transaction pipeline | Implemented baseline. Accepted JSON and XML notifications are validated, normalized and persisted once per bank and external event id |
| Real platform statistics for the dashboard | Implemented. Empty platform returns zeros, nothing is seeded |
| Signature verification switch | Implemented. The stored setting is what the webhook endpoints obey, so the toggle in the admin interface changes behaviour without a restart. A partial save no longer resets the other settings |
| Stanbic signature verification | Implemented for HMAC-SHA256; enabled verification fails closed when the header or server-side secret is missing |
| KCB signature verification | Implemented for SHA256withRSA, verified end to end with a rehearsal key pair |
| NCBA connector and notification endpoint | Implemented. The username, the password and the HashVal are checked, a repeated TransID is answered with OK: Duplicate Notification, and anything that fails is refused with a FAIL result rather than an error status |
| Authentication on admin endpoints | HTTP Basic when `PERMIT_ALL` is false. Replace with token based auth before release |
| KCB OAuth client credentials and connection test | Implemented |
| Callback address reachability | Implemented. The webhook step resolves the callback hostname, so a stopped tunnel fails the step instead of being reported as publicly reachable |
| KCB callback registration with BUNI | Not implemented. KCB registers the callback address on the application, see the runbook |
| Equity connector | Not started. Add a class per bank behind `BankConnector` |
| Database migrations | JPA schema update is used for now. Introduce Flyway before production |

## Revision history

| Version | Date | Summary |
| --- | --- | --- |
| 0.5.0 | 18 September 2026 | NCBA connector, XML notification endpoint, HashVal verification and rehearsal script. The signature verification setting is now enforced, and a partial settings save no longer resets the other values. Test count 21 |
| 0.4.0 | 18 September 2026 | Test JVM attach flag set in the build, so a plain mvn test passes on Windows and CI. Test count in this document corrected from six to eleven |
| 0.3.0 | 17 September 2026 | Callback reachability check. The webhook step resolves the callback hostname, so a stopped tunnel fails the step instead of reporting it as publicly reachable |
| 0.2.0 | 15 September 2026 | KCB connector, signature verification and instant payment notification endpoint. Real platform statistics. Seeded figures removed |
| 0.1.0 | 14 September 2026 | Stanbic integration, admin API and notification endpoint |
