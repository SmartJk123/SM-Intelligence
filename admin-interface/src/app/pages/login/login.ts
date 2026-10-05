import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  host: { class: 'block' },
})
export class LoginPage {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly step = signal<'login'>('login');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly error = signal('');
  protected readonly loading = signal(false);
  protected readonly showPassword = signal(false);

  protected async submitCredentials(event: Event): Promise<void> {
    event.preventDefault();
    if (this.loading()) return;
    const email = this.email().trim();
    if (!email || !this.password()) {
      this.error.set('Enter your email address and password.');
      return;
    }
    this.error.set('');
    this.loading.set(true);
    try {
      await this.auth.login(email, this.password());
      void this.router.navigate(['/admin', 'dashboard']);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Sign in failed. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }
}
