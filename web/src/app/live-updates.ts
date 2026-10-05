// Real-time balance and transaction notifications over a WebSocket, so money
// in/out appears without the customer refreshing the page.
import { Injectable, signal } from '@angular/core';

export interface LiveTransaction {
  accountId: string;
  direction: 'CREDIT' | 'DEBIT';
  amountMinor: number;
  description: string;
  date: string;
}

export interface LiveNotification extends LiveTransaction {
  id: string;
  read: boolean;
}

const RECONNECT_DELAY_MS = 4000;
const MAX_NOTIFICATIONS = 50;

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
  }

  markAllRead(): void {
    this.notifications.update((items) => items.map((item) => ({ ...item, read: true })));
    this.unreadCount.set(0);
  }

  private open(): void {
    if (!this.connected) return;
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this.socket = new WebSocket(`${protocol}//${location.host}/api/ws`);
    this.socket.addEventListener('message', (event) => this.handleMessage(event.data));
    this.socket.addEventListener('close', () => this.scheduleReconnect());
    this.socket.addEventListener('error', () => this.socket?.close());
  }

  private scheduleReconnect(): void {
    if (!this.connected) return;
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
  }
}
