// ═══════════════════════════════════════════════════════════════════════════
// TYPES
// ═══════════════════════════════════════════════════════════════════════════

export type Page =
  | 'dashboard'
  | 'organisations'
  | 'users'
  | 'bank-integrations'
  | 'bank-accounts'
  | 'transactions'
  | 'reconciliation'
  | 'notifications'
  | 'audit-logs'
  | 'reports'
  | 'settings';

export type Theme = 'dark' | 'light';
export type AuthStep = 'login' | '2fa' | 'done';
export type OrgStatus = 'Active' | 'Suspended' | 'Pending';
export type UserStatus = 'Active' | 'Inactive' | 'Suspended';
export type TxStatus = 'Processed' | 'Pending' | 'Failed' | 'Unmatched';
export type RecStatus = 'Matched' | 'Unmatched' | 'Duplicate' | 'Needs Review' | 'Failed';
export type Severity = 'Critical' | 'Warning' | 'Info';
export type BankStatus = 'CONNECTED' | 'HEALTHY' | 'WARNING' | 'ERROR' | 'PENDING';
export type AccentColor = 'blue' | 'green' | 'gold' | 'red';

export interface Bank {
  id: string;
  name: string;
  full: string;
  logo: string;
  bgColor: string;
}

export interface Organisation {
  id: string;
  name: string;
  type: string;
  users: number;
  accounts: number;
  status: OrgStatus;
  joined: string;
}

export interface AppUser {
  id: string;
  name: string;
  email: string;
  org: string;
  role: string;
  status: UserStatus;
  lastLogin: string;
  /** INDIVIDUAL or ORGANIZATION, as chosen at sign up or by the admin who added them. */
  accountType: CustomerType;
  isAdmin: boolean;
}

export type CustomerType = 'INDIVIDUAL' | 'ORGANIZATION';

/** The ?type= values the Customers menu uses, and the account type each one shows. */
export const CUSTOMER_FILTERS: { query: string; label: string; accountType: CustomerType }[] = [
  { query: 'individual', label: 'Individuals', accountType: 'INDIVIDUAL' },
  { query: 'organisation', label: 'Organisations', accountType: 'ORGANIZATION' },
];

export interface Transaction {
  id: string;
  org: string;
  bank: string;
  acc: string;
  desc: string;
  amount: number;
  type: 'Income' | 'Expense';
  status: TxStatus;
  date: string;
}

export interface ReconciliationRecord {
  id: string;
  tx: string;
  amount: number;
  org: string;
  exp: string;
  status: RecStatus;
}

export interface AppNotification {
  id: string;
  title: string;
  desc: string;
  severity: Severity;
  cat: string;
  time: string;
  /** ISO timestamp from the backend. */
  createdAt: string;
  read: boolean;
}

export interface AuditLog {
  id: string;
  /** Formatted for display; occurredAt is the ISO value. */
  time: string;
  occurredAt: string;
  /** Email of whoever acted, or "System". */
  user: string;
  /** Customer, Staff or System. */
  role: string;
  action: string;
  resource: string;
  detail: string;
  ip: string;
  /** Success or Failed. */
  result: string;
}

export interface ChartPoint {
  label: string;
  inc: number;
  exp: number;
}

export interface BankAccount {
  id: number;
  org: string;
  bank: string;
  name: string;
  masked: string;
  currency: string;
  lastSync: string;
  status: 'Connected' | 'Warning';
}

// ═══════════════════════════════════════════════════════════════════════════
// BANK DIRECTORY
// ═══════════════════════════════════════════════════════════════════════════

/**
 * The banks the platform can connect to. This is configuration, not data: it
 * holds identity only. Every operational figure, such as health, latency and
 * notification counts, comes from GET /api/v1/admin/bank-integrations so that
 * nothing on screen is invented and a bank with no traffic reports zero.
 */
export const BANKS: Bank[] = [
  { id: 'kcb', name: 'KCB', full: 'KCB Bank Kenya', logo: 'banks/kcb.png', bgColor: '#6abe30' },
  { id: 'ncba', name: 'NCBA', full: 'NCBA Bank Kenya', logo: 'banks/ncba.png', bgColor: '#f5f0eb' },
  { id: 'equity', name: 'Equity', full: 'Equity Bank Kenya', logo: 'banks/equity.png', bgColor: '#fff' },
  { id: 'stanbic', name: 'Stanbic', full: 'Stanbic Bank Kenya', logo: 'banks/stanbic.jpg', bgColor: '#fff' },
];

/*
 * Sample records were removed for the clean start of real time testing.
 *
 * These collections are empty on purpose. Each is filled by a backend call once
 * its endpoint exists: organisations, users, transactions, bank accounts,
 * reconciliation, notifications and audit logs. Until then the interface shows
 * an empty state instead of invented records.
 */
export const ORGS: Organisation[] = [];

export const USERS: AppUser[] = [];

export const TRANSACTIONS: Transaction[] = [];

export const BANK_ACCOUNTS: BankAccount[] = [];

export const RECONCILE: ReconciliationRecord[] = [];

