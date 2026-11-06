import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { ActivityGateway, AdminNotificationDto } from './activity.gateway';
import { AuthService } from './auth.service';
import { AppNotification, Severity } from './data';

/** How often the bell checks for new notifications while the portal is open. */
const REFRESH_MS = 30_000;

/**
 * Single source of truth for notifications. The sidebar badge, the top bar
 * dropdown, the notification page and the message dialog all read from here so
 * that read state stays consistent across the application.
 *
 * The notifications come from identity-service: new sign ups, first sign ins,
 * administrator sign ins, suspended accounts trying to sign in and services
 * going down or recovering. They are reloaded every 30 seconds.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly gateway = inject(ActivityGateway);
  private readonly auth = inject(AuthService);

  private readonly state = signal<AppNotification[]>([]);
  private readonly errorState = signal<string | null>(null);
  private readonly loadedState = signal(false);

  readonly all = this.state.asReadonly();
  /** Why the last load failed, or null. */
  readonly loadError = this.errorState.asReadonly();
  readonly loaded = this.loadedState.asReadonly();

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

  constructor() {
    void this.load();
    const timer = setInterval(() => void this.load(), REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  async load(): Promise<void> {
    if (!this.auth.token()) {
      return;
    }
    try {
      const items = await this.gateway.notifications();
      this.state.set(items.map(toAppNotification));
      this.errorState.set(null);
    } catch {
      // Keep what is on screen; say why it may be out of date.
      this.errorState.set('Notifications could not be loaded. Check that identity-service and the gateway are running.');
    } finally {
      this.loadedState.set(true);
    }
  }

  open(notification: AppNotification): void {
    this.markRead(notification.id);
    const current = this.state().find((item) => item.id === notification.id);
    this.selected.set(current ?? { ...notification, read: true });
  }

  close(): void {
    this.selected.set(null);
  }

  markRead(id: string): void {
    const target = this.state().find((item) => item.id === id);
    if (!target || target.read) {
      return;
    }
    this.state.update((items) => items.map((item) => (item.id === id ? { ...item, read: true } : item)));
    // Shown as read at once; the next load corrects it if the backend did not store it.
    this.gateway.markRead(id).catch(() => undefined);
  }

  markAllRead(): void {
    this.state.update((items) => items.map((item) => ({ ...item, read: true })));
    this.gateway.markAllRead().catch(() => undefined);
  }
}

function toAppNotification(dto: AdminNotificationDto): AppNotification {
  return {
    id: dto.id,
    title: dto.title,
    desc: dto.message,
    severity: toSeverity(dto.severity),
    cat: dto.category,
    time: relativeTime(dto.createdAt),
    createdAt: dto.createdAt,
    read: dto.read,
  };
}

function toSeverity(value: string): Severity {
  switch (value) {
    case 'CRITICAL':
      return 'Critical';
    case 'WARNING':
      return 'Warning';
    default:
      return 'Info';
  }
}

/** "just now", "5 min ago", "3 h ago", then the date. */
export function relativeTime(iso: string): string {
  const then = new Date(iso);
  if (Number.isNaN(then.getTime())) {
    return '';
  }
  const minutes = Math.round((Date.now() - then.getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return then.toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'short' });
}
