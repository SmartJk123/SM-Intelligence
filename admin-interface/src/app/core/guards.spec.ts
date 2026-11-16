import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { loginGuard } from './login.guard';
import { permissionGuard } from './permission.guard';
import { AuthService } from './auth.service';
import { StaffAccessService } from './staff-access.service';
import { Page } from './data';

const state = {} as RouterStateSnapshot;
const routeTo = (path: string) => ({ routeConfig: { path } }) as unknown as ActivatedRouteSnapshot;
const urlOf = (result: unknown) => TestBed.inject(Router).serializeUrl(result as UrlTree);
const run = <T>(guard: () => T) => TestBed.runInInjectionContext(guard);

function withAuth(signedIn: boolean) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), { provide: AuthService, useValue: { isAuthenticated: () => signedIn } }],
  });
}

function withPages(allowed: Page[]) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: StaffAccessService,
        useValue: { load: vi.fn().mockResolvedValue(null), can: (page: Page) => allowed.includes(page) },
      },
    ],
  });
}

describe('authGuard', () => {
  it('lets a signed-in admin through', () => {
    withAuth(true);
    expect(run(() => authGuard(routeTo('dashboard'), state))).toBe(true);
  });

  it('sends everyone else to the login page', () => {
    withAuth(false);
    expect(urlOf(run(() => authGuard(routeTo('dashboard'), state)))).toBe('/admin/login');
  });
});

describe('loginGuard', () => {
  it('shows the login page when signed out', () => {
    withAuth(false);
    expect(run(() => loginGuard(routeTo('login'), state))).toBe(true);
  });

  it('sends a signed-in admin straight to the dashboard', () => {
    withAuth(true);
    expect(urlOf(run(() => loginGuard(routeTo('login'), state)))).toBe('/admin/dashboard');
  });
});

describe('permissionGuard', () => {
  it('opens a page the staff role allows', async () => {
    withPages(['dashboard', 'audit-logs']);
    expect(await run(() => permissionGuard(routeTo('audit-logs'), state))).toBe(true);
  });

  it('redirects a page the role lacks to the first page it can open', async () => {
    withPages(['users', 'reports']);
    expect(urlOf(await run(() => permissionGuard(routeTo('settings'), state)))).toBe('/admin/users');
  });

  it('sends a role with no pages at all back to login', async () => {
    withPages([]);
    expect(urlOf(await run(() => permissionGuard(routeTo('settings'), state)))).toBe('/admin/login');
  });

  it('does not check routes that are not admin pages, such as a detail page', async () => {
    withPages([]);
    expect(await run(() => permissionGuard(routeTo('users/:id'), state))).toBe(true);
    expect(TestBed.inject(StaffAccessService).load).not.toHaveBeenCalled();
  });
});
