# Customer frontend: account onboarding

Registration and login connect to the repository's identity service. The old hosted-sample URL, sample authentication mode, seeded personas and in-memory financial API have been removed.

## Start

Use the Node engine declared in `package.json` and npm 11+. Start the backend as described in [backend/README.md](../backend/README.md), then:

```powershell
cd C:\Users\admin\SM-Intelligence\web
npm ci
npm start
```

Open http://localhost:4200. The Angular server uses port 4200 and the authentication adapter uses loopback port 4301. Stop both with Ctrl+C. `WEB_PORT` and `AUTH_ADAPTER_PORT` can select other ports; an occupied port is reported instead of silently changing it.

`IDENTITY_API_URL` defaults to `http://localhost:8080` (the gateway). To target identity directly, set it to `http://localhost:8081` before running `npm start`. Remote URLs require HTTPS. No old hosted backend is selected implicitly.

## Current behavior

- Registration sends the correct identity fields, including Individual/Organization, and signs the user in from the returned JWT after verifying it through `/api/auth/me`.
- Login and page refresh load the actual backend profile and stable user UUID. Passwords and JWTs are never returned to browser code by the adapter.
- Logout deletes the adapter session and clears its HttpOnly cookie. JWTs expire in the backend; there is no backend refresh-token or global revocation endpoint yet.
- The identity database owns user records. Restarting the adapter requires signing in again, but does not delete registered backend users.
- Users without an active saved account are redirected to `/setup`, including direct workspace links and refreshes. Saving the first account unlocks the dashboard. Onboarding progress comes from the accounts database, not browser storage.
- Once set up, the Financial Overview page (`web/src/app/dashboard.ts`) reads real balances and transactions from accounts-service/transactions-service through `GET /api/dashboard` (see [API-CONTRACT.md](API-CONTRACT.md)). Credit outstanding is separate from available cash. A user with no `Transaction` rows yet sees zeros, not sample data — those rows are not created automatically from a live bank notification today. Budgets and investments still display 'Not connected yet'. No temporary or sample financial records are created. Profile is read-only; appearance remains a device preference.

This adapter is for loopback development. Production requires a deployed session layer, HTTPS/Secure cookies and persistent session management; a static frontend build by itself does not implement the adapter endpoints. See [API-CONTRACT.md](API-CONTRACT.md).

## Invoice capture

Open **Invoices** from workspace navigation (on mobile, under **More**). Take a photo or upload JPEG, PNG or PDF (10 MB maximum). English OCR runs locally in the browser with Tesseract; PDF.js reads PDF text or renders scanned pages for OCR. The first OCR use downloads language data from the Tesseract CDN; document contents are not sent there. PDFs are limited to five pages for extraction. OCR is best-effort: review all suggestions, fill missing values, then confirm the review checkbox and save. You can enter details manually if recognition fails.

Saving uploads the original file and reviewed fields to the transactions service through the authenticated adapter and gateway. Pending invoices persist across restarts and appear on both Invoices and Transactions. Totals are grouped by currency and cover the loaded records. Bank balances and posted transactions remain separate. See backend/README.md for starting the additional service.

**Preview** opens the saved invoice inside a read-only dialog without downloading it. Images display directly; PDFs have Previous/Next page controls. Close the dialog or press Escape to return to the list.

**Delete** asks for confirmation, then permanently removes your saved invoice, its attachment and its pending entry. Totals refresh after deletion. Restart the transactions service and frontend development server after updating to enable the DELETE endpoint and adapter route.

You can also copy an invoice image or screenshot and paste it into **Or paste an invoice** with Ctrl+V, or click **Paste from clipboard**. The clipboard button needs HTTPS or localhost and may request browser permission. Pasted images use the same 10 MB limit, extraction and review flow. Text and links are not accepted as invoice attachments; use Upload a file for PDFs when your browser cannot paste files.

For testing on a phone connected to the same trusted Wi-Fi, set the following in the frontend PowerShell terminal before `npm start`, replacing the IP with your Windows machine's LAN IPv4 address:

```powershell
$env:WEB_HOST = '0.0.0.0'
$env:WEB_ORIGIN = 'http://192.168.1.10:4200'
npm start
```

Open that exact address on the phone and allow port 4200 on your Windows private network if prompted. The backend and adapter stay on their existing addresses; only Angular is exposed to the LAN. This HTTP LAN address supports file upload, but browsers block live camera access there.

**Take a photo** opens a live camera preview and asks for camera permission. The rear camera is preferred; use **Switch camera** when needed. **Capture photo** stops the camera and shows a still preview. Choose **Retake** or **Use photo** to send it to invoice extraction. Closing the dialog, leaving the page or taking a photo releases camera access. No microphone is requested.

Live camera works on `http://localhost:4200` on the PC, or on a trusted HTTPS origin. For phone testing, use a certificate that covers your PC's LAN address and whose issuing CA is trusted on the phone; bypassing a self-signed certificate warning is not a reliable substitute. With those certificate files available:

```powershell
$env:WEB_HOST = '0.0.0.0'
$env:WEB_HTTPS = 'true'
$env:WEB_ORIGIN = 'https://192.168.1.10:4200'
$env:WEB_SSL_CERT = 'C:\path\to\lan-certificate.pem'
$env:WEB_SSL_KEY = 'C:\path\to\lan-key.pem'
npm start
```

Replace the IP and paths with your own, then open the HTTPS address on your phone and allow camera permission. Existing HTTP and HTTPS sessions are separate, so sign in again. Production still requires a deployed session layer and secure cookies. Remove these environment variables to return to the usual localhost setup.

## Checks

```powershell
npm run test:auth
npm test -- --watch=false
npm run build
```

## Account onboarding services

Start PostgreSQL with `docker compose -f backend/docker-compose.yml up -d postgres-accounts` and run `./backend/mvn.ps1 -pl accounts-service spring-boot:run` from the repository root. The gateway forwards accounts to port 8082. Restart `npm start` after adapter changes (this requires signing in again). `ACCOUNTS_API_URL` optionally overrides the accounts upstream; when identity points directly to 8081, set `ACCOUNTS_API_URL=http://localhost:8082`. Account creation stores only a masked identifier and an owner-scoped fingerprint of the account number.
