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
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks();
  });
  it('cancels deletion without a request and refreshes totals after confirmed deletion', async () => {
    const page = TestBed.createComponent(Invoices).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    const invoice = {
      id: 'delete-id',
      vendor: 'Store',
      invoiceNumber: 'INV-1',
      amount: 250,
      currency: 'KES',
      invoiceDate: '2026-09-24',
      dueDate: null,
      filename: 'invoice.pdf',
      status: 'PENDING' as const,
      source: 'INVOICE' as const,
    };
    http.expectOne('/api/invoices?page=0').flush([invoice]);
    await Promise.resolve();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    await page.deleteInvoice(invoice);
    http.expectNone('/api/invoices/delete-id');
    confirm.mockReturnValue(true);
    const deleting = page.deleteInvoice(invoice);
    const request = http.expectOne('/api/invoices/delete-id');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    http.expectOne('/api/invoices?page=0').flush([]);
    await deleting;
    expect(page.invoices()).toEqual([]);
    expect(page.totals()).toEqual([]);
    expect(page.deleting()).toBeNull();
  });
  it('keeps the saved invoice when deletion fails', async () => {
    const page = TestBed.createComponent(Invoices).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne('/api/invoices?page=0')
      .flush([{ id: 'id', vendor: 'Store', amount: 250, currency: 'KES' }]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const deleting = page.deleteInvoice(page.invoices()[0]);
    http
      .expectOne('/api/invoices/id')
      .flush({ error: 'Service unavailable' }, { status: 503, statusText: 'Unavailable' });
    await deleting;
    expect(page.invoices()).toHaveLength(1);
    expect(page.error()).toBe('Service unavailable');
    expect(page.deleting()).toBeNull();
  });
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
