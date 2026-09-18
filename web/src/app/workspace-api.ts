import { AccountApi } from './account-api';
// Shared workspace data service for loading, creating and deleting customer-facing financial records.
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout, tap } from 'rxjs';

// Workspace pages intentionally share one aggregate endpoint to keep their data model consistent and reduce round trips.
export interface BankAccount {
  id: string;
  bank: string;
  accountName: string;
  maskedIdentifier: string;
  accountType: 'DEPOSIT' | 'CREDIT';
  availableBalanceMinor: number;
  creditOutstandingMinor: number;
}
export interface Entry {
  id: string;
  accountId: string;
  date: string;
  description: string;
  category: string;
  direction: 'CREDIT' | 'DEBIT';
  amountMinor: number;
  status: string;
}
export interface Budget {
  id: string;
  category: string;
  allocatedMinor: number;
  start: string;
  end: string;
  accountId: string;
  threshold: number;
}
export interface Investment {
  id: string;
  name: string;
  type: string;
  principalMinor: number;
  currentValueMinor: number | null;
  valuationDate: string;
  maturityDate: string;
}
export interface Profile {
  name: string;
  email: string;
  kind: string;
  organization: string;
  lowBalanceMinor: number;
  budgetAlerts: boolean;
  balanceAlerts: boolean;
  maturityAlerts: boolean;
}
export interface WorkspaceData {
  source: 'sample' | 'live' | 'local';
  accounts: BankAccount[];
  transactions: Entry[];
  budgets: Budget[];
  investments: Investment[];
  profile: Profile;
  read: string[];
  audit: { at: string; action: string }[];
}

// Collection must match a backend-supported workspace resource. See API-CONTRACT.md.
@Injectable({ providedIn: 'root' })
export class WorkspaceApi {
  private http = inject(HttpClient);
  private account = inject(AccountApi);
  load() {
    return firstValueFrom(this.http.get<WorkspaceData>('/api/workspace').pipe(timeout(15000), tap(data => this.account.displayName.set(data.profile.name))));
  }
  save(collection: string, body: unknown) {
    return firstValueFrom(
      this.http.post('/api/workspace/' + collection, body).pipe(timeout(15000), tap(() => {
        if (collection === 'profile' && body && typeof body === 'object' && 'name' in body && typeof body.name === 'string')
          this.account.displayName.set(body.name.trim());
      })),
    );
  }
  remove(collection: string, id: string) {
    return firstValueFrom(
      this.http
        .delete('/api/workspace/' + collection + '/' + encodeURIComponent(id))
        .pipe(timeout(15000)),
    );
  }
}
