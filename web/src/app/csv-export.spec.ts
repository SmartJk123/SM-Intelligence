import { minorToAmount, toCsv } from './csv-export';

describe('CSV export', () => {
  it('quotes cells that contain commas, quotes or line breaks', () => {
    expect(toCsv([['Name', 'Note'], ['Jane, W.', 'said "hi"'], ['line', 'one\ntwo']])).toBe(
      'Name,Note\r\n"Jane, W.","said ""hi"""\r\nline,"one\ntwo"',
    );
  });

  it('writes empty cells for missing values', () => {
    expect(toCsv([['a', null, undefined, 0]])).toBe('a,,,0');
  });

  it('converts minor units to a plain two-decimal amount', () => {
    expect(minorToAmount(123450)).toBe('1234.50');
    expect(minorToAmount(-500)).toBe('-5.00');
    expect(minorToAmount(0)).toBe('0.00');
  });
});
