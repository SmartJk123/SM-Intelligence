import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL } from './api.config';
import { AuthService } from './auth.service';

/**
 * A 401 on a call that carried the admin's token means the session ended
 * (expired, or the account lost admin access) while a page was open. Send the
 * operator back to sign in rather than leaving every screen failing.
 *
 * A 401 from the bank integration service is left to the page instead: it
 * usually means that service was started without identity-service's
 * JWT_SECRET, and signing out would not fix it.
 */
export const sessionExpiryInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && request.headers.has('Authorization')
        && !request.url.startsWith(API_BASE_URL)) {
        auth.logout();
        void router.navigate(['/admin', 'login']);
      }
      return throwError(() => error);
    }),
  );
};
