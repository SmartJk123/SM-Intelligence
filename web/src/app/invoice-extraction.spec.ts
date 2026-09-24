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
});
