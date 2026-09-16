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
      {
        path: 'dashboard',
        title: 'Financial Overview | SM-Intelligence',
        loadComponent: () => import('./dashboard').then((m) => m.Dashboard),
      },
      { path: 'overview', redirectTo: 'dashboard', pathMatch: 'full' },
      ...[
        { page: 'accounts', title: 'Accounts' },
        { page: 'transactions', title: 'Transactions' },
        { page: 'cashflow', title: 'Cash Flow' },
        { page: 'budgets', title: 'Budgets' },
        { page: 'investments', title: 'Investments' },
        { page: 'analysis', title: 'Analytics' },
        { page: 'reports', title: 'Reports' },
        { page: 'notifications', title: 'Notifications' },
        { page: 'settings', title: 'Profile and Settings' },
      ].map(({ page, title }) => ({
        path: page,
        title: title + ' | SM-Intelligence',
        data: { page },
        loadComponent: () => import('./workspace-page').then((m) => m.WorkspacePage),
      })),
    ],
  },
  { path: '**', redirectTo: '' },
];


