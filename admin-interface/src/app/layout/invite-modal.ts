import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { InvitePreset, InviteType } from '../core/ui.service';
import { OrganizationMemberRole } from '../core/organization.gateway';
import { OrganizationService } from '../core/organization.service';
import { AccountLinkService } from '../core/account-link.service';
import { BANKS } from '../core/data';
import { BankLogo } from '../shared/bank-logo';

/** form: filling it in. saved: an organisation was saved, no invite yet. sent: an invite went out. */
type Stage = 'form' | 'saved' | 'sent';

@Component({
  selector: 'app-invite-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BankLogo],
  templateUrl: './invite-modal.html',
})
export class InviteModal implements OnInit {
  readonly type = input.required<InviteType>();
  /** When set, the form is an owner invite for one organisation that already exists. */
  readonly preset = input<InvitePreset | null>(null);
  readonly closed = output<void>();

  private readonly organizations = inject(OrganizationService);
  private readonly accountLinks = inject(AccountLinkService);

  /** Starts as the type the caller asked for. Moves to 'user' after an organisation is saved. */
  protected readonly mode = signal<InviteType>('org');
  protected readonly stage = signal<Stage>('form');
  protected readonly working = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly emailSent = signal<boolean | null>(null);
  protected readonly recipientEmail = signal('');

  /** The organisation that was just saved, so the invite can be sent without picking it again. */
  protected readonly savedOrg = signal<InvitePreset | null>(null);

  /** Organisation name (org mode) or the invited person's full name (user mode). */
  protected readonly name = signal('');
  protected readonly email = signal('');
  protected readonly role = signal<OrganizationMemberRole>('ADMIN');
  protected readonly orgType = signal('SME');
  protected readonly selectedOrgId = signal('');

  /** Bank account for the invitee, optional: an admin may not have these details yet. */
  protected readonly banks = BANKS;
  protected readonly bankId = signal('');
  protected readonly accountNumber = signal('');
  protected readonly bankLinkWarning = signal<string | null>(null);
  protected readonly bankLinked = signal<{ bankName: string; accountNumber: string } | null>(null);

  protected readonly orgs = this.organizations.organisations;
  protected readonly businessTypes = ['SME', 'Business', 'Rental Management', 'Property Management'];
  protected readonly roles: { value: OrganizationMemberRole; label: string }[] = [
    { value: 'OWNER', label: 'Owner' },
    { value: 'ADMIN', label: 'Admin' },
    { value: 'MEMBER', label: 'Member' },
    { value: 'VIEWER', label: 'Viewer' },
  ];

  protected readonly isOrg = computed(() => this.mode() === 'org');

  /** The organisation an invite is for, when it is already decided. */
  protected readonly fixedOrg = computed(() => this.savedOrg() ?? this.preset());

  protected readonly canSubmit = computed(() => {
    if (this.isOrg()) {
      return this.name().trim().length >= 2;
    }
    return !!this.email() && !!this.name() && !!this.selectedOrgId();
  });

  protected readonly title = computed(() => {
    if (this.isOrg()) {
      return 'Add Organisation';
    }
    return this.fixedOrg() ? 'Send Invite' : 'Add User';
  });

  protected readonly subtitle = computed(() => {
    if (this.isOrg()) {
      return 'Save the organisation first. You can send the owner their invite afterwards.';
    }
    const org = this.fixedOrg();
    return org
      ? `An account will be created for the invitee in ${org.organizationName}, and they will be emailed a link to choose their password.`
      : 'An account will be created, and they will be emailed a link to choose their password.';
  });

  ngOnInit(): void {
    const preset = this.preset();
    if (this.type() === 'user' && preset) {
      this.mode.set('user');
      this.selectedOrgId.set(preset.organizationId);
      this.role.set('OWNER');
    } else {
      this.mode.set(this.type());
    }
  }

  protected async submit(): Promise<void> {
    if (!this.canSubmit() || this.working()) {
      return;
    }
    this.working.set(true);
    this.errorMessage.set(null);
    try {
      if (this.isOrg()) {
        await this.save();
      } else {
        await this.invite();
      }
    } catch (error) {
      this.errorMessage.set(describeError(error, this.isOrg() ? 'saving the organisation' : 'sending the invite'));
    } finally {
      this.working.set(false);
    }
  }

  /** Saves the organisation only. No account is created and nothing is emailed. */
  private async save(): Promise<void> {
    const created = await this.organizations.createOrganization({
      organizationName: this.name().trim(),
      businessType: this.orgType(),
    });
    this.savedOrg.set({ organizationId: created.id, organizationName: created.name });
    this.stage.set('saved');
  }

  private async invite(): Promise<void> {
    const member = await this.organizations.inviteMember(this.selectedOrgId(), {
      name: this.name().trim(),
      email: this.email().trim(),
      role: this.role(),
    });
    this.emailSent.set(member.emailSent);
    this.recipientEmail.set(this.email().trim());
    this.bankLinkWarning.set(null);
    this.bankLinked.set(null);
    const accountNumber = this.accountNumber().trim();
    if (this.bankId() && accountNumber) {
      try {
        await this.accountLinks.link({
          bankId: this.bankId(),
          accountNumber,
          userId: member.userId,
          accountName: null,
        });
        const bankName = this.banks.find((bank) => bank.id === this.bankId())?.name ?? this.bankId();
        this.bankLinked.set({ bankName, accountNumber });
      } catch {
        // The invite already succeeded; a failed link is fixable later from the user's
        // detail page, and must not be reported as the invite itself having failed.
        this.bankLinkWarning.set('The invite was sent, but the bank account could not be linked. Add it from the user’s profile.');
      }
    }
    this.stage.set('sent');
  }

  /** From the "saved" screen: carry straight on to inviting the owner of the new organisation. */
  protected startOwnerInvite(): void {
    const org = this.savedOrg();
    if (!org) {
      return;
    }
    this.mode.set('user');
    this.selectedOrgId.set(org.organizationId);
    this.role.set('OWNER');
    this.name.set('');
    this.email.set('');
    this.bankId.set('');
    this.accountNumber.set('');
    this.bankLinked.set(null);
    this.bankLinkWarning.set(null);
    this.errorMessage.set(null);
    this.stage.set('form');
  }
}

function describeError(error: unknown, doing: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Could not reach the backend. Check that identity-service and api-gateway are running.';
    }
    if (error.status === 409) {
      return error.error?.message ?? 'That email address is already in use here.';
    }
    if (error.status === 400) {
      return error.error?.message ?? 'Check the details and try again.';
    }
    return `The backend returned HTTP ${error.status}. Check the service log for the cause.`;
  }
  return `Something went wrong ${doing}.`;
}
