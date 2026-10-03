// Real-time feed of money in/out, pushed live over the workspace WebSocket.
import { WorkspaceIcon } from './workspace-icon';
import { Component, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LiveUpdates } from './live-updates';

@Component({
  imports: [CurrencyPipe, DatePipe, RouterLink, WorkspaceIcon],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">LIVE ACTIVITY</p>
          <h1>Notifications</h1>
          <p class="muted">Money in and out, as it's reported by your bank.</p>
        </div>
        @if (live.unreadCount()) {
          <button class="button secondary" (click)="live.markAllRead()">Mark all read</button>
        }
      </header>
      @if (!live.notifications().length) {
        <div class="dashboard-empty">
          <h2>Nothing yet</h2>
          <p>
            A notification appears here the moment a posted transaction reaches your dashboard
            while this page is connected. Earlier activity is on your
            <a routerLink="/dashboard">Financial Overview</a>.
          </p>
        </div>
      } @else {
        <div class="dashboard-panel">
          @for (item of live.notifications(); track item.id) {
            <div class="notification-row" [class.unread]="!item.read">
              <span class="notif-icon" [class.positive]="item.direction === 'CREDIT'">
                <app-workspace-icon [name]="item.direction === 'CREDIT' ? 'content-income' : 'content-expense'" />
              </span>
              <div class="notif-body">
                <strong>{{ item.description }}</strong>
                <small>{{ item.date | date: 'd MMM y, HH:mm' : 'UTC' }}</small>
              </div>
              <span class="amount-cell" [class.positive]="item.direction === 'CREDIT'">
                {{ item.direction === 'CREDIT' ? '+' : '−' }}{{ item.amountMinor / 100 | currency: 'KES' : '' : '1.2-2' }}
              </span>
            </div>
          }
        </div>
      }
    </section>
  `,
})
export class NotificationsPage {
  readonly live = inject(LiveUpdates);
}
