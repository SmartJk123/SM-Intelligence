import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL, IDENTITY_ORGANIZATIONS_URL } from './api.config';
import { AuthService } from './auth.service';

/**
 * Sends the admin's identity-service token to the bank integration service
 * (verified there with the JWT_SECRET it shares with identity-service) and to
 * identity-service's organisations API. Only those addresses get the token, so
 * it never leaks to another host.
 */
export const bankServiceAuthInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthService).token();
  const ours = request.url.startsWith(API_BASE_URL) || request.url.startsWith(IDENTITY_ORGANIZATIONS_URL);
  if (!token || !ours) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