export const NOTIFICATIONS: AppNotification[] = [];


export const CHART_DATA: ChartPoint[] = [];

export type NavGroup = 'Overview' | 'Operations' | 'System';

export const NAV_GROUPS: NavGroup[] = ['Overview', 'Operations', 'System'];

export const NAV: {
  id: Page;
  label: string;
  icon: string;
  group: NavGroup;
  /** Sub-items that open the same page with ?type=, shown indented under the parent. */
  children?: { label: string; query: string }[];
}[] = [
  { id: 'dashboard', label: 'Dashboard', icon: 'dashboard', group: 'Overview' },
  { id: 'organisations', label: 'Organisations', icon: 'apartment', group: 'Operations' },
  {
    id: 'users',
    label: 'Customers',
    icon: 'group',
    group: 'Operations',
    children: CUSTOMER_FILTERS.map(({ label, query }) => ({ label, query })),
  },
  { id: 'bank-integrations', label: 'Bank Integrations', icon: 'account_balance', group: 'Operations' },
  { id: 'bank-accounts', label: 'Bank Accounts', icon: 'account_balance_wallet', group: 'Operations' },
  { id: 'transactions', label: 'Transactions', icon: 'swap_vert', group: 'Operations' },
  { id: 'reconciliation', label: 'Reconciliation', icon: 'rule', group: 'Operations' },
  { id: 'notifications', label: 'Notifications', icon: 'notifications', group: 'System' },
  { id: 'audit-logs', label: 'Audit Logs', icon: 'history', group: 'System' },
  { id: 'reports', label: 'Reports', icon: 'grid_view', group: 'System' },
  { id: 'settings', label: 'Settings', icon: 'settings', group: 'System' },
];

export interface PageMeta {
  eyebrow: string;
  title: string;
  subtitle: string;
}

/** Context shown above each page so the screen explains itself. */
export const PAGE_META: Record<Page, PageMeta> = {
  dashboard: {
    eyebrow: 'Overview',
    title: 'Dashboard',
    subtitle: 'Platform health, bank integrations and financial activity at a glance.',
  },
  organisations: {
    eyebrow: 'Operations',
    title: 'Organisations',
    subtitle: 'Businesses using SmartMoney, together with their users, accounts and status.',
  },
  users: {
    eyebrow: 'Operations',
    title: 'Customers',
    subtitle: 'Individuals and organisations on the platform: access, account type and sign in activity.',
  },
  'bank-integrations': {
    eyebrow: 'Operations',
    title: 'Bank Integrations',
    subtitle: 'Connection, API and webhook health for each bank provider.',
  },
  'bank-accounts': {
    eyebrow: 'Operations',
    title: 'Bank Accounts',
    subtitle: 'Connected accounts, currency and synchronisation state.',
  },
  transactions: {
    eyebrow: 'Operations',
    title: 'Transactions',
    subtitle: 'Every movement received from the connected bank accounts.',
  },
  reconciliation: {
    eyebrow: 'Operations',
    title: 'Reconciliation',
    subtitle: 'Matched, unmatched and duplicate records, and what needs review.',
  },
  notifications: {
    eyebrow: 'System',
    title: 'Notifications',
    subtitle: 'Integration, reconciliation, security and organisation alerts.',
  },
  'audit-logs': {
    eyebrow: 'System',
    title: 'Audit Logs',
    subtitle: 'A complete record of administrative activity on the platform.',
  },
  reports: {
    eyebrow: 'System',
    title: 'Reports',
    subtitle: 'Operational and compliance reporting across the platform.',
  },
  settings: {
    eyebrow: 'System',
    title: 'Settings',
    subtitle: 'Platform configuration, security policy, roles and bank settings.',
  },
};

export const PAGE_LABELS: Record<Page, string> = {
  dashboard: 'Dashboard',
  organisations: 'Organisations',
  users: 'Customers',
  'bank-integrations': 'Bank Integrations',
  'bank-accounts': 'Bank Accounts',
  transactions: 'Transactions',
  reconciliation: 'Reconciliation',
  notifications: 'Notifications',
  'audit-logs': 'Audit Logs',
  reports: 'Reports',
  settings: 'Settings',
};

export const NOTIFICATION_DESTINATIONS: Record<string, Page> = {
  'Bank Integration': 'bank-integrations',
  Reconciliation: 'reconciliation',
  Transaction: 'transactions',
  Organisation: 'organisations',
  User: 'users',
  Customer: 'users',
  Security: 'audit-logs',
  System: 'dashboard',
};

export const SEVERITY_COLORS: Record<Severity, string> = {
  Critical: 'var(--red)',
  Warning: 'var(--gold)',
  Info: 'var(--blue)',
};

export const SEVERITY_BACKGROUNDS: Record<Severity, string> = {
  Critical: 'var(--red-soft)',
  Warning: 'var(--gold-soft)',
  Info: 'var(--blue-soft)',
};

/** Formats a numeric amount the same way the design does (thousands separators). */
export function money(value: number): string {
  return value.toLocaleString('en-US');
}
