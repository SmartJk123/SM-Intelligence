// Spending trends over time: which categories, and whether they're rising or falling.
import { CategoryChart } from './finance-chart';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { LiveUpdates } from './live-updates';
import { forecast, ForecastInput, InvestmentForecast } from './investment-forecast';

interface AnalyticsData {
  currency: 'KES';
  period: { from: string; to: string; months: number };
  monthlySpending: { month: string; spendingMinor: number }[];
  categories: { category: string; value: number }[];
  categoryTrend: { category: string; monthly: number[] }[];
  transactionCount: number;
  investments?: (ForecastInput & { id: string; name: string; type: string })[];
}

@Component({
  imports: [CurrencyPipe, DatePipe, CategoryChart],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">WHERE YOUR MONEY GOES</p>
          <h1>Analytics</h1>
          <p class="muted">Posted deposit-account spending, by category and by month.</p>
        </div>
        <button class="button secondary" (click)="load()" [disabled]="loading()">Refresh</button>
      </header>
      <div class="dashboard-toolbar">
        <div role="group" aria-label="Reporting window">
          <button [attr.aria-pressed]="months() === 3" (click)="changeMonths(3)" [disabled]="loading()">3 months</button
          ><button [attr.aria-pressed]="months() === 6" (click)="changeMonths(6)" [disabled]="loading()">6 months</button
          ><button [attr.aria-pressed]="months() === 12" (click)="changeMonths(12)" [disabled]="loading()">12 months</button>
        </div>
      </div>
      @if (loading()) {
        <div class="dashboard-loading" role="status" aria-live="polite"><span class="loading-dot"></span>Loading analytics…</div>
      }
      @if (error()) {
        <div class="dashboard-empty" role="alert">
          <h2>Connection unavailable</h2>
          <p>{{ error() }}</p>
          <button class="button" (click)="load()">Try again</button>
        </div>
      }
      @if (data(); as d) {
        @if (!d.transactionCount) {
          <div class="dashboard-empty">
            <h2>No spending in this window</h2>
            <p>Once a posted debit arrives on a deposit account, it appears here by category and month.</p>
          </div>
        } @else {
          <div class="dashboard-hybrid">
            <article class="dashboard-panel" style="grid-column: 1 / -1">
              <div class="panel-heading">
                <div>
                  <h2>Monthly spending</h2>
                  <p>{{ d.period.from | date: 'MMM y' : 'UTC' }} – {{ d.period.to | date: 'MMM y' : 'UTC' }}</p>
                </div>
              </div>
              <div class="viz-plot">
                <div class="viz-axis">
                  <span>{{ maxMonthly() / 100 | currency: 'KES' : '' : '1.0-0' }}</span>
                  <span>{{ maxMonthly() / 200 | currency: 'KES' : '' : '1.0-0' }}</span>
                  <span>0</span>
                </div>
                <div class="viz-columns">
                  @for (m of d.monthlySpending; track m.month; let i = $index) {
                    <button
                      class="viz-column"
                      [class.selected]="selectedMonth() === i"
                      (click)="selectedMonth.set(i)"
                      [attr.aria-pressed]="selectedMonth() === i"
                      [attr.aria-label]="'Inspect ' + m.month"
                    >
                      <span class="viz-bars"><span class="viz-out" [style.height.%]="(m.spendingMinor / maxMonthly()) * 100"></span></span>
                      <small>{{ m.month + '-01' | date: 'MMM' : 'UTC' }}</small>
                    </button>
                  }
                </div>
              </div>
              @if (d.monthlySpending[selectedMonth()]; as m) {
                <div class="viz-detail" aria-live="polite">
                  <strong>{{ m.month + '-01' | date: 'MMMM y' : 'UTC' }}</strong>
                  <span class="money-out">↘ Spent {{ m.spendingMinor / 100 | currency: 'KES' : 'code' }}</span>
                </div>
              }
            </article>
          </div>
        }
        <article class="dashboard-panel" style="margin-top: 20px">
          <div class="panel-heading">
            <div>
              <h2>Investment interest forecast</h2>
              <p>Simple interest at the annual rate: amount invested × rate (% a year) × time held. Estimates, not guaranteed returns.</p>
            </div>
          </div>
          @if (!d.investments?.length) {
            <p class="muted">No investments recorded yet. Add one on the Investments page with its interest rate.</p>
          }
          @for (i of withoutRate(); track i.id) {
            <form class="forecast-rate" (submit)="$event.preventDefault(); saveRate(i, rateInput.value)">
              <span><strong>{{ i.name }}</strong> needs its interest rate for a forecast.</span>
              <label>Interest rate (% a year)
                <input #rateInput type="number" min="0" max="100" step="0.01" required placeholder="e.g. 12" />
              </label>
              <button class="button" [disabled]="savingRate() === i.id">{{ savingRate() === i.id ? 'Saving…' : 'Save rate' }}</button>
            </form>
          }
          @if (rateError()) { <p class="field-error">{{ rateError() }}</p> }
          @if (forecasts().rows.length) {
            <div class="dashboard-metrics">
              <article><p>Amount invested</p><strong>{{ forecasts().baseMinor / 100 | currency: 'KES' : 'code' }}</strong></article>
              <article><p>Worth today (estimated)</p><strong>{{ forecasts().todayMinor / 100 | currency: 'KES' : 'code' }}</strong>
                <small class="positive">+{{ (forecasts().todayMinor - forecasts().baseMinor) / 100 | currency: 'KES' : 'code' }} earned so far</small></article>
              <article><p>Forecast interest</p><strong class="positive">+{{ forecasts().interestMinor / 100 | currency: 'KES' : 'code' }}</strong><small>over the full term</small></article>
              <article><p>Worth at maturity</p><strong>{{ forecasts().projectedMinor / 100 | currency: 'KES' : 'code' }}</strong></article>
            </div>
            <div class="viz-plot" aria-label="Forecast value by year">
              <div class="viz-axis">
                <span>{{ maxYearly() / 100 | currency: 'KES' : '' : '1.0-0' }}</span>
                <span>{{ maxYearly() / 200 | currency: 'KES' : '' : '1.0-0' }}</span>
                <span>0</span>
              </div>
              <div class="viz-columns">
                @for (y of forecasts().byYear; track y.year) {
                  <div class="viz-column" [attr.title]="y.year + ': ' + (y.valueMinor / 100 | currency: 'KES' : 'code')">
                    <span class="viz-bars"><span class="viz-in" [style.height.%]="(y.valueMinor / maxYearly()) * 100"></span></span>
                    <small>{{ y.year }}</small>
                  </div>
                }
              </div>
            </div>
            <div class="dashboard-table-wrap" tabindex="0" aria-label="Forecast by investment">
              <table>
                <thead>
                  <tr><th>Investment</th><th>Rate</th><th>Term</th><th>Time to maturity</th><th style="text-align: right">Invested</th><th style="text-align: right">Worth today</th><th style="text-align: right">At maturity</th></tr>
                </thead>
                <tbody>
                  @for (row of forecasts().rows; track row.id) {
                    <tr>
                      <td>{{ row.name }}<br /><small class="muted">{{ row.type }}</small></td>
                      <td>{{ row.f.rate }}%</td>
                      <td>{{ row.f.termLabel ?? 'No maturity date' }}@if (row.f.termLength) {<br /><small class="muted">{{ row.f.termLength }}</small>}</td>
                      <td>{{ row.f.remaining ?? '1 year shown' }}</td>
                      <td style="text-align: right">{{ row.f.baseMinor / 100 | currency: 'KES' : '' : '1.0-0' }}</td>
                      <td style="text-align: right">
                        {{ row.f.valueTodayMinor / 100 | currency: 'KES' : '' : '1.0-0' }}<br />
                        <small class="muted">held {{ row.f.heldFor }}</small>
                      </td>
                      <td style="text-align: right">
                        {{ row.f.projectedMinor / 100 | currency: 'KES' : '' : '1.0-0' }}<br />
                        <small class="positive">+{{ row.f.interestMinor / 100 | currency: 'KES' : '' : '1.0-0' }}</small>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </article>
        @if (d.transactionCount) {
          <div class="dashboard-hybrid" style="margin-top: 20px">
            <article class="dashboard-panel" style="grid-column: 1 / -1">
              <div class="panel-heading">
                <div>
                  <h2>By category</h2>
                  <p>Share of total spending in this window</p>
                </div>
              </div>
              <app-category-chart [points]="d.categories" />
            </article>
          </div>
        }
      }
    </section>
  `,
})
export class Analysis {
  private http = inject(HttpClient);
  private readonly live = inject(LiveUpdates);
  readonly data = signal<AnalyticsData | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly months = signal(6);
  readonly selectedMonth = signal(0);

  readonly maxMonthly = computed(() =>
    Math.max(1, ...(this.data()?.monthlySpending.map((m) => m.spendingMinor) ?? [0])),
  );

  /** Each investment's forecast, the totals, and the combined projected value per year. */
  readonly forecasts = computed(() => {
    const rows = (this.data()?.investments ?? [])
      .map((i) => ({ id: i.id, name: i.name, type: i.type, f: forecast(i) }))
      .filter((r): r is { id: string; name: string; type: string; f: InvestmentForecast } => r.f !== null);
    const years = new Map<number, number>();
    for (const r of rows) {
      const last = r.f.yearly.at(-1)!;
      // Each holding keeps its maturity value in later years, so the total never dips.
      for (const y of r.f.yearly) years.set(y.year, (years.get(y.year) ?? 0) + y.valueMinor);
      const end = Math.max(...rows.map((x) => x.f.yearly.at(-1)!.year));
      for (let y = last.year + 1; y <= end; y++) years.set(y, (years.get(y) ?? 0) + last.valueMinor);
    }
    return {
      rows,
      baseMinor: rows.reduce((s, r) => s + r.f.baseMinor, 0),
      todayMinor: rows.reduce((s, r) => s + r.f.valueTodayMinor, 0),
      interestMinor: rows.reduce((s, r) => s + r.f.interestMinor, 0),
      projectedMinor: rows.reduce((s, r) => s + r.f.projectedMinor, 0),
      byYear: [...years.entries()].sort((a, b) => a[0] - b[0]).slice(0, 30).map(([year, valueMinor]) => ({ year, valueMinor })),
    };
  });
  /** Investments with no interest rate yet, offered an inline rate field. */
  readonly withoutRate = computed(() => (this.data()?.investments ?? []).filter((i) => i.interestRate === null || i.interestRate === undefined));
  readonly savingRate = signal<string | null>(null);
  readonly rateError = signal('');

  /** Saves just the interest rate, keeping the rest of the investment as it is. */
  async saveRate(i: NonNullable<AnalyticsData['investments']>[number], value: string): Promise<void> {
    const rate = Number(value);
    if (value === '' || !Number.isFinite(rate) || rate < 0 || rate > 100) { this.rateError.set('Enter a rate between 0 and 100.'); return; }
    this.rateError.set('');
    this.savingRate.set(i.id);
    try {
      await firstValueFrom(this.http.post('/api/workspace/investments', {
        id: i.id, name: i.name, type: i.type, principalMinor: i.principalMinor, currentValueMinor: i.currentValueMinor,
        valuationDate: i.valuationDate, maturityDate: i.maturityDate, interestRate: rate,
      }).pipe(timeout(15000)));
      await this.load();
    } catch (error: any) {
      this.rateError.set(error?.error?.error || 'The rate could not be saved. Please retry.');
    } finally {
      this.savingRate.set(null);
    }
  }

  readonly maxYearly = computed(() => Math.max(1, ...this.forecasts().byYear.map((y) => y.valueMinor)));

  constructor() {
    void this.load();
    effect(() => {
      if (this.live.lastUpdate() === null) return;
      void this.load();
    });
  }

  changeMonths(months: number): void {
    if (this.loading() || months === this.months()) return;
    this.months.set(months);
    void this.load();
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const d = await firstValueFrom(
        this.http.get<AnalyticsData>('/api/analytics?months=' + this.months()).pipe(timeout(15000)),
      );
      if (d?.currency !== 'KES' || !Array.isArray(d.monthlySpending) || !Array.isArray(d.categories))
        throw new Error('Invalid response');
      if (!Array.isArray(d.investments)) {
        // An older web server does not send investments with analytics: read them from the workspace.
        const w = await firstValueFrom(
          this.http.get<{ investments?: AnalyticsData['investments'] }>('/api/workspace').pipe(timeout(15000)),
        ).catch(() => null);
        d.investments = w?.investments ?? [];
      }
      this.data.set(d);
      this.selectedMonth.set(Math.max(0, d.monthlySpending.length - 1));
    } catch (error) {
      this.data.set(null);
      this.error.set(
        error instanceof HttpErrorResponse && error.status === 501
          ? 'Live financial activity is not connected yet.'
          : 'We could not load analytics. Please try again.',
      );
    } finally {
      this.loading.set(false);
    }
  }
}
