import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AccentColor, RECONCILE, RecStatus, ReconciliationRecord } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const SUMMARY_ORDER: { status: RecStatus; label: string; color: AccentColor }[] = [
  { status: 'Matched', label: 'Matched', color: 'green' },
  { status: 'Unmatched', label: 'Unmatched', color: 'red' },
  { status: 'Needs Review', label: 'Needs review', color: 'gold' },
  { status: 'Duplicate', label: 'Duplicate', color: 'red' },
  { status: 'Failed', label: 'Failed', color: 'red' },
];

@Component({
  selector: 'app-reconciliation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, ActionMenu],
  templateUrl: './reconciliation.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Reconciliation {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);

  /** Mutable so that auto-match and the row actions change the queue in place. */
  protected readonly records = signal<ReconciliationRecord[]>(
    RECONCILE.map((record) => ({ ...record })),
  );

  protected readonly statusFilter = signal('All');
  protected readonly headings = [
    'Rec. ID',
    'Transaction',
    'Organisation',
    'Expected ref',
    'Match status',
    'Action',
  ];

  protected readonly filtered = computed(() => {
    const filter = this.statusFilter();
    return filter === 'All'
      ? this.records()
      : this.records().filter((record) => record.status === filter);
  });

  protected readonly summary = computed(() =>
    SUMMARY_ORDER.map((item) => ({
      ...item,
      value: this.records().filter((record) => record.status === item.status).length,
    })),
  );

  protected readonly unmatchedCount = computed(
    () =>
      this.records().filter(
        (record) =>
          (record.status === 'Unmatched' || record.status === 'Needs Review') && !!record.exp,
      ).length,
  );

  protected readonly rowMenu: ActionMenuItem[] = [
    { id: 'match', label: 'Match now', tone: 'primary' },
    { id: 'review', label: 'Mark for review' },
    { id: 'duplicate', label: 'Mark as duplicate', tone: 'danger' },
    { id: 'ignore', label: 'Ignore and remove', tone: 'danger' },
  ];

  protected delayFor(index: number): string {
    return `delay-${(index + 1) * 50}`;
  }

  /** KPI cards filter the queue. Selecting the active card again clears it. */
  protected applyCardAction(action: string): void {
    const match = SUMMARY_ORDER.find((item) => item.label === action);
    const status = match?.status ?? action;
    this.statusFilter.update((current) => (current === status ? 'All' : status));
  }

  /** Matches every record that carries an expected reference. */
  protected autoMatch(): void {
    const candidates = this.records().filter(
      (record) =>
        (record.status === 'Unmatched' || record.status === 'Needs Review') && !!record.exp,
    );

    if (candidates.length === 0) {
      this.toasts.show(
        'Nothing to match',
        'info',
        'No unmatched records with an expected reference were found.',
      );
      return;
    }

    const ids = new Set(candidates.map((record) => record.id));
    this.records.update((list) =>
      list.map((record) =>
        ids.has(record.id) ? { ...record, status: 'Matched' as RecStatus } : record,
      ),
    );
    this.toasts.show(
      'Auto-match complete',
      'success',
      `${candidates.length} record${candidates.length === 1 ? '' : 's'} matched against expected references.`,
    );
  }

  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No records match the current filter.');
      return;
    }
    this.csv.download(
      `smartmoney-reconciliation-${this.csv.stamp()}.csv`,
      ['Rec. ID', 'Transaction', 'Organisation', 'Expected ref', 'Amount', 'Status'],
      rows.map((record) => [
        record.id,
        record.tx,
        record.org,
        record.exp,
        record.amount,
        record.status,
      ]),
    );
    this.toasts.show('Export ready', 'success', `${rows.length} records written to CSV.`);
  }

  protected onRowAction(action: string, record: ReconciliationRecord): void {
    switch (action) {
      case 'match':
        this.setStatus(record, 'Matched', 'Matched', `${record.id} was matched to ${record.exp}.`);
        break;
      case 'review':
        this.setStatus(record, 'Needs Review', 'Sent for review', `${record.id} needs an administrator to confirm.`);
        break;
      case 'duplicate':
        this.setStatus(record, 'Duplicate', 'Marked as duplicate', `${record.id} will not be counted twice.`);
        break;
      case 'ignore':
        this.records.update((list) => list.filter((item) => item.id !== record.id));
        this.toasts.show('Removed from the queue', 'info', `${record.id} was ignored and removed.`);
        break;
    }
  }

  private setStatus(
    record: ReconciliationRecord,
    status: RecStatus,
    title: string,
    detail: string,
  ): void {
    this.records.update((list) =>
      list.map((item) => (item.id === record.id ? { ...item, status } : item)),
    );
    this.toasts.show(title, status === 'Duplicate' ? 'warning' : 'success', detail);
  }
}
