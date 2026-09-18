// Reusable SVG/chart components for financial trends and category breakdowns.
import { smoothChartPath } from './smooth-chart';
import { Component, input, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';

@Component({
  selector: 'app-finance-chart',
  imports: [CurrencyPipe, DatePipe],
  template: `
    <div class="viz-controls" role="group" aria-label="Chart series">
      <button [attr.aria-pressed]="series() === 'both'" (click)="series.set('both')">Compare</button
      ><button [attr.aria-pressed]="series() === 'in'" (click)="series.set('in')">↗ Money in</button
      ><button [attr.aria-pressed]="series() === 'out'" (click)="series.set('out')">
        ↘ Money out
      </button>
    </div>
    <p class="viz-hint">Select a period to explore its values · KES</p>
    @if (mode() === 'line') {
      <div class="hybrid-line-chart">
        <div class="hybrid-scale"><span>0 KES</span><span>Scale: {{ maximum() / 100 | currency: 'KES' : 'code' : '1.0-0' }}</span></div>
        <svg viewBox="0 0 600 200" role="img" aria-label="Cash flow trend. Use the period buttons below for exact values.">
          <path d="M10 10H590 M10 95H590 M10 180H590" class="hybrid-grid" />
          @if (series() !== 'out') { <path [attr.d]="linePath('in') + ' L590,180 L10,180 Z'" class="hybrid-area" /><path [attr.d]="linePath('in')" class="hybrid-income" /> }
          @if (series() !== 'in') { <path [attr.d]="linePath('out')" class="hybrid-outflow" /> }
          @if (points()[selected()]) {
            <line [attr.x1]="pointX(selected())" [attr.x2]="pointX(selected())" y1="10" y2="180" class="hybrid-guide" />
            @if (series() !== 'out') { <circle [attr.cx]="pointX(selected())" [attr.cy]="pointY(selected(), 'in')" r="4" class="hybrid-income-dot" /> }
            @if (series() !== 'in') { <circle [attr.cx]="pointX(selected())" [attr.cy]="pointY(selected(), 'out')" r="4" class="hybrid-outflow-dot" /> }
          }
        </svg>
        <div class="hybrid-periods">
          @for (p of points(); track p.from; let i = $index) {
            <button (click)="selected.set(i)" [attr.aria-pressed]="selected() === i">{{ p.from | date: 'd MMM' : 'UTC' }}</button>
          }
        </div>
      </div>
    } @else {
    <div class="viz-plot">
      <div class="viz-axis">
        <span>{{ maximum() / 100 | currency: 'KES' : '' : '1.0-0' }}</span
        ><span>{{ maximum() / 200 | currency: 'KES' : '' : '1.0-0' }}</span
        ><span>0</span>
      </div>
      <div class="viz-columns">
        @for (p of points(); track p.from; let i = $index) {
          <button
            class="viz-column"
            [class.selected]="selected() === i"
            (click)="selected.set(i)"
            [attr.aria-pressed]="selected() === i"
            [attr.aria-label]="'Inspect ' + p.from + ' to ' + p.to"
          >
            <span class="viz-bars">
              @if (series() !== 'out') {
                <span class="viz-in" [style.height.%]="(p.in / maximum()) * 100"></span>
              }
              @if (series() !== 'in') {
                <span class="viz-out" [style.height.%]="(p.out / maximum()) * 100"></span>
              }</span
            ><small>{{ p.from | date: 'd MMM' : 'UTC' }}</small>
          </button>
        }
      </div>
    </div>
    }
    @if (points()[selected()]; as p) {
      <div class="viz-detail" aria-live="polite">
        <strong>{{ p.from | date: 'd MMM' : 'UTC' }} – {{ p.to | date: 'd MMM' : 'UTC' }}</strong
        ><span class="money-in">↗ In {{ p.in / 100 | currency: 'KES' : 'code' }}</span
        ><span class="money-out">↘ Out {{ p.out / 100 | currency: 'KES' : 'code' }}</span
        ><span>Net {{ (p.in - p.out) / 100 | currency: 'KES' : 'code' }}</span>
      </div>
    }
  `,
})

export class FinanceChart {
  readonly mode = input('bar');
  linePath(key: 'in' | 'out') {
    return smoothChartPath(this.points().map(p => p[key]), this.maximum());
  }
  pointX(index: number) { return 10 + index * 580 / Math.max(1, this.points().length - 1); }
  pointY(index: number, key: 'in' | 'out') { return 180 - this.points()[index][key] / this.maximum() * 170; }
  readonly points = input.required<{ from: string; to: string; in: number; out: number }[]>();
  readonly selected = signal(0);
  readonly series = signal('both');
  maximum() {
    return Math.max(
      1,
      ...this.points().flatMap((p) =>
        this.series() === 'in' ? [p.in] : this.series() === 'out' ? [p.out] : [p.in, p.out],
      ),
    );
  }
}

@Component({
  selector: 'app-category-chart',
  imports: [CurrencyPipe],
  template: `
    <div class="category-viz">
      <div
        class="category-ring"
        [style.background]="gradient()"
        role="img"
        aria-label="Category share of posted debits. Select a labeled category for exact values."
      >
        <div>
          <small>{{ selected() || 'Total posted debits' }}</small
          ><strong>{{ value() / 100 | currency: 'KES' : 'code' }}</strong
          ><span>{{ selected() ? percent(value()) + '% of total' : 'All categories' }}</span>
        </div>
      </div>
      <div class="category-legend">
        <button (click)="selected.set('')" [attr.aria-pressed]="!selected()">
          All categories <strong>100%</strong>
        </button>
        @for (p of points(); track p.category; let i = $index) {
          <button
            (click)="selected.set(p.category)"
            [attr.aria-pressed]="selected() === p.category"
          >
            <i [style.background]="colors[i % colors.length]"></i><span>{{ p.category }}</span
            ><strong>{{ percent(p.value) }}%</strong>
          </button>
        }
      </div>
    </div>
    <p class="viz-hint" aria-live="polite">
      {{ selected() || 'All categories' }}: {{ value() / 100 | currency: 'KES' : 'code' }} in posted
      debits.
    </p>
  `,
})

export class CategoryChart {
  readonly points = input.required<{ category: string; value: number }[]>();
  readonly selected = signal('');
  readonly colors = ['#2563EB', '#4F46E5', '#0891B2', '#7C3AED', '#64748B', '#C9A227'];
  total() {
    return this.points().reduce((s, p) => s + p.value, 0);
  }
  value() {
    return this.points().find((p) => p.category === this.selected())?.value ?? this.total();
  }
  percent(v: number) {
    return this.total() ? Math.round((v / this.total()) * 100) : 0;
  }
  gradient() {
    let start = 0;
    return (
      'conic-gradient(' +
      this.points()
        .map((p, i) => {
          const end = start + (p.value / Math.max(1, this.total())) * 100;
          const color =
            this.selected() && p.category !== this.selected()
              ? 'var(--ws-line)'
              : this.colors[i % this.colors.length];
          const stop = color + ' ' + start + '% ' + end + '%';
          start = end;
          return stop;
        })
        .join(',') +
      ')'
    );
  }
}
