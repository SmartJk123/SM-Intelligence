# admin-interface/backend

This folder no longer holds a running service. The bank integration service moved to
[`backend/bank-integration-service`](../../backend/bank-integration-service), and that is the only
copy that runs, with its settings in `backend/bank-integration-service/.env.local`.

What is here:

| Path | What it is |
| --- | --- |
| [`deploy/cpanel`](deploy/cpanel/README.md) | The production webhook receivers for cPanel (PHP), their `.htaccess`, `schema.sql` and config samples |
| `.env.local`, `data/`, `keys/`, logs | Leftovers from before the move. Ignored by git and not read by anything |

## One receiver per bank, on purpose

Each bank notifies us in its own format and proves the notification is genuine in its own way, so each
PHP receiver differs where the bank does and shares the rest (settings, database, duplicates, import feed):

| Receiver | Format | Authentication |
| --- | --- | --- |
| `ncba-webhook.php` | SOAP/XML | HashVal (SHA-256 of the secret key and fields), username and password |
| `kcb-webhook.php` | JSON | RSA `Signature` header, checked with KCB's public key |
| `equity-webhook.php` | JSON (Jenga IPN) | Basic Auth registered with the callback on Jenga HQ |

Each one mirrors the Java controller of the same bank in `backend/bank-integration-service`
(`NcbaWebhookController`, `KcbWebhookController`, `EquityWebhookController`). The bank integration service
imports what the receivers store through their `/export` feeds. When a bank's rules change, change both
the PHP receiver and the Java controller.

Stanbic has no cPanel receiver yet: its notification format and authentication have not been published.
