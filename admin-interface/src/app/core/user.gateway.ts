export type IdentityAccountType = 'INDIVIDUAL' | 'ORGANIZATION';

/** A registered user as identity-service reports it. */
export interface UserSummary {
  id: string;
  name: string;
  emailAddress: string;
  phoneNumber: string | null;
  accountType: IdentityAccountType;
  organizationName: string | null;
  businessType: string | null;
  industry: string | null;
  createdAt: string;
}

/**
 * Boundary between the admin interface and identity-service's user listing.
 * Read-only: creating/editing users happens through registration, not here.
 */
export interface UserGateway {
  loadUsers(): Promise<UserSummary[]>;
}
