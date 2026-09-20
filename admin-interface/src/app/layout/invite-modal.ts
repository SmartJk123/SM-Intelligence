import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { InviteType } from '../core/ui.service';
import { ORGS } from '../core/data';

@Component({
  selector: 'app-invite-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invite-modal.html',
})
export class InviteModal {
  readonly type = input.required<InviteType>();
  readonly closed = output<void>();

  protected readonly sent = signal(false);
  protected readonly email = signal('');
  protected readonly name = signal('');
  protected readonly role = signal('Admin');
  protected readonly orgType = signal('SME');

  protected readonly orgs = ORGS;
  protected readonly businessTypes = ['SME', 'Business', 'Rental Management', 'Property Management'];
  protected readonly roles = ['Owner', 'Admin', 'Manager', 'Finance', 'Support'];

  protected readonly isOrg = computed(() => this.type() === 'org');
  protected readonly canSend = computed(() => !!this.email() && !!this.name());

  protected readonly inviteLink = `https://app.smartmoney.io/invite/${Math.random()
    .toString(36)
    .slice(2, 10)}`;

  protected readonly title = computed(() => (this.isOrg() ? 'Add Organisation' : 'Add User'));

  protected send(): void {
    if (!this.canSend()) {
      return;
    }
    this.sent.set(true);
  }
}
