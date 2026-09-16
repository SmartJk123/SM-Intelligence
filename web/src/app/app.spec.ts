// Account-journey tests covering validation, authentication, setup and API behavior.
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Auth } from './auth';
import { Setup } from './setup';
import { AccountApi } from './account-api';
import { setupGuard } from './setup.guard';

describe('Account journey', () => {
  const api = {
    authenticated: signal(false),
    setupCompleted: signal(false),
    kind: signal<'individual' | 'organization'>('individual'),
    login: vi.fn(),
    register: vi.fn(),
    saveSetup: vi.fn(),
    restoreSession: vi.fn(),
  };
  beforeEach(() => {
    vi.resetAllMocks();
    api.authenticated.set(false);
    api.setupCompleted.set(false);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AccountApi, useValue: api }],
    });
  });
  it('blocks invalid registration and password mismatches', async () => {
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue('/register');
    const a = TestBed.createComponent(Auth).componentInstance;
    a.form.patchValue({
      name: 'Test User',
      email: 'test@example.com',
      password: 'long-test-password',
      confirm: 'wrong',
    });
    await a.submit();
    expect(api.register).not.toHaveBeenCalled();
    expect(a.mismatch()).toBe(true);
  });
  it('navigates to setup only after successful login', async () => {
    const router = TestBed.inject(Router);
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const a = TestBed.createComponent(Auth).componentInstance;
    a.form.patchValue({ email: 'test@example.com', password: 'test-password' });
    api.login.mockRejectedValueOnce(new Error('offline'));
    await a.submit();
    expect(nav).not.toHaveBeenCalled();
    api.login.mockResolvedValueOnce(undefined);
    await a.submit();
    expect(nav).toHaveBeenCalledWith(['/setup']);
  });
  it('protects direct setup access without a restored session', async () => {
    api.restoreSession.mockResolvedValue(false);
    const result = await TestBed.runInInjectionContext(() => setupGuard({} as never, {} as never));
    expect(String(result)).toBe('/login');
  });
  it('validates account number, negative amount and future date before saving', async () => {
    const s = TestBed.createComponent(Setup).componentInstance;
    s.form.patchValue({ accountNumber: 'abc', balance: '-1' });
    await s.save();
    expect(api.saveSetup).not.toHaveBeenCalled();
    s.form.patchValue({ accountNumber: '00123456', balance: '0', balanceDate: '2999-01-01' });
    await s.save();
    expect(api.saveSetup).not.toHaveBeenCalled();
  });
  it('preserves credit as debt, uses KES, masks and clears the full number after save', async () => {
    api.saveSetup.mockResolvedValue(undefined);
    const s = TestBed.createComponent(Setup).componentInstance;
    s.form.patchValue({ accountNumber: '00123456', cardType: 'credit', balance: '1250.50' });
    await s.save();
    expect(api.saveSetup).toHaveBeenCalledWith(
      expect.objectContaining({
        currency: 'KES',
        cardType: 'credit',
        balance: 1250.5,
        accountNumber: '00123456',
      }),
    );
    expect(s.saved()).toBe(true);
    expect(s.summary().lastFour).toBe('3456');
    expect(s.form.controls.accountNumber.value).toBe('');
  });
  it('preserves entries on a failed save and allows retry', async () => {
    api.saveSetup.mockRejectedValueOnce(new Error('offline'));
    const s = TestBed.createComponent(Setup).componentInstance;
    s.form.patchValue({ accountNumber: '00123456', balance: '0' });
    await s.save();
    expect(s.saved()).toBe(false);
    expect(s.form.controls.accountNumber.value).toBe('00123456');
    expect(s.pending()).toBe(false);
    api.saveSetup.mockResolvedValueOnce(undefined);
    await s.save();
    expect(s.saved()).toBe(true);
  });
  it('does not overwrite a custom account name when a bank is selected', () => {
    const s = TestBed.createComponent(Setup).componentInstance;
    s.form.controls.accountName.setValue('Savings');
    s.form.controls.accountName.markAsDirty();
    s.chooseBank('Equity');
    expect(s.form.controls.accountName.value).toBe('Savings');
  });
});

describe('Account API contract', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('requires a valid server session before authenticating', async () => {
    const api = TestBed.inject(AccountApi);
    const pending = api.login({ email: 'test@example.com', password: 'password' });
    expect(api.authenticated()).toBe(false);
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ user: { id: 'test', kind: 'individual', setupCompleted: false } });
    await pending;
    expect(api.authenticated()).toBe(true);
  });
  it('rejects an unconfirmed save response', async () => {
    const pending = TestBed.inject(AccountApi).saveSetup({
      bank: 'KCB',
      accountName: 'Savings',
      accountNumber: '123456',
      cardType: 'debit',
      balance: 0,
      balanceDate: '2026-09-09',
      currency: 'KES',
    });
    const assertion = expect(pending).rejects.toThrow('did not confirm');
    TestBed.inject(HttpTestingController).expectOne('/api/accounts').flush({});
    await assertion;
  });
});
