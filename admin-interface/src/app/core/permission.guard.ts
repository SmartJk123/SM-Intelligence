import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateChildFn, Router } from '@angular/router';
import { NAV, Page } from './data';
import { StaffAccessService } from './staff-access.service';

/**
 * Keeps a staff member to the pages their role allows. A page they cannot open
 * sends them to the first page they can (the sidebar hides the others).
 */
export const permissionGuard: CanActivateChildFn = async (route: ActivatedRouteSnapshot) => {
  const access = inject(StaffAccessService);
  const router = inject(Router);
  const page = route.routeConfig?.path as Page | undefined;
  if (!page || !NAV.some((item) => item.id === page)) {
    return true;
  }
  await access.load();
  if (access.can(page)) {
    return true;
  }
  const first = NAV.find((item) => access.can(item.id));
  return first ? router.createUrlTree(['/admin', first.id]) : router.createUrlTree(['/admin/login']);
};
