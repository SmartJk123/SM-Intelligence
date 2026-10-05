// Authentication and initial account-setup API service.
import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
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
export interface SavedAccount {
  id: string;
  accountName: string;
  institution: string;
  maskedIdentifier: string;
  accountType: 'DEPOSIT' | 'CREDIT';
  currency: string;
  availableBalance: number;
  creditOutstanding: number;
  accountStatus: string;
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
  readonly accounts = signal<SavedAccount[]>([]);
  readonly accountsError = signal('');
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
  /** Asks for a reset link. Resolves the same way whether or not the address has an account. */
  async forgotPassword(email: string) {
    await firstValueFrom(this.http.post('/api/auth/forgot-password', { email }).pipe(timeout(30000)));
  }
  async resetPassword(token: string, password: string) {
    await firstValueFrom(this.http.post('/api/auth/reset-password', { token, password }).pipe(timeout(30000)));
  }
  async restoreSession() {
    try {
      this.acceptSession(
        await firstValueFrom(this.http.get<Session>('/api/auth/session').pipe(timeout(95000))),
      );
      return true;
    } catch {
      this.accounts.set([]);
      this.accountsError.set('');
      this.displayName.set('');
      this.email.set('');
      this.kind.set('individual');
      this.authenticated.set(false);
      this.setupCompleted.set(false);
      return false;
    }
  }
  async refreshAccounts() {
    this.accountsError.set('');
    try {
      const accounts = await firstValueFrom(this.http.get<SavedAccount[]>('/api/accounts').pipe(timeout(20000)));
      this.accounts.set(accounts.filter(account => account.accountStatus === 'ACTIVE'));
      this.setupCompleted.set(this.accounts().length > 0);
    } catch (error) {
      this.accounts.set([]);
      this.setupCompleted.set(false);
      if (error instanceof HttpErrorResponse && error.status === 401) this.authenticated.set(false);
      this.accountsError.set('We could not load your accounts. Check your connection and retry.');
    }
  }
  async saveSetup(input: AccountDetails): Promise<void> {
    const account = await firstValueFrom(this.http.post<SavedAccount>('/api/accounts', input).pipe(timeout(20000)));
    this.accounts.update(accounts => [...accounts.filter(item => item.id !== account.id), account]);
    this.setupCompleted.set(true);
    this.accountsError.set('');
  }
  async logout() {
    await firstValueFrom(this.http.post('/api/auth/logout', {}).pipe(timeout(95000)));
    this.authenticated.set(false);
    this.accounts.set([]);
    this.accountsError.set('');
    this.setupCompleted.set(false);
    this.displayName.set('');
    this.email.set('');
    this.kind.set('individual');
  }
}
