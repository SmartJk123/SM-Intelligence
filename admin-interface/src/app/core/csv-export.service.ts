import { Injectable } from '@angular/core';

export type CsvCell = string | number | null | undefined;

/**
 * Writes a real file to disk through the browser download mechanism.
 * A byte order mark is included so Excel opens the file with the right encoding.
 */
@Injectable({ providedIn: 'root' })
export class CsvExportService {

  download(fileName: string, headers: string[], rows: CsvCell[][]): void {
    const lines = [headers, ...rows].map((row) => row.map(escapeCell).join(','));
    const csv = '\uFEFF' + lines.join('\r\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.style.display = 'none';
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    setTimeout(() => URL.revokeObjectURL(url), 1500);
  }

  /** File name suffix, for example smartmoney-transactions-2026-09-15.csv */
  stamp(): string {
    return new Date().toISOString().slice(0, 10);
  }
}

function escapeCell(value: CsvCell): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}
