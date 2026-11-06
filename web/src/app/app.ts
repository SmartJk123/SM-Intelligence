// Root application shell.
import { AccountApi } from './account-api';
import { IdleTimeout } from './idle-timeout';
import { Component, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    @if (!inWorkspace()) {
      <header class="site-header">
        <a class="brand" routerLink="/"
          ><span class="system-logo" aria-hidden="true"></span
          ><span>SM-Intelligence<small>SMARTMONEY</small></span></a
        >
        <nav aria-label="Main navigation" [class.authenticated-nav]="api.authenticated()">
          @if (api.authenticated()) {
            <a [routerLink]="api.setupCompleted() ? '/dashboard' : '/setup'">{{ api.setupCompleted() ? 'Dashboard' : 'Account setup' }}</a
            ><button class="button small" (click)="signOut()" [disabled]="signingOut()">
              Sign out
            </button>
          } @else {
            <a routerLink="/" fragment="features">Features</a
            ><a routerLink="/" fragment="how">How it works</a><a routerLink="/login">Sign in</a
            ><a class="button small" routerLink="/register">Get started</a>
          }
        </nav>
      </header>
    }
    @if (idle.secondsLeft() !== null) {
      <div class="idle-warning" role="alertdialog" aria-live="assertive" aria-labelledby="idle-title">
        <p id="idle-title">
          <strong>Still there?</strong> For your security you will be signed out in
          {{ idle.secondsLeft() }} seconds because of inactivity.
        </p>
        <button class="button small" type="button" (click)="idle.stayActive()">Stay signed in</button>
      </div>
    }
    @if (logoutError()) {
      <p role="alert">{{ logoutError() }}</p>
    }
    <main id="main"><router-outlet /></main>
    @if (!inWorkspace()) {
      <footer>
        <a class="brand" routerLink="/"
          ><span class="system-logo" aria-hidden="true"></span>SM-Intelligence</a
        ><span>Every shilling. One clear view.</span><span>© 2026 SM-Intelligence</span>
      </footer>
    }
  `,
})

// Controls public navigation, workspace detection and global sign-out behavior.
export class App {
  readonly api = inject(AccountApi);
  /** Signs out after 15 minutes without interaction; shows the warning above first. */
  readonly idle = inject(IdleTimeout);
  private router = inject(Router);
  readonly inWorkspace = signal(false);
  constructor() {
    // One-time, scoped reset requested for the fresh workspace rollout.
    try {
      if (localStorage.getItem('sm-fresh-workspace-v1') !== 'done') {
        localStorage.removeItem('sm-intelligence-workspace-v1');
        localStorage.removeItem('sm-intelligence-appearance');
        sessionStorage.removeItem('sm-auth-session-v1');
        sessionStorage.removeItem('sm-sample-auth-v1');
        localStorage.setItem('sm-fresh-workspace-v1', 'done');
      }
    } catch {}

    const update = () =>
      this.inWorkspace.set(
        [
          '/dashboard',
          '/overview',
          '/accounts',
          '/accounts/new',
          '/transactions',
          '/invoices',
          '/cashflow',
          '/budgets',
          '/investments',
          '/analysis',
          '/reports',
          '/notifications',
          '/settings',
        ].includes(this.router.url.split('?')[0]),
      );
    this.router.events.subscribe((e) => {
      if (e instanceof NavigationEnd) update();
    });
    try {
      document.documentElement.dataset['theme'] =
        localStorage.getItem('sm-intelligence-appearance') === 'dark' ? 'dark' : 'light';
    } catch {}
    update();
  }
  readonly signingOut = signal(false);
  readonly logoutError = signal('');
  async signOut() {
    this.signingOut.set(true);
    this.logoutError.set('');
    try {
      await this.api.logout();
      await this.router.navigateByUrl('/login');
    } catch {
      this.logoutError.set('Sign out failed. Please try again.');
    } finally {
      this.signingOut.set(false);
    }
  }
}
