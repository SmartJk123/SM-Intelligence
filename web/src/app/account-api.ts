import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
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
  user: { id: string; kind: AccountKind; setupCompleted: boolean };
}
// Same-origin, cookie-based API contract. See API-CONTRACT.md before backend integration.
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private http = inject(HttpClient);
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
    this.kind.set(session.user.kind);
    this.setupCompleted.set(session.user.setupCompleted);
  }
  async login(input: { email: string; password: string }) {
    this.acceptSession(
      await firstValueFrom(this.http.post<Session>('/api/auth/login', input).pipe(timeout(15000))),
    );
  }
  async register(input: Registration) {
    this.acceptSession(
      await firstValueFrom(
        this.http.post<Session>('/api/auth/register', input).pipe(timeout(15000)),
      ),
    );
  }
  async restoreSession() {
    try {
      this.acceptSession(
        await firstValueFrom(this.http.get<Session>('/api/auth/session').pipe(timeout(15000))),
      );
      return true;
    } catch {
      this.authenticated.set(false);
      this.setupCompleted.set(false);
      return false;
    }
  }
  async saveSetup(input: AccountDetails): Promise<void> {
    const result = await firstValueFrom(
      this.http.post<{ id: string }>('/api/accounts', input).pipe(timeout(15000)),
    );
    if (!result?.id)
      throw new Error('The account service did not confirm the save. Please try again.');
    this.setupCompleted.set(true);
  }
  async logout() {
    await firstValueFrom(this.http.post('/api/auth/logout', {}).pipe(timeout(15000)));
    this.authenticated.set(false);
    this.setupCompleted.set(false);
  }
}
