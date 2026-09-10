import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <header class="site-header">
      <a class="brand" routerLink="/"
        ><span class="system-logo" aria-hidden="true"></span
        ><span>SM-Intelligence<small>SMARTMONEY</small></span></a
      >
      <nav aria-label="Main navigation">
        <a routerLink="/" fragment="features">Features</a
        ><a routerLink="/" fragment="how">How it works</a><a routerLink="/login">Sign in</a
        ><a class="button small" routerLink="/register">Get started</a>
      </nav>
    </header>
    <main id="main"><router-outlet /></main>
    <footer>
      <a class="brand" routerLink="/"
        ><span class="system-logo" aria-hidden="true"></span>SM-Intelligence</a
      ><span>Every shilling. One clear view.</span><span>© 2026 SM-Intelligence</span>
    </footer>
  `,
})
export class App {}
