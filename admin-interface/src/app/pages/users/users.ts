import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AppUser } from '../../core/data';
import { AuthService } from '../../core/auth.service';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { UiService } from '../../core/ui.service';
import { UpdateUserRequest, UserService, UserSummary } from '../../core/user.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Avatar } from '../../shared/avatar';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';
import { UserDetail } from './user-detail';

const EXPORT_HEADERS = ['Name', 'Email', 'Organisation', 'Role', 'Status', 'Last login'];
const NEVER_SIGNED_IN = 'Never signed in';

/**
 * Every account registered on the customer web app, managed through
 * identity-service. Suspending, restoring and editing here change the same
 * record the web app signs in against.
 *
 * "Send password reset email" triggers the same one-time link as the web app's
 * "Forgot password?". identity-service answers the same whether or not mail is
 * set up, so the toast says the link goes out only when SMTP is configured.
 */
@Component({
  selector: 'app-users',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, Avatar, ActionMenu, UserDetail],
  templateUrl: './users.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Users {
  private readonly ui = inject(UiService);
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly auth = inject(AuthService);
  protected readonly userService = inject(UserService);

  private readonly detail = viewChild(UserDetail);

  protected readonly statusFilters = ['All', 'Active', 'Suspended', NEVER_SIGNED_IN];
  protected readonly query = signal('');
  protected readonly statusFilter = signal('All');
  protected readonly selectedId = signal<string | null>(null);
  protected readonly openInEdit = signal(false);
  protected readonly busy = signal(false);
  protected readonly headings = [
    'User',
    'Email',
    'Organisation',
    'Role',
    'Status',
    'Last login',
    'Action',
  ];

  protected readonly users = computed<AppUser[]>(() => this.userService.users());
  protected readonly selected = computed<UserSummary | undefined>(() => {
    const id = this.selectedId();
    // Read users() so the panel refreshes after a change.
    this.users();
    return id ? this.userService.summary(id) : undefined;
  });

  protected readonly total = computed(() => this.users().length);
  protected readonly active = computed(() => this.users().filter((user) => user.status === 'Active').length);
  protected readonly neverSignedIn = computed(() => this.users().filter((user) => user.lastLogin === 'Never').length);
  protected readonly suspended = computed(() => this.users().filter((user) => user.status === 'Suspended').length);

  protected readonly filtered = computed<AppUser[]>(() => {
    const term = this.query().trim().toLowerCase();
    const status = this.statusFilter();
    return this.users().filter(
      (user) =>
        (status === 'All' ||
          (status === NEVER_SIGNED_IN ? user.lastLogin === 'Never' : user.status === status)) &&
        (term.length === 0 ||
          user.name.toLowerCase().includes(term) ||
          user.email.toLowerCase().includes(term) ||
          user.org.toLowerCase().includes(term)),
    );
  });

  constructor() {
    void this.userService.loadUsers();
  }

  protected actionsFor(user: AppUser): ActionMenuItem[] {
    const items: ActionMenuItem[] = [
      { id: 'view', label: 'View profile', tone: 'primary' },
      { id: 'edit', label: 'Edit user' },
      { id: 'reset', label: 'Send password reset email' },
      { id: 'export', label: 'Export this row' },
    ];
    if (user.status === 'Suspended') {
      items.push({ id: 'activate', label: 'Restore access' });
    } else if (user.id !== this.auth.userId()) {
      items.push({ id: 'suspend', label: 'Suspend access', tone: 'danger' });
    }
    return items;
  }

  protected onSearch(value: string): void {
    this.query.set(value);
  }

  protected applyCardAction(action: string): void {
    this.query.set('');
    this.statusFilter.set(action === 'all' ? 'All' : action);
  }

  /** Emails the user a one-time link to choose a new password (valid 60 minutes). */
  private async sendPasswordReset(user: AppUser): Promise<void> {
    try {
      await this.userService.sendPasswordReset(user.email);
      this.toasts.show('Password reset requested', 'success',
        `${user.email} will get a reset link if identity-service has SMTP set up.`);
    } catch {
      this.toasts.show('Could not request a reset', 'danger', 'Check that identity-service and the gateway are running.');
    }
  }

  protected inviteUser(): void {
    this.ui.openInvite('user');
  }

  protected refresh(): void {
    void this.userService.loadUsers();
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
      case 'view':
        this.open(user.id, false);
        break;
      case 'edit':
        this.open(user.id, true);
        break;
      case 'reset':
        void this.sendPasswordReset(user);
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
        void this.changeStatus(user.id, 'SUSPENDED');
        break;
      case 'activate':
        void this.changeStatus(user.id, 'ACTIVE');
        break;
    }
  }

  protected isSelf(id: string): boolean {
    return id === this.auth.userId();
  }

  protected close(): void {
    this.selectedId.set(null);
  }

  protected async save(request: UpdateUserRequest): Promise<void> {
    const id = this.selectedId();
    if (!id) return;
    this.busy.set(true);
    try {
      const updated = await this.userService.updateUser(id, request);
      this.detail()?.finishEdit();
      this.toasts.show('User updated', 'success', `${updated.name} was saved.`);
    } catch (error) {
      this.toasts.show('Not saved', 'warning', failureMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected async changeStatus(id: string, status: 'ACTIVE' | 'SUSPENDED'): Promise<void> {
    const name = this.userService.summary(id)?.name ?? 'The user';
    if (status === 'SUSPENDED' && !confirm(`Suspend ${name}? They will be signed out of the web app and unable to sign in.`)) {
      return;
    }
    this.busy.set(true);
    try {
      await this.userService.changeStatus(id, status);
      if (status === 'SUSPENDED') {
        this.toasts.show('Access suspended', 'warning', `${name} can no longer sign in.`);
      } else {
        this.toasts.show('Access restored', 'success', `${name} can sign in again.`);
      }
    } catch (error) {
      this.toasts.show('Not changed', 'warning', failureMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  private open(id: string, edit: boolean): void {
    this.openInEdit.set(edit);
    this.selectedId.set(id);
  }
}

function failureMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const message = error.error?.message ?? Object.values(error.error?.details ?? {})[0];
    if (typeof message === 'string' && message) return message;
    if (error.status === 0) return 'identity-service cannot be reached.';
    if (error.status === 403) return 'Your account is not allowed to do this.';
  }
  return 'The change could not be saved. Please try again.';
}
