import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { AccountApi } from './account-api';
import { setupGuard, dashboardGuard, entryGuard } from './setup.guard';
import { Auth } from './auth';
describe('Setup-aware destinations',()=>{
 const api={authenticated:signal(false),setupCompleted:signal(false),restoreSession:vi.fn(),login:vi.fn(),register:vi.fn()};
 beforeEach(()=>{vi.resetAllMocks();api.authenticated.set(false);api.setupCompleted.set(false);TestBed.configureTestingModule({providers:[provideRouter([]),{provide:AccountApi,useValue:api}]});});
 const run=(guard:typeof setupGuard)=>TestBed.runInInjectionContext(()=>guard({} as never,{} as never));
 it('sends returning completed users to the dashboard after login',async()=>{const nav=vi.spyOn(TestBed.inject(Router),'navigate').mockResolvedValue(true);api.login.mockImplementation(async()=>{api.setupCompleted.set(true);});const auth=TestBed.createComponent(Auth).componentInstance;auth.form.patchValue({email:'test@example.com',password:'password'});await auth.submit();expect(nav).toHaveBeenCalledWith(['/dashboard']);});
 it('keeps new registrations on the setup path',async()=>{const router=TestBed.inject(Router);vi.spyOn(router,'url','get').mockReturnValue('/register');const nav=vi.spyOn(router,'navigate').mockResolvedValue(true);api.register.mockResolvedValue(undefined);const auth=TestBed.createComponent(Auth).componentInstance;auth.form.patchValue({name:'Test User',email:'test@example.com',password:'long-test-password',confirm:'long-test-password'});await auth.submit();expect(nav).toHaveBeenCalledWith(['/setup']);});
 it('prevents unfinished users from bypassing setup',async()=>{api.authenticated.set(true);expect(String(await run(dashboardGuard))).toBe('/setup');expect(await run(setupGuard)).toBe(true);});
 it('redirects completed users away from setup and login',async()=>{api.authenticated.set(true);api.setupCompleted.set(true);expect(String(await run(setupGuard))).toBe('/dashboard');expect(String(await run(entryGuard))).toBe('/dashboard');expect(await run(dashboardGuard)).toBe(true);});
 it('restores completion from the server on a fresh page load',async()=>{api.restoreSession.mockImplementation(async()=>{api.authenticated.set(true);api.setupCompleted.set(true);return true;});expect(await run(dashboardGuard)).toBe(true);expect(api.restoreSession).toHaveBeenCalledOnce();});
 it('redirects unauthenticated dashboard access to login',async()=>{api.restoreSession.mockResolvedValue(false);expect(String(await run(dashboardGuard))).toBe('/login');expect(await run(entryGuard)).toBe(true);});
});
