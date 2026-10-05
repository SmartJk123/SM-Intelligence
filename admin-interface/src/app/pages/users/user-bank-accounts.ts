import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { AccountLink, AccountLinkService, LINKABLE_BANKS } from '../../core/account-link.service';
import { ToastService } from '../../core/toast.service';

/**
 * The bank accounts linked to one customer, inside the user panel. Linking an
 * account makes its notifications appear on the customer's web dashboard,
 * including ones that arrived before the link was made.
 */
@Component({
  selector: 'app-user-bank-accounts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-bank-accounts.html',
})
export class UserBankAccounts implements OnInit {
  private readonly links = inject(AccountLinkService);
  private readonly toasts = inject(ToastService);

  readonly userId = input.required<string>();

  protected readonly banks = LINKABLE_BANKS;
  protected readonly items = signal<AccountLink[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly adding = signal(false);
  protected readonly busy = signal(false);
  protected readonly formError = signal('');
  protected readonly draftBank = signal('ncba');
  protected readonly draftNumber = signal('');
  protected readonly draftName = signal('');

  ngOnInit(): void {
    void this.load();
  }

  protected bankName(id: string): string {
    return this.banks.find((bank) => bank.id === id)?.name ?? id.toUpperCase();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await this.links.list(this.userId()));
      this.loadError.set('');
    } catch (error) {
      this.loadError.set(message(error, 'Linked accounts could not be loaded.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected startAdd(): void {
    this.draftBank.set('ncba');
    this.draftNumber.set('');
    this.draftName.set('');
    this.formError.set('');
    this.adding.set(true);
  }

  protected async save(): Promise<void> {
    const number = this.draftNumber().replace(/[\s-]/g, '');
    if (!/^[A-Za-z0-9]{4,34}$/.test(number)) {
      this.formError.set('Enter the account number: 4 to 34 letters or digits.');
      return;
    }
    this.busy.set(true);
    this.formError.set('');
    try {
      const link = await this.links.link({
        bankId: this.draftBank(),
        accountNumber: number,
        userId: this.userId(),
        accountName: this.draftName().trim() || null,
      });
      this.items.update((items) => [...items, link]);
      this.adding.set(false);
      this.toasts.show(
        'Account linked',
        link.pendingDeliveries > 0 ? 'warning' : 'success',
        link.pendingDeliveries > 0
          ? `${link.pendingDeliveries} earlier movement(s) could not be delivered yet and will be retried.`
          : `${this.bankName(link.bankId)} movements now reach this customer's dashboard.`,
      );
    } catch (error) {
      this.formError.set(message(error, 'The account could not be linked.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async sync(link: AccountLink): Promise<void> {
    this.busy.set(true);
    try {
      const updated = await this.links.sync(link.id);
      this.items.update((items) => items.map((item) => (item.id === updated.id ? updated : item)));
      this.toasts.show(
        updated.pendingDeliveries === 0 ? 'Up to date' : 'Still waiting',
        updated.pendingDeliveries === 0 ? 'success' : 'warning',
        updated.pendingDeliveries === 0
          ? 'Every movement is on the dashboard.'
          : updated.lastError ?? `${updated.pendingDeliveries} movement(s) still waiting.`,
      );
    } catch (error) {
      this.toasts.show('Retry failed', 'warning', message(error, 'Please try again.'));
    } finally {
      this.busy.set(false);
    }
  }

  protected async unlink(link: AccountLink): Promise<void> {
    if (!confirm(`Unlink ${this.bankName(link.bankId)} ${link.accountNumber}? New movements will stop reaching this customer's dashboard. Earlier ones stay.`)) {
      return;
    }
    this.busy.set(true);
    try {
      await this.links.unlink(link.id);
      this.items.update((items) => items.filter((item) => item.id !== link.id));
      this.toasts.show('Account unlinked', 'success', `${link.accountName} was removed.`);
    } catch (error) {
      this.toasts.show('Not unlinked', 'warning', message(error, 'Please try again.'));
    } finally {
      this.busy.set(false);
    }
  }
}

function message(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (typeof error.error?.message === 'string' && error.error.message) return error.error.message;
    if (error.status === 0) return 'The bank integration service cannot be reached.';
    if (error.status === 401) return 'The bank integration service refused the admin token. Check its JWT_SECRET matches identity-service.';
  }
  return fallback;
}
