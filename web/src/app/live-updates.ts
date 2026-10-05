// Real-time balance and transaction notifications over a WebSocket, so money
// in/out appears without the customer refreshing the page.
import { Injectable, signal } from '@angular/core';

export interface LiveTransaction {
  accountId: string;
  direction: 'CREDIT' | 'DEBIT';
  amountMinor: number;
  /** Sender (money in) or recipient (money out) name, as the bank reported it. */
  description: string;
  date: string;
  bank?: string;
}

export interface LiveNotification extends LiveTransaction {
  id: string;
  read: boolean;
}

export type LiveStatus = 'offline' | 'connecting' | 'live';

const RECONNECT_DELAY_MS = 4000;
const MAX_NOTIFICATIONS = 50;
const TOAST_DURATION_MS = 8000;
const MAX_TOASTS = 3;

@Injectable({ providedIn: 'root' })
export class LiveUpdates {
  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  private connected = false;

  /** Most recent notifications, newest first. */
  readonly notifications = signal<LiveNotification[]>([]);
  readonly unreadCount = signal(0);
  /** Bumped on every transaction, so a page can watch it and reload its own data. */
  readonly lastUpdate = signal<LiveTransaction | null>(null);
  /** Whether the socket is open, so a page can show that figures update on their own. */
  readonly status = signal<LiveStatus>('offline');
  /** Payments to pop up on screen as they arrive; each removes itself after a few seconds. */
  readonly toasts = signal<LiveNotification[]>([]);

  connect(): void {
    if (this.connected || typeof WebSocket === 'undefined') return;
    this.connected = true;
    this.open();
  }

  disconnect(): void {
    this.connected = false;
    clearTimeout(this.reconnectTimer);
    this.socket?.close();
    this.socket = null;
    this.status.set('offline');
    this.toasts.set([]);
  }

  dismissToast(id: string): void {
    this.toasts.update((items) => items.filter((item) => item.id !== id));
  }

  markAllRead(): void {
    this.notifications.update((items) => items.map((item) => ({ ...item, read: true })));
    this.unreadCount.set(0);
  }

  private open(): void {
    if (!this.connected) return;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.status.set('connecting');
    this.socket = new WebSocket(`${protocol}//${location.host}/api/ws`);
    this.socket.addEventListener('open', () => this.status.set('live'));
    this.socket.addEventListener('message', (event) => this.handleMessage(event.data));
    this.socket.addEventListener('close', () => this.scheduleReconnect());
    this.socket.addEventListener('error', () => this.socket?.close());
  }

  private scheduleReconnect(): void {
    if (!this.connected) return;
    this.status.set('offline');
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = setTimeout(() => this.open(), RECONNECT_DELAY_MS);
  }

  private handleMessage(raw: string): void {
    let payload: LiveTransaction & { type: string };
    try {
      payload = JSON.parse(raw);
    } catch {
      return;
    }
    if (payload.type !== 'transaction') return;
    const { type: _type, ...transaction } = payload;
    this.lastUpdate.set(transaction);
    const notification: LiveNotification = { ...transaction, id: crypto.randomUUID(), read: false };
    this.notifications.update((items) => [notification, ...items].slice(0, MAX_NOTIFICATIONS));
    this.unreadCount.update((count) => count + 1);
    this.toasts.update((items) => [notification, ...items].slice(0, MAX_TOASTS));
    setTimeout(() => this.dismissToast(notification.id), TOAST_DURATION_MS);
  }
}
