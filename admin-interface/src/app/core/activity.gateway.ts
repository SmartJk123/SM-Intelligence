import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';

/** One entry of identity-service's audit trail. */
export interface AuditEventDto {
  id: string;
  occurredAt: string;
  actorId: string | null;
  actorEmail: string | null;
  /** CUSTOMER, STAFF or SYSTEM. */
  actorRole: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  targetLabel: string | null;
  detail: string | null;
  ipAddress: string | null;
  /** SUCCESS or FAILURE. */
  outcome: string;
}

export interface AdminNotificationDto {
  id: string;
  createdAt: string;
  category: string;
  /** INFO, WARNING or CRITICAL. */
  severity: string;
  title: string;
  message: string;
  read: boolean;
}

export interface OutboxEmailDto {
  id: string;
  createdAt: string;
  recipient: string;
  subject: string;
  /** Links and temporary passwords appear as [hidden]. */
  body: string;
  templateKey: string | null;
  /** SENT, FAILED or NOT_CONFIGURED. */
  status: string;
  error: string | null;
}

export interface EmailTemplateDto {
  key: string;
  name: string;
  description: string;
  subject: string;
  body: string;
  placeholders: string[];
  customised: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
}

/** Customer added from the admin portal (identity-service POST /api/v1/admin/users). */
export interface CreateCustomerRequest {
  name: string;
  email: string;
  phoneNumber?: string | null;
  accountType: 'INDIVIDUAL' | 'ORGANIZATION';
  organizationId?: string | null;
  newOrganizationName?: string | null;
  businessType?: string | null;
  role?: string | null;
}

export interface CreateCustomerResult {
  user: { id: string; name: string; emailAddress: string; accountType: string };
  emailSent: boolean;
  organizationId: string | null;
  organizationName: string | null;
}

const ADMIN_URL = `${IDENTITY_API_BASE_URL}/admin`;

/**
 * identity-service's admin activity API, reached through api-gateway. The token is
 * added here, as in HttpUserGateway, because the shared interceptor only covers
 * the bank and organisation addresses.
 */
@Injectable({ providedIn: 'root' })
export class ActivityGateway {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private get headers() {
    return { Authorization: `Bearer ${this.auth.token()}` };
  }

  auditEvents(limit = 500): Promise<AuditEventDto[]> {
    return firstValueFrom(this.http.get<AuditEventDto[]>(`${ADMIN_URL}/audit-events`, {
      headers: this.headers,
      params: { limit },
    }));
  }

  notifications(limit = 200): Promise<AdminNotificationDto[]> {
    return firstValueFrom(this.http.get<AdminNotificationDto[]>(`${ADMIN_URL}/notifications`, {
      headers: this.headers,
      params: { limit },
    }));
  }

  markRead(id: string): Promise<AdminNotificationDto> {
    return firstValueFrom(this.http.post<AdminNotificationDto>(`${ADMIN_URL}/notifications/${id}/read`, null, {
      headers: this.headers,
    }));
  }

  markAllRead(): Promise<unknown> {
    return firstValueFrom(this.http.post(`${ADMIN_URL}/notifications/read-all`, null, { headers: this.headers }));
  }

  outbox(limit = 200): Promise<OutboxEmailDto[]> {
    return firstValueFrom(this.http.get<OutboxEmailDto[]>(`${ADMIN_URL}/email-outbox`, {
      headers: this.headers,
      params: { limit },
    }));
  }

  templates(): Promise<EmailTemplateDto[]> {
    return firstValueFrom(this.http.get<EmailTemplateDto[]>(`${ADMIN_URL}/email-templates`, { headers: this.headers }));
  }

  updateTemplate(key: string, subject: string, body: string): Promise<EmailTemplateDto> {
    return firstValueFrom(this.http.put<EmailTemplateDto>(`${ADMIN_URL}/email-templates/${key}`, { subject, body }, {
      headers: this.headers,
    }));
  }

  resetTemplate(key: string): Promise<EmailTemplateDto> {
    return firstValueFrom(this.http.post<EmailTemplateDto>(`${ADMIN_URL}/email-templates/${key}/reset`, null, {
      headers: this.headers,
    }));
  }

  createCustomer(request: CreateCustomerRequest): Promise<CreateCustomerResult> {
    return firstValueFrom(this.http.post<CreateCustomerResult>(`${ADMIN_URL}/users`, request, { headers: this.headers }));
  }
}
