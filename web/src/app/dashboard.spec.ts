import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Dashboard } from './dashboard';
import { AccountApi } from './account-api';

describe('Dashboard availability states', () => {
  beforeEach(() => TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it.each([501, 503])('keeps the layout and saved accounts when activity returns %s', async status => {
    TestBed.inject(AccountApi).accounts.set([{id:'account',accountName:'My savings',institution:'KCB',maskedIdentifier:'•••• 1234',accountType:'DEPOSIT',currency:'KES',availableBalance:125,creditOutstanding:0,accountStatus:'ACTIVE'}]);
    const fixture = TestBed.createComponent(Dashboard);
    TestBed.inject(HttpTestingController).expectOne('/api/dashboard?days=30&bank=').flush({}, {status, statusText:'Unavailable'});
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('.dashboard-metrics article').length).toBe(4);
    expect(el.querySelectorAll('.dashboard-panel').length).toBe(4);
    expect(el.textContent).toContain('My savings');
    expect(el.textContent).toContain('125.00');
    expect(el.textContent).toContain('No cash-flow data available');
    expect(el.textContent).toContain(status === 501 ? 'Not connected yet' : 'Connection unavailable');
    expect(el.querySelector('.positive')?.textContent).toContain('—');
  });
  it('shows an empty result without reporting a connection failure', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    const empty = fixture.componentInstance.view();
    TestBed.inject(HttpTestingController).expectOne('/api/dashboard?days=30&bank=').flush(empty);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No posted cash movements');
    expect(fixture.nativeElement.textContent).toContain('No transactions yet');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.querySelectorAll('.dashboard-panel').length).toBe(4);
  });
});
