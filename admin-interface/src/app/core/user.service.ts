import { Injectable, inject, signal } from '@angular/core';
import { AppUser, Organisation } from './data';
import { HttpUserGateway } from './http-user.gateway';
import { IdentityUserStatus, UpdateUserRequest, UserSummary } from './user.gateway';

export type { UpdateUserRequest, UserSummary } from './user.gateway';

/**
 * Single source of truth for registered users, backed by identity-service.
 *
 * A signup on the customer-facing web app is a row in identity-service's
 * users table, and so is every change made here: suspending an account stops
 * that user signing in to the web app, and restoring it lets them back in.
 * Organisations created in admin come from organization.service.ts; companies
 * that signed up on the web are grouped here by organizationName.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly gateway = inject(HttpUserGateway);

  private readonly usersState = signal<UserSummary[]>([]);
  private readonly loadedState = signal(false);
  private readonly errorState = signal<string | null>(null);

  /** False until the backend has answered once. */
  readonly loaded = this.loadedState.asReadonly();
  /** Why the last load failed, or null. */
  readonly loadError = this.errorState.asReadonly();

  constructor() {
    void this.loadUsers();
  }

  async loadUsers(): Promise<void> {
    try {
      this.usersState.set(await this.gateway.loadUsers());
      this.errorState.set(null);
    } catch {
      // The list stays as it was rather than being invented.
      this.errorState.set('Users could not be loaded. Check that identity-service is running.');
    } finally {
      this.loadedState.set(true);
    }
  }

  readonly users = () => this.usersState().map(toAppUser);

  /** The full record behind a table row, for the detail panel. */
  summary(id: string): UserSummary | undefined {
    return this.usersState().find((user) => user.id === id);
  }

  async updateUser(id: string, request: UpdateUserRequest): Promise<UserSummary> {
    return this.replace(await this.gateway.updateUser(id, request));
  }

  /** Asks identity-service to email the user a reset link. It answers the same whether or not mail is set up. */
  async sendPasswordReset(emailAddress: string): Promise<void> {
    await this.gateway.sendPasswordReset(emailAddress);
  }

  async changeStatus(id: string, status: IdentityUserStatus): Promise<UserSummary> {
    return this.replace(await this.gateway.changeStatus(id, status));
  }

  readonly organisations = () => {
    const groups = new Map<string, UserSummary[]>();
    for (const user of this.usersState()) {
      if (user.accountType !== 'ORGANIZATION' || !user.organizationName) {
        continue;
      }
      const key = user.organizationName.trim().toLowerCase();
      const group = groups.get(key) ?? [];
      group.push(user);
      groups.set(key, group);
    }
    return [...groups.values()].map(toOrganisation);
  };

  private replace(updated: UserSummary): UserSummary {
    this.usersState.update((users) => users.map((user) => (user.id === updated.id ? updated : user)));
    return updated;
  }
}

function toAppUser(user: UserSummary): AppUser {
  return {
    id: user.id,
    name: user.name,
    email: user.emailAddress,
    org: user.organizationName ?? '—',
    role:
      user.role === 'PLATFORM_ADMIN'
        ? 'Platform admin'
        : user.accountType === 'ORGANIZATION'
          ? 'Organisation'
          : 'Individual',
    status: user.status === 'SUSPENDED' ? 'Suspended' : 'Active',
    lastLogin: formatLastLogin(user.lastLoginAt),
  };
}

function formatLastLogin(value: string | null): string {
  if (!value) return 'Never';
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? 'Never'
    : date.toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'short' });
}

function toOrganisation(members: UserSummary[]): Organisation {
  const first = members[0];
  const earliest = members.reduce(
    (min, m) => (m.createdAt < min ? m.createdAt : min),
    first.createdAt,
  );
  return {
    id: first.id,
    name: first.organizationName ?? first.name,
    type: first.businessType ?? '—',
    users: members.length,
    // No linkage to accounts-service yet, so this can't be a real count.
    accounts: 0,
    status: 'Active',
    joined: new Date(earliest).toLocaleDateString('en-KE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }),
  };
}
