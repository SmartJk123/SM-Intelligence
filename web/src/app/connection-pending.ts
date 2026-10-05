import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AccountApi } from './account-api';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  imports: [CurrencyPipe, RouterLink],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading"><div><p class="eyebrow">YOUR WORKSPACE</p>
        <h1>{{ title }}</h1><p class="muted">Welcome, {{ api.displayName() }}.</p></div></header>
      @if (page === 'settings') {
        <section class="ws-card"><h2>Your profile</h2><p>{{ api.displayName() }}</p><p>{{ api.email() }}</p>
          <p>{{ api.kind() === 'organization' ? 'Company' : 'Individual' }} account</p>
          <h2>Appearance</h2><button class="button secondary" (click)="toggleTheme()">{{ dark() ? 'Use light mode' : 'Use dark mode' }}</button>
        </section>
      } @else if (page === 'dashboard' || page === 'accounts') {
        <section class="ws-card">
          <h2>Your accounts</h2>
          <p class="muted">Manual opening snapshots. These balances do not update from your bank.</p>
          @for (account of api.accounts(); track account.id) {
            <article class="account-summary">
              <div><strong>{{ account.accountName }}</strong>
                <small>{{ account.institution }} · {{ account.maskedIdentifier }}</small>
                <p>{{ account.accountType === 'CREDIT' ? 'Credit outstanding' : 'Available balance' }}:
                  {{ (account.accountType === 'CREDIT' ? account.creditOutstanding : account.availableBalance) | currency:account.currency }}</p>
              </div>
            </article>
          }
          <div class="actions"><a class="button" routerLink="/invoices">Manage invoices →</a>
            <a class="button secondary" routerLink="/accounts/new">Add another account</a></div>
          <p class="muted">Saved invoices are pending expenses and do not change your account balances.</p>
        </section>
      } @else {
        <section class="ws-card" role="status"><h2>{{ page === 'dashboard' ? 'You’re signed in' : 'Not connected yet' }}</h2>
          <p>Your profile is ready. Accounts and financial activity will appear here when those services are connected.</p>
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
