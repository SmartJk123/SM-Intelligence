import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AccountLink, AccountLinkService, LINKABLE_BANKS } from '../../core/account-link.service';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { UserService } from '../../core/user.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const EXPORT_HEADERS = ['Owner', 'Email', 'Bank', 'Account', 'Account number', 'Status', 'Linked'];

/** One bank account, linked to exactly one customer. */
interface AccountRow {
  id: number;
  accountId: string;
  owner: string;
  email: string;
  bankId: string;
  bank: string;
  name: string;
  accountNumber: string;
  masked: string;
  status: 'Connected' | 'Warning';
  lastError: string | null;
  pendingDeliveries: number;
  linkedAt: string;
}

@Component({
  selector: 'app-bank-accounts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, ActionMenu],
  templateUrl: './bank-accounts.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class BankAccounts implements OnInit {
  private readonly links = inject(AccountLinkService);
  private readonly users = inject(UserService);
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly statusFilters = ['All', 'Connected', 'Warning'];
  protected readonly query = signal('');
  protected readonly statusFilter = signal('All');
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly headings = [
    'Owner',
    'Bank',
    'Account',
    'Account number',
    'Status',
    'Linked',
    'Action',
  ];

  /** Every bank account linked to a customer. One row is one account and exactly one owner. */
  protected readonly accounts = signal<AccountRow[]>([]);

  protected readonly total = computed(() => this.accounts().length);
  protected readonly connected = computed(
    () => this.accounts().filter((account) => account.status === 'Connected').length,
  );
  protected readonly needsAttention = computed(
    () => this.accounts().filter((account) => account.status === 'Warning').length,
  );

  protected readonly filtered = computed<AccountRow[]>(() => {
    const term = this.query().trim().toLowerCase();
    const status = this.statusFilter();
    return this.accounts().filter(
      (account) =>
        (status === 'All' || account.status === status) &&
        (term.length === 0 ||
          account.owner.toLowerCase().includes(term) ||
          account.email.toLowerCase().includes(term) ||
          account.bank.toLowerCase().includes(term) ||
          account.name.toLowerCase().includes(term) ||
          account.masked.includes(term)),
    );
  });

  protected readonly rowMenu: ActionMenuItem[] = [
    { id: 'view', label: 'View owner', tone: 'primary' },
    { id: 'sync', label: 'Retry delivery' },
    { id: 'copy', label: 'Copy account number' },
    { id: 'unlink', label: 'Unlink account', tone: 'danger' },
  ];

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      if (!this.users.loaded()) {
        await this.users.loadUsers();
      }
      const links = await this.links.list();
      this.accounts.set(links.map((link) => this.toRow(link)));
      this.loadError.set('');
    } catch {
      this.loadError.set('Linked accounts could not be loaded. Check that bank-integration-service is running.');
    } finally {
      this.loading.set(false);
    }
  }

  private toRow(link: AccountLink): AccountRow {
    const user = this.users.summary(link.userId);
    const digits = link.accountNumber.length;
    return {
      id: link.id,
      accountId: link.accountId,
      owner: user?.name ?? 'Unknown customer',
      email: user?.emailAddress ?? link.userId,
      bankId: link.bankId,
      bank: LINKABLE_BANKS.find((bank) => bank.id === link.bankId)?.name ?? link.bankId.toUpperCase(),
      name: link.accountName,
      accountNumber: link.accountNumber,
      masked: digits > 4 ? `•••• ${link.accountNumber.slice(-4)}` : link.accountNumber,
      status: link.pendingDeliveries > 0 || link.lastError ? 'Warning' : 'Connected',
      lastError: link.lastError,
      pendingDeliveries: link.pendingDeliveries,
      linkedAt: new Date(link.createdAt).toLocaleDateString('en-KE', { dateStyle: 'medium' }),
    };
  }

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
      rows.map((a) => [a.owner, a.email, a.bank, a.name, a.masked, a.status, a.linkedAt]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} account${rows.length === 1 ? '' : 's'} written to CSV.`,
    );
  }

  protected async onRowAction(action: string, account: AccountRow): Promise<void> {
    switch (action) {
      case 'view':
        void this.router.navigate(['/admin', 'users']);
        this.toasts.show('Owner', 'info', `${account.owner} · ${account.email}`);
        break;
      case 'sync':
        await this.retry(account);
        break;
      case 'copy':
        void this.copyText(account.accountNumber, 'Account number copied');
        break;
      case 'unlink':
        await this.unlink(account);
        break;
      default:
        this.toasts.show('Account selected', 'info', `${account.owner} · ${account.bank} ${account.masked}`);
    }
  }

  private async retry(account: AccountRow): Promise<void> {
    try {
      const updated = await this.links.sync(account.id);
      this.accounts.update((current) =>
        current.map((row) => (row.id === updated.id ? this.toRow(updated) : row)),
      );
      this.toasts.show(
        updated.pendingDeliveries === 0 ? 'Up to date' : 'Still waiting',
        updated.pendingDeliveries === 0 ? 'success' : 'warning',
        updated.pendingDeliveries === 0
          ? 'Every movement is on the dashboard.'
          : updated.lastError ?? `${updated.pendingDeliveries} movement(s) still waiting.`,
      );
    } catch {
      this.toasts.show('Retry failed', 'warning', 'The bank integration service could not be reached.');
    }
  }

  private async unlink(account: AccountRow): Promise<void> {
    if (!confirm(`Unlink ${account.bank} ${account.masked} from ${account.owner}? New movements will stop reaching their dashboard.`)) {
      return;
    }
    try {
      await this.links.unlink(account.id);
      this.accounts.update((current) => current.filter((row) => row.id !== account.id));
      this.toasts.show('Account unlinked', 'success', `${account.name} was removed from ${account.owner}.`);
    } catch {
      this.toasts.show('Not unlinked', 'warning', 'Please try again.');
    }
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
