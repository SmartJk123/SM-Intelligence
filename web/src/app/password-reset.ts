// Forgot password (/forgot-password) and choose a new password (/reset-password?token=...).
import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AccountApi } from './account-api';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-layout">
      <aside class="auth-story">
        <p class="eyebrow">FINANCIAL CLARITY STARTS HERE</p>
        <h2>Locked out?<br />Let us get you back in.</h2>
        <p>We will email you a one-time link to choose a new password.</p>
        <div class="story-line"></div>
        <small>For individuals and organizations</small>
      </aside>
      <div class="auth-panel">
        <a class="text-link back" routerLink="/login">← Back to sign in</a>
        @if (!token) {
          <p class="eyebrow">FORGOT PASSWORD</p>
          <h1>Reset your password</h1>
          @if (sent()) {
            <p role="status">
              If an account exists for {{ requestForm.controls.email.value.trim() }}, a reset link is on its way.
              It works once and expires in 60 minutes. Check your spam folder if it does not arrive.
            </p>
            <a class="button full" routerLink="/login">Back to sign in</a>
          } @else {
            <p class="muted">Enter the email address you sign in with.</p>
            <form [formGroup]="requestForm" (ngSubmit)="request()" novalidate>
              <label for="email">Email address</label
              ><input
                id="email"
                type="email"
                formControlName="email"
                autocomplete="email"
                placeholder="you@example.com"
                [attr.aria-invalid]="requestForm.controls.email.touched && requestForm.controls.email.invalid"
              />
              @if (requestForm.controls.email.touched && requestForm.controls.email.invalid) {
                <p class="field-error">Enter a valid email address.</p>
              }
              @if (error()) {
                <p role="alert" class="alert">{{ error() }}</p>
              }
              <button class="button full" type="submit" [disabled]="pending()">
                {{ pending() ? 'Please wait…' : 'Send reset link →' }}
              </button>
            </form>
          }
        } @else {
          <p class="eyebrow">CHOOSE A NEW PASSWORD</p>
          <h1>Set a new password</h1>
          @if (done()) {
            <p role="status">Your password has been changed. Sign in with your new password.</p>
            <a class="button full" routerLink="/login">Continue to sign in</a>
          } @else {
            <form [formGroup]="resetForm" (ngSubmit)="reset()" novalidate>
              <label for="password">New password</label>
              <div class="password-field">
                <input
                  id="password"
                  [type]="showPassword() ? 'text' : 'password'"
                  formControlName="password"
                  autocomplete="new-password"
                  aria-describedby="password-help"
                  [attr.aria-invalid]="resetForm.controls.password.touched && resetForm.controls.password.invalid"
                /><button
                  type="button"
                  (click)="showPassword.set(!showPassword())"
                  [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
                >
                  {{ showPassword() ? 'Hide' : 'Show' }}
                </button>
              </div>
              <small id="password-help">Use at least 12 characters.</small>
              @if (resetForm.controls.password.touched && resetForm.controls.password.invalid) {
                <p class="field-error">Use a password with at least 12 characters.</p>
              }
              <label for="confirm">Confirm new password</label
              ><input
                id="confirm"
                type="password"
                formControlName="confirm"
                autocomplete="new-password"
                [attr.aria-invalid]="mismatch()"
              />
              @if (mismatch()) {
                <p class="field-error">Your passwords do not match.</p>
              }
              @if (error()) {
                <p role="alert" class="alert">{{ error() }}</p>
              }
              @if (expired()) {
                <a class="text-link" routerLink="/forgot-password">Request a new reset link</a>
              }
              <button class="button full" type="submit" [disabled]="pending()">
                {{ pending() ? 'Please wait…' : 'Save new password →' }}
              </button>
            </form>
          }
        }
      </div>
    </section>
  `,
})
export class PasswordReset {
  private readonly api = inject(AccountApi);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  /** Present on /reset-password links; absent on /forgot-password. */
  readonly token = this.router.url.startsWith('/reset-password')
    ? inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? ''
    : '';
  readonly requestForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  readonly resetForm = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(12)]],
    confirm: ['', [Validators.required]],
  });
  readonly pending = signal(false);
  readonly error = signal('');
  readonly sent = signal(false);
  readonly done = signal(false);
  readonly expired = signal(false);
  readonly showPassword = signal(false);

  constructor() {
    if (this.router.url.startsWith('/reset-password') && !this.token) {
      this.expired.set(true);
      this.error.set('This reset link is incomplete. Request a new one.');
    }
  }

  mismatch() {
    const c = this.resetForm.controls;
    return c.confirm.touched && c.confirm.value !== c.password.value;
  }

  async request() {
    if (this.pending()) return;
    this.error.set('');
    this.requestForm.markAllAsTouched();
    if (this.requestForm.invalid) return;
    this.pending.set(true);
    try {
      await this.api.forgotPassword(this.requestForm.controls.email.value.trim());
      this.sent.set(true);
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && e.status === 400
          ? 'Enter a valid email address.'
          : 'We could not send the reset link right now. Please try again shortly.',
      );
    } finally {
      this.pending.set(false);
    }
  }

  async reset() {
    if (this.pending()) return;
    this.error.set('');
    this.resetForm.markAllAsTouched();
    if (this.resetForm.invalid || this.mismatch()) {
      this.error.set('Please check the highlighted fields.');
      return;
    }
    const password = this.resetForm.controls.password.value;
    if (new TextEncoder().encode(password).length > 72) {
      this.error.set('Password must be at most 72 UTF-8 bytes.');
      return;
    }
    this.pending.set(true);
    try {
      await this.api.resetPassword(this.token, password);
      this.resetForm.reset();
      this.done.set(true);
    } catch (e) {
      const invalidToken = e instanceof HttpErrorResponse && e.error?.error === 'invalid-token';
      this.expired.set(invalidToken);
      this.error.set(
        invalidToken
          ? 'This reset link is invalid, already used, or expired.'
          : e instanceof HttpErrorResponse && e.status === 400
            ? 'That password was not accepted. Use 12 to 72 characters.'
            : 'We could not change your password right now. Please try again shortly.',
      );
    } finally {
      this.pending.set(false);
    }
  }
}
