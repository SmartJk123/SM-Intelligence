import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AccountApi } from './account-api';
export const setupGuard: CanActivateFn = async () => {
  const api = inject(AccountApi);
  const router = inject(Router);
  return api.authenticated() || (await api.restoreSession()) || router.createUrlTree(['/login']);
};
