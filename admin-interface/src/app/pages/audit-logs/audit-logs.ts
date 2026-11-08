import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AuditService } from '../../core/audit.service';
import { AuditLog } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const EXPORT_HEADERS = ['Timestamp', 'Who', 'Type', 'Action', 'Resource', 'Detail', 'IP', 'Result'];

/**
 * The platform audit trail: every sign up, sign in (and failed attempt), sign out
 * and password reset by customers, and every change staff make. Read from
 * identity-service; the newest 500 entries are shown.
 */
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
  protected readonly audit = inject(AuditService);

  protected readonly resultFilters = ['All', 'Success', 'Failed'];
  protected readonly actorFilters = ['Everyone', 'Customers', 'Staff', 'System'];

  protected readonly query = signal('');
  protected readonly resultFilter = signal('All');
  protected readonly actorFilter = signal('Everyone');

  private readonly logs = this.audit.logs;
  protected readonly total = computed(() => this.logs().length);
  protected readonly successCount = computed(() => this.logs().filter((log) => log.result === 'Success').length);
  protected readonly failedCount = computed(() => this.logs().filter((log) => log.result !== 'Success').length);
  protected readonly signInsToday = computed(() => {
    const today = new Date().toDateString();
    return this.logs().filter((log) => log.action === 'Signed in' && new Date(log.occurredAt).toDateString() === today)
      .length;
  });

  protected readonly filtered = computed<AuditLog[]>(() => {
    const term = this.query().trim().toLowerCase();
    const result = this.resultFilter();
    const actor = this.actorFilter();
    const role = actor === 'Customers' ? 'Customer' : actor;
    return this.logs().filter((log) => {
      const matchesResult = result === 'All' || log.result === result;
      const matchesActor = actor === 'Everyone' || log.role === role;
      const matchesTerm =
        term.length === 0 ||
        log.user.toLowerCase().includes(term) ||
        log.action.toLowerCase().includes(term) ||
        log.resource.toLowerCase().includes(term) ||
        log.detail.toLowerCase().includes(term) ||
        log.ip.includes(term);
      return matchesResult && matchesActor && matchesTerm;
    });
  });

  protected readonly rowMenu: ActionMenuItem[] = [
    { id: 'view', label: 'View entry' },
    { id: 'copy', label: 'Copy resource' },
    { id: 'actor', label: 'Filter by this person' },
  ];

  protected readonly headings = ['Timestamp', 'Who', 'Type', 'Action', 'Resource', 'IP', 'Result', ''];

  constructor() {
    void this.audit.load();
  }

  protected refresh(): void {
    void this.audit.load().then(() => this.toasts.show('Audit log refreshed', 'info', 'Entries reloaded from identity-service.'));
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected applyCardAction(action: string): void {
    this.query.set('');
    this.actorFilter.set('Everyone');
    if (action === 'signins') {
      this.resultFilter.set('All');
      this.query.set('signed in');
      return;
    }
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
      rows.map((log) => [log.occurredAt, log.user, log.role, log.action, log.resource, log.detail, log.ip, log.result]),
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
        void this.copyText(log.resource, 'Resource copied');
        break;
      case 'actor':
        this.resultFilter.set('All');
        this.actorFilter.set('Everyone');
        this.query.set(log.user);
        this.toasts.show('Filtered by person', 'info', `Showing activity for ${log.user}.`);
        break;
      default:
        this.toasts.show(log.action, 'info', `${log.user} at ${log.time}${log.detail ? '. ' + log.detail : ''}`);
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
