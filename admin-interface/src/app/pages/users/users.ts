import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AppUser, USERS, UserStatus } from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Avatar } from '../../shared/avatar';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

const EXPORT_HEADERS = ['Name', 'Email', 'Organisation', 'Role', 'Status', 'Last login'];

@Component({
  selector: 'app-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, Avatar, ActionMenu],
  templateUrl: './users.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Users {
  private readonly ui = inject(UiService);
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);

  private readonly statusOverrides = signal<Record<number, UserStatus>>({});

  protected readonly statusFilters = ['All', 'Active', 'Inactive', 'Suspended'];
  protected readonly query = signal('');
  protected readonly statusFilter = signal('All');
  protected readonly headings = [
    'User',
    'Email',
    'Organisation',
    'Role',
    'Status',
    'Last login',
    'Action',
  ];

  protected readonly users = computed<AppUser[]>(() => {
    const overrides = this.statusOverrides();
    return USERS.map((user) => ({ ...user, status: overrides[user.id] ?? user.status }));
  });

  protected readonly total = computed(() => this.users().length);
  protected readonly active = computed(() => this.count('Active'));
  protected readonly inactive = computed(() => this.count('Inactive'));
  protected readonly suspended = computed(() => this.count('Suspended'));

  protected readonly filtered = computed<AppUser[]>(() => {
    const term = this.query().trim().toLowerCase();
    const status = this.statusFilter();
    return this.users().filter(
      (user) =>
        (status === 'All' || user.status === status) &&
        (term.length === 0 ||
          user.name.toLowerCase().includes(term) ||
          user.email.toLowerCase().includes(term) ||
          user.org.toLowerCase().includes(term)),
    );
  });

  protected actionsFor(user: AppUser): ActionMenuItem[] {
    return [
      { id: 'view', label: 'View profile', tone: 'primary' },
      { id: 'edit', label: 'Edit user' },
      { id: 'reset', label: 'Reset password' },
      { id: 'export', label: 'Export this row' },
      user.status === 'Active'
        ? { id: 'suspend', label: 'Suspend access', tone: 'danger' }
        : { id: 'activate', label: 'Activate access' },
    ];
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected applyCardAction(action: string): void {
    this.query.set('');
    this.statusFilter.set(action === 'all' ? 'All' : action);
  }

  protected inviteUser(): void {
    this.ui.openInvite('user');
  }

  protected exportCsv(): void {
    const rows = this.filtered();
    if (rows.length === 0) {
      this.toasts.show('Nothing to export', 'warning', 'No users match the current filters.');
      return;
    }
    this.csv.download(
      `smartmoney-users-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      rows.map((user) => [user.name, user.email, user.org, user.role, user.status, user.lastLogin]),
    );
    this.toasts.show(
      'Export ready',
      'success',
      `${rows.length} user${rows.length === 1 ? '' : 's'} written to CSV.`,
    );
  }

  protected onRowAction(action: string, user: AppUser): void {
    switch (action) {
      case 'edit':
        this.toasts.show('Edit user', 'info', `${user.name} is ready for editing.`);
        break;
      case 'reset':
        this.toasts.show('Password reset sent', 'success', `A reset link was sent to ${user.email}.`);
        break;
      case 'export':
        this.csv.download(
          `smartmoney-user-${user.id}-${this.csv.stamp()}.csv`,
          EXPORT_HEADERS,
          [[user.name, user.email, user.org, user.role, user.status, user.lastLogin]],
        );
        this.toasts.show('Row exported', 'success', `${user.name} written to CSV.`);
        break;
      case 'suspend':
        this.setStatus(user, 'Suspended', 'Access suspended', 'warning');
        break;
      case 'activate':
        this.setStatus(user, 'Active', 'Access restored', 'success');
        break;
      default:
        this.toasts.show('User selected', 'info', `${user.name} · ${user.role}`);
    }
  }

  private setStatus(
    user: AppUser,
    status: UserStatus,
    title: string,
    tone: 'success' | 'warning',
  ): void {
    this.statusOverrides.update((current) => ({ ...current, [user.id]: status }));
    this.toasts.show(title, tone, `${user.name} is now ${status.toLowerCase()}.`);
  }

  private count(status: UserStatus): number {
    return this.users().filter((user) => user.status === status).length;
  }
}
