// First-time account setup component.
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { BankLogo } from './bank-logo';
import { HttpErrorResponse } from '@angular/common/http';
import { AccountApi, AccountDetails } from './account-api';

@Component({
  imports: [ReactiveFormsModule, RouterLink, BankLogo],
  template: `
    <section class="account-setup">
      <header class="setup-heading">
        <p class="eyebrow">YOUR FINANCIAL PICTURE STARTS HERE</p>
        <h1>Account setup</h1>
      </header>
      <ol class="account-progress" aria-label="Setup progress">
        <li [class.current]="!saved()" [attr.aria-current]="!saved() ? 'step' : null">
          <span>{{ saved() ? '✓' : '1' }}</span
          >Account details
        </li>
        <li [class.current]="saved()" [attr.aria-current]="saved() ? 'step' : null">
          <span>2</span>Complete
        </li>
      </ol>
      <div class="account-setup-card">
        @if (saved()) {
          <div class="success">
            <span class="success-icon">✓</span>
            <h2>Your account is saved</h2>
            <p>Your manual balance snapshot is ready. Live bank syncing is not connected.</p>
            <div class="account-summary">
              <app-bank-logo [bank]="summary().bank" />
              <div>
                <strong>{{ summary().name }}</strong
                ><small
                  >{{ summary().bank }} · •••• {{ summary().lastFour }} · Bank account</small
                >
              </div>
            </div>
            <button class="button full" (click)="addAnother()">Add another account</button
            ><a class="button full" routerLink="/dashboard">Continue to dashboard →</a>
          </div>
        } @else {
          <h2>Add your bank account</h2>
          <p class="muted">Save at least one account to unlock your dashboard. Enter a manual balance snapshot; this does not connect to your bank.</p>
          @if (api.accountsError()) {
            <p role="alert" class="alert">{{ api.accountsError() }}</p>
            <button type="button" class="button secondary" (click)="retryAccounts()">Retry loading accounts</button>
          }
          <form [formGroup]="form" (ngSubmit)="save()" novalidate>
            <fieldset class="bank-options">
              <legend>Select bank</legend>
              @for (bank of banks; track bank) {
                <label class="bank-option" [class.selected]="form.controls.bank.value === bank"
                  ><input
                    type="radio"
                    formControlName="bank"
                    [value]="bank"
                    (change)="chooseBank(bank)"
                  /><app-bank-logo [bank]="bank" /><span>{{ bank }} Bank</span></label
                >
              }
            </fieldset>
            <label for="accountName">Account name</label
            ><input
              id="accountName"
              formControlName="accountName"
              autocomplete="off"
              placeholder="e.g. KCB Personal Account"
              [attr.aria-invalid]="invalid('accountName')"
            />
            @if (invalid('accountName')) {
              <p class="field-error">Enter an account name.</p>
            }
            <label for="accountNumber">Account number</label
            ><input
              id="accountNumber"
              formControlName="accountNumber"
              inputmode="numeric"
              autocomplete="off"
              placeholder="Enter bank account number"
              aria-describedby="account-number-help"
              [attr.aria-invalid]="invalid('accountNumber')"
            /><small id="account-number-help"
              >Only the last four digits will be shown after setup.</small
            >
            @if (invalid('accountNumber')) {
              <p class="field-error">Enter an account number with 4–34 digits.</p>
            }
            <label for="balance">Opening available balance (KES)</label
            ><input
              id="balance"
              formControlName="balance"
              type="number"
              min="0"
              step="0.01"
              placeholder="0.00"
              [attr.aria-invalid]="invalid('balance')"
            />
            @if (invalid('balance')) {
              <p class="field-error">
                Enter zero or a positive amount with up to two decimal places.
              </p>
            }
            <label for="balanceDate">Balance effective date</label
            ><input
              id="balanceDate"
              type="date"
              formControlName="balanceDate"
              [max]="today"
              [attr.aria-invalid]="invalid('balanceDate')"
            />
            <div class="account-summary">
              <app-bank-logo [bank]="form.controls.bank.value" />
              <div>
                <strong>{{ form.controls.accountName.value || 'Your bank account' }}</strong
                ><small
                  >{{ form.controls.bank.value }} Bank · {{ maskedNumber }} · Bank account</small
                >
              </div>
            </div>
            @if (error()) {
              <p role="alert" class="alert">{{ error() }}</p>
            }
            <button type="submit" class="button full" [disabled]="pending()">
              {{ pending() ? 'Saving account…' : 'Save account' }}
            </button>
          </form>
        }
      </div>
    </section>
  `,
})

