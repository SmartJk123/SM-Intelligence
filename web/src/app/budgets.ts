// Monthly category spending limits, with progress against real posted debits.
import { Component, effect, inject, signal } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, timeout } from 'rxjs';
import { LiveUpdates } from './live-updates';

interface BudgetView {
  id: string;
  category: string;
  monthlyLimitMinor: number;
  spentMinor: number;
  percentUsed: number;
  alertThresholdPercentage: number;
}

@Component({
  imports: [CurrencyPipe],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">STAY WITHIN YOUR LIMITS</p>
          <h1>Budgets</h1>
          <p class="muted">A monthly KES limit per category, tracked against real posted spending.</p>
        </div>
        <button class="button" (click)="adding.set(!adding())">{{ adding() ? 'Cancel' : 'Add budget' }}</button>
      </header>
      @if (adding()) {
        <form class="dashboard-panel" (ngSubmit)="save()" style="margin-bottom: 20px">
          <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: end">
            <label style="flex: 1; min-width: 180px">
              <span style="display: block; font-size: 12px; margin-bottom: 4px; color: #6b8095">Category</span>
              <input [value]="category()" (input)="category.set($any($event.target).value)" placeholder="e.g. Groceries" style="width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #dce6f1" />
            </label>
            <label style="width: 160px">
              <span style="display: block; font-size: 12px; margin-bottom: 4px; color: #6b8095">Monthly limit (KES)</span>
              <input type="number" min="0" step="1" [value]="limit()" (input)="limit.set($any($event.target).value)" style="width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #dce6f1" />
            </label>
            <button type="submit" class="button" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save budget' }}</button>
          </div>
          @if (formError()) { <p class="field-error" style="margin-top: 10px">{{ formError() }}</p> }
        </form>
      }
      @if (loading()) {
        <div class="dashboard-loading" role="status" aria-live="polite"><span class="loading-dot"></span>Loading budgets…</div>
      }
      @if (error()) {
        <div class="dashboard-empty" role="alert">
          <h2>Connection unavailable</h2>
          <p>{{ error() }}</p>
          <button class="button" (click)="load()">Try again</button>
        </div>
      }
      @if (budgets(); as list) {
        @if (!list.length) {
          <div class="dashboard-empty">
            <h2>No budgets yet</h2>
            <p>Add one above to start tracking a category against a monthly limit.</p>
          </div>
        } @else {
          <div class="dashboard-hybrid">
            @for (b of list; track b.id) {
              <article class="dashboard-panel" style="grid-column: 1 / -1">
                <div class="panel-heading">
                  <div>
                    <h2>{{ b.category }}</h2>
                    <p>
                      {{ b.spentMinor / 100 | currency: 'KES' : 'code' }} of
                      {{ b.monthlyLimitMinor / 100 | currency: 'KES' : 'code' }} this month
                    </p>
                  </div>
                  <button class="button secondary" (click)="remove(b.id)">Remove</button>
                </div>
                <div style="height: 10px; border-radius: 999px; background: #edf2f7; overflow: hidden">
                  <div
                    [style.width.%]="clampPercent(b.percentUsed)"
                    [style.background]="b.percentUsed >= 100 ? '#c0392b' : b.percentUsed >= b.alertThresholdPercentage ? '#c9a227' : '#16856a'"
                    style="height: 100%"
                  ></div>
                </div>
                <p class="dashboard-footnote" style="margin-top: 10px">
                  {{ b.percentUsed }}% used
                  @if (b.percentUsed >= 100) { — over the limit }
                  @else if (b.percentUsed >= b.alertThresholdPercentage) { — approaching the limit }
                </p>
              </article>
            }
          </div>
        }
      }
    </section>
  `,
})
export class Budgets {
  private http = inject(HttpClient);
  private readonly live = inject(LiveUpdates);
  readonly budgets = signal<BudgetView[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly adding = signal(false);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly category = signal('');
  readonly limit = signal('');

  constructor() {
    void this.load();
    effect(() => {
      if (this.live.lastUpdate() === null) return;
      void this.load();
    });
  }

  clampPercent(value: number): number {
    return Math.min(100, Math.max(0, value));
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const d = await firstValueFrom(this.http.get<{ budgets: BudgetView[] }>('/api/budgets').pipe(timeout(15000)));
      if (!Array.isArray(d?.budgets)) throw new Error('Invalid response');
      this.budgets.set(d.budgets);
    } catch {
      this.budgets.set(null);
      this.error.set('We could not load your budgets. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  async save(): Promise<void> {
    this.formError.set('');
    const category = this.category().trim();
    const limit = Number(this.limit());
    if (!category) { this.formError.set('Enter a category.'); return; }
    if (!Number.isFinite(limit) || limit < 0) { this.formError.set('Enter a monthly limit of 0 or more.'); return; }
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.http.post('/api/budgets', { category, monthlyLimit: limit }).pipe(timeout(15000)),
      );
      this.category.set('');
      this.limit.set('');
      this.adding.set(false);
      await this.load();
    } catch (error: any) {
      this.formError.set(error?.error?.error || 'The budget could not be saved. Please retry.');
    } finally {
      this.saving.set(false);
    }
  }

  async remove(id: string): Promise<void> {
    if (!confirm('Remove this budget? You can add it again later.')) return;
    try {
      await firstValueFrom(this.http.delete('/api/budgets/' + id).pipe(timeout(15000)));
      await this.load();
    } catch {
      this.error.set('The budget could not be removed. Please retry.');
    }
  }
}
