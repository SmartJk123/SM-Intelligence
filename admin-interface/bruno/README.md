# Bruno API collection

[Bruno](https://www.usebruno.com/) keeps its collections as plain text files, so
this folder is versioned with the rest of the repository and a change to an API
request shows up in a pull request like any other change.

## Quick start

Start the backend first, then run this from the `bruno` folder:

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\bruno"
.\run-collection.ps1
```

The helper prints a summary and exits with a non zero code when a test fails. An
empty platform reports 28 requests and 58 tests.

If PowerShell refuses to run the script, allow scripts for that window only:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

## The one mistake that catches everyone out

The Bruno CLI treats the current directory as the collection root. This
collection sits inside a folder called `SmartMoney Sandbox`, so you have to be
inside that folder, not in `bruno` above it. The name contains a space, and a
`cd` that is written without quotes fails like this:

```text
PS> cd admin-interface\bruno\SmartMoney Sandbox
Set-Location: A positional parameter cannot be found that accepts argument 'Sandbox'.
```

The `cd` never happened, so the next command runs one level up and the CLI says:

```text
You can run only at the root of a collection
```

Both messages are the same problem. Quote the path, or use `run-collection.ps1`,
which changes into the collection itself.

## When every request fails and the summary reads 0/0

A summary of `28 (28 Failed)` with `Tests 0/0`, blank status codes in the list and
a duration under 200 ms means nothing answered. A live service takes seconds and
prints a status code on every line.

Start the backend first, in its own window, and leave it running:

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\backend"
.\run-local.ps1
```

`Tests 0/0` is the part to remember. Requests that reached a real service and
failed an assertion would still be counted as tests.

## Running the CLI by hand

```powershell
cd "C:\Users\user\Desktop\ECLECTICS\SM-Intelligence\admin-interface\bruno\SmartMoney Sandbox"
npx --yes '@usebruno/cli@latest' run . --env local -r
```

Two details matter here.

- `-r` is required. The requests live in folders, and without `-r` the runner
  reads the collection root only and reports zero requests inside a passing
  summary, which has tested nothing.
- Keep the single quotes around `'@usebruno/cli@latest'`. Without them
  PowerShell can read the leading `@` as the splat operator.

## Desktop application

1. Open Bruno and choose **Open Collection**.
2. Select `admin-interface/bruno/SmartMoney Sandbox`. The folder is the one that
   holds `bruno.json`.
3. Pick an environment in the top right, `local` or `tunnel`.
4. Start the backend, then run the folders from the top down.

## Environments

| Environment | Base URL | Use |
| --- | --- | --- |
| `local` | `http://localhost:8080` | Everything while the service runs on this machine |
| `tunnel` | Replace with your tunnel host | Proving the bank can reach the notification address |

## What to put in the environment

This is the whole file. Nothing else is needed.

| Name | `local` value | `tunnel` value | Secret |
| --- | --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | `https://your-tunnel-host` | No |
| `bankId` | `kcb` | `stanbic` | No |

There are no credentials in the collection because the service has no
authentication while `PERMIT_ALL` is true. Both values above are safe to commit,
and both are already committed in `environments/`.

## Keeping it safe while collaborating

Bruno collections are plain text, so a value typed into a `.bru` file is a value
committed to the repository and visible to everyone with access, including in
the git history after it is removed. The rules this collection follows:

- No secret has a value in any `.bru` file. There are none today, and the rule
  is what keeps it that way when authentication is switched on.
- `admin-interface/bruno/.gitignore` excludes `.env`, `.env.*` and any
  `*.local.bru`, so a personal file or a filled in credential file cannot be
  committed by accident.
- Use `*.local.bru` for an environment file that holds your own values. It is
  ignored, and the shared files stay clean.
- The bank credentials for the service never come near this folder. They live in
  `backend/.env.local`, which the backend has its own ignore rule for.

To confirm a file is protected before committing anything, ask git directly:

```powershell
git check-ignore -v "admin-interface/bruno/SmartMoney Sandbox/.env"
```

That prints the ignore rule that matched. No output means the file is not
ignored and would be committed.

### When authentication is switched on

Create `SmartMoney Sandbox/.env` by copying `.env.example`, fill in the two
values, and reference them from the environment file rather than typing them in:

```text
adminUser: {{process.env.SM_ADMIN_USER}}
adminPassword: {{process.env.SM_ADMIN_PASSWORD}}
```

The CLI loads `.env` from the collection root, interpolates those names, and
masks the values in its output. Switching the requests from `auth: none` to
`auth: basic` is the remaining step. That part has not been exercised yet,
because the service runs unauthenticated today.

Bruno has a secret store for the desktop application as well. Whichever route you
take, the test is the same: the value must not appear in any `.bru` file.

## What the requests cover

| Folder | Purpose |
| --- | --- |
| `00 Smoke` | Health, the landing page, the dashboard statistics |
| `01 Integrations` | The calls the Bank Integrations page makes, including the notification address to give the bank |
| `02 Credentials` | Which credentials the service has loaded, without returning a value |
| `03 Connection Tests` | The four step connection test, which is what moves the API and webhook tiles |
| `04 Webhooks` | The notification address answering, plus a sample notification in each bank's shape. The NCBA sample carries a hash that cannot be right, so a FAIL result is the expected answer while verification is on |
| `05 Callback Registration` | Registering the callback with Stanbic, and the empty answer KCB is expected to give |
| `06 Events` | The recent activity list |

Two requests in `04 Webhooks` can legitimately answer `401`, and the NCBA sample
answers `200` with a `FAIL` result. Read the note in the request before treating
either as a fault.

## Files in this folder

| File | Committed | Purpose |
| --- | --- | --- |
| `run-collection.ps1` | Yes | Runs the collection with the right flags and the right working directory |
| `.gitignore` | Yes | Keeps credentials out of the repository |
| `SmartMoney Sandbox/` | Yes | The collection itself |
| `SmartMoney Sandbox/.env.example` | Yes | The shape of the file to copy, with no values in it |
| `SmartMoney Sandbox/.env` | No | Your own values, ignored by git |
