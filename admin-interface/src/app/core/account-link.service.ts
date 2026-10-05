import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from './api.config';

/** A bank account assigned to a customer, as the bank integration service reports it. */
export interface AccountLink {
  id: number;
  bankId: string;
  accountNumber: string;
  userId: string;
  accountId: string;
  accountName: string;
  createdAt: string;
  /** Movements on this account not yet on the customer's dashboard. */
  pendingDeliveries: number;
  lastError: string | null;
}

export interface LinkAccountRequest {
  bankId: string;
  accountNumber: string;
  userId: string;
  accountName: string | null;
}

/** The banks an account can be linked for. */
export const LINKABLE_BANKS = [
  { id: 'ncba', name: 'NCBA' },
  { id: 'kcb', name: 'KCB' },
  { id: 'stanbic', name: 'Stanbic' },
  { id: 'equity', name: 'Equity' },
];

const LINKS_URL = `${API_BASE_URL}/admin/account-links`;

/**
 * Links a customer's bank account so its movements appear on their web
 * dashboard. The bank integration service creates the dashboard account,
 * delivers every movement already received for it, and every later one.
 */
@Injectable({ providedIn: 'root' })
export class AccountLinkService {
  private readonly http = inject(HttpClient);

  /** Every linked account across every customer when userId is omitted. */
  list(userId?: string): Promise<AccountLink[]> {
    return firstValueFrom(
      this.http.get<AccountLink[]>(LINKS_URL, { params: userId ? { userId } : {} }),
    );
  }

  link(request: LinkAccountRequest): Promise<AccountLink> {
    return firstValueFrom(this.http.post<AccountLink>(LINKS_URL, request));
  }

  sync(id: number): Promise<AccountLink> {
    return firstValueFrom(this.http.post<AccountLink>(`${LINKS_URL}/${id}/sync`, {}));
  }

  unlink(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${LINKS_URL}/${id}`));
  }
}
