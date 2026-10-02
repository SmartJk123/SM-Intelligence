import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AUDIT_LOGS, AuditLog } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const EXPORT_HEADERS = ['Timestamp', 'User', 'Role', 'Action', 'Resource', 'IP', 'Result'];

@Component({
  selector: 'app-audit-logs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, ActionMenu],
  templateUrl: './audit-logs.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class AuditLogs {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);

  protected readonly logs = AUDIT_LOGS;
  protected readonly resultFilters = ['All', 'Success', 'Failed'];

  protected readonly query = signal('');
  protected readonly resultFilter = signal('All');

  protected readonly total = AUDIT_LOGS.length;
  protected readonly successCount = AUDIT_LOGS.filter((log) => log.result === 'Success').length;
  protected readonly failedCount = AUDIT_LOGS.filter((log) => log.result !== 'Success').length;
  protected readonly actorCount = new Set(AUDIT_LOGS.map((log) => log.user)).size;

  protected readonly filtered = computed<AuditLog[]>(() => {
    const term = this.query().trim().toLowerCase();
    const result = this.resultFilter();
    return AUDIT_LOGS.filter((log) => {
      const matchesResult = result === 'All' || log.result === result;
      const matchesTerm =
        term.length === 0 ||
        log.user.toLowerCase().includes(term) ||
        log.action.toLowerCase().includes(term) ||
        log.resource.toLowerCase().includes(term) ||
        log.ip.includes(term);
      return matchesResult && matchesTerm;
    });
  });

  protected readonly rowMenu: ActionMenuItem[] = [
    { id: 'view', label: 'View entry' },
    { id: 'copy', label: 'Copy resource id' },
    { id: 'actor', label: 'Filter by this actor' },
    { id: 'flag', label: 'Flag as suspicious', tone: 'danger' },
  ];

  protected readonly headings = [
    'Timestamp',
    'User',
    'Role',
    'Action',
    'Resource',
    'IP',
    'Result',
    'Action',
  ];

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected applyCardAction(action: string): void {
    this.query.set('');
    this.resultFilter.set(action === 'all' ? 'All' : action);
  }

  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No entries match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-audit-log-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      rows.map((log) => [log.time, log.user, log.role, log.action, log.resource, log.ip, log.result]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} audit ${rows.length === 1 ? 'entry' : 'entries'} written to CSV.`,
    );
  }

  protected onRowAction(action: string, log: AuditLog): void {
    switch (action) {
      case 'copy':
        void this.copyText(log.resource, 'Resource id copied');
        break;
      case 'actor':
        this.resultFilter.set('All');
        this.query.set(log.user);
        this.toasts.show('Filtered by actor', 'info', `Showing activity for ${log.user}.`);
        break;
      case 'flag':
        this.toasts.show('Flagged for review', 'warning', `${log.action} by ${log.user} was flagged.`);
        break;
      default:
        this.toasts.show('Audit entry', 'info', `${log.action} by ${log.user} at ${log.time}`);
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
