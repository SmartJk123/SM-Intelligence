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
    this.error.set('');
    this.loading.set(true);
    try {
      await this.auth.login(this.email().trim(), this.password());
      void this.router.navigate(['/admin', 'dashboard']);
    } catch (error) {
      this.error.set(error instanceof Error ? error.message : 'Sign in failed. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  protected signInWithGoogle(): void {
    this.error.set('Google sign-in is not available yet. Use your email and password.');
  }
}
