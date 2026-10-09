import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AccountApi } from './account-api';
import { IDLE_LIMIT_MS, IDLE_WARNING_MS, IdleTimeout } from './idle-timeout';

describe('Idle sign-out', () => {
  const api = { authenticated: signal(false), logout: vi.fn() };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.resetAllMocks();
    api.authenticated.set(false);
    api.logout.mockImplementation(async () => api.authenticated.set(false));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: AccountApi, useValue: api }],
    });
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });
  afterEach(() => vi.useRealTimers());

  const signedIn = () => {
    const idle = TestBed.inject(IdleTimeout);
    api.authenticated.set(true);
    TestBed.tick();
    return idle;
  };
  const answerKeepalives = () =>
    TestBed.inject(HttpTestingController).match('/api/auth/session').filter((r) => !r.cancelled).forEach((r) => r.flush({}));

  it('warns a minute before and then signs an inactive customer out', async () => {
    const idle = signedIn();
    await vi.advanceTimersByTimeAsync(IDLE_LIMIT_MS - IDLE_WARNING_MS + 1000);
    answerKeepalives();
    expect(idle.secondsLeft()).toBeGreaterThan(0);
    expect(idle.secondsLeft()).toBeLessThanOrEqual(60);

    await vi.advanceTimersByTimeAsync(IDLE_WARNING_MS);
    expect(api.logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'idle' } });
  });

  it('keeps an active customer signed in', async () => {
    const idle = signedIn();
    for (let minute = 0; minute < 30; minute++) {
      await vi.advanceTimersByTimeAsync(60_000);
      window.dispatchEvent(new Event('keydown'));
      answerKeepalives();
    }
    expect(idle.secondsLeft()).toBeNull();
    expect(api.logout).not.toHaveBeenCalled();
  });

  it('"Stay signed in" removes the warning, but moving the mouse does not', async () => {
    const idle = signedIn();
    await vi.advanceTimersByTimeAsync(IDLE_LIMIT_MS - 30_000);
    answerKeepalives();
    window.dispatchEvent(new Event('mousemove'));
    await vi.advanceTimersByTimeAsync(1000);
    expect(idle.secondsLeft()).not.toBeNull();

    const stay = idle.stayActive();
    answerKeepalives();
    await stay;
    expect(idle.secondsLeft()).toBeNull();
    await vi.advanceTimersByTimeAsync(60_000);
    expect(api.logout).not.toHaveBeenCalled();
  });
});
