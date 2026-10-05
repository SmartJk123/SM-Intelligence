# KCB BUNI Webhook Integration Guide

This document outlines the architecture, setup, and implementation details for the KCB BUNI (Sandbox) webhook integration. It is designed to help team members and autonomous agents understand the existing implementation and replicate it in other services like a web app or an admin portal.

## 1. Overview

The `bank-integration-service` acts as the bridge between KCB's BUNI sandbox environment and our internal microservices. When a simulated transaction occurs in BUNI, BUNI sends an Instant Payment Notification (IPN) via an HTTP POST request to our exposed webhook endpoint.

- **Service Port:** `8090`
- **Webhook Endpoint:** `POST /api/v1/webhooks/kcb`
- **Signature Verification:** Disabled for sandbox (controlled via environment variables)

## 2. Local Development & Ngrok Setup

To receive webhooks from BUNI on your local machine, you must expose your local server to the internet using [ngrok](https://ngrok.com/).

### Step-by-Step Ngrok Setup

1. **Start the Bank Integration Service:**
   Ensure the service is running and configured correctly. Use the provided startup script which ensures environment variables (like signature bypass) are loaded:
   ```bash
   cd /home/frank/SmartMoney_intelligence/SM-Intelligence/backend
   ./start-bank-integration.sh
   ```
   *(Note: This runs on port 8090).*

2. **Start Ngrok:**
   In a new terminal window, expose port 8090:
   ```bash
   ngrok http 8090
   ```
   This will output a Forwarding URL, e.g., `https://<random-id>.ngrok-free.app`.

3. **Configure the BUNI Portal:**
   - Log in to the KCB BUNI developer portal.
   - Navigate to your application's webhook/IPN settings.
   - Set the Webhook URL to your ngrok address: `https://<random-id>.ngrok-free.app/api/v1/webhooks/kcb`.
   - Save the configuration.

BUNI will now route transaction notifications to your local running service.

## 3. Implementation Details

### Sandbox Configuration (`.env`)
In the BUNI sandbox, cryptographic signatures sent with IPNs might not match production RSA keys. To prevent our backend from rejecting these payloads with a `401 Unauthorized` error, we disable signature verification locally:

```env
# Inside SM-Intelligence/backend/.env
KCB_SIGNATURE_VERIFICATION=false
```

### How the Service Processes IPNs
1. **Ingestion:** `KcbWebhookController` receives the POST request.
2. **Validation:** If `KCB_SIGNATURE_VERIFICATION` is true, it verifies the RSA signature using KCB's public key. If false, it bypasses this check.
3. **Parsing:** The JSON payload is parsed to extract core details like `transactionId`, `amount`, `currency`, `accountNumber`, etc.
4. **Normalization:** The raw data is converted into a standard `NormalizedTransactionEntity`.
5. **Persistence & Broadcasting:** The transaction is saved to the local database and broadcasted to other microservices (or synced to the mobile app) via message queues or REST API calls.

## 4. Replicating This Implementation in Another App (e.g., Admin Portal)

If you are building a separate admin portal, a web app backend, or a new microservice that needs to listen to KCB webhooks independently, follow these steps:

### A. Create the Webhook Endpoint
Your application needs an unauthenticated, publicly accessible `POST` endpoint to receive JSON payloads.

**Example (Node.js / Express):**
```javascript
const express = require('express');
const app = express();
app.use(express.json());

app.post('/api/webhooks/kcb', (req, res) => {
    const payload = req.body;
    
    console.log('Received BUNI IPN:', payload);
    
    // 1. (Optional for Sandbox) Verify Signature here
    // if (process.env.VERIFY_KCB_SIGNATURES === 'true') { ... }
    
    // 2. Extract Data
    const amount = payload.amount;
    const account = payload.accountIdentifier;
    const txRef = payload.transactionReference;
    
    // 3. Process business logic (e.g., update dashboard metrics)
    
    // 4. Respond with 200 OK quickly to prevent KCB from retrying
    res.status(200).json({ status: 'success' });
});

app.listen(3000, () => console.log('Server running on port 3000'));
```

### B. Handle Security & Signatures
For production, you **must** verify the cryptographic signature sent in the request. For the sandbox environment (BUNI), you must implement a toggle in your environment variables to conditionally disable this check, just like we did with `KCB_SIGNATURE_VERIFICATION=false`.

### C. Acknowledge Promptly
Bank webhooks expect a very fast response (typically a `200 OK` within seconds). If your processing takes a long time, save the raw payload to a database or queue, return `200 OK` immediately, and process it asynchronously in the background.

### D. Ngrok for Testing
Developers working on the admin portal will need to run `ngrok http <PORT>` (matching their service's port) and update the BUNI portal with their own temporary ngrok URL to receive live sandbox notifications during development.

---
**Mobile Troubleshooting Tip:** If the mobile app fails to connect to the backend while testing with an Android device over USB tethering, the `RetrofitClient` has been configured to automatically fall back from `127.0.0.1` (adb reverse) to the host machine's LAN IP. Make sure both devices are on the same Wi-Fi network if adb drops.
