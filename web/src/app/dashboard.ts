import { Component } from '@angular/core';
@Component({
  template: `
    <section class="section dashboard-page">
      <p class="eyebrow">YOUR WORKSPACE</p>
      <h1>User dashboard</h1>
      <p class="muted">Welcome back. Your account setup is complete.</p>
      <div class="feature">
        <h2>Your financial overview</h2>
        <p>
          Your dashboard is ready for the next development phase. Account summaries, balances, and
          transactions will appear here once the financial data service is connected.
        </p>
      </div>
    </section>
  `,
})
export class Dashboard {}
