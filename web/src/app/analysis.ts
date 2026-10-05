// Spending trends over time: which categories, and whether they're rising or falling.
import { CategoryChart } from './finance-chart';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { LiveUpdates } from './live-updates';

interface AnalyticsData {
  currency: 'KES';
  period: { from: string; to: string; months: number };
  monthlySpending: { month: string; spendingMinor: number }[];
  categories: { category: string; value: number }[];
  categoryTrend: { category: string; monthly: number[] }[];
  transactionCount: number;
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
