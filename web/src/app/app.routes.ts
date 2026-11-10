// Central route map.
// Guards protect authentication/setup flows; workspace pages are lazy-loaded.
import { setupGuard, dashboardGuard, entryGuard } from './setup.guard';
import { Routes } from '@angular/router';

// Add authenticated customer-facing routes here so they inherit dashboardGuard and WorkspaceShell.
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'SM-Intelligence | Financial clarity',
    loadComponent: () => import('./landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    canActivate: [entryGuard],
    title: 'Sign in | SM-Intelligence',
    loadComponent: () => import('./auth').then((m) => m.Auth),
  },
  {
    path: 'register',
    canActivate: [entryGuard],
    title: 'Create an account | SM-Intelligence',
    loadComponent: () => import('./auth').then((m) => m.Auth),
  },
  {
    path: 'forgot-password',
    title: 'Forgot password | SM-Intelligence',
    loadComponent: () => import('./password-reset').then((m) => m.PasswordReset),
  },
  {
    path: 'reset-password',
    title: 'Choose a new password | SM-Intelligence',
    loadComponent: () => import('./password-reset').then((m) => m.PasswordReset),
  },
  {
    path: 'setup',
    canActivate: [setupGuard],
    title: 'Set up your workspace | SM-Intelligence',
    loadComponent: () => import('./setup').then((m) => m.Setup),
  },
  {
    path: '',
    canActivateChild: [dashboardGuard],
    loadComponent: () => import('./workspace-shell').then((m) => m.WorkspaceShell),
    children: [
      { path: 'accounts/new', title: 'Add an account | SM-Intelligence', loadComponent: () => import('./setup').then(m => m.Setup) },
      {
        path: 'dashboard',
        title: 'Financial Overview | SM-Intelligence',
        loadComponent: () => import('./dashboard').then((m) => m.Dashboard),
      },
      { path: 'overview', redirectTo: 'dashboard', pathMatch: 'full' },
      // Invoices is hidden until the page is reliable. Uncomment to bring it back (and its link in workspace-shell.ts).
      // { path: 'invoices', title: 'Invoices | SM-Intelligence', loadComponent: () => import('./invoices').then(m => m.Invoices) },
      // The pages below share WorkspacePage, which reads route data.page.
      ...[
        { page: 'accounts', title: 'Accounts' },
        { page: 'transactions', title: 'Transactions' },
        { page: 'investments', title: 'Investments' },
        { page: 'reports', title: 'Reports' },
        { page: 'notifications', title: 'Notifications' },
        { page: 'settings', title: 'Profile and Settings' },
      ].map(({ page, title }) => ({
        path: page,
        title: title + ' | SM-Intelligence',
        data: { page },
        loadComponent: () => import('./workspace-page').then((m) => m.WorkspacePage),
      })),
      { path: 'cashflow', title: 'Cash Flow | SM-Intelligence', loadComponent: () => import('./cash-flow').then((m) => m.CashFlow) },
      { path: 'analysis', title: 'Analytics | SM-Intelligence', loadComponent: () => import('./analysis').then((m) => m.Analysis) },
      { path: 'budgets', title: 'Budgets | SM-Intelligence', loadComponent: () => import('./budgets').then((m) => m.Budgets) },
    ],
  },
  { path: '**', redirectTo: '' },
];
