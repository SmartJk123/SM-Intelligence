import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';
import { UserGateway, UserSummary } from './user.gateway';

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
    return firstValueFrom(
      this.http.get<UserSummary[]>(`${IDENTITY_API_BASE_URL}/admin/users`, { headers: this.headers }),
    );
  }
}
