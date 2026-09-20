import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { BANK_ACCOUNTS, BankAccount } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const EXPORT_HEADERS = [
  'Organisation',
  'Bank',
  'Account',
  'Masked number',
  'Currency',
  'Last sync',
  'Status',
];

@Component({
  selector: 'app-bank-accounts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, ActionMenu],
  templateUrl: './bank-accounts.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class BankAccounts {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);

  protected readonly statusFilters = ['All', 'Connected', 'Warning'];
  protected readonly query = signal('');
  protected readonly statusFilter = signal('All');
  protected readonly headings = [
    'Organisation',
    'Bank',
    'Account',
    'Masked number',
    'Currency',
    'Last sync',
    'Status',
    'Action',
  ];

  /** Local copy so sync and reconnect actions visibly change the row. */
  protected readonly accounts = signal<BankAccount[]>(BANK_ACCOUNTS.map((account) => ({ ...account })));

  protected readonly total = computed(() => this.accounts().length);
  protected readonly connected = computed(
    () => this.accounts().filter((account) => account.status === 'Connected').length,
  );
  protected readonly needsAttention = computed(
    () => this.accounts().filter((account) => account.status === 'Warning').length,
  );

  protected readonly filtered = computed<BankAccount[]>(() => {
    const term = this.query().trim().toLowerCase();
    const status = this.statusFilter();
    return this.accounts().filter(
      (account) =>
        (status === 'All' || account.status === status) &&
        (term.length === 0 ||
          account.org.toLowerCase().includes(term) ||
          account.bank.toLowerCase().includes(term) ||
          account.name.toLowerCase().includes(term) ||
          account.masked.includes(term)),
    );
  });

  protected readonly rowMenu: ActionMenuItem[] = [
    { id: 'view', label: 'View account', tone: 'primary' },
    { id: 'sync', label: 'Sync now' },
    { id: 'copy', label: 'Copy account number' },
    { id: 'reconnect', label: 'Reconnect account' },
  ];

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected applyCardAction(action: string): void {
    this.query.set('');
    this.statusFilter.set(action === 'all' ? 'All' : action);
  }

  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No accounts match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-bank-accounts-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      rows.map((a) => [a.org, a.bank, a.name, a.masked, a.currency, a.lastSync, a.status]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} account${rows.length === 1 ? '' : 's'} written to CSV.`,
    );
  }

  protected onRowAction(action: string, account: BankAccount): void {
    switch (action) {
      case 'sync':
        this.updateAccount(account.id, { lastSync: 'Just now', status: 'Connected' });
        this.toasts.show('Sync complete', 'success', `${account.org} · ${account.name} is up to date.`);
        break;
      case 'reconnect':
        this.updateAccount(account.id, { lastSync: 'Just now', status: 'Connected' });
        this.toasts.show('Account reconnected', 'success', `${account.bank} ${account.masked} is connected again.`);
        break;
      case 'copy':
        void this.copyText(account.masked, 'Account number copied');
        break;
      default:
        this.toasts.show('Account selected', 'info', `${account.org} · ${account.bank} ${account.masked}`);
    }
  }

  private updateAccount(id: number, patch: Partial<BankAccount>): void {
    this.accounts.update((current) =>
      current.map((account) => (account.id === id ? { ...account, ...patch } : account)),
    );
  }

  private async copyText(value: string, title: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(value);
      this.toasts.show(title, 'success', value);
    } catch {
      this.toasts.show('Copy failed', 'danger', 'Your browser blocked clipboard access.');
    }
  }
}
