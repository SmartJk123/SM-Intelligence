import { Routes } from '@angular/router';
import { AdminLayout } from './layout/admin-layout';
import { authGuard } from './core/auth.guard';
import { loginGuard } from './core/login.guard';
import { AuditLogs } from './pages/audit-logs/audit-logs';
import { BankAccounts } from './pages/bank-accounts/bank-accounts';
import { BankIntegrations } from './pages/bank-integrations/bank-integrations';
import { Dashboard } from './pages/dashboard/dashboard';
import { LoginPage } from './pages/login/login';
import { NotificationsPage } from './pages/notifications/notifications';
import { Organisations } from './pages/organisations/organisations';
import { Reconciliation } from './pages/reconciliation/reconciliation';
import { Reports } from './pages/reports/reports';
import { Settings } from './pages/settings/settings';
import { Transactions } from './pages/transactions/transactions';
import { Users } from './pages/users/users';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'admin/dashboard' },
  { path: 'admin/login', component: LoginPage, canActivate: [loginGuard] },
  {
    path: 'admin',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: Dashboard },
      { path: 'organisations', component: Organisations },
      { path: 'users', component: Users },
      { path: 'bank-integrations', component: BankIntegrations },
      { path: 'bank-accounts', component: BankAccounts },
      { path: 'transactions', component: Transactions },
      { path: 'reconciliation', component: Reconciliation },
      { path: 'notifications', component: NotificationsPage },
      { path: 'audit-logs', component: AuditLogs },
      { path: 'reports', component: Reports },
      { path: 'settings', component: Settings },
    ],
  },
  { path: '**', redirectTo: 'admin/dashboard' },
];
