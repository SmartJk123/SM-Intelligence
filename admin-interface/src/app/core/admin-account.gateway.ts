import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_API_BASE_URL, IDENTITY_AUTH_BASE_URL } from './api.config';
import { AuthService } from './auth.service';

export interface StaffAccess {
  fullAccess: boolean;
  roleName: string;
  permissions: string[];
  allPermissions: { key: string; label: string }[];
}

export interface StaffMember {
  id: string;
  name: string;
  email: string;
  roleId: string | null;
  roleName: string;
  fullAccess: boolean;
  /** Listed in ADMIN_EMAILS: always full access, cannot be changed here. */
  fromAdminEmails: boolean;
  status: string;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface StaffRole {
  id: string;
  name: string;
  description: string | null;
  permissions: string[];
  staffCount: number;
  updatedAt: string;
}

export interface SettingsSection {
  section: string;
  values: Record<string, string | number | boolean>;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface AdminSession {
  id: string;
  device: string;
  userAgent: string | null;
  ipAddress: string | null;
  signedInAt: string;
  current: boolean;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
  backupCodes: string[];
}

export interface AdminProfileDto {
  id: string;
  name: string;
  emailAddress: string;
  phoneNumber: string | null;
  mfaEnabled: boolean;
}

const ADMIN_URL = `${IDENTITY_API_BASE_URL}/admin`;

/**
 * identity-service calls for Settings: staff, roles, saved settings, and the
 * signed-in administrator's own profile, password, two-factor and sessions.
 * The token is added here, as in HttpUserGateway.
 */
@Injectable({ providedIn: 'root' })
export class AdminAccountGateway {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private get headers() {
    return { Authorization: `Bearer ${this.auth.token()}` };
  }

  private get<T>(url: string): Promise<T> {
    return firstValueFrom(this.http.get<T>(url, { headers: this.headers }));
  }

  private send<T>(method: 'POST' | 'PUT' | 'PATCH' | 'DELETE', url: string, body?: unknown): Promise<T> {
    return firstValueFrom(this.http.request<T>(method, url, { headers: this.headers, body }));
  }

  // Staff and roles
  access = () => this.get<StaffAccess>(`${ADMIN_URL}/staff/me`);
  staff = () => this.get<StaffMember[]>(`${ADMIN_URL}/staff`);
  addStaff = (name: string, email: string, roleId: string) =>
    this.send<{ staff: StaffMember; emailSent: boolean; existingAccount: boolean }>('POST', `${ADMIN_URL}/staff`, { name, email, roleId });
  changeStaffRole = (id: string, roleId: string) => this.send<StaffMember>('PATCH', `${ADMIN_URL}/staff/${id}`, { roleId });
  removeStaff = (id: string) => this.send<void>('DELETE', `${ADMIN_URL}/staff/${id}`);
  roles = () => this.get<StaffRole[]>(`${ADMIN_URL}/staff-roles`);
  saveRole = (role: { id?: string; name: string; description: string; permissions: string[] }) =>
    role.id
      ? this.send<StaffRole>('PUT', `${ADMIN_URL}/staff-roles/${role.id}`, role)
      : this.send<StaffRole>('POST', `${ADMIN_URL}/staff-roles`, role);
  deleteRole = (id: string) => this.send<void>('DELETE', `${ADMIN_URL}/staff-roles/${id}`);

  // Saved settings
  settings = () => this.get<Record<string, SettingsSection>>(`${ADMIN_URL}/settings`);
  saveSettings = (section: string, values: Record<string, unknown>) =>
    this.send<SettingsSection>('PUT', `${ADMIN_URL}/settings/${section}`, values);

  // The signed-in administrator
  me = () => this.get<AdminProfileDto>(`${IDENTITY_AUTH_BASE_URL}/me`);
  updateMe = (name: string, phoneNumber: string | null) =>
    this.send<AdminProfileDto>('PUT', `${IDENTITY_AUTH_BASE_URL}/me`, { name, phoneNumber });
  changePassword = (currentPassword: string, newPassword: string) =>
    this.send<unknown>('POST', `${IDENTITY_AUTH_BASE_URL}/change-password`, { currentPassword, newPassword });
  sessions = () => this.get<AdminSession[]>(`${IDENTITY_AUTH_BASE_URL}/sessions`);
  revokeSession = (id: string) => this.send<void>('DELETE', `${IDENTITY_AUTH_BASE_URL}/sessions/${id}`);
  mfaSetup = () => this.send<MfaSetup>('POST', `${IDENTITY_AUTH_BASE_URL}/mfa/setup`);
  mfaEnable = (code: string) => this.send<unknown>('POST', `${IDENTITY_AUTH_BASE_URL}/mfa/enable`, { code });
  mfaDisable = (password: string, code: string) =>
    this.send<unknown>('POST', `${IDENTITY_AUTH_BASE_URL}/mfa/disable`, { password, code });
}
