import { FinanceChart } from './finance-chart';
import { Component, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { BankLogo } from './bank-logo';

interface Account {
  id: string;
  bank: string;
  accountName: string;
  maskedIdentifier: string;
  accountType: 'DEPOSIT' | 'CREDIT';
  availableBalanceMinor: number;
  creditOutstandingMinor: number;
}
interface Transaction {
  id: string;
  accountId: string;
  description: string;
  category: string;
  direction: 'CREDIT' | 'DEBIT';
  amountMinor: number;
  status: string;
  date: string;
}
interface DashboardData {
  source: 'sample' | 'live';
  currency: 'KES';
  user: { name: string; kind: string };
  period: { from: string; to: string; days: number };
  summary: {
    availableCashMinor: number;
    creditOutstandingMinor: number;
    moneyInMinor: number;
    moneyOutMinor: number;
    netCashFlowMinor: number;
  };
  accounts: Account[];
  cashFlow: { from: string; to: string; moneyInMinor: number; moneyOutMinor: number }[];
  transactions: Transaction[];
  transactionCount: number;
}
@Component({
  imports: [CurrencyPipe, DatePipe, BankLogo, FinanceChart],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">YOUR FINANCIAL PICTURE</p>
          <h1>Overview</h1>
          <p class="muted">
            {{
              data() ? 'Welcome back, ' + data()!.user.name + '.' : 'Your money, in one clear view.'
            }}
          </p>
        </div>
        <button class="button secondary" (click)="load()" [disabled]="loading()">Refresh</button>
      </header>
      <div class="dashboard-toolbar">
        <span>{{
          data()?.user?.kind === 'organization' ? 'Organization overview' : 'Personal overview'
        }}</span>
        <div role="group" aria-label="Reporting period">
          <button
            [attr.aria-pressed]="days() === 30"
            (click)="changePeriod(30)"
            [disabled]="loading()"
          >
            Last 30 days</button
          ><button
            [attr.aria-pressed]="days() === 90"
            (click)="changePeriod(90)"
            [disabled]="loading()"
          >
            Last 90 days
          </button>
        </div>
      </div>
      @if (loading()) {
        <div class="dashboard-loading" role="status" aria-live="polite">
          <span class="loading-dot"></span>Loading your financial overview…
        </div>
      } @else if (error()) {
        <div class="dashboard-empty" role="alert">
          <h2>We couldn’t load your dashboard</h2>
          <p>{{ error() }}</p>
          <button class="button" (click)="load()">Try again</button>
        </div>
      } @else if (data(); as d) {
        @if (d.source === 'sample') {
          <p class="sample-label">Sample data · Local development</p>
        }
        <div class="dashboard-metrics">
          <article>
            <p>Available cash</p>
            <strong>{{
              d.summary.availableCashMinor / 100 | currency: 'KES' : 'code' : '1.2-2'
            }}</strong
            ><small>Deposit accounts only</small>
          </article>
          <article>
            <p>Credit outstanding</p>
            <strong>{{
              d.summary.creditOutstandingMinor / 100 | currency: 'KES' : 'code' : '1.2-2'
            }}</strong
            ><small>Amount owed · excluded from cash</small>
          </article>
          <article>
            <p>Money in</p>
            <strong class="positive">{{
              d.summary.moneyInMinor / 100 | currency: 'KES' : 'code' : '1.2-2'
            }}</strong
            ><small>Posted deposit-account credits</small>
          </article>
          <article>
            <p>Money out</p>
            <strong>{{ d.summary.moneyOutMinor / 100 | currency: 'KES' : 'code' : '1.2-2' }}</strong
            ><small>Posted deposit-account debits</small>
          </article>
        </div>
        <div class="dashboard-columns">
          <article class="dashboard-panel">
            <div class="panel-heading">
              <div>
                <h2>Cash flow</h2>
                <p>
                  {{ d.period.from | date: 'd MMM' : 'UTC' }} –
                  {{ d.period.to | date: 'd MMM y' : 'UTC' }}
                </p>
              </div>
              <div class="chart-legend">
                <span class="money-in">↗ Money in</span><span class="money-out">↘ Money out</span>
              </div>
            </div>
            @if (d.summary.moneyInMinor === 0 && d.summary.moneyOutMinor === 0) {
              <div class="chart-empty">No posted cash movements in this period.</div>
            } @else {
              <app-finance-chart [points]="chartPoints" />
            }
            <div class="net-flow">
              <span>Net cash flow</span
              ><strong>{{
                d.summary.netCashFlowMinor / 100 | currency: 'KES' : 'code' : '1.2-2'
              }}</strong>
            </div>
            <details class="chart-details">
              <summary>View exact cash-flow figures</summary>
              <div class="dashboard-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Period</th>
                      <th>Money in (KES)</th>
                      <th>Money out (KES)</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (point of d.cashFlow; track point.from) {
                      <tr>
                        <td>{{ point.from }} – {{ point.to }}</td>
                        <td>{{ point.moneyInMinor / 100 | currency: 'KES' : '' : '1.2-2' }}</td>
                        <td>{{ point.moneyOutMinor / 100 | currency: 'KES' : '' : '1.2-2' }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            </details>
          </article>
          <article class="dashboard-panel">
            <div class="panel-heading">
              <div>
                <h2>Your accounts</h2>
                <p>{{ d.accounts.length }} accounts · KES</p>
              </div>
            </div>
            @if (!d.accounts.length) {
              <div class="chart-empty">No accounts to display yet.</div>
            }
            @for (account of d.accounts; track account.id) {
              <div class="dashboard-account">
                <app-bank-logo [bank]="account.bank" />
                <div>
                  <strong>{{ account.accountName }}</strong
                  ><small
                    >{{ account.bank }} · {{ account.maskedIdentifier }} ·
                    {{ account.accountType === 'CREDIT' ? 'Credit' : 'Deposit' }}</small
                  ><span
                    >{{
                      (account.accountType === 'CREDIT'
                        ? account.creditOutstandingMinor
                        : account.availableBalanceMinor) / 100 | currency: 'KES' : 'code' : '1.2-2'
                    }}
                    {{ account.accountType === 'CREDIT' ? 'owed' : '' }}</span
                  >
                </div>
              </div>
            }
          </article>
        </div>
        <article class="dashboard-panel">
          <div class="panel-heading">
            <div>
              <h2>Recent transactions</h2>
              <p>{{ d.transactionCount }} records in this period · showing up to 12</p>
            </div>
          </div>
          @if (!d.transactions.length) {
            <div class="dashboard-empty">
              <h3>No transactions yet</h3>
              <p>Your activity will appear here once transaction records are available.</p>
            </div>
          } @else {
            <div class="dashboard-table-wrap" tabindex="0" aria-label="Recent transactions">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Account</th>
                    <th>Status</th>
                    <th class="amount-cell">Amount (KES)</th>
                  </tr>
                </thead>
                <tbody>
                  @for (tx of d.transactions; track tx.id) {
                    <tr>
                      <td>{{ tx.date | date: 'd MMM y' : 'UTC' }}</td>
                      <td>
                        <strong>{{ tx.description }}</strong
                        ><small>{{ tx.category }}</small>
                      </td>
                      <td>{{ accountName(tx.accountId) }}</td>
                      <td>
                        <span
                          [attr.data-status]="tx.status"
                          class="transaction-status"
                          [class.posted]="tx.status === 'POSTED'"
                          >{{ tx.status }}</span
                        >
                      </td>
                      <td class="amount-cell" [class.positive]="tx.direction === 'CREDIT'">
                        {{ tx.direction === 'CREDIT' ? '+' : '−'
                        }}{{ tx.amountMinor / 100 | currency: 'KES' : '' : '1.2-2' }}
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
          <p class="dashboard-footnote">
            Cash-flow totals include posted deposit-account activity only. Pending, cancelled, and
            credit-account entries are excluded. Account balances are current snapshots.
          </p>
        </article>
      }
    </section>
  `,
})
export class Dashboard {
  private http = inject(HttpClient);
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly days = signal(30);
  constructor() {
    void this.load();
  }
  async load() {
    this.loading.set(true);
    this.error.set('');
    try {
      const d = await firstValueFrom(
        this.http.get<DashboardData>('/api/dashboard?days=' + this.days()).pipe(timeout(15000)),
      );
      if (
        d?.currency !== 'KES' ||
        !d.summary ||
        !Array.isArray(d.accounts) ||
        !Array.isArray(d.transactions) ||
        !Array.isArray(d.cashFlow)
      )
        throw new Error('Invalid response');
      this.data.set(d);
    } catch {
      this.data.set(null);
      this.error.set('Please try again. Your account details have not been changed.');
    } finally {
      this.loading.set(false);
    }
  }
  changePeriod(days: number) {
    if (this.loading() || days === this.days()) return;
    this.days.set(days);
    void this.load();
  }
  get chartPoints() {
    return (
      this.data()?.cashFlow.map((p) => ({
        from: p.from,
        to: p.to,
        in: p.moneyInMinor,
        out: p.moneyOutMinor,
      })) ?? []
    );
  }
  barHeight(value: number) {
    const max = Math.max(
      1,
      ...(this.data()?.cashFlow.flatMap((p) => [p.moneyInMinor, p.moneyOutMinor]) ?? []),
    );
    return (value / max) * 100;
  }
  accountName(id: string) {
    return this.data()?.accounts.find((a) => a.id === id)?.accountName ?? 'Account';
  }
}
