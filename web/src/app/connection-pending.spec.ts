import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AccountApi } from './account-api';
import { ConnectionPending } from './connection-pending';

describe('Authentication-only workspace', () => {
  it('shows the actual signed-in profile without requesting or fabricating financial data', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: {snapshot:{data:{page:'accounts'},title:'Accounts | SM-Intelligence'}} } ] });
    TestBed.inject(AccountApi).displayName.set('Team Member');
    const fixture = TestBed.createComponent(ConnectionPending);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Team Member');
    expect(fixture.nativeElement.textContent).toContain('Your accounts');
    expect(fixture.nativeElement.querySelector('input')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone('/api/workspace');
    TestBed.inject(HttpTestingController).expectNone('/api/dashboard');
  });
});
