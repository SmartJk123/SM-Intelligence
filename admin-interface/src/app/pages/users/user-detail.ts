import { ChangeDetectionStrategy, Component, OnInit, input, output, signal } from '@angular/core';
import { UpdateUserRequest, UserSummary } from '../../core/user.service';
import { Avatar } from '../../shared/avatar';
import { Badge } from '../../shared/badge';
import { UserBankAccounts } from './user-bank-accounts';

/**
 * Side panel for one registered user: the identity record, and a form for the
 * fields an administrator may correct. Saving and suspending are done by the
 * Users page through UserService, which calls identity-service.
 */
@Component({
  selector: 'app-user-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Avatar, Badge, UserBankAccounts],
  templateUrl: './user-detail.html',
})
export class UserDetail implements OnInit {
  readonly user = input.required<UserSummary>();
  /** True when the panel shows the signed in admin, who cannot suspend themselves. */
  readonly isSelf = input(false);
  readonly startInEdit = input(false);
  readonly busy = input(false);

  readonly closed = output<void>();
  readonly saved = output<UpdateUserRequest>();
  readonly statusChange = output<'ACTIVE' | 'SUSPENDED'>();

  protected readonly editing = signal(false);
  protected readonly draftName = signal('');
  protected readonly draftPhone = signal('');
  protected readonly draftOrg = signal('');
  protected readonly draftBusinessType = signal('');
  protected readonly draftIndustry = signal('');
  protected readonly formError = signal('');

  ngOnInit(): void {
    if (this.startInEdit()) this.startEdit();
  }

  protected startEdit(): void {
    const user = this.user();
    this.draftName.set(user.name);
    this.draftPhone.set(user.phoneNumber ?? '');
    this.draftOrg.set(user.organizationName ?? '');
    this.draftBusinessType.set(user.businessType ?? '');
    this.draftIndustry.set(user.industry ?? '');
    this.formError.set('');
    this.editing.set(true);
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.formError.set('');
  }

  protected saveEdit(): void {
    const name = this.draftName().trim();
    const phone = this.draftPhone().trim();
    if (name.length < 2 || name.length > 100) {
      this.formError.set('Name must be between 2 and 100 characters.');
      return;
    }
    if (phone && (phone.length < 7 || phone.length > 20)) {
      this.formError.set('Phone number must be between 7 and 20 characters, or empty.');
      return;
    }
    this.formError.set('');
    this.saved.emit({
      name,
      phoneNumber: phone || null,
      organizationName: this.draftOrg().trim() || null,
      businessType: this.draftBusinessType().trim() || null,
      industry: this.draftIndustry().trim() || null,
    });
  }

  /** Called by the page once identity-service has accepted the change. */
  finishEdit(): void {
    this.editing.set(false);
  }

  protected statusLabel(): 'Active' | 'Suspended' {
    return this.user().status === 'SUSPENDED' ? 'Suspended' : 'Active';
  }

  protected date(value: string | null): string {
    if (!value) return 'Never';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime())
      ? 'Never'
      : parsed.toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'short' });
  }
}
