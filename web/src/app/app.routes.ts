import { setupGuard } from './setup.guard';
import { Routes } from '@angular/router';
export const routes: Routes = [
  {
    path: '',
    title: 'SM-Intelligence | Financial clarity',
    loadComponent: () => import('./landing').then((m) => m.Landing),
  },
  {
    path: 'login',
    title: 'Sign in | SM-Intelligence',
    loadComponent: () => import('./auth').then((m) => m.Auth),
  },
  {
    path: 'register',
    title: 'Create an account | SM-Intelligence',
    loadComponent: () => import('./auth').then((m) => m.Auth),
  },
  {
    path: 'setup',
    canActivate: [setupGuard],
    title: 'Set up your workspace | SM-Intelligence',
    loadComponent: () => import('./setup').then((m) => m.Setup),
  },
  { path: '**', redirectTo: '' },
];
