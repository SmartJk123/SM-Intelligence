import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AccountApi } from './account-api';
export const setupGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession()))
    return router.createUrlTree(['/login']);
  return api.setupCompleted() ? router.createUrlTree(['/dashboard']) : true;
};
export const dashboardGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession()))
    return router.createUrlTree(['/login']);
  return api.setupCompleted() ? true : router.createUrlTree(['/setup']);
};
export const entryGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  if (!api.authenticated() && !(await api.restoreSession())) return true;
  return router.createUrlTree([api.setupCompleted() ? '/dashboard' : '/setup']);
};
