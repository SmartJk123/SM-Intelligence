import { Injectable, NgZone, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

/** An administrator is signed out after this long without touching the portal. */
export const IDLE_LIMIT_MS = 15 * 60 * 1000;
/** How long before sign-out the warning appears. */
export const IDLE_WARNING_MS = 60 * 1000;
const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'mousemove'] as const;

/**
 * Signs an inactive administrator out. The admin token stays valid in this tab
 * (sessionStorage) for up to a day, so an unattended portal would otherwise stay
 * open on every customer's data. AdminLayout starts this while signed in.
 */
@Injectable({ providedIn: 'root' })
export class IdleTimeoutService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  /** Seconds left before sign-out while the warning is showing, otherwise null. */
  readonly secondsLeft = signal<number | null>(null);

  /** Settings, Security can change the limit; IDLE_LIMIT_MS until it has loaded. */
  private limitMs = IDLE_LIMIT_MS;
  private lastActivity = Date.now();
  private ticker: ReturnType<typeof setInterval> | null = null;
  private readonly onActivity = () => {
    // While the warning shows, only "Stay signed in" keeps the session.
    if (this.secondsLeft() !== null) return;
    this.lastActivity = Date.now();
  };

  start(): void {
    if (this.ticker) return;
    this.lastActivity = Date.now();
    this.zone.runOutsideAngular(() => {
      for (const event of ACTIVITY_EVENTS) window.addEventListener(event, this.onActivity, { passive: true });
      this.ticker = setInterval(() => this.zone.run(() => this.tick()), 1000);
    });
  }

  stop(): void {
    for (const event of ACTIVITY_EVENTS) window.removeEventListener(event, this.onActivity);
    if (this.ticker) clearInterval(this.ticker);
    this.ticker = null;
    this.secondsLeft.set(null);
  }

  /** Sets the idle limit in minutes (Settings, Security, Admin idle sign-out). */
  configure(minutes: number): void {
    if (Number.isFinite(minutes) && minutes >= 1) {
      this.limitMs = Math.round(minutes) * 60 * 1000;
    }
  }

  stayActive(): void {
    this.lastActivity = Date.now();
    this.secondsLeft.set(null);
  }

  private tick(): void {
    const idle = Date.now() - this.lastActivity;
    if (idle >= this.limitMs) {
      this.stop();
      this.auth.logout();
      void this.router.navigate(['/admin/login'], { queryParams: { reason: 'idle' } });
    } else if (idle >= this.limitMs - Math.min(IDLE_WARNING_MS, this.limitMs / 2)) {
      this.secondsLeft.set(Math.ceil((this.limitMs - idle) / 1000));
    } else {
      this.secondsLeft.set(null);
    }
  }
}
