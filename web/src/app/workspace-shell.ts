// Authenticated workspace layout and navigation shared by all customer-facing finance pages.
import { WorkspaceIcon } from './workspace-icon';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AccountApi } from './account-api';
import { LiveUpdates } from './live-updates';

// Single source of truth for authenticated navigation labels and routes.
export const workspaceLinks = [
  { path: 'dashboard', label: 'Financial Overview', group: '', icon: '🌌' },
  { path: 'accounts', label: 'Accounts', group: 'FINANCES', icon: '💳' },
  { path: 'transactions', label: 'Transactions', group: '', icon: '🧾' },
  { path: 'invoices', label: 'Invoices', group: '', icon: '📄' },
  { path: 'cashflow', label: 'Cash Flow', group: '', icon: '💸' },
  { path: 'budgets', label: 'Budgets', group: '', icon: '🛡️' },
  { path: 'investments', label: 'Investments', group: '', icon: '💼' },
  { path: 'analysis', label: 'Analytics', group: '', icon: '📐' },
  { path: 'reports', label: 'Reports', group: '', icon: '📄' },
  { path: 'notifications', label: 'Notifications', group: 'ACCOUNT', icon: '🔔' },
  { path: 'settings', label: 'Profile and Settings', group: '', icon: '👤' },
];

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet, WorkspaceIcon],
  template: ` <div class="workspace-shell" (keydown.escape)="closeMenu()">
    <button
      class="ws-shade"
      [class.open]="menu()"
      (click)="closeMenu()"
      aria-label="Close navigation"
    ></button>
    <aside class="ws-sidebar" id="workspace-navigation" [class.open]="menu()">
      <a routerLink="/" class="brand ws-brand" aria-label="SM-Intelligence home"
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
            [attr.aria-label]="link.label" [attr.title]="link.label"
            [routerLink]="'/' + link.path"
            routerLinkActive="active"
            ariaCurrentWhenActive="page"
            (click)="menu.set(false)"
            ><app-workspace-icon [name]="link.path" /><span class="ws-link-label">{{ link.label }}</span></a
          >
        }
      </nav>
      <div class="ws-sidebar-foot">
        <span class="ws-avatar">{{ api.initials() }}</span>
        <div>
          <strong>{{
            api.kind() === 'organization' ? 'Company workspace' : 'Personal workspace'
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
          ><a class="ws-top-brand" routerLink="/">SM-Intelligence</a>
        </div>
        <nav class="ws-top-nav" aria-label="Dashboard pages">
          @for (link of links.slice(0, 8); track link.path) {
            <a [routerLink]="'/' + link.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{ link.path === 'dashboard' ? 'Overview' : link.label }}</a>
          }
        </nav>
        <div>
          <span class="ws-currency">KES</span
          ><a routerLink="/notifications" class="ws-notif-link" aria-label="Open notifications" (click)="live.markAllRead()"
            >Notifications <app-workspace-icon name="notifications" />@if (live.unreadCount(); as count) {<span class="ws-notif-badge">{{ count > 9 ? '9+' : count }}</span>}</a
          ><a class="ws-avatar" routerLink="/settings" [attr.title]="api.displayName() || 'Profile and settings'" aria-label="Open profile and settings">{{ api.initials() }}</a>
        </div>
      </header>
      @if (error()) {
        <p class="ws-alert" role="alert">{{ error() }}</p>
      }
      <router-outlet />
    </div>
    <nav class="ws-bottom" aria-label="Mobile navigation">
      <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page"
        ><app-workspace-icon name="dashboard" /><small>Overview</small></a
      ><a routerLink="/accounts" routerLinkActive="active" ariaCurrentWhenActive="page"
        ><app-workspace-icon name="accounts" /><small>Accounts</small></a
      ><a routerLink="/transactions" routerLinkActive="active" ariaCurrentWhenActive="page"
        ><app-workspace-icon name="transactions" /><small>Activity</small></a
      ><a routerLink="/reports" routerLinkActive="active" ariaCurrentWhenActive="page"
        ><app-workspace-icon name="reports" /><small>Reports</small></a
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

export class WorkspaceShell implements OnInit, OnDestroy {
  readonly links = workspaceLinks;
  readonly api = inject(AccountApi);
  readonly live = inject(LiveUpdates);
  private router = inject(Router);
  readonly menu = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  ngOnInit() {
    this.live.connect();
  }
  ngOnDestroy() {
    this.live.disconnect();
  }
  closeMenu() {
    this.menu.set(false);
    document.getElementById('ws-menu-trigger')?.focus();
  }
  async logout() {
    this.pending.set(true);
    try {
      this.live.disconnect();
      await this.api.logout();
      await this.router.navigateByUrl('/login');
    } catch {
      this.error.set('Unable to sign out. Please try again.');
    } finally {
      this.pending.set(false);
    }
  }
}
