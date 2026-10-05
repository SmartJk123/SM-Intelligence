import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_ORGANIZATIONS_URL } from './api.config';
import {
  CreateOrganizationRequest,
  InviteMemberRequest,
  OrganizationGateway,
  OrganizationMemberSummary,
  OrganizationSummary,
} from './organization.gateway';

/**
 * Live implementation of the organization gateway.
 *
 * Everything goes through identity-service, because account creation and the
 * set-password email must never happen in the browser. The admin's token is
 * added by bankServiceAuthInterceptor.
 */
@Injectable({ providedIn: 'root' })
export class HttpOrganizationGateway implements OrganizationGateway {
  private readonly http = inject(HttpClient);

  async loadOrganizations(): Promise<OrganizationSummary[]> {
    return firstValueFrom(
      this.http.get<OrganizationSummary[]>(IDENTITY_ORGANIZATIONS_URL),
    );
  }

  async loadMembers(organizationId: string): Promise<OrganizationMemberSummary[]> {
    return firstValueFrom(
      this.http.get<OrganizationMemberSummary[]>(
        `${IDENTITY_ORGANIZATIONS_URL}/${organizationId}/members`,
      ),
    );
  }

  async createOrganization(request: CreateOrganizationRequest): Promise<OrganizationSummary> {
    return firstValueFrom(
      this.http.post<OrganizationSummary>(IDENTITY_ORGANIZATIONS_URL, request),
    );
  }

  async inviteMember(
    organizationId: string,
    request: InviteMemberRequest,
  ): Promise<OrganizationMemberSummary> {
    return firstValueFrom(
      this.http.post<OrganizationMemberSummary>(
        `${IDENTITY_ORGANIZATIONS_URL}/${organizationId}/members`,
        request,
      ),
    );
  }
}
