import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DemoMovement } from '../../core/bank-integration.gateway';
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
  imports: [KpiCard, SurfaceCard],
  templateUrl: './transactions.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Transactions {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly integrations = inject(BankIntegrationService);

  protected readonly money = money;
  protected readonly banks = ['kcb', 'stanbic', 'ncba'];
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
