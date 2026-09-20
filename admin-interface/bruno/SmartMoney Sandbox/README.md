# SmartMoney Sandbox

A Bruno collection for the SmartMoney Intelligence API. It is safe to run at any
time: it holds no credentials, it starts no payment, and the only request that
contacts a bank is `05 Callback Registration/stanbic-register-callback.bru`.

## Before the first run

Start the backend and leave that window open.

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\backend"
.\run-local.ps1 -Offline
```

Then select the `local` environment in Bruno.

## Running the whole collection

From a terminal, use the helper in the folder above. It changes into this
collection and passes the flags, so the working directory cannot be wrong.

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\bruno"
.\run-collection.ps1
.\run-collection.ps1 -Environment tunnel
.\run-collection.ps1 -Folder '04 Webhooks'
```

Running the CLI directly works too, but you must already be inside this folder
and you must pass `-r`.

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\bruno\SmartMoney Sandbox"
npx --yes '@usebruno/cli@latest' run . --env local -r
```

If a run reports zero requests and still passes, `-r` was missing. If it reports
`You can run only at the root of a collection`, the working directory is the
`bruno` folder rather than this one. Both come from the folder name containing a
space, which makes an unquoted `cd` fail quietly.

A healthy run of an empty platform reports 28 requests and 58 tests, all
passing. From the desktop application, run the folders in number order.

## What the environment holds

| Name | `local` | `tunnel` | Secret |
| --- | --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | your tunnel host | No |
| `bankId` | `kcb` | `stanbic` | No |

That is all. The service has no authentication while `PERMIT_ALL` is true, so
the collection needs no credentials. Do not add a value to a `.bru` file: these
files are plain text and are committed. See the folder above for how credentials
are handled once authentication is switched on.

## Reading the results

| You see | It means |
| --- | --- |
| `tokenReady: false` on a credential request | The service has no usable credentials for that bank. The `apiKeyAdvice` field says what is missing |
| `publiclyReachable: false` on a notification URL request | The address is localhost, or its hostname no longer resolves. Either way, no bank can deliver a notification |
| A failed `token` step in a connection test | The bank refused the credentials or the token address is wrong. The step detail carries the bank's own message |
| `401` on a sample notification | Signature verification is on and no signature was supplied. That is the endpoint failing closed, not a fault |

`publiclyReachable` is true only when the address is public HTTPS and its host
resolves, so it reports false for a stopped tunnel as well as for localhost. It
is a resolution check, not a delivery test, so it still does not prove that
anything is listening on the other end. A quick tunnel stops when its terminal
closes and the old address never comes back, so check the address itself before
giving it to a bank.

```powershell
Invoke-WebRequest -Uri "https://your-tunnel-host/api/v1/webhooks/kcb" -UseBasicParsing
```

When that command cannot resolve the host, the tunnel is down. Start it again,
put the new address in `PUBLIC_BASE_URL`, and restart the service.

## Switching to a tunnel

Put your tunnel host in the `tunnel` environment, then run
`04 Webhooks/kcb-probe.bru` against it before registering anything with a bank.

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\bruno"
.\run-collection.ps1 -Environment tunnel -Folder '04 Webhooks'
```
