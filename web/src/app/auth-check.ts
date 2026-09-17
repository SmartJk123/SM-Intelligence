import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AccountApi } from './account-api';
@Component({
  imports: [RouterLink],
  template: `
    <section class="auth-panel">
      @if (api.authenticated()) {
        <p class="eyebrow">SAMPLE BACKEND TEST</p>
        <h1>Sign-in successful</h1>
        <p role="status">Signed in as {{ api.email() }}.</p>
        <p>The backend accepted your credentials and returned an access token. Financial workspace endpoints are not available in this sample.</p>
        <button class="button" (click)="api.logout()">Sign out</button>
      } @else {
        <h1>You are signed out</h1>
        <p>Your session has ended. Sign in again to continue testing.</p>
        <a class="button" routerLink="/login">Sign in</a>
      }
    </section>
  `,
})
export class AuthCheck { readonly api = inject(AccountApi); }
