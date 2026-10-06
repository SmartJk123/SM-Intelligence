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

## 3. Cryptographic Verification & Security

### SHA256withRSA Signature Verification
KCB BUNI delivers an HTTP `Signature` header containing a Base64-encoded `SHA256withRSA` signature of the raw request body.

The `bank-integration-service` verifies incoming IPNs against the configured KCB public key:
```env
# Inside SM-Intelligence/backend/.env
KCB_SIGNATURE_VERIFICATION=true
KCB_SIGNATURE_HEADER=Signature
KCB_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n..."
```

### Ingestion & Processing Pipeline
1. **Ingestion:** `KcbWebhookController` receives `POST /api/v1/webhooks/kcb`.
2. **Cryptographic Validation:** `KcbSignatureVerifier` verifies the RSA signature using `SHA256withRSA`. If invalid or missing, it fails closed with `401 Unauthorized`.
3. **Idempotency & Deduplication:** `WebhookIngestionService` checks `externalEventId` (from `transactionReference` or `transactionID`). Repeated bank redeliveries are acknowledged idempotently without double-posting to the ledger.
4. **Normalization:** `NormalizedTransactionService` parses the JSON body into a standard `NormalizedTransactionEntity` (supporting numeric amounts, comma-formatted strings, and multi-tenant account resolution).
5. **Downstream Propagation:** The normalized transaction is recorded and dispatched asynchronously to downstream ledger and notification services.

## 4. Single-Gateway Architectural Principle

In our microservices architecture, **`bank-integration-service` is the sole, authoritative gateway for all bank webhooks.**
* External banks (KCB, NCBA, Stanbic, Equity) communicate **exclusively** with `bank-integration-service:8090`.
* Downstream services (such as `accounts-service`, `transactions-service`, web apps, and admin portals) **must never** expose independent webhook endpoints.
* Instead, downstream services consume verified, deduplicated, and normalized financial movements via internal REST APIs or asynchronous event channels.
