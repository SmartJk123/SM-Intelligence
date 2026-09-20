import { Injectable, computed, signal } from '@angular/core';
import { AppNotification, NOTIFICATIONS } from './data';

/**
 * Single source of truth for notifications. The sidebar badge, the top bar
 * dropdown, the notification page and the message dialog all read from here so
 * that read state stays consistent across the application.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly state = signal<AppNotification[]>(
    NOTIFICATIONS.map((notification) => ({ ...notification })),
  );

  readonly all = this.state.asReadonly();

  readonly unreadCount = computed(
    () => this.state().filter((notification) => !notification.read).length,
  );

  readonly criticalCount = computed(
    () => this.state().filter((notification) => notification.severity === 'Critical').length,
  );

  readonly warningCount = computed(
    () => this.state().filter((notification) => notification.severity === 'Warning').length,
  );

  /** The three most recent notifications, used by the top bar dropdown. */
  readonly preview = computed(() => this.state().slice(0, 3));

  /** Notification currently shown in the message dialog, if any. */
  readonly selected = signal<AppNotification | null>(null);

  open(notification: AppNotification): void {
    this.markRead(notification.id);
    const current = this.state().find((item) => item.id === notification.id);
    this.selected.set(current ?? { ...notification, read: true });
  }

  close(): void {
    this.selected.set(null);
  }

  markRead(id: number): void {
    this.state.update((items) =>
      items.map((item) => (item.id === id ? { ...item, read: true } : item)),
    );
  }

  markAllRead(): void {
    this.state.update((items) => items.map((item) => ({ ...item, read: true })));
  }
}
