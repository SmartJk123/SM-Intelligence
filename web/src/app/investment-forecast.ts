// Simple-interest forecast for one investment: value x rate (% a year) x time.
// Shared by the Investments page and the Analytics forecast so both show the same figures.

export interface ForecastInput {
  principalMinor: number;
  currentValueMinor: number | null;
  valuationDate: string;
  maturityDate: string;
  interestRate: number | null;
  startDate?: string;
}

export interface InvestmentForecast {
  /** Value the forecast starts from: the latest valuation, or the principal when there is none. */
  baseMinor: number;
  rate: number;
  /** "2026–2036" from the start year to the maturity year, or null without a maturity date. */
  termLabel: string | null;
  /** Whole length of the investment, e.g. "10 years" or "1 year 6 months". */
  termLength: string | null;
  /** Time left from the valuation date to maturity, e.g. "9 years 3 months"; "Matured" once past it. */
  remaining: string | null;
  /** Years used for the forecast: to maturity, or one year when there is no maturity date. */
  years: number;
  interestMinor: number;
  projectedMinor: number;
  /** Estimated worth today: the value grown at the annual rate from the valuation date to today (capped at maturity). */
  valueTodayMinor: number;
  /** Interest earned so far, from the valuation date to today. */
  earnedSoFarMinor: number;
  /** How long it has been held, e.g. "10 months". */
  heldFor: string;
  /** Projected value at the end of each calendar year up to maturity (at most 30 points). */
  yearly: { year: number; valueMinor: number }[];
}

const DAY_MS = 86_400_000;
const YEAR_DAYS = 365.25;

function months(from: Date, to: Date): number {
  return (to.getUTCFullYear() - from.getUTCFullYear()) * 12 + (to.getUTCMonth() - from.getUTCMonth())
    - (to.getUTCDate() < from.getUTCDate() ? 1 : 0);
}

/** "10 years", "1 year 6 months", "3 months", "Less than a month". */
export function describeMonths(total: number): string {
  if (total <= 0) return 'Less than a month';
  const y = Math.floor(total / 12), m = total % 12;
  const part = (n: number, word: string) => n + ' ' + word + (n === 1 ? '' : 's');
  return [y ? part(y, 'year') : '', m ? part(m, 'month') : ''].filter(Boolean).join(' ');
}

/** Null when no interest rate was entered, so the page can ask for one. */
export function forecast(inv: ForecastInput, today = new Date()): InvestmentForecast | null {
  if (inv.interestRate === null || inv.interestRate === undefined || !Number.isFinite(inv.interestRate)) return null;
  const rate = inv.interestRate;
  const baseMinor = inv.currentValueMinor ?? inv.principalMinor;
  const valued = inv.valuationDate ? new Date(inv.valuationDate + 'T00:00:00Z') : today;
  const from = Number.isNaN(valued.getTime()) ? today : valued;
  const maturity = inv.maturityDate ? new Date(inv.maturityDate + 'T00:00:00Z') : null;
  const start = inv.startDate ? new Date(inv.startDate + 'T00:00:00Z') : from;

  const years = maturity ? Math.max(0, (maturity.getTime() - from.getTime()) / DAY_MS / YEAR_DAYS) : 1;
  const valueAt = (t: number) => Math.round(baseMinor * (1 + (rate / 100) * t));
  const projectedMinor = valueAt(years);
  const elapsed = Math.max(0, (today.getTime() - from.getTime()) / DAY_MS / YEAR_DAYS);
  const valueTodayMinor = valueAt(maturity ? Math.min(years, elapsed) : elapsed);

  const yearly: { year: number; valueMinor: number }[] = [];
  const endYear = maturity ? maturity.getUTCFullYear() : from.getUTCFullYear() + 1;
  for (let y = from.getUTCFullYear(); y <= endYear && yearly.length < 30; y++) {
    const point = y === endYear && maturity ? maturity : new Date(Date.UTC(y, 11, 31));
    const t = Math.min(years, Math.max(0, (point.getTime() - from.getTime()) / DAY_MS / YEAR_DAYS));
    yearly.push({ year: y, valueMinor: valueAt(t) });
  }

  return {
    baseMinor,
    rate,
    termLabel: maturity ? start.getUTCFullYear() + '–' + maturity.getUTCFullYear() : null,
    termLength: maturity ? describeMonths(months(start, maturity)) : null,
    remaining: maturity ? (maturity <= today ? 'Matured' : describeMonths(months(today, maturity))) : null,
    years,
    interestMinor: projectedMinor - baseMinor,
    projectedMinor,
    valueTodayMinor,
    earnedSoFarMinor: valueTodayMinor - baseMinor,
    heldFor: describeMonths(months(start < from ? start : from, today)),
    yearly,
  };
}
