import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IDENTITY_AUTH_BASE_URL } from './api.config';

const TOKEN_STORAGE_KEY = 'smi_admin_token';

/** The signed-in administrator, read from the identity-service JWT's claims. */
export interface AdminSession {
  name: string;
  email: string;
  /** Token expiry, epoch milliseconds. */
  expiresAt: number;
}

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

  private readonly tokenState = signal<string | null>(readStoredToken());

  readonly token = this.tokenState.asReadonly();
  readonly session = computed(() => {
    const token = this.tokenState();
    return token ? decodeSession(token) : null;
  });

  /**
   * False once the token has expired (24 hours after sign-in), not only after
   * an explicit logout. Without the expiry check a stale token kept the login
   * guard redirecting to a dashboard whose every call failed with 401.
   */
  readonly isAuthenticated = (): boolean => {
    const session = this.session();
    if (session && session.expiresAt > Date.now()) return true;
    if (this.tokenState() !== null) this.logout();
    return false;
  };

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
    if (!decodeSession(response.token)) {
      throw new Error('The identity service returned an unreadable session. Please try again.');
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

function readStoredToken(): string | null {
  try {
    return typeof localStorage !== 'undefined' ? localStorage.getItem(TOKEN_STORAGE_KEY) : null;
  } catch {
    return null;
  }
}

/** Reads the claims identity-service puts in its JWT; null when the token is malformed. */
function decodeSession(token: string): AdminSession | null {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(payload), (char) => char.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes)) as { name?: string; email?: string; exp?: number };
    if (typeof claims.exp !== 'number') return null;
    return { name: claims.name?.trim() ?? '', email: claims.email ?? '', expiresAt: claims.exp * 1000 };
  } catch {
    return null;
  }
}
