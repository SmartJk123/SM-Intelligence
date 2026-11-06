import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { AdminTransaction, DemoMovement } from '../../core/bank-integration.gateway';
import { UserService } from '../../core/user.service';
import { Badge } from '../../shared/badge';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { money } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

type DirectionFilter = 'All' | 'Credit' | 'Debit';

const EXPORT_HEADERS = [
  'Reference',
  'Bank',
  'Description',
  'Direction',
  'Amount',
  'Currency',
  'Booked',
  'Simulated',
];

@Component({
  selector: 'app-transactions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge],
  templateUrl: './transactions.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Transactions {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly integrations = inject(BankIntegrationService);
  private readonly users = inject(UserService);

  /** live: every transaction the banks reported. demo: the demonstration account. */
  protected readonly tab = signal<'live' | 'demo'>('live');

  // ---- Live transactions (bank-integration-service, refreshed every 15 seconds) ----
  protected readonly live = signal<AdminTransaction[]>([]);
  protected readonly liveLoaded = signal(false);
  protected readonly liveError = signal<string | null>(null);
  protected readonly liveUpdatedAt = signal<Date | null>(null);
  protected readonly liveQuery = signal('');
  protected readonly liveBank = signal('');
  protected readonly liveDirection = signal<DirectionFilter>('All');
  protected readonly liveDelivery = signal('All');
  protected readonly deliveryFilters = ['All', 'Delivered', 'Not linked', 'Failed'];
  private readonly newIds = signal<ReadonlySet<number>>(new Set());

  private readonly customerNames = computed(() => {
    const names = new Map<string, string>();
    for (const user of this.users.summaries()) names.set(user.id, user.name);
    return names;
  });

  protected readonly liveRows = computed(() => {
    const term = this.liveQuery().trim().toLowerCase();
    const delivery = { Delivered: 'DELIVERED', 'Not linked': 'UNLINKED', Failed: 'FAILED' }[this.liveDelivery()] ?? '';
    return this.live().filter((tx) =>
      (!this.liveBank() || tx.bankId === this.liveBank()) &&
      (this.liveDirection() === 'All' || tx.direction === this.liveDirection().toUpperCase()) &&
      (!delivery || tx.delivery === delivery) &&
      (!term || [tx.reference, tx.narration, tx.counterpartyName, tx.accountMasked, this.customer(tx)]
        .some((value) => (value ?? '').toLowerCase().includes(term))));
  });

  private readonly today = computed(() => {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    return this.live().filter((tx) => new Date(tx.receivedAt).getTime() >= start.getTime());
  });
  protected readonly liveCountToday = computed(() => this.today().length);
  protected readonly liveInToday = computed(() =>
    this.today().filter((tx) => tx.direction === 'CREDIT').reduce((sum, tx) => sum + tx.amount, 0));
  protected readonly liveOutToday = computed(() =>
    this.today().filter((tx) => tx.direction === 'DEBIT').reduce((sum, tx) => sum + tx.amount, 0));
  protected readonly undelivered = computed(() => this.live().filter((tx) => tx.delivery !== 'DELIVERED').length);

  constructor() {
    void this.loadLive();
    const timer = setInterval(() => void this.loadLive(true), 15_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  protected async loadLive(background = false): Promise<void> {
    try {
      const rows = await this.integrations.loadTransactions(1000, false);
      const known = new Set(this.live().map((tx) => tx.id));
      if (background && known.size > 0) {
        const fresh = rows.filter((tx) => !known.has(tx.id)).map((tx) => tx.id);
        if (fresh.length > 0) {
          this.newIds.set(new Set(fresh));
          this.toasts.show('New transactions', 'info', `${fresh.length} new transaction${fresh.length === 1 ? '' : 's'} from the banks.`);
          setTimeout(() => this.newIds.set(new Set()), 6000);
        }
      }
      this.live.set(rows);
      this.liveError.set(null);
      this.liveUpdatedAt.set(new Date());
    } catch {
      this.liveError.set('Transactions could not be loaded. Check that bank-integration-service is running.');
    } finally {
      this.liveLoaded.set(true);
    }
  }

  protected isNew(tx: AdminTransaction): boolean {
    return this.newIds().has(tx.id);
  }

  protected customer(tx: AdminTransaction): string {
    if (!tx.customerId) return '';
    return this.customerNames().get(tx.customerId) ?? tx.accountName ?? 'Linked customer';
  }

  protected deliveryLabel(tx: AdminTransaction): string {
    return { DELIVERED: 'Delivered', UNLINKED: 'Not linked', FAILED: 'Failed', PENDING: 'Pending' }[tx.delivery] ?? tx.delivery;
  }

  protected updatedLabel(): string {
    const at = this.liveUpdatedAt();
    return at ? at.toLocaleTimeString('en-KE', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '…';
  }

  protected exportLive(): void {
    const rows = this.liveRows();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No transactions match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-bank-transactions-${this.csv.stamp()}.csv`,
      ['Received', 'Booked', 'Bank', 'Reference', 'Direction', 'Amount', 'Currency', 'Counterparty', 'Narration',
        'Account', 'Customer', 'Delivery', 'Delivery error'],
      rows.map((tx) => [tx.receivedAt, tx.bookingDate, tx.bankId.toUpperCase(), tx.reference, tx.direction, tx.amount,
        tx.currency, tx.counterpartyName, tx.narration, tx.accountMasked, this.customer(tx), this.deliveryLabel(tx),
        tx.deliveryError]),
    );
    this.toasts.show('Export ready', 'success', `${rows.length} transaction${rows.length === 1 ? '' : 's'} written to CSV.`);
  }

  protected readonly money = money;
  protected readonly banks = ['kcb', 'stanbic', 'ncba', 'equity'];
  protected readonly directionFilters: DirectionFilter[] = ['All', 'Credit', 'Debit'];

  protected readonly directionFilter = signal<DirectionFilter>('All');
  protected readonly bankId = signal('kcb');
  protected readonly amount = signal(2500);
  protected readonly sending = signal(false);

  /**
   * The demonstration account. A bank sandbox cannot show a real account
   * moving, so this account exists to show money arriving and leaving. Every
   * figure below is read back from the backend after each movement.
   */
  protected readonly summary = this.integrations.demoSummary;
  protected readonly movements = this.integrations.demoMovements;
  protected readonly accountNumber = computed(() => this.summary()?.accountNumber ?? '');
  protected readonly accountName = computed(() => this.summary()?.accountName ?? 'Demo business account');
  protected readonly creditToday = computed(() => this.summary()?.creditToday ?? 0);
  protected readonly debitToday = computed(() => this.summary()?.debitToday ?? 0);
  protected readonly netToday = computed(() => this.summary()?.netToday ?? 0);

  protected readonly filtered = computed<DemoMovement[]>(() => {
    const filter = this.directionFilter();
    return filter === 'All'
      ? this.movements()
      : this.movements().filter((movement) => movement.direction === filter);
  });

  protected amountColor(direction: string | null): string {
    return direction === 'Debit' ? 'var(--red-ink)' : 'var(--green-ink)';
  }

  protected directionLabel(direction: string | null): string {
    if (direction === 'Credit') { return 'Money in'; }
    if (direction === 'Debit') { return 'Money out'; }
    return 'Unclassified';
  }

  protected when(value: string | null): string {
    if (!value) { return 'not stated'; }
    const booked = new Date(value);
    return Number.isNaN(booked.getTime()) ? value : booked.toLocaleString();
  }

  /** The KPI cards filter the table below them. */
  protected applyCardAction(action: string): void {
    this.directionFilter.set(action === 'Credit' || action === 'Debit' ? action : 'All');
  }

  /** Downloads exactly the rows currently on screen. */
  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No transactions match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-transactions-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      rows.map((movement) => [
        movement.reference,
        movement.bankId,
        movement.narration ?? '',
        movement.direction ?? '',
        movement.amount,
        movement.currency,
        movement.bookingDate ?? '',
        String(movement.simulated),
      ]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} transaction${rows.length === 1 ? '' : 's'} written to CSV.`,
    );
  }

  protected refresh(): void {
    void this.integrations.loadDemo();
    this.toasts.show('Feed is current', 'info', 'The demo account was read again from the backend.');
  }

  /**
   * Records one movement on the demo account. The amount and the reason come
   * from the backend, and the list is reloaded afterwards, so the screen shows
   * what was stored rather than what the browser hoped was stored.
   */
  protected async send(direction: 'Credit' | 'Debit'): Promise<void> {
    const value = Number(this.amount());
    if (!Number.isFinite(value) || value <= 0) {
      this.toasts.show('Amount needed', 'warning', 'Enter an amount greater than zero.');
      return;
    }

    this.sending.set(true);
    try {
      const movement = await this.integrations.sendDemoMovement({
        bankId: this.bankId(),
        direction,
        amount: value,
        narration: direction === 'Credit' ? 'Demo funds received' : 'Demo payment out',
      });
      this.toasts.show(
        direction === 'Credit' ? 'Money in recorded' : 'Money out recorded',
        'success',
        `${movement.currency} ${this.money(movement.amount)} recorded on the demo account.`,
      );
    } catch (error) {
      this.toasts.show('The backend refused the movement', 'danger', this.describe(error));
    } finally {
      this.sending.set(false);
    }
  }

  private describe(error: unknown): string {
    const status = (error as { status?: number } | null)?.status;
    return status
      ? `The service answered HTTP ${status}. Check its log for the reason.`
      : 'The service could not be reached.';
  }
}
