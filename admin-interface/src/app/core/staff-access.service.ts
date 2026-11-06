import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { AuthService } from './auth.service';
import { AdminAccountGateway, StaffAccess } from './admin-account.gateway';
import { Page } from './data';

/** The permission each admin page needs (identity-service StaffPermissions). */
export const PAGE_PERMISSION: Record<Page, string> = {
  dashboard: 'dashboard',
  organisations: 'organisations',
  users: 'customers',
  'bank-integrations': 'bank-integrations',
  'bank-accounts': 'bank-accounts',
  transactions: 'transactions',
  reconciliation: 'reconciliation',
  notifications: 'notifications',
  'audit-logs': 'audit-logs',
  reports: 'reports',
  settings: 'settings',
};

/**
 * What the signed-in administrator's staff role allows. Pages the role lacks are
 * hidden from the sidebar and refused by the route guard; identity-service also
 * refuses its own endpoints for them. Until the answer arrives, nothing is hidden.
 */
@Injectable({ providedIn: 'root' })
export class StaffAccessService {
  private readonly gateway = inject(AdminAccountGateway);
  private readonly state = signal<StaffAccess | null>(null);
  private loading: Promise<StaffAccess | null> | null = null;

  readonly access = this.state.asReadonly();
  readonly roleName = computed(() => this.state()?.roleName ?? '');

  constructor() {
    // A different administrator (or none) after sign in or sign out: ask again.
    const auth = inject(AuthService);
    let lastToken = auth.token();
    effect(() => {
      const token = auth.token();
      if (token !== lastToken) {
        lastToken = token;
        untracked(() => this.reset());
      }
    });
  }

  /** Loads once per sign in; call reset() on sign out. */
  load(): Promise<StaffAccess | null> {
    this.loading ??= this.gateway
      .access()
      .then((access) => {
        this.state.set(access);
        return access;
      })
      .catch(() => {
        // An older identity-service without staff roles: behave as before (full access).
        this.loading = null;
        return null;
      });
    return this.loading;
  }

  reset(): void {
    this.state.set(null);
    this.loading = null;
  }

  can(page: Page): boolean {
    const access = this.state();
    return !access || access.fullAccess || access.permissions.includes(PAGE_PERMISSION[page]);
  }
}
