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
            <span class="feature-icon" aria-hidden="true">{{ feature.icon }}</span>
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
      icon: '🗃️',
      title: 'Account consolidation',
      text: 'Keep manual deposit and credit snapshots from your bank accounts in one place.',
    },
    {
      icon: '🔄',
      title: 'Cash-flow tracking',
      text: 'See money coming in and going out, organized by period, account, and category.',
    },
    {
      icon: '🩺',
      title: 'Business health score',
      text: 'Planned: meaningful indicators based on an approved methodology and sufficient data.',
    },
    {
      icon: '🔮',
      title: '30-day forecast',
      text: 'Planned: understand what may be ahead as forecasting becomes available.',
    },
    {
      icon: '📈',
      title: 'Budget monitoring',
      text: 'Bring category budgets and spending into the same view.',
    },
    {
      icon: '📊',
      title: 'Visual analytics',
      text: 'Turn financial records into charts, trends, and reports that are easier to understand.',
    },
  ];
}
