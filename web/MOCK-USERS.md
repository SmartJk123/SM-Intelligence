# Local fictional user scenarios

Run npm run start:mock from web. Restart to reset all in-memory data and refresh relative dates. All accounts use SamplePass123! These records do not connect to banks or execute payments.

| Email | Scenario |
| --- | --- |
| realistic@example.com | Nia Kamau: salary, freelance income, savings interest, rent, groceries, commuting, utilities and credit purchases across all four banks. |
| retail@example.com | Acacia Retail Demo: larger balances, regular sales settlements, inventory spending and premises costs. |
| individual@example.com | Existing compact personal sample. |
| business@example.com | Existing compact organization sample. |
| new@example.com | First-time account setup. |
| empty@example.com | Empty workspace. |
| slow@example.com | Delayed Overview responses. |
| error@example.com | First Overview request fails; retry succeeds. |

The two realistic personas have 90 days of deterministic fictional activity, including today and yesterday, posted receipts/payments, pending incoming/outgoing payments, cancelled/failed attempts, bank-specific budgets, low-balance warnings, and a maturity in three days. A mixture of read and unread notifications is included. Opening balances plus posted activity determine current balance snapshots; pending/failed/cancelled transactions do not change them. Credit debt remains separate from cash. Active warnings remain undated, and investments have no bank association under the current API model. This simulates static user histories, not live payment processing.
