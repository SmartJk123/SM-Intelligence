import { TestBed } from '@angular/core/testing';
import { CsvExportService } from './csv-export.service';

describe('CsvExportService', () => {
  let service: CsvExportService;
  let written: Blob | null;
  let fileName: string;

  beforeEach(() => {
    service = TestBed.inject(CsvExportService);
    written = null;
    fileName = '';
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
      written = blob as Blob;
      return 'blob:test';
    });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      fileName = this.download;
    });
  });

  afterEach(() => vi.restoreAllMocks());

  async function csv(): Promise<string> {
    expect(written).not.toBeNull();
    // jsdom's Blob has no text(), and readAsText drops the byte order mark,
    // so read the raw bytes and decode them keeping it.
    const bytes = await new Promise<ArrayBuffer>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as ArrayBuffer);
      reader.onerror = () => reject(reader.error);
      reader.readAsArrayBuffer(written!);
    });
    return new TextDecoder('utf-8', { ignoreBOM: true }).decode(bytes);
  }

  it('downloads the file under the given name, with a byte order mark for Excel', async () => {
    service.download('customers.csv', ['Name', 'Email'], [['Joy', 'joy@example.com']]);

    expect(fileName).toBe('customers.csv');
    const text = await csv();
    expect(text.charCodeAt(0)).toBe(0xfeff);
    expect(text.slice(1)).toBe('Name,Email\r\nJoy,joy@example.com');
  });

  it('quotes cells containing commas, quotes or new lines so columns do not shift', async () => {
    service.download('x.csv', ['Note'], [['Nairobi, Kenya'], ['She said "hi"'], ['two\nlines']]);

    const rows = (await csv()).slice(1).split('\r\n');
    expect(rows).toEqual(['Note', '"Nairobi, Kenya"', '"She said ""hi"""', '"two\nlines"']);
  });

  it('writes empty cells for null and undefined, and numbers as they are', async () => {
    service.download('x.csv', ['A', 'B', 'C'], [[null, undefined, 1250.5]]);

    expect((await csv()).slice(1).split('\r\n')[1]).toBe(',,1250.5');
  });

  it('removes the temporary link from the page after the download starts', () => {
    service.download('x.csv', ['A'], []);
    expect(document.querySelector('a[download]')).toBeNull();
  });

  it('stamps file names with today as YYYY-MM-DD', () => {
    expect(service.stamp()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
