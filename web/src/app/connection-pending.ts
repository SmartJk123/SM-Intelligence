import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AccountApi } from './account-api';

@Component({
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading"><div><p class="eyebrow">YOUR WORKSPACE</p>
        <h1>{{ title }}</h1><p class="muted">Welcome, {{ api.displayName() }}.</p></div></header>
      @if (page === 'settings') {
        <section class="ws-card"><h2>Your profile</h2><p>{{ api.displayName() }}</p><p>{{ api.email() }}</p>
          <p>{{ api.kind() === 'organization' ? 'Organization' : 'Individual' }} account</p>
          <h2>Appearance</h2><button class="button secondary" (click)="toggleTheme()">{{ dark() ? 'Use light mode' : 'Use dark mode' }}</button>
        </section>
      } @else {
        <section class="ws-card" role="status"><h2>{{ page === 'dashboard' ? 'You’re signed in' : 'Not connected yet' }}</h2>
          <p>Your profile is ready. Accounts and financial activity will appear here when those services are connected.</p>
          <p class="muted">There are no sample accounts or temporary financial records.</p>
        </section>
      }
    </section>
  `,
})
export class ConnectionPending {
  readonly api = inject(AccountApi);
  private route = inject(ActivatedRoute);
  readonly page = this.route.snapshot.data['page'] || 'dashboard';
  readonly title = this.route.snapshot.title?.split(' | ')[0] || 'Financial Overview';
  readonly dark = signal(document.documentElement.dataset['theme'] === 'dark');
  toggleTheme() {
    this.dark.update(v => !v);
    const theme = this.dark() ? 'dark' : 'light';
    document.documentElement.dataset['theme'] = theme;
    try { localStorage.setItem('sm-intelligence-appearance', theme); } catch {}
  }
}
