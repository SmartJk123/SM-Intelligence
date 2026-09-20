export type FlowRange = 'year' | 'quarter' | 'month';

export interface FlowSeries {
  name: string;
  color: string;
  values: number[];
}

export interface FlowDataset {
  labels: string[];
  series: FlowSeries[];
  /** Show every nth x axis label so dense ranges stay readable. */
  labelEvery: number;
}

export interface MixSegment {
  label: string;
  value: number;
  color: string;
}

export interface BudgetGoal {
  label: string;
  detail: string;
  spent: number;
  limit: number;
  color: string;
}

export interface PrimaryAccountCard {
  bank: string;
  accountName: string;
  masked: string;
  holder: string;
  balance: number;
  currency: string;
  validThru: string;
}

/** Deterministic wave so the demo data is stable between renders. */
function wave(length: number, base: number, swing: number, seed: number): number[] {
  return Array.from({ length }, (_, index) => {
    const wobble = Math.sin((index + seed) / 2.6) * swing;
    const jitter = ((index * 37 + seed * 13) % 42) - 21;
    return Math.max(40, Math.round(base + wobble + jitter));
  });
}

const MONTH_LABELS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
];

const WEEK_LABELS = Array.from({ length: 13 }, (_, index) => `W${index + 1}`);

const DAY_LABELS = Array.from({ length: 30 }, (_, index) => `${index + 1}`);

export const MONEY_FLOW: Record<FlowRange, FlowDataset> = {
  year: {
    labels: MONTH_LABELS,
    labelEvery: 1,
    series: [
      {
        name: 'Income',
        color: 'var(--c-indigo)',
        values: [820, 910, 760, 1080, 940, 1290, 1070, 1400, 1220, 1180, 1330, 1460],
      },
      {
        name: 'Expense',
        color: 'var(--c-pink)',
        values: [610, 700, 560, 840, 690, 910, 740, 980, 860, 820, 900, 1010],
      },
    ],
  },
  quarter: {
    labels: WEEK_LABELS,
    labelEvery: 1,
    series: [
      {
        name: 'Income',
        color: 'var(--c-indigo)',
        values: wave(13, 320, 70, 3),
      },
      {
        name: 'Expense',
        color: 'var(--c-pink)',
        values: wave(13, 240, 55, 8),
      },
    ],
  },
  month: {
    labels: DAY_LABELS,
    labelEvery: 5,
    series: [
      {
        name: 'Income',
        color: 'var(--c-indigo)',
        values: wave(30, 46, 14, 2),
      },
      {
        name: 'Expense',
        color: 'var(--c-pink)',
        values: wave(30, 33, 11, 7),
      },
    ],
  },
};

/** Transaction mix shown as the segmented bar in the statistics card. */
export const TRANSACTION_MIX: MixSegment[] = [
  { label: 'Income', value: 1460000, color: 'var(--c-indigo)' },
  { label: 'Expenses', value: 1010000, color: 'var(--c-pink)' },
  { label: 'Transfers', value: 320000, color: 'var(--c-cyan)' },
  { label: 'Uncategorised', value: 145000, color: 'var(--c-amber)' },
];

export const BUDGET_GOALS: BudgetGoal[] = [
  {
    label: 'Monthly outflow',
    detail: 'Operations, suppliers and utilities',
    spent: 148600,
    limit: 250000,
    color: 'var(--c-indigo)',
  },
  {
    label: 'Maintenance reserve',
    detail: 'Property upkeep across 6 sites',
    spent: 42000,
    limit: 90000,
    color: 'var(--c-cyan)',
  },
  {
    label: 'Collections target',
    detail: 'September rent roll',
    spent: 820000,
    limit: 1000000,
    color: 'var(--c-green)',
  },
];

export const PRIMARY_ACCOUNT: PrimaryAccountCard = {
  bank: 'KCB',
  accountName: 'Main Business',
  masked: '**** 4521',
  holder: 'SMARTMONEY ADMIN',
  balance: 750000,
  currency: 'KES',
  validThru: '09/28',
};

export const FLOW_RANGES: { id: FlowRange; label: string }[] = [
  { id: 'year', label: 'Year' },
  { id: 'quarter', label: 'Quarter' },
  { id: 'month', label: 'Month' },
];
