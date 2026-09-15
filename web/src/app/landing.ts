import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BankLogo } from './bank-logo';
@Component({
  imports: [RouterLink, BankLogo],
  template: `
    <section class="hero">
      <div class="hero-copy">
        <p class="eyebrow">YOUR MONEY, CONNECTED</p>
        <h1>Every shilling.<br /><span>One clear view.</span></h1>
        <p class="lead">
          Bring your accounts, cash flow, and financial decisions together. Make sense of your money
          and move forward with confidence.
        </p>
        <div class="actions">
          <a class="button navy" routerLink="/register">Get started</a
          ><a class="button glass" href="#how">See how it works</a>
        </div>
        <p class="hero-note">Built for individuals. Ready for growing businesses.</p>
      </div>

      <div class="hero-visual">
        <div class="orbit"></div>
        <article class="preview">
          <div class="preview-top">
            <span>Financial overview</span><span class="pill">Illustrative preview</span>
          </div>
          <p class="eyebrow">TOTAL BALANCE</p>
          <div class="amount">KES 284,500<span>.00</span></div>
          <p class="muted">Across your connected view</p>
          <div class="metric-row">
            <div><small>Money in</small><strong>KES 156,000</strong></div>
            <div><small>Money out</small><strong>KES 92,400</strong></div>
          </div>
          <div class="chart" role="img" aria-label="Illustrative monthly cash flow bar chart">
            <i style="height:38%"></i><i style="height:59%"></i><i style="height:46%"></i
            ><i style="height:75%"></i><i style="height:61%"></i><i style="height:90%"></i
            ><i style="height:71%"></i><i style="height:100%"></i>
          </div>
          <div class="chart-labels">
            <span>JAN</span><span>FEB</span><span>MAR</span><span>APR</span>
          </div>
        </article>
        <div class="floating">
          <span class="check">✓</span>
          <div>
            <strong>A clearer financial picture</strong><small>Your accounts, in one place</small>
          </div>
        </div>
      </div>
    </section>

    <div class="bank-strip">
      <span>A single view for your bank accounts</span><app-bank-logo bank="KCB" /><app-bank-logo
        bank="NCBA"
      /><app-bank-logo bank="Equity" /><app-bank-logo bank="Stanbic" /><small
        >Manual account entry · No live bank connections yet</small
      >
    </div>
    <section class="section" id="features">
      <div class="section-heading">
        <p class="eyebrow">LESS GUESSWORK. MORE CLARITY.</p>
        <h2>Your finances.<br />A fuller picture.</h2>
        <p>
          From the everyday details to the bigger decisions, make your money easier to understand.
        </p>
      </div>
      <div class="feature-grid">
        @for (feature of features; track feature.title) {
          <article class="feature">
            <span class="feature-icon" aria-hidden="true">
              <svg viewBox="0 0 32 32" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" focusable="false">
                <path [attr.d]="feature.icon" />
              </svg>            </span>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
          </article>
        }
      </div>
    </section>
    <section class="how section" id="how">
      <p class="eyebrow">A SIMPLE START</p>
      <h2>From scattered accounts<br />to a clear direction.</h2>
      <div class="steps">
        <article>
          <span>01</span>
          <h3>Create your account</h3>
          <p>Choose an Individual or Organization profile and add your details.</p>
        </article>
        <article>
          <span>02</span>
          <h3>Set up your workspace</h3>
          <p>Add your business details and a manual opening account snapshot.</p>
        </article>
        <article>
          <span>03</span>
          <h3>Build your financial picture</h3>
          <p>
            Your workspace will bring your records together as financial modules become available.
          </p>
        </article>
      </div>
      <a class="text-link" routerLink="/register">Create your account →</a>
    </section>
    <section class="section faq" id="faq">
      <div>
        <p class="eyebrow">GOOD TO KNOW</p>
        <h2>A few questions,<br />answered.</h2>
      </div>
      <div>
        <details>
          <summary>Who is SM-Intelligence for?</summary>
          <p>
            Individuals, freelancers, sole traders, and organizations looking for a clearer view of
            their finances.
          </p>
        </details>
        <details>
          <summary>Does this connect to my bank?</summary>
          <p>
            Not yet. Setup supports manual account snapshots. Live bank connections and payments are
            not part of this release.
          </p>
        </details>
        <details>
          <summary>How do I get started?</summary>
          <p>
            Create an account or sign in, then add your bank account details to complete account
            setup.
          </p>
        </details>
        <details>
          <summary>Can I switch between light and dark mode?</summary>
          <p>
            Light mode is the default. The appearance preference will live in customer Settings.
          </p>
        </details>
      </div>
    </section>
    <section class="cta">
      <p class="eyebrow">YOUR NEXT CHAPTER STARTS HERE</p>
      <h2>Make room for better<br />financial decisions.</h2>
      <a class="button navy" routerLink="/register">Get started with SM-Intelligence</a>
    </section>
  `,
})
export class Landing {
  features = [
    {
      icon: 'M3 10l13-7 13 7z M5 13v10 M11 13v10 M17 13v10 M23 13v5 M3 26h15 M2 29h16 M24 20c-3 0-5 1-5 3s2 3 5 3 5-1 5-3-2-3-5-3 M19 23v4c0 2 2 3 5 3s5-1 5-3v-4',
      title: 'Account consolidation',
      text: 'Keep manual deposit and credit snapshots from your bank accounts in one place.',
    },
    {
      icon: 'M6 9h20l-4-4 M26 23H6l4 4 M5 13V9 M27 19v4 M21 16a5 5 0 1 1-10 0 5 5 0 0 1 10 0 M16 13v6 M18 14h-3a1 1 0 0 0 0 2h2a1 1 0 0 1 0 2h-3',
      title: 'Cash-flow tracking',
      text: 'See money coming in and going out, organized by period, account, and category.',
    },
    {
      icon: 'M3 27V15h5v12 M12 27V10h5v17 M21 19V5h5v13 M2 29h16 M3 10l9-5 5 2 9-5 M22 2h4v4 M20 25l3 3 6-7',
      title: 'Business health score',
      text: 'Planned: meaningful indicators based on an approved methodology and sufficient data.',
    },
    {
      icon: 'M4 7h24v22H4z M4 12h24 M10 3v7 M22 3v7 M8 24l5-6 5 3 6-6 M20 15h4v4',
      title: '30-day forecast',
      text: 'Planned: understand what may be ahead as forecasting becomes available.',
    },
    {
      icon: 'M6 15c2-4 8-6 13-4l5-3v6l4 3v6h-4l-2 5h-4v-4h-7v4H7l-2-6H2v-6h4 M24 17h.1 M11 13h6 M17 5a3 3 0 1 1-6 0 3 3 0 0 1 6 0 M29 13c2-2 2-4 0-4',
      title: 'Budget monitoring',
      text: 'Bring category budgets and spending into the same view.',
    },
    {
      icon: 'M3 3v26h26 M7 25v-7h4v7 M15 25V14h4v11 M23 25V9h4v16 M6 13l7-6 6 3 9-7 M23 3h5v5',
      title: 'Visual analytics',
      text: 'Turn financial records into charts, trends, and reports that are easier to understand.',
    },
  ];
}
