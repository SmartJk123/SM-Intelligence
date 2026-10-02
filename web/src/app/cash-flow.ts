// Customer cash flow: money in vs money out, drawn from posted deposit-account activity.
import { WorkspaceIcon } from './workspace-icon';
import { FinanceChart } from './finance-chart';
import { Component, computed, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';

interface CashFlowData {
  bank?: string;
  currency: 'KES';
  period: { from: string; to: string; days: number };
  summary: { moneyInMinor: number; moneyOutMinor: number; netCashFlowMinor: number };
  cashFlow: { from: string; to: string; moneyInMinor: number; moneyOutMinor: number }[];
}

@Component({
  imports: [CurrencyPipe, DatePipe, FinanceChart, WorkspaceIcon],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">MONEY MOVING THROUGH YOUR ACCOUNTS</p>
          <h1>Cash Flow</h1>
          <p class="muted">Posted deposit-account credits and debits only.</p>
        </div>
        <button class="button secondary" (click)="load()" [disabled]="loading()">Refresh</button>
      </header>
      <div class="dashboard-toolbar">
        <label class="overview-bank">Bank
          <select [value]="bank()" (change)="changeBank($any($event.target).value)" [disabled]="loading()">
            <option value="">All banks</option>
            @for (name of banks; track name) { <option [value]="name">{{ name }}</option> }
          </select>
        </label>
        <div role="group" aria-label="Reporting period">
          <button [attr.aria-pressed]="days() === 30" (click)="changePeriod(30)" [disabled]="loading()">Last 30 days</button
          ><button [attr.aria-pressed]="days() === 90" (click)="changePeriod(90)" [disabled]="loading()">Last 90 days</button>
        </div>
      </div>
      @if (loading()) {
        <div class="dashboard-loading" role="status" aria-live="polite"><span class="loading-dot"></span>Loading cash flow…</div>
      }
      @if (error()) {
        <div class="dashboard-empty" role="alert">
          <h2>{{ disconnected() ? 'Not connected yet' : 'Connection unavailable' }}</h2>
          <p>{{ error() }}</p>
          <button class="button" (click)="load()">Try again</button>
        </div>
      }
      @if (data(); as d) {
        <div class="dashboard-metrics">
          <article>
            <p><app-workspace-icon name="content-income" /> Money in</p>
            <strong class="positive">{{ d.summary.moneyInMinor / 100 | currency: 'KES' : 'code' : '1.2-2' }}</strong>
            <small>Posted deposit-account credits</small>
          </article>
          <article>
            <p><app-workspace-icon name="content-expense" /> Money out</p>
            <strong>{{ d.summary.moneyOutMinor / 100 | currency: 'KES' : 'code' : '1.2-2' }}</strong>
            <small>Posted deposit-account debits</small>
          </article>
          <article>
            <p><app-workspace-icon name="content-flow" /> Net cash flow</p>
            <strong [class.positive]="d.summary.netCashFlowMinor >= 0">{{ d.summary.netCashFlowMinor / 100 | currency: 'KES' : 'code' : '1.2-2' }}</strong>
            <small>Money in minus money out</small>
          </article>
        </div>
        <div class="dashboard-hybrid">
          <article class="dashboard-panel hybrid-flow" style="grid-column: 1 / -1">
            <div class="panel-heading">
              <div>
                <h2><app-workspace-icon name="content-flow" /> Cash flow over time</h2>
                <p>{{ d.period.from | date: 'd MMM' : 'UTC' }} – {{ d.period.to | date: 'd MMM y' : 'UTC' }}</p>
              </div>
              <div class="chart-legend">
                <span class="money-in">↗ Money in</span><span class="money-out">↘ Money out</span>
              </div>
            </div>
            @if (d.summary.moneyInMinor === 0 && d.summary.moneyOutMinor === 0) {
              <div class="chart-empty">No posted cash movements in this period.</div>
            } @else {
              <app-finance-chart [points]="chartPoints()" mode="line" />
            }
            @if (d.cashFlow.length) {
              <div class="dashboard-table-wrap">
                <table>
                  <thead>
                    <tr><th>Period</th><th>Money in (KES)</th><th>Money out (KES)</th><th>Net (KES)</th></tr>
                  </thead>
                  <tbody>
                    @for (point of d.cashFlow; track point.from) {
                      <tr>
                        <td>{{ point.from }} – {{ point.to }}</td>
                        <td>{{ point.moneyInMinor / 100 | currency: 'KES' : '' : '1.2-2' }}</td>
                        <td>{{ point.moneyOutMinor / 100 | currency: 'KES' : '' : '1.2-2' }}</td>
                        <td [class.positive]="point.moneyInMinor - point.moneyOutMinor >= 0">
                          {{ (point.moneyInMinor - point.moneyOutMinor) / 100 | currency: 'KES' : '' : '1.2-2' }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </article>
        </div>
      }
    </section>
  `,
})
export class CashFlow {
  private http = inject(HttpClient);
  readonly disconnected = signal(false);
  readonly data = signal<CashFlowData | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly days = signal(30);
  readonly bank = signal('');
  readonly banks = ['KCB', 'Equity', 'Stanbic', 'NCBA'];

  constructor() {
    void this.load();
  }

  changeBank(bank: string) {
    if (this.loading() || bank === this.bank()) return;
    this.bank.set(bank);
    void this.load();
  }

  changePeriod(days: number) {
    if (this.loading() || days === this.days()) return;
    this.days.set(days);
    void this.load();
  }

  async load() {
    this.loading.set(true);
    this.error.set('');
    this.disconnected.set(false);
    this.data.set(null);
    try {
      const d = await firstValueFrom(
        this.http
          .get<CashFlowData>('/api/dashboard?days=' + this.days() + '&bank=' + encodeURIComponent(this.bank()))
          .pipe(timeout(15000)),
      );
      if (d?.currency !== 'KES' || !d.summary || !Array.isArray(d.cashFlow) || (this.bank() && d.bank !== this.bank()))
        throw new Error('Invalid response');
      this.data.set(d);
    } catch (error) {
      this.disconnected.set(error instanceof HttpErrorResponse && error.status === 501);
      this.data.set(null);
      this.error.set(
        this.disconnected()
          ? 'Live financial activity is not connected. Link a bank account to see cash flow.'
          : 'We could not load cash flow. Please try again.',
      );
    } finally {
      this.loading.set(false);
    }
  }

  readonly chartPoints = computed(() =>
    (this.data()?.cashFlow ?? []).map((p) => ({ from: p.from, to: p.to, in: p.moneyInMinor, out: p.moneyOutMinor })),
  );
}
