import { Component, input } from '@angular/core';
const paths: Record<string,string> = {
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
