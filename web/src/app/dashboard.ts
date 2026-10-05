// Customer financial overview.
import { WorkspaceIcon } from './workspace-icon';
import { CategoryChart, FinanceChart } from './finance-chart';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { BankLogo } from './bank-logo';
import { AccountApi } from './account-api';
import { LiveUpdates } from './live-updates';
import { RouterLink } from '@angular/router';

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
  bank?: string;
  source: 'live';
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

// Converts workspace API data into summary metrics and dashboard visualizations.
@Component({
  imports: [RouterLink, CurrencyPipe, DatePipe, BankLogo, FinanceChart, CategoryChart, WorkspaceIcon],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">YOUR FINANCIAL PICTURE</p>
          <h1>Financial Overview</h1>
          <p class="muted">
            {{
              'Welcome back, ' + (api.displayName() || 'there') + '.'
            }}
          </p>
        </div>
        <button class="button secondary" (click)="load()" [disabled]="loading()">Refresh</button>
      </header>
      <div class="dashboard-toolbar">
        <span>{{
          api.kind() === 'organization' ? 'Company overview' : 'Personal overview'
        }}</span>
        <label class="overview-bank">Bank
          <select [value]="bank()" (change)="changeBank($any($event.target).value)" [disabled]="loading()">
            <option value="">All banks</option>
            @for (name of banks; track name) { <option [value]="name">{{ name }}</option> }
          </select>
        </label>
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
      }
      @if (error()) {
        <div class="dashboard-empty" role="alert">
          <h2>{{ disconnected() ? 'Not connected yet' : 'Connection unavailable' }}</h2>
          <p>{{ error() }}</p>
          <button class="button" (click)="load()">Try again</button>
        </div>
      }
      @if (view(); as d) {
        <div class="dashboard-metrics">
          <article>
            <p><app-workspace-icon name="content-cash" /> Available cash</p>
            <strong>{{
              d.accounts.length > 0 || data() ? (d.summary.availableCashMinor / 100 | currency: 'KES' : 'code' : '1.2-2') : '—'
            }}</strong
            ><small>Manual deposit snapshots · no live sync</small>
          </article>
          <article>
            <p><app-workspace-icon name="content-credit" /> Credit outstanding</p>
            <strong>{{
              d.accounts.length > 0 || data() ? (d.summary.creditOutstandingMinor / 100 | currency: 'KES' : 'code' : '1.2-2') : '—'
            }}</strong
            ><small>Manual credit snapshots · excluded from cash</small>
          </article>
          <article>
            <p><app-workspace-icon name="content-income" /> Money in</p>
            <strong class="positive">{{
              data() ? (d.summary.moneyInMinor / 100 | currency: 'KES' : 'code' : '1.2-2') : '—'
            }}</strong
            ><small>Posted deposit-account credits</small>
          </article>
          <article>
            <p><app-workspace-icon name="content-expense" /> Money out</p>
            <strong>{{ data() ? (d.summary.moneyOutMinor / 100 | currency: 'KES' : 'code' : '1.2-2') : '—' }}</strong
            ><small>Posted deposit-account debits</small>
          </article>
        </div>
        <div class="dashboard-hybrid">
          <article class="dashboard-panel hybrid-flow">
            <div class="panel-heading">
              <div>
                <h2><app-workspace-icon name="content-flow" /> Cash flow</h2>
                <p>
                  {{ d.period.from | date: 'd MMM' : 'UTC' }} –
                  {{ d.period.to | date: 'd MMM y' : 'UTC' }}
                </p>
              </div>
              <div class="chart-legend">
                <span class="money-in">↗ Money in</span><span class="money-out">↘ Money out</span>
              </div>
            </div>
            @if (!data()) {
              <div class="chart-empty">{{ loading() ? 'Loading cash flow…' : 'No cash-flow data available. Bank activity is not connected.' }}</div>
            } @else if (d.summary.moneyInMinor === 0 && d.summary.moneyOutMinor === 0) {
              <div class="chart-empty">No posted cash movements in this period.</div>
            } @else {
              <app-finance-chart [points]="chartPoints" mode="line" />
            }
            <div class="net-flow">
              <span>Net cash flow</span
              ><strong>{{
                data() ? (d.summary.netCashFlowMinor / 100 | currency: 'KES' : 'code' : '1.2-2') : '—'
              }}</strong>
            </div>
            @if (data() && d.cashFlow.length) {
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
            }
          </article>
          <article class="dashboard-panel hybrid-spending">
            <div class="panel-heading"><div><h2><app-workspace-icon name="content-spending" /> Spending breakdown</h2><p>Recent posted deposit-account debits</p></div></div>
            @if (spendingPoints.length) { <app-category-chart [points]="spendingPoints" /> }
            @else { <div class="chart-empty">{{ loading() ? 'Loading spending…' : data() ? 'No posted spending in this period.' : 'No spending data available. Bank activity is not connected.' }}</div> }
            <p class="dashboard-footnote">Based on the recent transactions shown below, which may be a subset of this period.</p>
          </article>
        <article class="dashboard-panel hybrid-accounts">
            <div class="panel-heading">
              <div>
                <h2><app-workspace-icon name="content-accounts" /> Your accounts</h2>
                <p>{{ d.accounts.length }} accounts · KES · manual snapshots</p><a routerLink="/accounts/new">Add account</a>
              </div>
            </div>
            @if (!d.accounts.length) {
              <div class="chart-empty">{{ api.accountsError() ? 'Account connection unavailable. Try refreshing.' : 'No accounts available for this selection.' }}</div>
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
        <article class="dashboard-panel hybrid-transactions">
          <div class="panel-heading">
            <div>
              <h2><app-workspace-icon name="content-ledger" /> Recent transactions</h2>
              <p>{{ data() ? d.transactionCount + ' records in this period · showing up to 12' : 'Posted bank activity' }}</p>
            </div>
          </div>
          @if (!d.transactions.length) {
            <div class="dashboard-empty">
              <h3>{{ data() ? 'No transactions yet' : 'No transaction data available' }}</h3>
              <p>Your activity will appear here once transaction records are available. <a routerLink="/invoices">View invoice records</a></p>
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
                      <td>{{ tx.date | date: 'd MMM y' : 'UTC' }}<small>{{ tx.date | date: 'HH:mm' : 'UTC' }}</small></td>
                      <td>
                        <strong>{{ tx.description }}</strong
                        ><small>{{ tx.category }}</small>
                      </td>
                      <td>
                        <span class="transaction-account">
                          <app-bank-logo [bank]="accountBank(tx.accountId)" />
                          <small>{{ accountMasked(tx.accountId) }}</small>
                        </span>
                      </td>
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
          </div>
      }
    </section>
  `,
})

export class Dashboard {
  private http = inject(HttpClient);
  readonly api = inject(AccountApi);
  readonly disconnected = signal(false);
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly days = signal(30);
  readonly bank = signal('');
  readonly view = computed<DashboardData>(() => {
    if (this.data()) return this.data()!;
    const accounts = this.api.accounts().filter(a => !this.bank() || a.institution === this.bank()).map(a => ({
      id: a.id, bank: a.institution, accountName: a.accountName, maskedIdentifier: a.maskedIdentifier,
      accountType: a.accountType, availableBalanceMinor: Math.round(a.availableBalance * 100),
      creditOutstandingMinor: Math.round(a.creditOutstanding * 100),
    }));
    const to = new Date(); const from = new Date(to); from.setUTCDate(to.getUTCDate() - this.days() + 1);
    return {source: 'live', currency: 'KES', user: {name: this.api.displayName(), kind: this.api.kind()},
      period: {from: from.toISOString(), to: to.toISOString(), days: this.days()}, accounts,
      summary: {availableCashMinor: accounts.filter(a => a.accountType === 'DEPOSIT').reduce((sum,a) => sum+a.availableBalanceMinor,0),
        creditOutstandingMinor: accounts.filter(a => a.accountType === 'CREDIT').reduce((sum,a) => sum+a.creditOutstandingMinor,0),
        moneyInMinor: 0, moneyOutMinor: 0, netCashFlowMinor: 0},
      cashFlow: [], transactions: [], transactionCount: 0};
  });
  readonly banks = ['KCB', 'Equity', 'Stanbic', 'NCBA'];
  changeBank(bank: string) {
    if (this.loading() || bank === this.bank()) return;
    this.bank.set(bank);
    void this.load();
  }
  private readonly live = inject(LiveUpdates);
  constructor() {
    void this.load();
    // A transaction arriving for this customer while the page is open means
    // the figures on screen are stale; reload rather than wait for a manual refresh.
    effect(() => {
      if (this.live.lastUpdate() === null) return;
      void this.load();
    });
  }
  async load() {
    this.loading.set(true);
    this.error.set('');
    this.disconnected.set(false);
    this.data.set(null);
    try {
      const d = await firstValueFrom(
        this.http.get<DashboardData>('/api/dashboard?days=' + this.days() + '&bank=' + encodeURIComponent(this.bank())).pipe(timeout(15000)),
      );
      if (
        d?.currency !== 'KES' ||
        !d.summary ||
        !Array.isArray(d.accounts) ||
        !Array.isArray(d.transactions) ||
        !Array.isArray(d.cashFlow) || (this.bank() && d.bank !== this.bank())
      )
        throw new Error('Invalid response');
      this.data.set(d);
    } catch (error) {
      this.disconnected.set(error instanceof HttpErrorResponse && error.status === 501);
      this.data.set(null);
      this.error.set(this.disconnected() ? 'Live financial activity is not connected. Your dashboard layout and saved account snapshots remain available.' : 'We could not load financial activity. Your dashboard layout remains available. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }
  changePeriod(days: number) {
    if (this.loading() || days === this.days()) return;
    this.days.set(days);
    void this.load();
  }
  get spendingPoints() {
    const d = this.data();
    const totals = new Map<string, number>();
    for (const tx of d?.transactions ?? []) {
      if (tx.status !== 'POSTED' || tx.direction !== 'DEBIT' || !d?.accounts.some(a => a.id === tx.accountId && a.accountType === 'DEPOSIT')) continue;
      totals.set(tx.category, (totals.get(tx.category) ?? 0) + tx.amountMinor);
    }
    return [...totals].map(([category, value]) => ({ category, value })).sort((a,b) => b.value-a.value);
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
  accountBank(id: string) {
    return this.data()?.accounts.find((a) => a.id === id)?.bank ?? '';
  }
  accountMasked(id: string) {
    return this.data()?.accounts.find((a) => a.id === id)?.maskedIdentifier ?? '—';
  }
}
