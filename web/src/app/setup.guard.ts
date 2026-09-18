// Route guards for login, setup and dashboard access.
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AccountApi } from './account-api';

// Update carefully when authentication flow changes.
export const setupGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession()))
    return router.createUrlTree(['/login']);
  if (api.sampleMode) return router.createUrlTree(['/auth-check']);
  return api.setupCompleted() ? router.createUrlTree(['/dashboard']) : true;
};
export const dashboardGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession()))
    return router.createUrlTree(['/login']);
  if (api.sampleMode) return router.createUrlTree(['/auth-check']);
  // An empty workspace must remain accessible after login and from the navbar.
  return true;
};
export const entryGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession())) return true;
  if (api.sampleMode) return router.createUrlTree(['/auth-check']);
  return router.createUrlTree(['/dashboard']);
};

export const sampleGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.sampleMode) return router.createUrlTree(['/login']);
  return api.authenticated() || await api.restoreSession() || router.createUrlTree(['/login']);
};