// Validates bank/account details before sending them to AccountApi.
export class Setup {
  private fb = inject(FormBuilder);
  readonly api = inject(AccountApi);
  private router = inject(Router);
  readonly today = new Date().toLocaleDateString('en-CA');
  readonly banks = ['KCB', 'Equity', 'NCBA', 'Stanbic'];
  readonly saved = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly summary = signal({ bank: '', name: '', lastFour: '', type: 'debit' });
  readonly form = this.fb.nonNullable.group({
    bank: ['KCB', Validators.required],
    accountName: [this.defaultName('KCB'), [Validators.required, Validators.maxLength(150), Validators.pattern(/.*\S.*/)]],
    accountNumber: ['', [Validators.required, Validators.pattern(/^\d{4,34}$/)]],
    // An account number entered here is a bank (deposit) account, so it is always
    // debit: its balance is cash. The number alone cannot tell a credit card apart.
    cardType: ['debit' as 'debit' | 'credit'],
    balance: [
      '',
      [Validators.required, Validators.min(0), Validators.pattern(/^\d+(\.\d{1,2})?$/)],
    ],
    balanceDate: [this.today, Validators.required],
  });
  defaultName(bank: string) {
    return bank + (this.api.kind() === 'organization' ? ' Business Account' : ' Personal Account');
  }
  async retryAccounts() {
    await this.api.refreshAccounts();
    if (this.api.setupCompleted()) await this.router.navigate(['/dashboard']);
  }
  chooseBank(bank: string) {
    if (!this.form.controls.accountName.dirty)
      this.form.controls.accountName.setValue(this.defaultName(bank));
  }
  get maskedNumber() {
    const number = this.form.controls.accountNumber.value;
    return number ? '•••• ' + number.slice(-4) : '••••';
  }
  invalid(key: keyof typeof this.form.controls) {
    const c = this.form.controls[key];
    return c.touched && c.invalid;
  }
  async save() {
    if (this.pending()) return;
    this.error.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.error.set('Please check the account details above.');
      return;
    }
    const value = this.form.getRawValue();
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value.balanceDate) || value.balanceDate > this.today) {
      this.error.set('Choose a valid balance date that is not in the future.');
      return;
    }
    this.pending.set(true);
    try {
      const payload: AccountDetails = {
        ...value,
        accountName: value.accountName.trim(),
        balance: Number(value.balance),
        currency: 'KES',
      };
      await this.api.saveSetup(payload);
      this.summary.set({
        bank: value.bank,
        name: payload.accountName,
        lastFour: value.accountNumber.slice(-4),
        type: value.cardType,
      });
      this.form.controls.accountNumber.reset('');
      this.saved.set(true);
    } catch (error) {
      const takenByAnotherUser = error instanceof HttpErrorResponse && error.status === 409
        && String(error.error?.message ?? '').includes('another user');
      this.error.set(takenByAnotherUser
        ? 'This account number is already registered to another user. Check the number, or contact support if it is yours.'
        : error instanceof HttpErrorResponse && error.status === 409
          ? 'This account is already saved. Reload your accounts to continue.'
          : 'We could not save your account. Please try again. Your details remain here.');
      if (error instanceof HttpErrorResponse && error.status === 409 && !takenByAnotherUser) {
        this.api.accountsError.set('This account is already saved. Retry loading accounts to continue.');
      }
    } finally {
      this.pending.set(false);
    }
  }
  addAnother() {
    this.form.reset({
      bank: 'KCB',
      accountName: this.defaultName('KCB'),
      accountNumber: '',
      cardType: 'debit',
      balance: '',
      balanceDate: this.today,
    });
    this.error.set('');
    this.saved.set(false);
  }
}
