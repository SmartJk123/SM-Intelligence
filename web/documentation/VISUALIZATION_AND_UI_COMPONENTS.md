# Visualization and Shared UI Components

This document details the visual components and mathematical charting algorithms implemented in [`src/app/finance-chart.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts), [`src/app/smooth-chart.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/smooth-chart.ts), [`src/app/bank-logo.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/bank-logo.ts), and [`src/app/workspace-icon.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-icon.ts).

---

## 1. Cash-Flow Visualizer (`FinanceChart`)

[`FinanceChart`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L74) renders both time-series line trends and bar columns.

### Component Inputs and State
- `mode`: Input signal (`'bar'` or `'line'`). Defaults to `'bar'`.
- `points`: Required input array: `[{ from: string, to: string, in: number, out: number }]`.
- `series`: Local signal (`'both'`, `'in'`, `'out'`). Allows users to toggle comparing both flows or isolating Money In vs. Money Out ([`finance-chart.ts:8-14`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L8-L14)).
- `selected`: Local signal index of the currently inspected period.

### Line Chart Mode ([Lines 16-35](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L16-L35))
- Renders an SVG canvas (`viewBox="0 0 600 200"`).
- Background grid lines at `y = 10`, `y = 95`, and `y = 180`.
- Area fill for income under the curve (`class="hybrid-area"`).
- Guide line and data point indicators (`circle.hybrid-income-dot`, `circle.hybrid-outflow-dot`) highlighting the active selected period.
- Period buttons beneath the SVG allow keyboard and touch navigation between points.

### Bar Chart Mode ([Lines 36-63](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L36-L63))
- Y-axis scale displaying top, midpoint, and baseline figures in KES.
- Paired column layout representing Money In (blue/green) and Money Out (striped red/charcoal).

### Detail Inspector Readout ([Lines 64-71](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L64-L71))
Provides an accessible live region (`aria-live="polite"`) showing the exact KES figures for the selected period:
```html
<!-- src/app/finance-chart.ts:64-71 -->
<strong>{{ p.from | date: 'd MMM' : 'UTC' }} – {{ p.to | date: 'd MMM' : 'UTC' }}</strong>
<span class="money-in">↗ In {{ p.in / 100 | currency: 'KES' : 'code' }}</span>
<span class="money-out">↘ Out {{ p.out / 100 | currency: 'KES' : 'code' }}</span>
<span>Net {{ (p.in - p.out) / 100 | currency: 'KES' : 'code' }}</span>
```

---

## 2. Spline Curve Generator (`smoothChartPath`)

The function [`smoothChartPath()`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/smooth-chart.ts#L2-L14) in [`src/app/smooth-chart.ts`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/smooth-chart.ts) implements cubic Bezier curve interpolation.

```typescript
// src/app/smooth-chart.ts:1-14
/** Cubic segments stay within each pair of values; no fabricated extrema. */
export function smoothChartPath(values: number[], maximum: number): string {
  if (!values.length) return '';
  const coords = values.map((value, i) => ({
    x: 10 + i * 580 / Math.max(1, values.length - 1),
    y: 180 - value / Math.max(1, maximum) * 170,
  }));
  let path = 'M' + coords[0].x + ',' + coords[0].y;
  for (let i = 1; i < coords.length; i++) {
    const a = coords[i-1], b = coords[i], middle = (a.x+b.x)/2;
    path += ' C' + middle + ',' + a.y + ' ' + middle + ',' + b.y + ' ' + b.x + ',' + b.y;
  }
  return path;
}
```

### Mathematical Guarantee: No Fabricated Extrema
Standard Catmull-Rom or cardinal splines often overshoot local peaks or dip below zero, which is unacceptable for financial data. This algorithm uses horizontal midpoints:
- The control points for segment $[(x_{i-1}, y_{i-1}), (x_i, y_i)]$ are $(x_{\text{mid}}, y_{i-1})$ and $(x_{\text{mid}}, y_i)$ where $x_{\text{mid}} = \frac{x_{i-1} + x_i}{2}$.
- Because the $y$-coordinates of both control handles are bounded by $[y_{i-1}, y_i]$, the cubic curve is monotonically bounded between consecutive points. It can never produce fabricated dips below 0 or false peaks above the true maximum.

---

## 3. Spending Breakdown Donut Chart (`CategoryChart`)

[`CategoryChart`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L131) creates a lightweight CSS conic-gradient donut visualization without external chart libraries.

### Conic Gradient Engine ([Lines 144-162](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L144-L162))
```typescript
gradient() {
  let start = 0;
  return (
    'conic-gradient(' +
    this.points()
      .map((p, i) => {
        const end = start + (p.value / Math.max(1, this.total())) * 100;
        const color =
          this.selected() && p.category !== this.selected()
            ? 'var(--ws-line)'
            : this.colors[i % this.colors.length];
        const stop = color + ' ' + start + '% ' + end + '%';
        start = end;
        return stop;
      })
      .join(',') +
    ')'
  );
}
```

### Interactivity & Accessibility
- Slices use a 6-color categorical palette ([Line 134](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/finance-chart.ts#L134)): `['#2563EB', '#4F46E5', '#0891B2', '#7C3AED', '#64748B', '#C9A227']`.
- Selecting a category dims all other slices to `var(--ws-line)`.
- The central hollow cutout displays the exact KES amount and percentage share.

---

## 4. Kenyan Bank Logo (`BankLogo`)

[`BankLogo`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/bank-logo.ts#L53) displays official bank branding with image failure resilience.

### Supported Banks & CDN Endpoints ([Lines 3-24](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/bank-logo.ts#L3-L24))
- **KCB Bank**
- **NCBA Bank**
- **Equity Bank**
- **Stanbic Bank**

### Fallback Engine ([Lines 28-51](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/bank-logo.ts#L28-L51))
- Uses `referrerpolicy="no-referrer"` to bypass CDN hotlink protection.
- Listens to image error events: `(error)="failedUrl.set(item.src)"`.
- If an image fails to load or offline development occurs, smoothly falls back to a styled typographic badge: `<span class="bank-logo-fallback">{{ bank() }}</span>`.

---

## 5. Vector Icon System (`WorkspaceIcon`)

[`WorkspaceIcon`](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-icon.ts#L29) encapsulates 24 handcrafted SVG glyphs in a dictionary ([Lines 2-27](file:///home/frank/SmartMoney_intelligence/SM-Intelligence/web/src/app/workspace-icon.ts#L2-L27)).

- **Design**: 24×24 viewBox, `stroke-width="1.7"`, stroke-linecap and join rounded.
- **Icons Defined**: `content-budget`, `content-attention`, `content-forecast`, `content-cash`, `content-credit`, `content-income`, `content-expense`, `content-flow`, `content-spending`, `content-accounts`, `content-ledger`, `dashboard`, `accounts`, `transactions`, `cashflow`, `budgets`, `investments`, `analysis`, `reports`, `notifications`, `settings`, `wallet`, `income`, `expense`.
- **Accessibility**: Marked with `aria-hidden="true"` and `focusable="false"` to prevent screen reader noise.
