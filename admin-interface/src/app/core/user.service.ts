import { Injectable, inject, signal } from '@angular/core';
import { AppUser, Organisation } from './data';
import { HttpUserGateway } from './http-user.gateway';
import { UserSummary } from './user.gateway';

export type { UserSummary } from './user.gateway';

/**
 * Single source of truth for registered users, backed by identity-service.
 *
 * A signup on the customer-facing web app is a row in identity-service's
 * users table. This service reads that table so it shows up here without any
 * separate "sync" step. Organisations are derived from it: identity-service
 * has no separate Organization/membership entity yet, so an "organisation" is
 * however many ORGANIZATION-type signups share the same organisation name.
 * Two people typing the same company name become one row; a typo produces a
 * second one. That is a real limitation of today's data model, not a bug in
 * this grouping.
 */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly gateway = inject(HttpUserGateway);

  private readonly usersState = signal<UserSummary[]>([]);
  private readonly loadedState = signal(false);

  /** False until the backend has answered once. */
  readonly loaded = this.loadedState.asReadonly();

  constructor() {
    void this.loadUsers();
  }

  async loadUsers(): Promise<void> {
    try {
      this.usersState.set(await this.gateway.loadUsers());
    } catch {
      // Backend not reachable. The list stays as it was rather than being invented.
    } finally {
      this.loadedState.set(true);
    }
  }

  readonly users = () => this.usersState().map(toAppUser);

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
}

function toAppUser(user: UserSummary): AppUser {
  return {
    id: user.id,
    name: user.name,
    email: user.emailAddress,
    org: user.organizationName ?? '—',
    // Placeholder: identity-service has no per-user role/RBAC yet.
    role: user.accountType === 'ORGANIZATION' ? 'Owner' : 'Individual',
    // Placeholder: this listing only ever contains active (non-deleted) users,
    // and identity-service has no "Suspended" concept yet.
    status: 'Active',
    // Placeholder: identity-service does not record login timestamps yet.
    lastLogin: 'Never',
  };
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
