import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_AUTH_BASE_URL } from './api.config';

const TOKEN_STORAGE_KEY = 'smi_admin_token';

interface LoginResponse {
  token: string;
  role?: string;
  name?: string;
  emailAddress?: string;
}

/**
 * Real admin authentication against identity-service. A customer account
 * (role !== PLATFORM_ADMIN) is refused here, in the browser, even though the
 * backend also enforces it on every admin API call — this is what lets the
 * login screen show a clear reason instead of a generic failed request.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly tokenState = signal<string | null>(
    typeof localStorage !== 'undefined' ? localStorage.getItem(TOKEN_STORAGE_KEY) : null,
  );

  readonly isAuthenticated = () => this.tokenState() !== null;
  readonly token = this.tokenState.asReadonly();

  /** @throws Error with a message safe to show the operator. */
  async login(email: string, password: string): Promise<void> {
    let response: LoginResponse;
    try {
      response = await firstValueFrom(
        this.http.post<LoginResponse>(`${IDENTITY_AUTH_BASE_URL}/login`, {
          emailAddress: email,
          password,
        }),
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 400)) {
        throw new Error('Incorrect email address or password.');
      }
      if (error instanceof HttpErrorResponse && error.status === 403) {
        throw new Error('This account has been suspended. Contact support to restore access.');
      }
      throw new Error('Could not reach the identity service. Confirm it is running and try again.');
    }
    if (response.role !== 'PLATFORM_ADMIN') {
      throw new Error('This account is not an administrator of this platform.');
    }
    this.tokenState.set(response.token);
    try {
      localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
    } catch {
      // Storage may be unavailable (private browsing); the session still works for this tab.
    }
  }

  logout(): void {
    this.tokenState.set(null);
    try {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    } catch {
      // Nothing to clean up if storage was never available.
    }
  }
}
