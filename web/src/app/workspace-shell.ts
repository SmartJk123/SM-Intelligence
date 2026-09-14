import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AccountApi } from './account-api';
export const workspaceLinks = [
  { path: 'dashboard', label: 'Overview', group: '', icon: '▦' },
  { path: 'accounts', label: 'Accounts', group: 'FINANCES', icon: '▣' },
  { path: 'transactions', label: 'Transactions', group: '', icon: '⇄' },
  { path: 'cashflow', label: 'Cash Flow', group: '', icon: '↗' },
  { path: 'budgets', label: 'Budgets', group: '', icon: '◉' },
  { path: 'investments', label: 'Investments', group: '', icon: '◇' },
  { path: 'analysis', label: 'Analysis', group: 'INSIGHTS', icon: '▥' },
  { path: 'reports', label: 'Reports', group: '', icon: '▤' },
  { path: 'notifications', label: 'Notifications', group: 'ACCOUNT', icon: '♧' },
  { path: 'settings', label: 'Profile & Settings', group: '', icon: '⚙' },
];
@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: ` <div class="workspace-shell" (keydown.escape)="closeMenu()">
    <button
      class="ws-shade"
      [class.open]="menu()"
      (click)="closeMenu()"
      aria-label="Close navigation"
    ></button>
    <aside class="ws-sidebar" id="workspace-navigation" [class.open]="menu()">
      <a routerLink="/" class="brand ws-brand"
        ><span class="system-logo" aria-hidden="true"></span
        ><span>SM-Intelligence<small>SMARTMONEY</small></span></a
      >
      <button class="ws-close" (click)="closeMenu()" aria-label="Close navigation">✕</button>
      <nav aria-label="Workspace navigation">
        @for (link of links; track link.path) {
          @if (link.group) {
            <p class="ws-nav-group">{{ link.group }}</p>
          }
          <a
            [routerLink]="'/' + link.path"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            (click)="menu.set(false)"
            ><span aria-hidden="true">{{ link.icon }}</span
            >{{ link.label }}</a
          >
        }
      </nav>
      <div class="ws-sidebar-foot">
        <span class="ws-avatar">SM</span>
        <div>
          <strong>{{
            api.kind() === 'organization' ? 'Organization workspace' : 'Personal workspace'
          }}</strong
          ><small>Every shilling. One clear view.</small>
        </div>
      </div>
      <button class="ws-signout" (click)="logout()" [disabled]="pending()">Sign out</button>
    </aside>
    <div class="ws-main">
      <header class="ws-topbar">
        <div>
          <button
            id="ws-menu-trigger"
            class="ws-menu"
            (click)="menu.set(!menu())"
            [attr.aria-expanded]="menu()"
            aria-controls="workspace-navigation"
            aria-label="Open navigation"
          >
            ☰</button
          ><span>Dashboard</span>
        </div>
        <div>
          <span class="ws-currency">KES</span
          ><a routerLink="/notifications" aria-label="Open notifications"
            >Notifications <span aria-hidden="true">↗</span></a
          ><a class="ws-avatar" routerLink="/settings" aria-label="Open profile and settings">SM</a>
        </div>
      </header>
      @if (error()) {
        <p class="ws-alert" role="alert">{{ error() }}</p>
      }
      <router-outlet />
    </div>
    <nav class="ws-bottom" aria-label="Mobile navigation">
      <a routerLink="/dashboard" routerLinkActive="active">▦<small>Overview</small></a
      ><a routerLink="/accounts" routerLinkActive="active">▣<small>Accounts</small></a
      ><a routerLink="/transactions" routerLinkActive="active">⇄<small>Activity</small></a
      ><a routerLink="/analysis" routerLinkActive="active">▥<small>Insights</small></a
      ><button
        (click)="menu.set(!menu())"
        [attr.aria-expanded]="menu()"
        aria-controls="workspace-navigation"
      >
        ☰<small>More</small>
      </button>
    </nav>
  </div>`,
})
export class WorkspaceShell {
  readonly links = workspaceLinks;
  readonly api = inject(AccountApi);
  private router = inject(Router);
  readonly menu = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  closeMenu() {
    this.menu.set(false);
    document.getElementById('ws-menu-trigger')?.focus();
  }
  async logout() {
    this.pending.set(true);
    try {
      await this.api.logout();
      await this.router.navigateByUrl('/login');
    } catch {
      this.error.set('Unable to sign out. Please try again.');
    } finally {
      this.pending.set(false);
    }
  }
}
