export type OrganizationApiStatus = 'ACTIVE' | 'SUSPENDED' | 'DELETED';
export type OrganizationMemberRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

/** Organization as identity-service reports it. */
export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
  status: OrganizationApiStatus;
  businessType: string | null;
  memberCount: number;
  createdAt: string;
  /** Only present on a create response: whether the owner's credentials email sent. */
  emailSent?: boolean;
}

export interface OrganizationMemberSummary {
  userId: string;
  name: string;
  email: string;
  role: OrganizationMemberRole;
  memberStatus: 'ACTIVE' | 'INACTIVE';
  joinedAt: string;
  emailSent: boolean;
}

export interface CreateOrganizationRequest {
  organizationName: string;
  businessType: string;
  /**
   * Optional, and only together. Leave both out to save the organisation alone and
   * invite the owner later. Give both to create the owner account and email it now.
   */
  ownerName?: string;
  ownerEmail?: string;
}

export interface InviteMemberRequest {
  name: string;
  email: string;
  role: OrganizationMemberRole;
}

/**
 * Boundary between the admin interface and identity-service's organization
 * endpoints. There is no simulated implementation: an unreachable backend is
 * reported as unreachable rather than invented, same as the bank-integration
 * gateway.
 */
export interface OrganizationGateway {
  loadOrganizations(): Promise<OrganizationSummary[]>;
  loadMembers(organizationId: string): Promise<OrganizationMemberSummary[]>;
  createOrganization(request: CreateOrganizationRequest): Promise<OrganizationSummary>;
  inviteMember(organizationId: string, request: InviteMemberRequest): Promise<OrganizationMemberSummary>;
}
