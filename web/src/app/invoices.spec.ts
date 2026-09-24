import { TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Invoices } from './invoices';

describe('Invoice review and persistence', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { data: {} } } },
      ],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('requires review before upload and reloads saved pending entries from the backend', async () => {
    const fixture = TestBed.createComponent(Invoices);
    const page = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/invoices?page=0').flush([]);
    await fixture.whenStable();
    page.file.set(new File(['%PDF-test'], 'invoice.pdf', { type: 'application/pdf' }));
    page.form.patchValue({
      vendor: 'Store',
      amount: '250',
      currency: 'KES',
      invoiceDate: '2026-09-24',
    });
    await page.save();
    http.expectNone('/api/invoices');
    page.form.patchValue({ reviewed: true });
    const saving = page.save();
    const request = http.expectOne('/api/invoices');
    expect(request.request.body instanceof FormData).toBe(true);
    expect(request.request.body.get('amount')).toBe('250');
    const invoice = {
      id: 'id',
      vendor: 'Store',
      amount: 250,
      currency: 'KES',
      status: 'PENDING',
      invoiceDate: '2026-09-24',
    };
    request.flush(invoice);
    await new Promise((resolve) => setTimeout(resolve, 0));
    http.expectOne('/api/invoices?page=0').flush([invoice]);
    await saving;
    expect(page.invoices().length).toBe(1);
    expect(page.file()).toBeNull();
    expect(page.totals()[0].amount).toBe(250);
  });
  it('preserves the review form after duplicate rejection so no extra pending entry appears', async () => {
    const page = TestBed.createComponent(Invoices).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/invoices?page=0').flush([]);
    page.file.set(new File(['%PDF-test'], 'invoice.pdf', { type: 'application/pdf' }));
    page.form.patchValue({
      vendor: 'Store',
      amount: '250',
      currency: 'KES',
      invoiceDate: '2026-09-24',
      reviewed: true,
    });
    const saving = page.save();
    http
      .expectOne('/api/invoices')
      .flush({ error: 'Already saved' }, { status: 409, statusText: 'Conflict' });
    await saving;
    expect(page.error()).toBe('Already saved');
    expect(page.file()).not.toBeNull();
    expect(page.invoices()).toEqual([]);
  });
});
