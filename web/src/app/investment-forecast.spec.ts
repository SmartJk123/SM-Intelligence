import { describeMonths, forecast } from './investment-forecast';

describe('Investment forecast', () => {
  const today = new Date('2026-01-15T00:00:00Z');

  it('grows the current value by rate x time to maturity and labels the term by year', () => {
    const f = forecast({ principalMinor: 10_000_000, currentValueMinor: 10_000_000, valuationDate: '2026-01-15',
      maturityDate: '2036-01-15', interestRate: 10, startDate: '2026-01-15' }, today)!;
    expect(f.termLabel).toBe('2026–2036');
    expect(f.termLength).toBe('10 years');
    expect(f.remaining).toBe('10 years');
    // KES 100,000 at 10% a year for 10 years (simple interest) is KES 200,000, give or take leap days.
    expect(Math.abs(f.projectedMinor - 20_000_000)).toBeLessThan(2_000);
    expect(f.yearly[0].year).toBe(2026);
    expect(f.yearly.at(-1)!.year).toBe(2036);
  });

  it('uses the principal when there is no valuation, and one year when there is no maturity date', () => {
    const f = forecast({ principalMinor: 5_000_000, currentValueMinor: null, valuationDate: '2026-01-15',
      maturityDate: '', interestRate: 12 }, today)!;
    expect(f.baseMinor).toBe(5_000_000);
    expect(f.years).toBe(1);
    expect(f.interestMinor).toBe(600_000);
    expect(f.termLabel).toBeNull();
  });

  it('returns nothing without an interest rate, and says Matured once past maturity', () => {
    expect(forecast({ principalMinor: 1, currentValueMinor: 1, valuationDate: '2026-01-15', maturityDate: '', interestRate: null }, today)).toBeNull();
    const past = forecast({ principalMinor: 100, currentValueMinor: 100, valuationDate: '2020-01-01',
      maturityDate: '2025-01-01', interestRate: 5, startDate: '2020-01-01' }, today)!;
    expect(past.remaining).toBe('Matured');
    expect(describeMonths(18)).toBe('1 year 6 months');
  });
});
