import { Component, input } from '@angular/core';
const paths: Record<string,string> = {
  'content-budget': 'M3 9h18v12H3z M7 9V5h10v4 M9 5V2h6v3 M3 14h18 M9 17h6 M12 11v6',
  'content-attention': 'M12 3l10 18H2z M12 9v5 M12 17h.1',
  'content-forecast': 'M3 6h18v15H3z M7 3v6 M17 3v6 M3 11h18 M6 18l4-4 4 2 4-3 M15 13h3v3',
  'content-cash': 'M10 5c0 1.7-2 3-4 3S2 6.7 2 5s2-3 4-3 4 1.3 4 3 M2 5v12c0 1.7 2 3 4 3 M2 9c0 1.7 2 3 4 3 M2 13c0 1.7 2 3 4 3 M10 5v4 M22 12c0 1.7-2.7 3-6 3s-6-1.3-6-3 2.7-3 6-3 6 1.3 6 3 M10 12v7c0 1.7 2.7 3 6 3s6-1.3 6-3v-7 M10 16c0 1.7 2.7 3 6 3s6-1.3 6-3',
  'content-credit': 'M3 5h18v14H3z M3 9h18 M6 14h5 M6 16h3 M17 12v3 M17 17h.1',
  'content-income': 'M3 15h5l3 3h5c2 0 3-2 5-2l-4 5H9l-3-2H3 M14 2v9 M10 7l4 4 4-4 M3 13v8',
  'content-expense': 'M3 15h5l3 3h5c2 0 3-2 5-2l-4 5H9l-3-2H3 M14 11V2 M10 6l4-4 4 4 M3 13v8',
  'content-flow': 'M3 7h18l-4-4 M21 17H3l4 4 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  'content-spending': 'M3 3v18h18 M6 18v-5h3v5 M12 18V9h3v9 M18 18V5h3v13 M6 9l5-4 4 2 5-5',
  'content-accounts': 'M2 8l10-6 10 6z M4 11v8 M9 11v8 M15 11v8 M20 11v8 M3 19h18 M2 22h20',
  'content-ledger': 'M4 2h11l5 5v15H4z M15 2v6h5 M7 11h10 M7 15h6 M7 19h4 M15 17h3 M16.5 15.5v3',
  dashboard: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  accounts: 'M3 5h18v14H3z M3 9h18 M6 15h4',
  transactions: 'M6 3h12v18l-3-2-3 2-3-2-3 2z M9 7h6 M9 11h6 M9 15h3',
  cashflow: 'M4 8h15l-4-4 M20 16H5l4 4',
  budgets: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
  investments: 'M3 7h18v14H3z M8 7V3h8v4 M3 12h18 M10 12v3h4v-3',
  analysis: 'M4 3v18h17 M8 16v-4 M13 16V8 M18 16V5',
  reports: 'M5 3h10l4 4v14H5z M14 3v5h5 M8 12h8 M8 16h8',
  notifications: 'M5 17h14l-2-3V9a5 5 0 0 0-10 0v5z M10 21h4',
  settings: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0 M4 21v-3a8 6 0 0 1 16 0v3',
  wallet: 'M3 6h17v14H3z M3 6l13-3v3 M15 11h6v5h-6z',
  income: 'M5 16l6-6 4 4 6-9 M15 5h6v6',
  expense: 'M5 8l6 6 4-4 6 9 M15 19h6v-6',
};
@Component({selector:'app-workspace-icon',template:`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path [attr.d]="path()" /></svg>`})
export class WorkspaceIcon {
  readonly name = input('dashboard');
  path() { return paths[this.name()] ?? paths['dashboard']; }
}
