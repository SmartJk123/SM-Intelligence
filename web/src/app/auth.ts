// Login and registration component.
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccountApi, AccountKind } from './account-api';

@Component({
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <section class="auth-layout">
      <aside class="auth-story">
        <p class="eyebrow">FINANCIAL CLARITY STARTS HERE</p>
        <h2>Your next step.<br />A clearer view.</h2>
        <p>One place to understand your money, organize your accounts, and plan with confidence.</p>
        <div class="story-line"></div>
        <blockquote>
          Every shilling.<br />Every decision.<br /><span>All connected.</span>
        </blockquote>
        <small>For individuals and organizations</small>
      </aside>
      <div class="auth-panel">
        <a class="text-link back" routerLink="/">← Back to home</a>
        <p class="eyebrow">{{ register ? 'LET US GET YOU STARTED' : 'GOOD TO SEE YOU AGAIN' }}</p>
        <h1>{{ register ? 'Create your account' : 'Welcome back' }}</h1>
        <p class="muted">
          {{
            register
              ? 'Start building your financial picture.'
              : 'Sign in to your SM-Intelligence account.'
          }}
        </p>
        @if (created()) {
          <p role="status">Your account was created, but automatic sign-in could not complete. Please sign in to continue.</p>
          <a class="button" routerLink="/login">Continue to sign in</a>
        } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          @if (register) {
            <fieldset class="kind">
              <legend>Account type</legend>
              <label [class.selected]="form.controls.kind.value === 'individual'"
                ><input type="radio" formControlName="kind" value="individual" />Individual<small
                  >Personal or freelance</small
                ></label
              ><label [class.selected]="form.controls.kind.value === 'organization'"
                ><input
                  type="radio"
                  formControlName="kind"
                  value="organization"
                />Company<small>Company or team</small></label
              >
            </fieldset>
            <label for="name">Full name</label
            ><input
              id="name"
              formControlName="name"
              autocomplete="name"
              placeholder="Your full name"
              [attr.aria-invalid]="invalid('name')"
            />
            @if (invalid('name')) {
              <p class="field-error">Enter your full name.</p>
            }
          }
          <label for="email">Email address</label
          ><input
            id="email"
            name="email"
            type="email"
            formControlName="email"
            autocomplete="username"
            placeholder="you@example.com"
            [attr.aria-invalid]="invalid('email')"
          />
          @if (invalid('email')) {
            <p class="field-error">Enter a valid email address.</p>
          }
          @if (register) {
            <label for="phone">Phone number <span class="muted">(optional)</span></label
            ><input
              id="phone"
              type="tel"
              formControlName="phone"
              autocomplete="tel"
              placeholder="+254 700 000 000"
            />
            @if (invalid('phone')) {
              <p class="field-error">Enter a valid phone number, or leave it blank.</p>
            }
          }
          <label for="password">Password</label>
          <div class="password-field">
            <input
              id="password"
              name="password"
              [type]="showPassword() ? 'text' : 'password'"
              formControlName="password"
              [autocomplete]="register ? 'new-password' : 'current-password'"
              [attr.aria-invalid]="invalid('password')"
              aria-describedby="password-help"
            /><button
              type="button"
              (click)="showPassword.set(!showPassword())"
              [attr.aria-label]="showPassword() ? 'Hide password' : 'Show password'"
            >
              {{ showPassword() ? 'Hide' : 'Show' }}
            </button>
          </div>
          <small id="password-help">{{
            register ? 'Use at least 12 characters.' : 'Enter the password for your account.'
          }}</small>
          @if (!register) {
            <a class="text-link forgot-link" routerLink="/forgot-password">Forgot password?</a>
          }
          @if (invalid('password')) {
            <p class="field-error">
              {{
                register ? 'Use a password with at least 12 characters.' : 'Enter your password.'
              }}
            </p>
          }
          @if (register) {
            <label for="confirm">Confirm password</label
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
          }
          @if (error()) {
            <p role="alert" class="alert">{{ error() }}</p>
          }
          <button class="button full" type="submit" [disabled]="pending()">
            {{ pending() ? 'Please wait…' : register ? 'Create account →' : 'Sign in →' }}
          </button>
        </form>
        }
        <p class="auth-switch">
          {{ register ? 'Already have an account?' : 'New to SM-Intelligence?' }}
          <a [routerLink]="register ? '/login' : '/register'">{{
            register ? 'Sign in' : 'Create an account'
          }}</a>
        </p>
      </div>
    </section>
  `,
})

// Owns form validation, submission state and navigation after authentication.
export class Auth {
  private router = inject(Router);
  readonly api = inject(AccountApi);
  readonly created = signal(false);
  private fb = inject(FormBuilder);
  readonly register = this.router.url.startsWith('/register');
  readonly form = this.fb.nonNullable.group({
    kind: ['individual' as AccountKind],
    name: ['', this.register ? [Validators.required, Validators.pattern(/.*\S.*/)] : []],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.pattern(/^[+\d][\d\s()-]{6,19}$/)]],
    password: [
      '',
      this.register ? [Validators.required, Validators.minLength(12)] : [Validators.required],
    ],
    confirm: ['', this.register ? [Validators.required] : []],
  });
  readonly showPassword = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  invalid(name: keyof typeof this.form.controls) {
    const c = this.form.controls[name];
    return c.touched && c.invalid;
  }
  mismatch() {
    return (
      this.form.controls.confirm.touched &&
      this.form.controls.confirm.value !== this.form.controls.password.value
    );
  }
  async submit() {
    if (this.pending()) return;
    this.error.set('');
    this.form.markAllAsTouched();
    if (this.form.invalid || (this.register && this.mismatch())) {
      this.error.set('Please check the highlighted fields.');
      return;
    }
    this.pending.set(true);
    try {
      const value = this.form.getRawValue();
      if (this.register && new TextEncoder().encode(value.password).length > 72) {
        this.error.set('Password must be at most 72 UTF-8 bytes.'); return;
      }
      if (this.register) {
        const result = await this.api.register({
          name: value.name.trim(),
          email: value.email.trim(),
          phone: value.phone,
          password: value.password,
          kind: value.kind,
        });
        if (result === 'sign-in') {
          try {
            await this.api.login({ email: value.email.trim(), password: value.password });
          } catch {
            // Registration already succeeded: never submit it again on a login failure.
            this.form.controls.password.reset();
            this.form.controls.confirm.reset();
            this.created.set(true);
            return;
          }
        }
      }
      else await this.api.login({ email: value.email.trim(), password: value.password });
      this.form.controls.password.reset();
      this.form.controls.confirm.reset();
      await this.api.refreshAccounts();
      await this.router.navigate([this.api.setupCompleted() ? '/dashboard' : '/setup']);
    } catch (e) {
      this.error.set(
        'Unable to ' +
          (this.register ? 'create your account' : 'sign in') +
          '. Check your details and try again. If the problem continues, the account service may be unavailable.',
      );
    } finally {
      this.pending.set(false);
    }
  }
}
