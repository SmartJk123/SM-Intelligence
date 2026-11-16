import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

/** Builds an unsigned JWT with the claims identity-service puts in its tokens. */
function fakeToken(claims: Record<string, unknown>): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(claims)}.signature`;
}

const inOneHour = () => Math.floor(Date.now() / 1000) + 3600;

function start(token: string | null): AuthService {
  sessionStorage.clear();
  if (token) sessionStorage.setItem('smi_admin_token', token);
  TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  return TestBed.inject(AuthService);
}

describe('AuthService', () => {
  afterEach(() => sessionStorage.clear());

  it('is signed out when no token is stored', () => {
    const auth = start(null);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.session()).toBeNull();
  });

  it('reads the admin from a stored, unexpired token', () => {
    const auth = start(fakeToken({ sub: 'u-1', name: ' Joy Kamau ', email: 'joy@example.com', exp: inOneHour() }));

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.session()).toEqual(
      expect.objectContaining({ userId: 'u-1', name: 'Joy Kamau', email: 'joy@example.com' }),
    );
    expect(auth.userId()).toBe('u-1');
  });

  it('signs out and forgets an expired token, so the guard does not open a dead dashboard', () => {
    const auth = start(fakeToken({ sub: 'u-1', exp: Math.floor(Date.now() / 1000) - 60 }));

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.token()).toBeNull();
    expect(sessionStorage.getItem('smi_admin_token')).toBeNull();
    TestBed.inject(HttpTestingController).expectOne((req) => req.url.endsWith('/logout'));
  });

  it('treats a malformed token or one without an expiry as signed out', () => {
    expect(start('not-a-jwt').isAuthenticated()).toBe(false);
    TestBed.resetTestingModule();
    expect(start(fakeToken({ sub: 'u-1' })).isAuthenticated()).toBe(false);
  });

  it('ends the session at identity-service on logout and clears storage', () => {
    const token = fakeToken({ sub: 'u-1', exp: inOneHour() });
    const auth = start(token);

    auth.logout();

    const req = TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/logout'));
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
    expect(auth.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem('smi_admin_token')).toBeNull();
  });
});
