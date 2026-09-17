// Authentication and initial account-setup API service.
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';

import { SAMPLE_AUTH } from './auth-mode';
export const SAMPLE_AUTH_MODE = new InjectionToken<boolean>('sample auth', { providedIn: 'root', factory: () => SAMPLE_AUTH });

// Keep endpoints aligned with API-CONTRACT.md. Update carefully when authentication flow changes.
export type AccountKind = 'individual' | 'organization';
export interface Registration {
  name: string;
  email: string;
  phone: string;
  password: string;
  kind: AccountKind;
}
export interface AccountDetails {
  bank: string;
  accountName: string;
  accountNumber: string;
  cardType: 'debit' | 'credit';
  balance: number;
  balanceDate: string;
  currency: 'KES';
}
interface Session {
  user: { name?: string; email?: string; id: string; kind: AccountKind; setupCompleted: boolean };
}
// Same-origin, cookie-based API contract. See API-CONTRACT.md before backend integration.
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private http = inject(HttpClient);
  readonly sampleMode = inject(SAMPLE_AUTH_MODE);
  readonly email = signal('');
  readonly displayName = signal('');
  readonly initials = computed(() => {
    const name = this.displayName().trim() || this.email().split('@')[0];
    const words = name.match(/[\p{L}\p{N}]+/gu) || [];
    if (!words.length) return '?';
    const first = Array.from(words[0] || '')[0] || '';
    const last = words.length > 1 ? Array.from(words[words.length - 1] || '')[0] || '' : '';
    return (first + last).toLocaleUpperCase();
  });
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private readonly sessionKey = 'sm-sample-auth-v1';
  private acceptToken(token: string, email: string) {
    try {
      const claims = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      if (claims.sub !== email || !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) throw new Error();
      // Client expiry is only a UI check. Protected APIs must verify JWT signatures.
      clearTimeout(this.expiryTimer);
      this.email.set(email);
      this.authenticated.set(true);
      this.expiryTimer = setTimeout(() => { void this.logout(); }, Math.min(claims.exp * 1000 - Date.now(), 2147483647));
      try { sessionStorage.setItem(this.sessionKey, JSON.stringify({ token, email })); } catch {}
    } catch { throw new Error('The server returned an invalid or expired session.'); }
  }
  readonly authenticated = signal(false);
  readonly setupCompleted = signal(false);
  readonly kind = signal<AccountKind>('individual');
  private acceptSession(session: Session) {
    if (
      !session?.user?.id ||
      !['individual', 'organization'].includes(session.user.kind) ||
      typeof session.user.setupCompleted !== 'boolean'
    )
      throw new Error(
        'The account service returned an invalid session. Please try signing in again.',
      );
    this.authenticated.set(true);
    this.displayName.set(session.user.name?.trim() || '');
    this.email.set(session.user.email || '');
    this.kind.set(session.user.kind);
    this.setupCompleted.set(session.user.setupCompleted);
  }
  async login(input: { email: string; password: string }) {
    if (this.sampleMode) {
      const result = await firstValueFrom(this.http.post<{ accessToken?: string; token?: string }>('/sample-api/api/auth/login', input).pipe(timeout(90000)));
      // The hosted sample returns token; the supplied source ZIP returns accessToken.
      const token = result.accessToken ?? result.token;
      if (typeof token !== 'string' || !token) throw new Error('The sign-in response did not contain an access token.');
      this.acceptToken(token, input.email);
      return;
    }
    this.acceptSession(
      await firstValueFrom(this.http.post<Session>('/api/auth/login', input).pipe(timeout(95000))),
    );
  }
  async register(input: Registration): Promise<'sign-in' | void> {
    if (this.sampleMode) {
      await firstValueFrom(this.http.post('/sample-api/api/auth/register', {
        name: input.name, email: input.email, phoneNumber: input.phone.trim(), password: input.password,
      }).pipe(timeout(90000)));
      return;
    }
    const result = await firstValueFrom(
      this.http.post<Session | { registered: true }>('/api/auth/register', input).pipe(timeout(95000)),
    );
    if ('registered' in result && result.registered) return 'sign-in';
    this.acceptSession(result as Session);
  }
  async restoreSession() {
    if (this.sampleMode) {
      try {
        const saved = JSON.parse(sessionStorage.getItem(this.sessionKey) || 'null');
        if (!saved) return false;
        this.acceptToken(saved.token, saved.email);
        return true;
      } catch { await this.logout(); return false; }
    }
    try {
      this.acceptSession(
        await firstValueFrom(this.http.get<Session>('/api/auth/session').pipe(timeout(95000))),
      );
      return true;
    } catch {
      this.displayName.set('');
      this.authenticated.set(false);
      this.setupCompleted.set(false);
      return false;
    }
  }
  async saveSetup(input: AccountDetails): Promise<void> {
    const result = await firstValueFrom(
      this.http.post<{ id: string }>('/api/accounts', input).pipe(timeout(95000)),
    );
    if (!result?.id)
      throw new Error('The account service did not confirm the save. Please try again.');
    this.setupCompleted.set(true);
  }
  async logout() {
    if (this.sampleMode) {
      clearTimeout(this.expiryTimer);
      try { sessionStorage.removeItem(this.sessionKey); } catch {}
      this.authenticated.set(false); this.setupCompleted.set(false); this.email.set('');
      return;
    }
    await firstValueFrom(this.http.post('/api/auth/logout', {}).pipe(timeout(95000)));
    this.authenticated.set(false);
    this.setupCompleted.set(false);
  }
}
