import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Invoices } from './invoices';

describe('Pasted invoices', () => {
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
    vi.unstubAllGlobals();
  });
  function page() {
    const page = TestBed.createComponent(Invoices).componentInstance;
    TestBed.inject(HttpTestingController).expectOne('/api/invoices?page=0').flush([]);
    return page;
  }
  it('sends keyboard-pasted screenshots through the shared extraction flow', async () => {
    const component = page();
    const useFile = vi.spyOn(component, 'useFile').mockResolvedValue();
    const image = new File(['image'], 'screenshot.png', { type: 'image/png' });
    const preventDefault = vi.fn();
    await component.paste({
      preventDefault,
      clipboardData: { files: [image] },
    } as unknown as ClipboardEvent);
    expect(preventDefault).toHaveBeenCalledOnce();
    expect(useFile).toHaveBeenCalledWith(image);
  });
  it('does not replace an existing invoice with pasted text or start another extraction while busy', async () => {
    const component = page();
    const useFile = vi.spyOn(component, 'useFile').mockResolvedValue();
    const original = new File(['old'], 'original.png', { type: 'image/png' });
    component.file.set(original);
    await component.paste({
      preventDefault: vi.fn(),
      clipboardData: { files: [] },
    } as unknown as ClipboardEvent);
    expect(component.file()).toBe(original);
    expect(component.error()).toContain('Copied text');
    component.extracting.set(true);
    await component.paste({
      preventDefault: vi.fn(),
      clipboardData: { files: [original] },
    } as unknown as ClipboardEvent);
    expect(useFile).not.toHaveBeenCalled();
  });
  it('reads an image from the clipboard button and gives a keyboard fallback on denial', async () => {
    const component = page();
    const useFile = vi.spyOn(component, 'useFile').mockResolvedValue();
    const read = vi
      .fn()
      .mockResolvedValue([
        {
          types: ['text/html', 'image/png'],
          getType: async () => new Blob(['png'], { type: 'image/png' }),
        },
      ]);
    vi.stubGlobal(
      'navigator',
      new Proxy(navigator, {
        get(target, key) {
          return key === 'clipboard' ? { read } : Reflect.get(target, key);
        },
      }),
    );
    await component.pasteFromClipboard();
    expect(useFile.mock.calls[0][0].type).toBe('image/png');
    expect(component.readingClipboard()).toBe(false);
    read.mockRejectedValue(new DOMException('Denied', 'NotAllowedError'));
    await component.pasteFromClipboard();
    expect(component.error()).toContain('Ctrl+V');
    expect(component.readingClipboard()).toBe(false);
  });
});
