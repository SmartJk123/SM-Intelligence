import { TestBed } from '@angular/core/testing';
import { WritableSignal, signal } from '@angular/core';
import { AdminAccountGateway, StaffAccess } from './admin-account.gateway';
import { AuthService } from './auth.service';
import { PAGE_PERMISSION, StaffAccessService } from './staff-access.service';
import { NAV } from './data';

const role = (permissions: string[], fullAccess = false): StaffAccess => ({
  fullAccess,
  roleName: fullAccess ? 'Administrator' : 'Support',
  permissions,
  allPermissions: [],
});

describe('StaffAccessService', () => {
  let access: ReturnType<typeof vi.fn>;
  let token: WritableSignal<string | null>;
  let service: StaffAccessService;

  beforeEach(() => {
    access = vi.fn();
    token = signal<string | null>('token-a');
    TestBed.configureTestingModule({
      providers: [
        { provide: AdminAccountGateway, useValue: { access } },
        { provide: AuthService, useValue: { token } },
      ],
    });
    service = TestBed.inject(StaffAccessService);
  });

  it('hides nothing before the role has loaded', () => {
    expect(NAV.every((item) => service.can(item.id))).toBe(true);
  });

  it('allows only the pages in the role once loaded', async () => {
    access.mockResolvedValue(role(['customers', 'audit-logs']));
    await service.load();

    expect(service.can('users')).toBe(true);
    expect(service.can('audit-logs')).toBe(true);
    expect(service.can('settings')).toBe(false);
    expect(service.roleName()).toBe('Support');
  });

  it('allows every page for a full-access role', async () => {
    access.mockResolvedValue(role([], true));
    await service.load();
    expect(NAV.every((item) => service.can(item.id))).toBe(true);
  });

  it('asks identity-service only once per sign in', async () => {
    access.mockResolvedValue(role(['dashboard']));
    await Promise.all([service.load(), service.load()]);
    await service.load();
    expect(access).toHaveBeenCalledTimes(1);
  });

  it('falls back to full access when identity-service has no staff roles yet, and retries later', async () => {
    access.mockRejectedValueOnce(new Error('404'));
    expect(await service.load()).toBeNull();
    expect(service.can('settings')).toBe(true);

    access.mockResolvedValue(role(['dashboard']));
    await service.load();
    expect(access).toHaveBeenCalledTimes(2);
    expect(service.can('settings')).toBe(false);
  });

  it('forgets the role when a different admin signs in', async () => {
    access.mockResolvedValue(role(['dashboard']));
    await service.load();
    expect(service.can('settings')).toBe(false);

    token.set('token-b');
    TestBed.tick();

    expect(service.access()).toBeNull();
    expect(service.can('settings')).toBe(true);
  });

  it('has a permission for every page in the sidebar', () => {
    for (const item of NAV) expect(PAGE_PERMISSION[item.id]).toBeTruthy();
  });
});
