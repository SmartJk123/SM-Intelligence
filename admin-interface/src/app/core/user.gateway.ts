export type IdentityAccountType = 'INDIVIDUAL' | 'ORGANIZATION';
export type IdentityUserStatus = 'ACTIVE' | 'SUSPENDED';
export type IdentityUserRole = 'USER' | 'PLATFORM_ADMIN';

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
  status: IdentityUserStatus;
  role: IdentityUserRole;
  isEmailVerified: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Profile fields an administrator may correct. Email, account type and role are not editable. */
export interface UpdateUserRequest {
  name: string;
  phoneNumber: string | null;
  organizationName: string | null;
  businessType: string | null;
  industry: string | null;
}

/**
 * Boundary between the admin interface and identity-service's user management.
 * These are the same accounts the customer web app signs in with, so a change
 * here reaches that user on their next request.
 */
export interface UserGateway {
  loadUsers(): Promise<UserSummary[]>;
  updateUser(id: string, request: UpdateUserRequest): Promise<UserSummary>;
  changeStatus(id: string, status: IdentityUserStatus): Promise<UserSummary>;
  /** Emails the user a one-time link to choose a new password, as "Forgot password?" does. */
  sendPasswordReset(emailAddress: string): Promise<void>;
}
