# Workspace Modules & Feature Pages

This document details the 9 feature modules implemented inside the unified [`WorkspacePage`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L27) component and [`workspace-page.html`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html).

---

## 1. Universal Architecture of `WorkspacePage`

Instead of creating 9 separate boilerplates, `WorkspacePage` dynamically switches its context based on route configuration:
- Driven by `route.data` subscription ([`workspace-page.ts:53-63`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L53-L63)).
- The template dispatches between modules using Angular 17+ control flow `@switch (page)` ([`workspace-page.html:158`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L158)).
- Shared state loads from [`WorkspaceApi.load()`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L89) returning [`WorkspaceData`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-api.ts#L52-L61).

```mermaid
flowchart TD
    Route["Route /:page (accounts, transactions, cashflow, etc.)"] -->|data: { page }| Page["WorkspacePage Component"]
    Page --> Switch{"@switch (page)"}
    Switch --> M1["1. Accounts"]
    Switch --> M2["2. Transactions"]
    Switch --> M3["3. Cash Flow"]
    Switch --> M4["4. Budgets"]
    Switch --> M5["5. Investments"]
    Switch --> M6["6. Analytics"]
    Switch --> M7["7. Reports (PDF/CSV)"]
    Switch --> M8["8. Notifications"]
    Switch --> M9["9. Profile & Settings"]
```

---

## 2. The 9 Modules in Detail

### 1. Accounts Module (`/accounts`)
- **Template**: [`workspace-page.html:159-217`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L159-L217).
- **Functionality**:
  - Displays summary metrics: Total Available Cash (Deposit accounts), Credit Outstanding (debt), and Total Accounts.
  - Renders bank cards showing official logos ([`BankLogo`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/bank-logo.ts#L53)), masked identifiers (`•••• 4321`), card type (`Deposit` vs `Credit`), and current balance.
  - Deep links to transaction activity filtered by account: `/transactions?account={id}`.
  - Supports adding a new account via the universal editor ([`workspace-page.ts:451-475`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L451-L475)).

### 2. Transactions Module (`/transactions`)
- **Template**: [`workspace-page.html:218-323`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L218-L323).
- **Filtering Pipeline** ([`workspace-page.ts:103-117`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L103-L117)):
  Filters transaction ledger rows by:
  - Account selection (`this.account`)
  - Date range (`this.start` to `this.end`)
  - Status (`POSTED`, `PENDING`, `FAILED`, `REVERSED`, `CANCELLED`)
  - Category
  - Text search query matching description or category
- **CRUD Actions**: Create manual entries or edit existing rows ([`workspace-page.ts:476-507`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L476-L507)).

### 3. Cash Flow Module (`/cashflow`)
- **Template**: [`workspace-page.html:324-386`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L324-L386).
- **Rules**: Cash flow includes **posted deposit-account movements only**. Pending, cancelled, or credit movements are strictly excluded ([`workspace-page.ts:118-121`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L118-L121)).
- **Time Binning** ([`workspace-page.ts:143-155`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L143-L155)): Splits the selected date range into up to 6 equal sub-periods to chart cash movements using [`FinanceChart`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L74).
- **Tabular Breakdown**: Exact figures table displaying Period, Money In, Money Out, and Net KES.

### 4. Budgets Module (`/budgets`)
- **Template**: [`workspace-page.html:387-446`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L387-L446).
- **Calculations**:
  - `spent(b: Budget)` ([`workspace-page.ts:172-184`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L172-L184)): Sums posted debits in the category across the budget's time window.
  - `budgetStatus(b: Budget)` ([`workspace-page.ts:193-199`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L193-L199)): Categorizes into `Over budget`, `Approaching limit` (at or above threshold percentage, default 85%), or `Within budget`.
- **Management**: Add, edit, or delete category allocations ([`workspace-page.ts:508-530`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L508-L530)).

### 5. Investments Module (`/investments`)
- **Template**: [`workspace-page.html:447-515`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L447-L515).
- **Portfolio Integrity Rule** ([`workspace-page.ts:200-205`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L200-L205)):
  ```typescript
  get portfolio() {
    const rows = this.data()?.investments ?? [];
    return rows.some((i) => i.currentValueMinor === null)
      ? null
      : rows.reduce((s, i) => s + (i.currentValueMinor ?? 0), 0);
  }
  ```
  If any holding has an unrecorded valuation (`currentValueMinor === null`), the total portfolio value displays as "Unavailable" rather than fabricating an incomplete total.
- **Instrument Types**: Money Market, Fixed Deposit, Treasury Bill, Other.
- **Maturity Alerts**: Tracks upcoming maturities within 90 days.

### 6. Analytics Module (`/analysis`)
- **Template**: [`workspace-page.html:516-574`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L516-L574).
- **Spending Composition** ([`workspace-page.ts:159-168`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L159-L168)): Aggregates debits by category and feeds them to [`CategoryChart`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L131).
- **Actionable Alerts** ([`workspace-page.ts:209-248`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L209-L248)):
  - Low balance warnings when deposit accounts drop below user threshold.
  - Budget warnings for categories approaching or exceeding thresholds.
  - Upcoming investment maturity warnings.

### 7. Reports Module (`/reports`)
- **Template**: [`workspace-page.html:575-632`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L575-L632).
- **Report Scopes**: `transactions`, `accounts`, `cashflow`.
- **CSV Export** ([`workspace-page.ts:412-434`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L412-L434)):
  - Sanitizes against formula injection (`replace(/^[=+\-@\t\r]/, "'$&")`).
  - Prepends UTF-8 BOM (`\ufeff`) so Microsoft Excel parses Kenyan Shilling amounts properly.
- **Print / Save as PDF Export** ([`workspace-page.ts:321-411`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L321-L411)):
  - Opens a dedicated pop-up window formatted in A4 Landscape.
  - Inserts print styles, title, date range, table headers, formatted numbers, and auto-invokes `window.print()`.

### 8. Notifications Module (`/notifications`)
- **Template**: [`workspace-page.html:633-670`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L633-L670).
- **Notification Aggregator** ([`workspace-page.ts:259-282`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L259-L282)):
  Combines active system warnings with transaction ledger activity into [`NotificationItem`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/notification-filter.ts#L2) objects.
- **Advanced Filtering** ([`src/app/notification-filter.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/notification-filter.ts#L3-L13)): Filters by Timeline (`today`, `yesterday`, `week`, `month`, 30 days), Category, Transaction Direction, Bank, or Unread status.
- **Read State**: Persists read IDs to backend via [`markRead()`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L626-L633).

### 9. Profile and Settings Module (`/settings`)
- **Template**: [`workspace-page.html:671-702`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.html#L671-L702).
- **Profile Preferences**: Update full name, organization name, and low-balance threshold.
- **Alert Toggles**: Switch on/off low balance alerts, budget threshold alerts, and maturity alerts.
- **Appearance Switcher** ([`workspace-page.ts:634-647`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-page.ts#L634-L647)):
  - Explicit toggle between Light and Dark mode.
  - Updates `document.documentElement.dataset['theme']`.
  - Persists selection to `localStorage` under `sm-intelligence-appearance`.
