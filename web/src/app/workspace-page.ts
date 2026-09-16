import { matchesNotification, NotificationItem } from './notification-filter';
import { WorkspaceIcon } from './workspace-icon';
import { FinanceChart, CategoryChart } from './finance-chart';
import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { WorkspaceApi, WorkspaceData, Budget, Investment, Entry } from './workspace-api';
import { BankLogo } from './bank-logo';
import { AccountApi } from './account-api';
import { workspaceLinks } from './workspace-shell';
type EditField = {
  key: string;
  label: string;
  type?: string;
  required?: boolean;
  min?: number;
  max?: number;
  step?: string;
  options?: { value: string; label: string }[];
};
@Component({
  imports: [CurrencyPipe, DatePipe, FormsModule, RouterLink, BankLogo, FinanceChart, CategoryChart, WorkspaceIcon],
  templateUrl: './workspace-page.html',
})
export class WorkspacePage implements OnInit, OnDestroy {
  private api = inject(WorkspaceApi);
  private accountsApi = inject(AccountApi);
  private route = inject(ActivatedRoute);
  private sub?: Subscription;
  page = 'accounts';
  readonly data = signal<WorkspaceData | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly pending = signal(false);
  readonly today = new Date().toISOString().slice(0, 10);
  start = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
  end = this.today;
  account = '';
  search = '';
  status = '';
  category = '';
  report = 'transactions';
  unreadOnly = false;
  editing = '';
  fields: EditField[] = [];
  form: Record<string, any> = {};
  deleteTarget: { collection: string; id: string } | null = null;
  dark = signal(document.documentElement.dataset['theme'] === 'dark');
  ngOnInit() {
    this.sub = this.route.data.subscribe((d) => {
      this.page = d['page'];
      this.editing = '';
      this.message.set('');
      this.search = '';
      this.status = '';
      this.category = '';
      this.account = this.route.snapshot.queryParamMap.get('account') ?? '';
      void this.load();
    });
  }
  ngOnDestroy() {
    this.sub?.unsubscribe();
  }
  get title() {
    return workspaceLinks.find((l) => l.path === this.page)?.label ?? 'Workspace';
  }
  get description() {
    return (
      {
        accounts: 'Your bank accounts, clearly organized.',
        transactions: 'Explore the activity behind every balance.',
        cashflow: 'Understand where your money comes from and where it goes.',
        budgets: 'Give every shilling a purpose.',
        investments: 'Keep your portfolio and maturity dates in view.',
        analysis: 'Turn your financial activity into a clearer picture.',
        reports: 'Review and export your financial records.',
        notifications: 'Stay on top of what needs your attention.',
        settings: 'Make this workspace yours.',
      } as Record<string, string>
    )[this.page];
  }
  async load() {
    this.loading.set(true);
    this.error.set('');
    try {
      this.data.set(await this.api.load());
    } catch {
      this.error.set('We couldn’t load this page. Check the account service and try again.');
      this.data.set(null);
    } finally {
      this.loading.set(false);
    }
  }
  get validRange() {
    return !!this.start && !!this.end && this.start <= this.end;
  }
  get accounts() {
    return (this.data()?.accounts ?? []).filter((a) => !this.account || a.id === this.account);
  }
  get rows() {
    return !this.validRange
      ? []
      : (this.data()?.transactions ?? [])
          .filter(
            (t) =>
              (!this.account || t.accountId === this.account) &&
              t.date >= this.start &&
              t.date <= this.end &&
              (!this.status || t.status === this.status) &&
              (!this.category || t.category === this.category) &&
              `${t.description} ${t.category}`.toLowerCase().includes(this.search.toLowerCase()),
          )
          .sort((a, b) => b.date.localeCompare(a.date));
  }
  get cashRows() {
    const ids = new Set(this.accounts.filter((a) => a.accountType === 'DEPOSIT').map((a) => a.id));
    return this.rows.filter((t) => t.status === 'POSTED' && ids.has(t.accountId));
  }
  total(rows: Entry[], direction: string) {
    return rows.filter((t) => t.direction === direction).reduce((s, t) => s + t.amountMinor, 0);
  }
  get cashIn() {
    return this.total(this.cashRows, 'CREDIT');
  }
  get cashOut() {
    return this.total(this.cashRows, 'DEBIT');
  }
  get cash() {
    return this.accounts.reduce((s, a) => s + a.availableBalanceMinor, 0);
  }
  get debt() {
    return this.accounts.reduce((s, a) => s + a.creditOutstandingMinor, 0);
  }
  get categories() {
    return [...new Set((this.data()?.transactions ?? []).map((t) => t.category))].sort();
  }
  accountName(id: string) {
    return this.data()?.accounts.find((a) => a.id === id)?.accountName ?? 'All accounts';
  }
  get bins() {
    if (!this.validRange) return [];
    const days = Math.floor((Date.parse(this.end) - Date.parse(this.start)) / 86400000) + 1;
    return Array.from({ length: Math.min(6, days) }, (_, i) => {
      const count = Math.min(6, days);
      const date = (offset: number) =>
        new Date(Date.parse(this.start) + offset * 86400000).toISOString().slice(0, 10);
      const from = date(Math.floor((i * days) / count)),
        to = date(Math.floor(((i + 1) * days) / count) - 1);
      const rows = this.cashRows.filter((t) => t.date >= from && t.date <= to);
      return { from, to, in: this.total(rows, 'CREDIT'), out: this.total(rows, 'DEBIT') };
    });
  }
  height(value: number) {
    return (100 * value) / Math.max(1, ...this.bins.flatMap((b) => [b.in, b.out]));
  }
  get composition() {
    const rows = this.rows.filter((t) => t.status === 'POSTED' && t.direction === 'DEBIT');
    return this.categories
      .map((category) => ({
        category,
        value: rows.filter((t) => t.category === category).reduce((s, t) => s + t.amountMinor, 0),
      }))
      .filter((c) => c.value > 0)
      .sort((a, b) => b.value - a.value);
  }
  get totalSpending() {
    return this.composition.reduce((s, c) => s + c.value, 0);
  }
  spent(b: Budget) {
    return (this.data()?.transactions ?? [])
      .filter(
        (t) =>
          t.status === 'POSTED' &&
          t.direction === 'DEBIT' &&
          t.category.toLowerCase() === b.category.toLowerCase() &&
          t.date >= b.start &&
          t.date <= b.end &&
          (!b.accountId || t.accountId === b.accountId),
      )
      .reduce((s, t) => s + t.amountMinor, 0);
  }
  get budgets() {
    return (this.data()?.budgets ?? []).filter(
      (b) =>
        b.start <= this.end &&
        b.end >= this.start &&
        (!this.account || !b.accountId || b.accountId === this.account),
    );
  }
  budgetStatus(b: Budget) {
    return this.spent(b) > b.allocatedMinor
      ? 'Over budget'
      : this.spent(b) >= (b.allocatedMinor * b.threshold) / 100
        ? 'Approaching limit'
        : 'Within budget';
  }
  get portfolio() {
    const rows = this.data()?.investments ?? [];
    return rows.some((i) => i.currentValueMinor === null)
      ? null
      : rows.reduce((s, i) => s + (i.currentValueMinor ?? 0), 0);
  }
  get principal() {
    return (this.data()?.investments ?? []).reduce((s, i) => s + i.principalMinor, 0);
  }
  get alerts() {
    const d = this.data();
    if (!d) return [];
    const alerts: { id: string; title: string; message: string; target: string; read: boolean }[] =
      [];
    const add = (id: string, title: string, message: string, target: string) =>
      alerts.push({ id, title, message, target, read: d.read.includes(id) });
    if (d.profile.balanceAlerts)
      for (const a of d.accounts)
        if (a.accountType === 'DEPOSIT' && a.availableBalanceMinor < d.profile.lowBalanceMinor)
          add(
            'balance:' + a.id,
            'Low recorded balance',
            a.accountName + ' is below your balance threshold.',
            'accounts',
          );
    if (d.profile.budgetAlerts)
      for (const b of d.budgets)
        if (this.spent(b) >= (b.allocatedMinor * b.threshold) / 100)
          add(
            'budget:' + b.id,
            this.budgetStatus(b),
            b.category + ' has reached its warning threshold.',
            'budgets',
          );
    if (d.profile.maturityAlerts)
      for (const i of d.investments)
        if (
          i.maturityDate &&
          i.maturityDate >= this.today &&
          Date.parse(i.maturityDate) - Date.parse(this.today) <= 90 * 86400000
        )
          add(
            'maturity:' + i.id,
            'Upcoming maturity',
            i.name + ' matures on ' + i.maturityDate + '.',
            'investments',
          );
    return alerts;
  }
  notificationTimeline = '';
  notificationCategory = '';
  notificationTransaction = '';
  notificationImportant = '';
  notificationBank = '';
  readonly notificationBanks = ['KCB', 'Equity', 'Stanbic', 'NCBA'];
  resetNotificationFilters() {
    this.notificationTimeline = this.notificationCategory = this.notificationTransaction = this.notificationImportant = this.notificationBank = '';
    this.unreadOnly = false;
  }
  get notificationItems(): NotificationItem[] {
    const d = this.data();
    if (!d) return [];
    const bankFor = (id: string) => d.accounts.find(a => a.id === id)?.bank ?? '';
    const warnings = this.alerts.map(a => {
      const id = a.id.slice(a.id.indexOf(':')+1);
      const budget = a.id.startsWith('budget:') ? d.budgets.find(b => b.id === id) : undefined;
      // Active warnings have no persisted event timestamp; do not invent one.
      return {...a, date:'', category:budget?.category ?? '', transaction:'',
        important:a.id.startsWith('balance:') ? 'funds' : budget ? 'budget' : 'maturity',
        bank:a.id.startsWith('balance:') ? bankFor(id) : budget ? bankFor(budget.accountId) : ''};
    });
    const transactions = d.transactions.map(t => ({
      id:'transaction:' + t.id, title:t.description,
      message:(t.direction === 'CREDIT' ? 'Received' : 'Sent') + ' · KES ' + (t.amountMinor/100).toLocaleString('en-KE',{minimumFractionDigits:2,maximumFractionDigits:2}) + ' · ' + t.category + ' · ' + t.status,
      target:'transactions', read:d.read.includes('transaction:' + t.id), date:t.date.slice(0,10), category:t.category,
      transaction:t.status === 'PENDING' ? 'pending' : t.status === 'POSTED' ? (t.direction === 'CREDIT' ? 'received' : 'sent') : t.status.toLowerCase(), important:'', bank:bankFor(t.accountId)
    }));
    for (let i=0;i<transactions.length;i++) {
      const t = d.transactions[i];
      if(t.status !== 'POSTED') transactions[i].message = t.status + ' · KES ' + (t.amountMinor/100).toLocaleString('en-KE',{minimumFractionDigits:2,maximumFractionDigits:2}) + ' · ' + t.category;
    }
    return [...warnings, ...transactions.sort((a,b)=>b.date.localeCompare(a.date))];
  }
  get visibleAlerts() {
    const today = new Intl.DateTimeFormat('en-CA', {timeZone:'Africa/Nairobi',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    return this.notificationItems.filter(a => matchesNotification(a, {
      timeline:this.notificationTimeline,category:this.notificationCategory,transaction:this.notificationTransaction,
      important:this.notificationImportant,bank:this.notificationBank,unread:this.unreadOnly
    }, today));
  }
  get reportRows(): Record<string, unknown>[] {
    if (this.report === 'accounts')
      return this.accounts.map((a) => ({
        Bank: a.bank,
        Account: a.accountName,
        Identifier: a.maskedIdentifier,
        Type: a.accountType,
        'Available KES': a.availableBalanceMinor / 100,
        'Owed KES': a.creditOutstandingMinor / 100,
      }));
    if (this.report === 'cashflow')
      return this.bins.map((b) => ({
        From: b.from,
        To: b.to,
        'Money in KES': b.in / 100,
        'Money out KES': b.out / 100,
        'Net KES': (b.in - b.out) / 100,
      }));
    return this.rows.map((t) => ({
      Date: t.date,
      Description: t.description,
      Account: this.accountName(t.accountId),
      Category: t.category,
      Status: t.status,
      Direction: t.direction,
      'Amount KES': t.amountMinor / 100,
    }));
  }
  get reportColumns() {
    return Object.keys(this.reportRows[0] ?? {});
  }
  exportPdf() {
    const rows = this.reportRows;
    if (!rows.length || !this.validRange) return;
    const reportWindow = window.open('', '_blank');
    if (!reportWindow) {
      this.error.set('Allow pop-ups for this site, then select Export PDF again.');
      return;
    }
    reportWindow.opener = null;
    const doc = reportWindow.document;
    const title =
      (
        {
          transactions: 'Transaction ledger',
          accounts: 'Account balances',
          cashflow: 'Cash-flow summary',
        } as Record<string, string>
      )[this.report] ?? 'Financial report';
    doc.title = 'SM-Intelligence — ' + title;
    doc.documentElement.lang = 'en';
    const style = doc.createElement('style');
    style.textContent = `@page{size:A4 landscape;margin:16mm}*{box-sizing:border-box}body{font:11px Arial,sans-serif;color:#18334f;margin:24px}h1{font-size:25px;margin:10px 0}h2{font-size:13px;color:#2563eb;letter-spacing:1px}p{line-height:1.6}table{width:100%;border-collapse:collapse;table-layout:fixed;margin-top:22px}th,td{padding:9px 7px;border-bottom:1px solid #dce6f1;text-align:left;overflow-wrap:anywhere;vertical-align:top}th{background:#eaf2fe;color:#18334f;font-size:10px}td.amount{text-align:right;font-variant-numeric:tabular-nums}thead{display:table-header-group}tr{break-inside:avoid}.note{color:#52667c}.toolbar{padding:16px;background:#f1f5f9;display:flex;gap:16px;align-items:center}button{padding:10px 18px;border:0;border-radius:8px;background:#2563eb;color:white;cursor:pointer}@media print{body{margin:0}.toolbar{display:none}th{print-color-adjust:exact;-webkit-print-color-adjust:exact}}`;
    doc.head.append(style);
    const add = (tag: string, text: string, parent: HTMLElement = doc.body) => {
      const el = doc.createElement(tag);
      el.textContent = text;
      parent.append(el);
      return el;
    };
    const toolbar = add('div', '');
    toolbar.className = 'toolbar';
    const print = add('button', 'Print / Save as PDF', toolbar);
    print.addEventListener('click', () => reportWindow.print());
    add('span', 'Choose “Save as PDF” as your destination in the print dialog.', toolbar);
    add('h2', 'SMARTMONEY · SM-INTELLIGENCE');
    add('h1', title);
    add(
      'p',
      (this.data()?.profile.name ?? '') +
        ' · ' +
        (this.account ? this.accountName(this.account) : 'All accounts'),
    );
    add(
      'p',
      this.report === 'accounts'
        ? 'Current account balance snapshots · KES'
        : this.start + ' to ' + this.end + ' · KES',
    );
    add('p', 'Generated ' + new Date().toLocaleString('en-KE') + ' · ' + rows.length + ' records');
    if (this.data()?.source === 'sample')
      add('p', 'Sample data — local development. No live bank connection.').className = 'note';
    const table = add('table', '');
    const head = add('thead', '', table);
    const header = add('tr', '', head);
    const columns = Object.keys(rows[0]);
    for (const column of columns) {
      const th = add('th', column, header);
      th.setAttribute('scope', 'col');
    }
    const body = add('tbody', '', table);
    for (const row of rows) {
      const tr = add('tr', '', body);
      for (const column of columns) {
        const value = row[column];
        const td = add(
          'td',
          typeof value === 'number'
            ? new Intl.NumberFormat('en-KE', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
              }).format(value)
            : String(value ?? ''),
          tr,
        );
        if (typeof value === 'number') td.className = 'amount';
      }
    }
    add(
      'p',
      this.report === 'accounts'
        ? 'Balances are snapshots; the date filter does not change their values.'
        : this.report === 'cashflow'
          ? 'Cash flow includes posted deposit-account activity only.'
          : 'Transaction status and direction are included; non-posted records do not contribute to cash-flow totals.',
    ).className = 'note';
    this.message.set('PDF report opened. Choose Save as PDF in the print dialog.');
    reportWindow.focus();
    setTimeout(() => {
      if (!reportWindow.closed) reportWindow.print();
    }, 250);
  }
  exportCsv() {
    const rows = this.reportRows;
    if (!rows.length) return;
    const cell = (v: unknown) =>
      '"' +
      String(v ?? '')
        .replace(/^[=+\-@\t\r]/, "'$&")
        .replaceAll('"', '""') +
      '"';
    const text = [
      this.reportColumns.map(cell).join(','),
      ...rows.map((r) => this.reportColumns.map((k) => cell(r[k])).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\ufeff' + text], { type: 'text/csv;charset=utf-8;' }),
    );
    const a = document.createElement('a');
    a.href = url;
    a.download = 'sm-' + this.report + '-' + this.today + '.csv';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    this.message.set('CSV exported.');
  }
  open(kind: string, row?: any) {
    this.editing = kind;
    this.message.set('');
    this.error.set('');
    const accountOptions = (this.data()?.accounts ?? []).map((a) => ({
      value: a.id,
      label: a.accountName,
    }));
    const f = (
      key: string,
      label: string,
      type = 'text',
      extra: Partial<EditField> = {},
    ): EditField => ({ key, label, type, required: true, ...extra });
    const amount = (key: string, label: string) =>
      f(key, label, 'number', { min: 0, step: '0.01' });
    if (kind === 'accounts') {
      this.fields = [
        f('bank', 'Bank', 'select', {
          options: ['KCB', 'Equity', 'NCBA', 'Stanbic'].map((value) => ({ value, label: value })),
        }),
        f('accountName', 'Account name'),
        f('accountNumber', 'Account number'),
        f('cardType', 'Account type', 'select', {
          options: [
            { value: 'debit', label: 'Deposit / debit' },
            { value: 'credit', label: 'Credit' },
          ],
        }),
        amount('balance', 'Opening balance / credit owed (KES)'),
        f('balanceDate', 'Balance effective date', 'date'),
      ];
      this.form = {
        bank: 'KCB',
        accountName: '',
        accountNumber: '',
        cardType: 'debit',
        balance: 0,
        balanceDate: this.today,
      };
    }
    if (kind === 'transactions') {
      this.fields = [
        f('description', 'Description'),
        f('accountId', 'Account', 'select', { options: accountOptions }),
        f('date', 'Transaction date', 'date'),
        f('category', 'Category'),
        f('direction', 'Direction', 'select', {
          options: [
            { value: 'CREDIT', label: 'Credit / money in' },
            { value: 'DEBIT', label: 'Debit / money out' },
          ],
        }),
        amount('amount', 'Amount (KES)'),
        f('status', 'Status', 'select', {
          options: ['POSTED', 'PENDING', 'FAILED', 'REVERSED', 'CANCELLED'].map((value) => ({
            value,
            label: value,
          })),
        }),
      ];
      this.form = row
        ? { ...row, amount: row.amountMinor / 100 }
        : {
            accountId: accountOptions[0]?.value ?? '',
            description: '',
            date: this.today,
            category: 'Shopping',
            direction: 'DEBIT',
            amount: 0,
            status: 'POSTED',
          };
    }
    if (kind === 'budgets') {
      this.fields = [
        f('category', 'Category'),
        amount('allocated', 'Allocation (KES)'),
        f('start', 'Period start', 'date'),
        f('end', 'Period end', 'date'),
        f('threshold', 'Warning threshold (%)', 'number', { min: 1, max: 100, step: '1' }),
        f('accountId', 'Account scope', 'select', {
          required: false,
          options: [{ value: '', label: 'All accounts' }, ...accountOptions],
        }),
      ];
      this.form = row
        ? { ...row, allocated: row.allocatedMinor / 100 }
        : {
            category: '',
            allocated: 0,
            start: this.start,
            end: this.end,
            threshold: 85,
            accountId: '',
          };
    }
    if (kind === 'investments') {
      this.fields = [
        f('name', 'Investment name'),
        f('type', 'Investment type', 'select', {
          options: ['Money Market', 'Fixed Deposit', 'Treasury Bill', 'Other'].map((value) => ({
            value,
            label: value,
          })),
        }),
        amount('principal', 'Principal (KES)'),
        { ...amount('currentValue', 'Current value (KES)'), required: false },
        f('valuationDate', 'Valuation date', 'date'),
        f('maturityDate', 'Maturity date', 'date', { required: false }),
      ];
      this.form = row
        ? {
            ...row,
            principal: row.principalMinor / 100,
            currentValue: row.currentValueMinor === null ? '' : row.currentValueMinor / 100,
          }
        : {
            name: '',
            type: 'Money Market',
            principal: 0,
            currentValue: '',
            valuationDate: this.today,
            maturityDate: '',
          };
    }
    if (kind === 'profile') {
      const p = this.data()!.profile;
      this.fields = [
        f('name', 'Full name'),
        f('organization', 'Organization', 'text', { required: false }),
        amount('lowBalance', 'Low-balance alert threshold (KES)'),
      ];
      this.form = { ...p, lowBalance: p.lowBalanceMinor / 100 };
    }
    setTimeout(() => document.getElementById('editor-heading')?.focus());
  }
  async save(form: NgForm) {
    if (form.invalid || this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    try {
      const b = { ...this.form };
      const minor = (v: unknown) => {
        if (!/^\d+(\.\d{1,2})?$/.test(String(v)))
          throw new Error('Use nonnegative amounts with at most two decimal places.');
        const n = Math.round(Number(v) * 100);
        if (!Number.isSafeInteger(n)) throw new Error('Amount is too large.');
        return n;
      };
      if (this.editing === 'accounts') {
        await this.accountsApi.saveSetup({
          ...b,
          balance: Number(b['balance']),
          currency: 'KES',
        } as any);
      } else {
        if (this.editing === 'transactions') b['amountMinor'] = minor(b['amount']);
        if (this.editing === 'budgets') b['allocatedMinor'] = minor(b['allocated']);
        if (this.editing === 'investments') {
          b['principalMinor'] = minor(b['principal']);
          b['currentValueMinor'] =
            b['currentValue'] === '' || b['currentValue'] === null
              ? null
              : minor(b['currentValue']);
        }
        if (this.editing === 'profile') b['lowBalanceMinor'] = minor(b['lowBalance']);
        await this.api.save(this.editing, b);
      }
      this.editing = '';
      await this.load();
      this.message.set('Changes saved.');
    } catch (e: any) {
      this.error.set(e?.error?.error ?? e?.message ?? 'Unable to save. Please try again.');
    } finally {
      this.pending.set(false);
    }
  }
  async remove() {
    if (!this.deleteTarget) return;
    this.pending.set(true);
    try {
      await this.api.remove(this.deleteTarget.collection, this.deleteTarget.id);
      this.deleteTarget = null;
      await this.load();
      this.message.set('Record deleted.');
    } catch {
      this.error.set('Unable to delete this record. Please try again.');
    } finally {
      this.pending.set(false);
    }
  }
  async markRead(ids: string[]) {
    try {
      await this.api.save('read', { ids });
      await this.load();
    } catch {
      this.error.set('Unable to update notifications.');
    }
  }
  setTheme(dark: boolean) {
    if (dark !== this.dark()) this.toggleTheme();
  }
  toggleTheme() {
    const next = !this.dark();
    this.dark.set(next);
    document.documentElement.dataset['theme'] = next ? 'dark' : 'light';
    try {
      localStorage.setItem('sm-intelligence-appearance', next ? 'dark' : 'light');
      this.message.set('Appearance saved on this device.');
    } catch {
      this.message.set('Appearance changed for this visit.');
    }
  }
}
