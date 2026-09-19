// Authentication and initial account-setup API service.
import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';


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
    this.acceptSession(
      await firstValueFrom(this.http.post<Session>('/api/auth/login', input).pipe(timeout(95000))),
    );
  }
  async register(input: Registration): Promise<'sign-in' | void> {
    const result = await firstValueFrom(
      this.http.post<Session | { registered: true }>('/api/auth/register', input).pipe(timeout(95000)),
    );
    if ('registered' in result && result.registered) return 'sign-in';
    this.acceptSession(result as Session);
  }
  async restoreSession() {
    try {
      this.acceptSession(
        await firstValueFrom(this.http.get<Session>('/api/auth/session').pipe(timeout(95000))),
      );
      return true;
    } catch {
      this.displayName.set('');
      this.email.set('');
      this.kind.set('individual');
      this.authenticated.set(false);
      this.setupCompleted.set(false);
      return false;
    }
  }
  async saveSetup(_input: AccountDetails): Promise<void> {
    throw new Error('Account setup is not connected yet.');
  }
  async logout() {
    await firstValueFrom(this.http.post('/api/auth/logout', {}).pipe(timeout(95000)));
    this.authenticated.set(false);
    this.setupCompleted.set(false);
    this.displayName.set('');
    this.email.set('');
    this.kind.set('individual');
  }
}
