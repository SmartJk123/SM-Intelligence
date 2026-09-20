import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChildren,
} from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AuthStep } from '../../core/data';

const ALLOWED_DOMAIN = '@smartmoney.io';
const OTP_LENGTH = 6;
const MIN_PASSWORD_LENGTH = 6;

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  host: { class: 'block' },
})
export class LoginPage {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly allowedDomain = ALLOWED_DOMAIN;
  protected readonly otpSlots = [0, 1, 2, 3, 4, 5];

  protected readonly step = signal<AuthStep>('login');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly otp = signal<string[]>(Array(OTP_LENGTH).fill(''));
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly showPassword = signal(false);

  private readonly otpInputs = viewChildren<ElementRef<HTMLInputElement>>('otpInput');

  protected submitCredentials(event: Event): void {
    event.preventDefault();
    this.error.set('');

    if (!this.email().endsWith(ALLOWED_DOMAIN)) {
      this.error.set(`Only ${ALLOWED_DOMAIN} domain accounts are permitted.`);
      return;
    }
    if (this.password().length < MIN_PASSWORD_LENGTH) {
      this.error.set('Invalid credentials.');
      return;
    }

    this.loading.set(true);
    setTimeout(() => {
      this.loading.set(false);
      this.step.set('2fa');
    }, 1200);
  }

  protected signInWithGoogle(): void {
    this.error.set('');
    this.loading.set(true);
    setTimeout(() => {
      this.loading.set(false);
      this.step.set('2fa');
    }, 1000);
  }

  protected onOtpInput(index: number, value: string): void {
    if (!/^[0-9]?$/.test(value)) {
      return;
    }
    const next = [...this.otp()];
    next[index] = value;
    this.otp.set(next);

    if (value && index < OTP_LENGTH - 1) {
      this.otpInputs()[index + 1]?.nativeElement.focus();
    }
    if (next.every((digit) => digit !== '') && next.join('').length === OTP_LENGTH) {
      this.loading.set(true);
      setTimeout(() => {
        this.loading.set(false);
        this.finishLogin();
      }, 800);
    }
  }

  protected onOtpKeydown(index: number, event: KeyboardEvent): void {
    if (event.key === 'Backspace' && !this.otp()[index] && index > 0) {
      this.otpInputs()[index - 1]?.nativeElement.focus();
    }
  }

  protected backToLogin(): void {
    this.step.set('login');
  }

  private finishLogin(): void {
    this.auth.login();
    void this.router.navigate(['/admin', 'dashboard']);
  }
}
