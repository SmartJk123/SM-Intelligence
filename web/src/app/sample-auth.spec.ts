import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AccountApi, SAMPLE_AUTH_MODE } from './account-api';

describe('Hosted sample auth contract', () => {
  let api: AccountApi;
  let http: HttpTestingController;
  const email = 'sample@example.invalid';
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SAMPLE_AUTH_MODE, useValue: true }] });
    api = TestBed.inject(AccountApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(async () => { await api.logout(); http.verify(); });
  it('maps registration fields without assuming registration starts a session', async () => {
    const pending = api.register({name:'Sample', email, password:'TestPassword123!', phone:' +254700000000 ', kind:'individual'});
    const request = http.expectOne('/sample-api/api/auth/register');
    expect(request.request.body).toEqual({name:'Sample', email, password:'TestPassword123!', phoneNumber:'+254700000000'});
    request.flush({id:'sample',name:'Sample',email});
    await pending;
    expect(api.authenticated()).toBe(false);
  });
  it.each(['token', 'accessToken'])('accepts the %s response, restores it and signs out without a nonexistent endpoint', async (field) => {
    const pending = api.login({email,password:'TestPassword123!'});
    const accessToken = 'header.'+btoa(JSON.stringify({sub:email,exp:Math.floor(Date.now()/1000)+60}))+'.signature';
    http.expectOne('/sample-api/api/auth/login').flush({[field]: accessToken, tokenType:'Bearer'});
    await pending;
    expect(api.authenticated()).toBe(true);
    expect(await api.restoreSession()).toBe(true);
    expect(sessionStorage.getItem('sm-sample-auth-v1')).not.toContain('TestPassword123!');
    await api.logout();
    expect(api.authenticated()).toBe(false);
    expect(await api.restoreSession()).toBe(false);
  });
  it('rejects malformed tokens and failed credentials', async () => {
    let pending=api.login({email,password:'incorrect'});
    let assertion=expect(pending).rejects.toThrow();
    http.expectOne('/sample-api/api/auth/login').flush({accessToken:'invalid'});
    await assertion;
    expect(api.authenticated()).toBe(false);
    pending=api.login({email,password:'incorrect'});
    assertion=expect(pending).rejects.toBeDefined();
    http.expectOne('/sample-api/api/auth/login').flush({}, {status:401,statusText:'Unauthorized'});
    await assertion;
    expect(api.authenticated()).toBe(false);
  });
});
