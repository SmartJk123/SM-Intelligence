import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_API_BASE_URL } from './api.config';
import { UserGateway, UserSummary } from './user.gateway';

/**
 * Live implementation of the user gateway. There is no simulated mode: an
 * unreachable identity-service is reported as unreachable, not invented.
 */
@Injectable({ providedIn: 'root' })
export class HttpUserGateway implements UserGateway {
  private readonly http = inject(HttpClient);

  async loadUsers(): Promise<UserSummary[]> {
    return firstValueFrom(
      this.http.get<UserSummary[]>(`${IDENTITY_API_BASE_URL}/admin/users`),
    );
  }
}
