import { Injectable, inject, signal } from '@angular/core';
import { Organisation, OrgStatus } from './data';
import {
  CreateOrganizationRequest,
  InviteMemberRequest,
  OrganizationMemberSummary,
  OrganizationSummary,
} from './organization.gateway';
import { HttpOrganizationGateway } from './http-organization.gateway';

export type { OrganizationMemberSummary, OrganizationSummary } from './organization.gateway';

const STATUS_MAP: Record<string, OrgStatus> = {
  ACTIVE: 'Active',
  SUSPENDED: 'Suspended',
  DELETED: 'Suspended',
};

/**
 * Single source of truth for organizations, backed by identity-service.
 *
 * The Organisations page reads this instead of a static array, so an
 * organisation created through the invite modal shows up without a reload,
 * and a refresh reflects what the backend actually stored.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly gateway = inject(HttpOrganizationGateway);

  private readonly orgsState = signal<Organisation[]>([]);
  private readonly loadedState = signal(false);

  readonly organisations = this.orgsState.asReadonly();
  /** False until the backend has answered once, so the table can show a loading state. */
  readonly loaded = this.loadedState.asReadonly();

  constructor() {
    void this.loadOrganizations();
  }

  async loadOrganizations(): Promise<void> {
    try {
      const orgs = await this.gateway.loadOrganizations();
      this.orgsState.set(orgs.map(toOrganisation));
    } catch {
      // Backend not reachable. The list stays as it was rather than being invented.
    } finally {
      this.loadedState.set(true);
    }
  }

  async loadMembers(organizationId: string): Promise<OrganizationMemberSummary[]> {
    return this.gateway.loadMembers(organizationId);
  }

  /** Creates an organisation and its owner account, then reloads the list. */
  async createOrganization(request: CreateOrganizationRequest): Promise<OrganizationSummary> {
    const created = await this.gateway.createOrganization(request);
    await this.loadOrganizations();
    return created;
  }

  /** Invites a member into an existing organisation, then reloads the list. */
  async inviteMember(
    organizationId: string,
    request: InviteMemberRequest,
  ): Promise<OrganizationMemberSummary> {
    const member = await this.gateway.inviteMember(organizationId, request);
    await this.loadOrganizations();
    return member;
  }
}

function toOrganisation(summary: OrganizationSummary): Organisation {
  return {
    id: summary.id,
    name: summary.name,
    type: summary.businessType ?? '—',
    users: summary.memberCount,
    accounts: 0,
    status: STATUS_MAP[summary.status] ?? 'Active',
    joined: new Date(summary.createdAt).toLocaleDateString('en-KE', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }),
  };
}
