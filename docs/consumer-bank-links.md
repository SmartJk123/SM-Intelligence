# SmartMoney Intelligence: Consumer Bank Links

The quick access links for a bank's own customers: internet banking, app store listings and the
USSD codes the banks publish.

These belong to the business user interface, not the admin surface. The administrator is not the
front user. An administrator registers applications, copies a Token URL and generates keys, which
is what the developer portals on the Bank Integrations page are for. This list is kept here so the
front-end team can pick it up rather than have it disappear.

## Document control

| Field | Value |
| --- | --- |
| Document type | Reference list |
| Version | 1.0 |
| Status | Handover. Nothing in the admin interface links to these any more |
| Owner | Eclectics, SmartMoney Intelligence |
| Last updated | 20 September 2026 |
| Related documents | [API contract](api-contract.md), [Service README](../backend/README.md) |
| Prerequisites | None |

## The links

| Bank | Online banking | Google Play | App Store | USSD |
| --- | --- | --- | --- | --- |
| KCB | <https://ke.kcbgroup.com/> | `com.kcb.mobilebanking.android.mbp` | `id6738310699` | not published |
| NCBA | <https://www.ncbagroup.com/> | `com.nicbank.android` | `id725534467` | not published |
| Equity | <https://equitybank.co.ke/> | store search, see the note below | store search, see the note below | not published |
| Stanbic | <https://www.stanbicbank.co.ke/> | `com.cfcstanbicbank.app` | `id1012923421` | `*208#` |

Full store addresses:

```text
KCB     https://play.google.com/store/apps/details?id=com.kcb.mobilebanking.android.mbp
KCB     https://apps.apple.com/ke/app/new-kcb-mobile/id6738310699
NCBA    https://play.google.com/store/apps/details?id=com.nicbank.android
NCBA    https://apps.apple.com/ke/app/ncba-now/id725534467
Stanbic https://play.google.com/store/apps/details?id=com.cfcstanbicbank.app
Stanbic https://apps.apple.com/ke/app/stanbic-bank-kenya/id1012923421
```

## Before any of this is published

- KCB, NCBA and Stanbic came from those banks' own websites when the list was assembled in
  September 2026. Store identifiers change when an app is republished, so confirm each listing
  before it is shared with customers.
- Equity's links are store searches, not listings. Equity's site could not be reached when the list
  was assembled, so its two entries open a search and must be replaced with the exact listings.
- No bank here publishes a customer USSD code except Stanbic. Do not invent one for the others.
- A customer-facing screen should not offer a USSD code it cannot dial. On a phone the code can be
  offered as a tel: link; on a desktop it can only be copied.
