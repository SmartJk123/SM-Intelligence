import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { OrgStatus, Organisation } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { UserService } from '../../core/user.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';
import { OrganisationDetail } from './organisation-detail';

const EXPORT_HEADERS = [
  'Organisation',
  'Business type',
  'Users',
  'Bank accounts',
  'Status',
  'Joined',
];

@Component({
  selector: 'app-organisations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, ActionMenu, OrganisationDetail],
  templateUrl: './organisations.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Organisations {
  private readonly ui = inject(UiService);
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly userDirectory = inject(UserService);

  /** Status changes made from the row menu, so the actions really take effect. */
  private readonly statusOverrides = signal<Record<string, OrgStatus>>({});

  /** Edits saved from the detail view, applied straight to the table. */
  private readonly edits = signal<Record<string, { name: string; type: string }>>({});

  /** Organisation currently open in the detail view. */
  protected readonly selectedOrg = signal<Organisation | null>(null);

  protected readonly statusFilters = ['All', 'Active', 'Pending', 'Suspended'];
  protected readonly query = signal('');
  protected readonly statusFilter = signal('All');
  protected readonly headings = [
    'Organisation',
    'Business type',
    'Users',
    'Accounts',
    'Status',
    'Joined',
    'Action',
  ];

  protected readonly orgs = computed<Organisation[]>(() => {
    const overrides = this.statusOverrides();
    const edits = this.edits();
    return this.userDirectory.organisations().map((org) => ({
      ...org,
      ...(edits[org.id] ?? {}),
      status: overrides[org.id] ?? org.status,
    }));
  });

  protected readonly total = computed(() => this.orgs().length);
  protected readonly active = computed(() => this.count('Active'));
  protected readonly pending = computed(() => this.count('Pending'));
  protected readonly suspended = computed(() => this.count('Suspended'));

  protected readonly filtered = computed<Organisation[]>(() => {
    const term = this.query().trim().toLowerCase();
    const status = this.statusFilter();
    return this.orgs().filter(
      (org) =>
        (status === 'All' || org.status === status) &&
        (term.length === 0 ||
          org.name.toLowerCase().includes(term) ||
          org.type.toLowerCase().includes(term)),
    );
  });

  protected actionsFor(org: Organisation): ActionMenuItem[] {
    return [
      { id: 'view', label: 'View organisation', tone: 'primary' },
      { id: 'edit', label: 'Edit details' },
      { id: 'export', label: 'Export this row' },
      org.status === 'Active'
        ? { id: 'suspend', label: 'Suspend organisation', tone: 'danger' }
        : { id: 'activate', label: 'Activate organisation' },
    ];
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  /** KPI cards act as filters for the table below them. */
  protected applyCardAction(action: string): void {
    this.query.set('');
    this.statusFilter.set(action === 'all' ? 'All' : action);
  }

  protected addOrganisation(): void {
    this.ui.openInvite('org');
  }

  protected openDetail(org: Organisation): void {
    this.selectedOrg.set(org);
  }

  protected closeDetail(): void {
    this.selectedOrg.set(null);
  }

  /** Persists edits made in the detail view. */
  protected onDetailSaved(payload: { name: string; type: string }): void {
    const org = this.selectedOrg();
    if (!org) {
      return;
    }
    this.edits.update((current) => ({ ...current, [org.id]: payload }));
    this.selectedOrg.set({ ...org, ...payload });
    this.toasts.show('Organisation updated', 'success', `${payload.name} was saved.`);
  }

  /** Applies a status change made in the detail view. */
  protected onDetailStatusChange(status: OrgStatus): void {
    const org = this.selectedOrg();
    if (!org) {
      return;
    }
    this.statusOverrides.update((current) => ({ ...current, [org.id]: status }));
    this.selectedOrg.set({ ...org, status });
    this.toasts.show(
      status === 'Active' ? 'Organisation activated' : 'Organisation suspended',
      status === 'Active' ? 'success' : 'warning',
      `${org.name} is now ${status.toLowerCase()}.`,
    );
  }

  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No organisations match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-organisations-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      rows.map((org) => [org.name, org.type, org.users, org.accounts, org.status, org.joined]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} organisation${rows.length === 1 ? '' : 's'} written to CSV.`,
    );
  }

  protected onRowAction(action: string, org: Organisation): void {
    switch (action) {
      case 'edit':
        this.toasts.show('Edit organisation', 'info', `${org.name} is ready for editing.`);
        break;
      case 'export':
        this.csv.download(
          `smartmoney-organisation-${org.id}-${this.csv.stamp()}.csv`,
          EXPORT_HEADERS,
          [[org.name, org.type, org.users, org.accounts, org.status, org.joined]],
        );
        this.toasts.show('Row exported', 'success', `${org.name} written to CSV.`);
        break;
      case 'suspend':
        this.setStatus(org, 'Suspended', 'Organisation suspended', 'warning');
        break;
      case 'activate':
        this.setStatus(org, 'Active', 'Organisation activated', 'success');
        break;
      default:
        this.toasts.show('Organisation selected', 'info', `${org.name} · ${org.type}`);
    }
  }

  private setStatus(
    org: Organisation,
    status: OrgStatus,
    title: string,
    tone: 'success' | 'warning',
  ): void {
    this.statusOverrides.update((current) => ({ ...current, [org.id]: status }));
    this.toasts.show(title, tone, `${org.name} is now ${status.toLowerCase()}.`);
  }

  private count(status: OrgStatus): number {
    return this.orgs().filter((org) => org.status === status).length;
  }
}
