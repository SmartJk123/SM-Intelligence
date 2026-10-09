// Signs the customer out after a period without any interaction, so an unattended
// screen does not stay open on someone's finances.
import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, timeout } from 'rxjs';
import { AccountApi } from './account-api';

/** Matches SESSION_IDLE_MINUTES in tools/auth-server.mjs, which ends the session on the server too. */
export const IDLE_LIMIT_MS = 15 * 60 * 1000;
/** How long before sign-out the warning appears. */
export const IDLE_WARNING_MS = 60 * 1000;
/** While the customer is active, the server session is refreshed at most this often. */
const KEEPALIVE_MS = 4 * 60 * 1000;
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'mousemove'] as const;

@Injectable({ providedIn: 'root' })
export class IdleTimeout {
  private api = inject(AccountApi);
  private http = inject(HttpClient);
  private router = inject(Router);
  private zone = inject(NgZone);

  /** Seconds left before sign-out while the warning is showing, otherwise null. */
  readonly secondsLeft = signal<number | null>(null);

  private lastActivity = Date.now();
  private lastKeepalive = Date.now();
  private ticker: ReturnType<typeof setInterval> | null = null;
  private readonly onActivity = () => {
    // Moving the mouse while the warning shows does not count: the customer
    // must choose "Stay signed in", so an unattended screen still signs out.
    if (this.secondsLeft() !== null) return;
    this.lastActivity = Date.now();
  };

  constructor() {
    effect(() => (this.api.authenticated() ? this.start() : this.stop()));
  }

  /** Called by the warning's "Stay signed in" button. */
  async stayActive() {
    this.lastActivity = Date.now();
    this.secondsLeft.set(null);
    await this.keepalive();
  }

  private start() {
    if (this.ticker) return;
    this.lastActivity = Date.now();
    this.lastKeepalive = Date.now();
    this.zone.runOutsideAngular(() => {
      for (const event of ACTIVITY_EVENTS) window.addEventListener(event, this.onActivity, { passive: true });
      this.ticker = setInterval(() => this.zone.run(() => this.tick()), 1000);
    });
  }

  private stop() {
    for (const event of ACTIVITY_EVENTS) window.removeEventListener(event, this.onActivity);
    if (this.ticker) clearInterval(this.ticker);
    this.ticker = null;
    this.secondsLeft.set(null);
  }

  private tick() {
    const idle = Date.now() - this.lastActivity;
    if (idle >= IDLE_LIMIT_MS) {
      void this.signOut();
    } else if (idle >= IDLE_LIMIT_MS - IDLE_WARNING_MS) {
      this.secondsLeft.set(Math.ceil((IDLE_LIMIT_MS - idle) / 1000));
    } else {
      this.secondsLeft.set(null);
      if (Date.now() - this.lastKeepalive >= KEEPALIVE_MS) void this.keepalive();
    }
  }

  /** Any authenticated request tells the server the customer is still here. */
  private async keepalive() {
    this.lastKeepalive = Date.now();
    try {
      await firstValueFrom(this.http.get('/api/auth/session').pipe(timeout(15000)));
    } catch {
      // The session already ended on the server; the next tick or request signs out.
    }
  }

  private async signOut() {
    this.stop();
    try {
      await this.api.logout();
    } catch {
      // The server may already have ended the session; clear it here regardless.
      this.api.authenticated.set(false);
    }
    await this.router.navigate(['/login'], { queryParams: { reason: 'idle' } });
  }
}
