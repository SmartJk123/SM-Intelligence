import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import {
  BANK_ACCOUNTS,
  ORGS,
  OrgStatus,
  Organisation,
  TRANSACTIONS,
  USERS,
  money,
} from '../../core/data';
import { CsvExportService } from '../../core/csv-export.service';
import { ToastService } from '../../core/toast.service';
import { Avatar } from '../../shared/avatar';
import { Badge } from '../../shared/badge';

/**
 * Slide over detail view for one organisation: overview, editable business
 * information, its users, its bank accounts and its recent transactions.
 */
@Component({
  selector: 'app-organisation-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge, Avatar],
  templateUrl: './organisation-detail.html',
  host: { '(document:keydown.escape)': 'closed.emit()' },
})
export class OrganisationDetail {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);

  readonly org = input.required<Organisation>();
  readonly closed = output<void>();
  readonly saved = output<{ name: string; type: string }>();
  readonly statusChange = output<OrgStatus>();

  protected readonly money = money;
  protected readonly businessTypes = [
    'SME',
    'Business',
    'Rental Management',
    'Property Management',
  ];

  protected readonly editing = signal(false);
  protected readonly draftName = signal('');
  protected readonly draftType = signal('');

  protected readonly users = computed(() =>
    USERS.filter((user) => user.org === this.org().name),
  );
  protected readonly accounts = computed(() =>
    BANK_ACCOUNTS.filter((account) => account.org === this.org().name),
  );
  protected readonly transactions = computed(() =>
    TRANSACTIONS.filter((tx) => tx.org === this.org().name).slice(0, 4),
  );

  protected readonly inflow = computed(() =>
    TRANSACTIONS.filter((tx) => tx.org === this.org().name && tx.type === 'Income').reduce(
      (sum, tx) => sum + tx.amount,
      0,
    ),
  );

  protected readonly outflow = computed(() =>
    TRANSACTIONS.filter((tx) => tx.org === this.org().name && tx.type === 'Expense').reduce(
      (sum, tx) => sum + tx.amount,
      0,
    ),
  );

  protected readonly directoryPosition = computed(() => {
    const index = ORGS.findIndex((item) => item.id === this.org().id);
    return index < 0 ? 'Unknown' : `${index + 1} of ${ORGS.length}`;
  });

  protected startEdit(): void {
    this.draftName.set(this.org().name);
    this.draftType.set(this.org().type);
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
  }

  protected saveEdit(): void {
    const name = this.draftName().trim();
    const type = this.draftType();
    if (name.length < 2) {
      this.toasts.show('Name is too short', 'warning', 'Enter at least two characters.');
      return;
    }
    this.saved.emit({ name, type });
    this.editing.set(false);
  }

  protected toggleStatus(): void {
    this.statusChange.emit(this.org().status === 'Active' ? 'Suspended' : 'Active');
  }

  protected exportOrganisation(): void {
    const org = this.org();
    this.csv.download(
      `smartmoney-organisation-${org.id}-${this.csv.stamp()}.csv`,
      ['Name', 'Business type', 'Status', 'Users', 'Accounts', 'Joined'],
      [[org.name, org.type, org.status, this.users().length, this.accounts().length, org.joined]],
    );
    this.toasts.show('Export ready', 'success', `${org.name} written to CSV.`);
  }

  protected amountColor(type: string): string {
    return type === 'Income' ? 'var(--green-ink)' : 'var(--red-ink)';
  }
}
