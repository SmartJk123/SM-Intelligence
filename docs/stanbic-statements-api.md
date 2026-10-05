# SmartMoney Intelligence: Standard Bank Statements API Assessment

What the Standard Bank Statement Management API does, how it would fit SmartMoney Intelligence, and
what has to be confirmed with Standard Bank before any work starts.

## Document control

| Field | Value |
| --- | --- |
| Document type | Assessment |
| Version | 1.0 |
| Status | Under review. Invited to the next rollout phase, reply due by close of business 2 October 2026 |
| Owner | Eclectics, SmartMoney Intelligence |
| Last updated | 28 September 2026 |
| Related documents | [Stanbic integration setup](../admin-interface/docs/stanbic-integration-setup.md), [Public endpoint and trial runbook](public-endpoint-and-trial-runbook.md) |
| Source | Standard Bank API Marketplace, Statement Management API page, and `nbp-document-api.yaml` version 3.4.0 (asset `nbp-document-api`, API version v3) |

## 1. Background

Standard Bank ran a closed pilot of the Statements API and, on 25 September 2026, invited us to
review it and ask for a 30 minute introductory meeting. The lead is closed if there is no reply by
close of business 2 October 2026.

This is a separate product from the Stanbic sandbox integration in
[Stanbic integration setup](../admin-interface/docs/stanbic-integration-setup.md). That one uses OAuth with a Token URL.
This one sits behind an IBM API Connect gateway with its own client id and secret, so it needs its
own subscription and its own keys.

## 2. What the API is

A statement document service. It lists the statements available for an account and period, then
downloads a chosen statement as a file. It does not return individual transactions as data, and it
has no webhook or push notification of any kind.

| Environment | Base URL |
| --- | --- |
| Sandbox, per the Marketplace | `https://api-gatewaynp.standardbank.co.za/npextorg/extnonprod/nbp-document` |
| Server named in the YAML | `https://sit-gateway.apinp.standardbank.co.za/sit/sit/nbp-document` |
| Mocking service | On the Marketplace page, no credentials needed |

Use the Marketplace sandbox address. The YAML server is an internal test gateway.

### Operations

| Operation | Method and path | Purpose |
| --- | --- | --- |
| `getDocumentCategoriesByChannel` | `GET /document-services/statements/v2/account/statements/categories/{channelId}?userId=` | Statement categories for a channel |
| `getDocumentCategories` | `GET /document-services/statements/v2/account/statements/categories?userId=` | The same without a channel. Marked deprecated on the Marketplace page, so avoid it |
| `postAccountDocuments` | `POST /document-services/statements/v2/account/statements?userId=` | Lists available statements for the posted accounts, dates and categories |
| `getAccountDocumentsDownload` | `GET /document-services/statements/v2/account/statements/download/{uid}?userId=` | Downloads a statement by its `uid` |
| `downloadDocumentType` | `GET /document-services/statements/v2/account/statements/download/{uid}/document/{documentUid}?userId=` | Downloads one format of a statement |
| `downloadWithoutListing` | `POST /document-services/statements/v3/account/statements/download/email` | Downloads by category and date range in one call, and can email the document |
| `getCachedRows` | `POST /content-services/lookups/cache` | Lookup table access. Purpose not documented |

### The normal flow

1. `getDocumentCategoriesByChannel` to learn the category ids for our channel.
2. `postAccountDocuments` with `accountIds`, `dateFrom`, `dateTo`, `category`, `channelId`, and
   paging through `size` with `from` or `cursor`. Each result carries a `uid` and a `documents` list,
   one entry per available format, each with its own `uid` and `contentType`.
3. `downloadDocumentType` with the statement `uid` and the chosen document `uid`. The response holds
   the file as base64 in `content`, or a `url` to fetch it from.

`downloadWithoutListing` collapses steps 2 and 3 into one call for a single category and period.

### Authentication, as far as the specification states it

| Header | Source |
| --- | --- |
| `X-IBM-Client-Id` | Issued with the subscription |
| `X-IBM-Client-Secret` | Issued with the subscription |
| `authorization` | Required on every call, described only as "validation tokens". How it is obtained is not documented |
| `x-fapi-interaction-id` | Optional UUID for tracing a call |

Every call also requires a `userId` query parameter that the specification does not define.

## 3. Fit for SmartMoney Intelligence

| Need | Covered | Notes |
| --- | --- | --- |
| Real time transaction notification | No | No webhook. The `/api/v1/webhooks/stanbic` endpoint plays no part in this product |
| Transaction data a program can read | Unknown | The examples show `pdf`. Useful only if CSV, Excel or MT940 is offered |
| Historical backfill | Possibly | Past statements by period, if a readable format exists |
| Month end reconciliation | Possibly | Same condition |

It is a supplementary data source at best. It cannot replace a push feed like NCBA or KCB.

## 4. Blockers and open questions

| Item | Why it matters |
| --- | --- |
| "Available to South African clients only" | Every gateway is on `standardbank.co.za` and the compliance terms are South African law (POPIA, NCA, Consumer Protection Act). If the account is with Stanbic Bank Kenya, the product may not be open to us at all |
| Statement formats | PDF only would mean parsing PDFs, which breaks whenever the layout changes |
| The `authorization` token | Not documented. The integration cannot be built without it |
| The meaning of `userId` | Required everywhere, undefined |
| `channelId` and category values | Not listed, and category lookup needs the channel |
| Statement latency | Whether intraday or provisional statements exist, or only period end ones |
| Push notifications | Whether any Standard Bank or Stanbic API offers them |

### Inconsistencies in the published material

- The page states a processing time for "Balance Enquiry API", which is a different product.
- The YAML declares both `Authorization` and `authorization` as separate required headers.
- `getDocumentCategories` is shown as deprecated on the page while the YAML marks it
  `deprecated: false`.
- The schemas spell `addtions` and `DownloadAccontDocumentReq` as written. Field names have to be
  used exactly as the YAML spells them.

## 5. Onboarding path, from the Marketplace page

1. Subscribe to the Statements Management API on the Marketplace, naming the entity.
2. Standard Bank validates the transactional relationship.
3. A business proposal is presented and signed.
4. The Take-On Document is completed, covering technical and business requirements.
5. Sandbox keys are issued.
6. After testing, a Technical Change Request (TCR) is signed.
7. Production keys are issued on TCR approval.

Third party applications must also pass penetration and performance testing, follow the Standard
Bank UX/UI design, and meet the listed regulatory requirements.

## 6. Next steps

1. Try the mocking service. Run `postAccountDocuments` and a download, and note the `contentType`
   values. That answers the format question before the meeting.
2. Reply by 2 October 2026 with the questions in section 4 and ask for the introductory meeting.
3. Decide after the meeting. If the product is South Africa only or PDF only, close the lead.

## Revision history

| Version | Date | Summary |
| --- | --- | --- |
| 1.0 | 28 September 2026 | First assessment from the Marketplace page and `nbp-document-api.yaml` 3.4.0 |
