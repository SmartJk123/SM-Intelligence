import { AccountApi } from './account-api';
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
            <a routerLink="/dashboard">Dashboard</a
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
export class App {
  readonly api = inject(AccountApi);
  private router = inject(Router);
  readonly inWorkspace = signal(false);
  constructor() {
    const update = () =>
      this.inWorkspace.set(
        [
          '/dashboard',
          '/overview',
          '/accounts',
          '/transactions',
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
