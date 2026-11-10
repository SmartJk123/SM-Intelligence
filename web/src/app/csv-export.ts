// CSV export helpers for customer-facing data.
// toCsv is pure so it stays easy to test; downloadCsv hands the file to the browser.

export type CsvCell = string | number | null | undefined;

/** Builds CSV text. A cell holding a comma, quote or line break is quoted, as Excel expects. */
export function toCsv(rows: CsvCell[][]): string {
  return rows.map((row) => row.map(escapeCell).join(',')).join('\r\n');
}

/** Saves the rows as a .csv file. The byte order mark makes Excel read it as UTF-8. */
export function downloadCsv(fileName: string, rows: CsvCell[][]): void {
  const blob = new Blob(['﻿' + toCsv(rows)], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

/** Minor units (cents) to a plain amount such as 1234.50, without currency symbols or separators. */
export function minorToAmount(minor: number): string {
  return (minor / 100).toFixed(2);
}

function escapeCell(value: CsvCell): string {
  const text = value === null || value === undefined ? '' : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}
