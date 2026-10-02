import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InvoicePreview } from './invoice-preview';

describe('Read-only invoice preview', () => {
  const descriptors = ['showModal', 'close'].map(
    (name) => [name, Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, name)] as const,
  );
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    for (const [name] of descriptors)
      Object.defineProperty(HTMLDialogElement.prototype, name, {
        configurable: true,
        value: vi.fn(),
      });
    vi.stubGlobal(
      'URL',
      class extends URL {
        static override createObjectURL = vi.fn().mockReturnValue('blob:invoice-preview');
        static override revokeObjectURL = vi.fn();
      },
    );
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
    for (const [name, descriptor] of descriptors) {
      if (descriptor) Object.defineProperty(HTMLDialogElement.prototype, name, descriptor);
      else Reflect.deleteProperty(HTMLDialogElement.prototype, name);
    }
  });
  async function open() {
    const fixture = TestBed.createComponent(InvoicePreview);
    fixture.componentRef.setInput('invoiceId', 'invoice-1');
    fixture.componentRef.setInput('filename', 'invoice.png');
    await fixture.whenStable();
    const request = TestBed.inject(HttpTestingController).expectOne(
      '/api/invoices/invoice-1/document',
    );
    return { fixture, request, preview: fixture.componentInstance };
  }
  it('displays an image within the dialog without a download link and releases it on close', async () => {
    const { fixture, request, preview } = await open();
    request.flush(new Blob(['image'], { type: 'image/png' }));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe(
      'blob:invoice-preview',
    );
    expect(fixture.nativeElement.querySelector('a, input, textarea, iframe')).toBeNull();
    const closed = vi.fn();
    preview.closed.subscribe(closed);
    preview.dismiss();
    expect(closed).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:invoice-preview');
  });
  it('explains inaccessible documents and allows retry', async () => {
    const { request, preview } = await open();
    request.flush(new Blob(), { status: 404, statusText: 'Not found' });
    expect(preview.error()).toContain('not available');
    preview.load();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/invoices/invoice-1/document')
      .flush(new Blob(['image'], { type: 'image/jpeg' }));
    expect(preview.error()).toBe('');
    expect(preview.imageUrl()).toBe('blob:invoice-preview');
  });
  it('cancels a pending document request when closed', async () => {
    const { request, preview } = await open();
    preview.dismiss();
    expect(request.cancelled).toBe(true);
  });
});
