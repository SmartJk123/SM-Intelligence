import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_API_BASE_URL, IDENTITY_AUTH_BASE_URL } from './api.config';
import { AuthService } from './auth.service';
import { IdentityUserStatus, UpdateUserRequest, UserGateway, UserSummary } from './user.gateway';

/** identity-service serves admin user management under /api/v1/admin/users. */
const USERS_URL = `${IDENTITY_API_BASE_URL}/admin/users`;

/**
 * Live implementation of the user gateway. There is no simulated mode: an
 * unreachable identity-service is reported as unreachable, not invented.
 * Every call carries the admin's bearer token, since identity-service
 * requires an active PLATFORM_ADMIN account for this whole API.
 */
@Injectable({ providedIn: 'root' })
export class HttpUserGateway implements UserGateway {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private get headers() {
    return { Authorization: `Bearer ${this.auth.token()}` };
  }

  async loadUsers(): Promise<UserSummary[]> {
    return firstValueFrom(this.http.get<UserSummary[]>(USERS_URL, { headers: this.headers }));
  }

  async updateUser(id: string, request: UpdateUserRequest): Promise<UserSummary> {
    return firstValueFrom(this.http.put<UserSummary>(`${USERS_URL}/${encodeURIComponent(id)}`, request, { headers: this.headers }));
  }

  async sendPasswordReset(emailAddress: string): Promise<void> {
    await firstValueFrom(this.http.post(`${IDENTITY_AUTH_BASE_URL}/forgot-password`, { emailAddress }));
  }

  async changeStatus(id: string, status: IdentityUserStatus): Promise<UserSummary> {
    return firstValueFrom(
      this.http.patch<UserSummary>(`${USERS_URL}/${encodeURIComponent(id)}/status`, { status }, { headers: this.headers }),
    );
  }
}
