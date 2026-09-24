import { parseInvoiceText } from './invoice-extraction';

describe('Invoice extraction suggestions', () => {
  it('prefers the amount due over subtotal and tax and detects Kenyan currency', () => {
    const result = parseInvoiceText(
      'Acme Supplies\nInvoice No: INV-001\nInvoice Date: 2026-09-24\nDue Date: 2026-10-24\nSubtotal KES 1,000.00\nTax total KES 160.00\nAmount due KES 1,160.00',
    );
    expect(result).toEqual({
      vendor: 'Acme Supplies',
      invoiceNumber: 'INV-001',
      amount: '1160.00',
      currency: 'KES',
      invoiceDate: '2026-09-24',
      dueDate: '2026-10-24',
    });
  });
  it('does not guess ambiguous dates, currencies or missing totals', () => {
    const result = parseInvoiceText(
      'Supplier: Test Store\nInvoice date: 04/05/2026\nSubtotal 200\nTax total 10',
    );
    expect(result.vendor).toBe('Test Store');
    expect(result.invoiceDate).toBe('');
    expect(result.amount).toBe('');
    expect(result.currency).toBe('');
  });
  it('rejects impossible dates and supports unambiguous day-first dates', () => {
    expect(parseInvoiceText('Date: 2026-02-31').invoiceDate).toBe('');
    expect(parseInvoiceText('Date: 24/09/2026').invoiceDate).toBe('2026-09-24');
  });
  it('extracts the issue and due dates from the detailed sample PDF rows', () => {
    const result = parseInvoiceText(
      'NORTHSTAR DIGITAL LTD\nInvoice number   INV-DEMO-2026-001   Issue date   24 Sep 2026\nReference   PO-DEMO-1042   Due date   8 Oct 2026\nCurrency   KES   Payment terms   Net 14 days\nTOTAL DUE KES   224,808.00',
    );
    expect(result).toEqual({
      vendor: 'NORTHSTAR DIGITAL LTD',
      invoiceNumber: 'INV-DEMO-2026-001',
      amount: '224808.00',
      currency: 'KES',
      invoiceDate: '2026-09-24',
      dueDate: '2026-10-08',
    });
  });
  it('handles named months with labels and values on separate lines', () => {
    const result = parseInvoiceText('Issue date\n24 September 2026\nDue date\nOctober 8th, 2026');
    expect(result.invoiceDate).toBe('2026-09-24');
    expect(result.dueDate).toBe('2026-10-08');
  });
  it('keeps issue and due dates distinct on the same line regardless of order', () => {
    const result = parseInvoiceText('Due date: 8 Oct 2026   Invoice date: 24 Sept. 2026');
    expect(result.invoiceDate).toBe('2026-09-24');
    expect(result.dueDate).toBe('2026-10-08');
  });
  it('validates named-month calendar dates and does not borrow an unrelated date', () => {
    expect(parseInvoiceText('Issue date: 29 Feb 2026\nDue date: 31 April 2026').invoiceDate).toBe(
      '',
    );
    expect(parseInvoiceText('Due date: 31 April 2026').dueDate).toBe('');
    expect(parseInvoiceText('Issue date: 29 February 2028').invoiceDate).toBe('2028-02-29');
    expect(parseInvoiceText('Issue date\nReference 1042\nDue date: 8 Oct 2026').invoiceDate).toBe(
      '',
    );
  });
});
