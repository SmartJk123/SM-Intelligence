// Monthly category spending limits, with progress against real posted debits.
// The customer allocates each expense to a budget, so spending lands in the right one.
import { Component, computed, effect, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
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

interface ExpenseView {
  id: string;
  date: string;
  description: string;
  amountMinor: number;
  allocatedTo: string | null;
}

/** Common categories offered when adding a budget; the customer can still type their own. */
export const SUGGESTED_CATEGORIES = [
  'Groceries', 'Rent', 'Utilities', 'Transport', 'Fuel', 'Airtime and data', 'Eating out',
  'Health', 'Education', 'Shopping', 'Entertainment', 'Salaries', 'Supplies', 'Savings', 'Investments', 'Other',
];

const field = 'width: 100%; padding: 10px 12px; border-radius: 10px; border: 1px solid #dce6f1; background: #fff';

@Component({
  imports: [CurrencyPipe, DatePipe],
  template: `
    <section class="dashboard-page">
      <header class="dashboard-heading">
        <div>
          <p class="eyebrow">STAY WITHIN YOUR LIMITS</p>
          <h1>Budgets</h1>
          <p class="muted">Set a monthly KES limit per category, then allocate each expense to the budget it was for.</p>
        </div>
        <button class="button" (click)="adding.set(!adding())">{{ adding() ? 'Cancel' : 'Add budget' }}</button>
      </header>
      @if (adding()) {
        <form class="dashboard-panel" (submit)="$event.preventDefault(); save()" style="margin-bottom: 20px">
          <div style="display: flex; gap: 12px; flex-wrap: wrap; align-items: end">
            <label style="flex: 1; min-width: 200px">
              <span style="display: block; font-size: 12px; margin-bottom: 4px; color: #6b8095">Category</span>
              <select [value]="categoryChoice()" (change)="categoryChoice.set($any($event.target).value)" style="${field}">
                <option value="" disabled>Choose a category</option>
                @for (c of availableCategories(); track c) { <option [value]="c">{{ c }}</option> }
                <option value="__custom">Something else…</option>
              </select>
            </label>
            @if (categoryChoice() === '__custom') {
              <label style="flex: 1; min-width: 180px">
                <span style="display: block; font-size: 12px; margin-bottom: 4px; color: #6b8095">Category name</span>
                <input [value]="customCategory()" (input)="customCategory.set($any($event.target).value)" maxlength="60" placeholder="e.g. School fees" style="${field}" />
              </label>
            }
            <label style="width: 160px">
              <span style="display: block; font-size: 12px; margin-bottom: 4px; color: #6b8095">Monthly limit (KES)</span>
              <input type="number" min="0" step="1" [value]="limit()" (input)="limit.set($any($event.target).value)" style="${field}" />
            </label>
            <button type="submit" class="button" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save budget' }}</button>
          </div>
          @if (formError()) { <p class="field-error" style="margin-top: 10px">{{ formError() }}</p> }
        </form>
      }
      @if (loading() && !budgets()) {
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
        <div class="dashboard-metrics" aria-label="This month at a glance">
          <article>
            <p>Money out this month</p>
            <strong>{{ summary().moneyOutMinor / 100 | currency: 'KES' : 'code' }}</strong>
            <small>{{ expenses().length }} posted expenses from your accounts</small>
          </article>
          <article>
            <p>Counted in budgets</p>
            <strong>{{ summary().inBudgetsMinor / 100 | currency: 'KES' : 'code' }}</strong>
            <small>of {{ summary().budgetedMinor / 100 | currency: 'KES' : 'code' }} budgeted</small>
          </article>
          <article>
            <p>Left to spend</p>
            <strong [class.positive]="summary().leftMinor >= 0">{{ summary().leftMinor / 100 | currency: 'KES' : 'code' }}</strong>
            <small>Budgeted minus spent</small>
          </article>
          <article>
            <p>Not allocated</p>
            <strong>{{ unallocated().totalMinor / 100 | currency: 'KES' : 'code' }}</strong>
            <small>{{ unallocated().count }} expenses to allocate below</small>
          </article>
        </div>
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
                  @if (b.percentUsed >= 100) { · over the limit }
                  @else if (b.percentUsed >= b.alertThresholdPercentage) { · approaching the limit }
                </p>
              </article>
            }
          </div>
        }

        <article class="dashboard-panel" style="margin-top: 20px">
          <div class="panel-heading">
            <div>
              <h2>Allocate this month's expenses</h2>
              <p>
                Choose what each payment was for, and it counts towards that budget.
                @if (unallocated().count) {
                  {{ unallocated().count }} not allocated ({{ unallocated().totalMinor / 100 | currency: 'KES' : 'code' }}).
                } @else if (expenses().length) {
                  Every expense is allocated.
                }
              </p>
            </div>
            @if (allocatedCount()) {
              <button class="button secondary" type="button" (click)="showAllocated.set(!showAllocated())">
                {{ showAllocated() ? 'Hide allocated' : 'Show allocated (' + allocatedCount() + ')' }}
              </button>
            }
          </div>
          @if (allocateMessage()) { <p class="dashboard-footnote positive" role="status">{{ allocateMessage() }}</p> }
          @if (!expenses().length) {
            <p class="muted">No posted expenses yet this month. They appear here as your banks report them.</p>
          } @else {
            @if (!list.length) {
              <p class="muted" style="margin-bottom: 10px">Add a budget first, then allocate expenses to it.</p>
            }
            @if (!visibleExpenses().length) {
              <p class="muted">Everything this month is allocated. Use "Show allocated" to review or change them.</p>
            } @else {
            <div class="dashboard-table-wrap" tabindex="0" aria-label="This month's expenses">
              <table>
                <thead>
                  <tr><th>Date</th><th>Paid to</th><th style="text-align: right">Amount</th><th>Allocated to</th></tr>
                </thead>
                <tbody>
                  @for (e of visibleExpenses(); track e.id) {
                    <tr>
                      <td>{{ e.date | date: 'd MMM' }}</td>
                      <td>{{ e.description }}</td>
                      <td style="text-align: right">{{ e.amountMinor / 100 | currency: 'KES' : 'code' }}</td>
                      <td>
                        <select
                          [attr.aria-label]="'Budget for ' + e.description"
                          [disabled]="allocating() === e.id || !list.length"
                          (change)="allocate(e, $any($event.target).value)"
                          style="padding: 6px 10px; border-radius: 8px; border: 1px solid #dce6f1; background: #fff; min-width: 160px"
                        >
                          <option value="" [selected]="!e.allocatedTo">Not allocated</option>
                          @for (b of list; track b.id) {
                            <option [value]="b.category" [selected]="e.allocatedTo === b.category">{{ b.category }}</option>
                          }
                          @if (e.allocatedTo && !hasBudget(e.allocatedTo)) {
                            <option [value]="e.allocatedTo" selected>{{ e.allocatedTo }} (no budget)</option>
                          }
                        </select>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            }
            @if (allocateError()) { <p class="field-error" style="margin-top: 10px">{{ allocateError() }}</p> }
          }
        </article>
      }
    </section>
  `,
})
export class Budgets {
  private http = inject(HttpClient);
  private readonly live = inject(LiveUpdates);
  readonly budgets = signal<BudgetView[] | null>(null);
  readonly expenses = signal<ExpenseView[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly adding = signal(false);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly categoryChoice = signal('');
  readonly customCategory = signal('');
  readonly limit = signal('');
  readonly allocating = signal<string | null>(null);
  /** Allocated expenses are hidden from the list unless the customer asks to see them. */
  readonly showAllocated = signal(false);
  readonly allocateMessage = signal('');
  readonly allocatedCount = computed(() => this.expenses().filter((e) => e.allocatedTo).length);
  readonly visibleExpenses = computed(() =>
    this.showAllocated() ? this.expenses() : this.expenses().filter((e) => !e.allocatedTo));
  readonly allocateError = signal('');

  /** Suggested categories that do not have a budget yet. */
  readonly availableCategories = computed(() => {
    const taken = new Set((this.budgets() ?? []).map((b) => b.category.toLowerCase()));
    return SUGGESTED_CATEGORIES.filter((c) => !taken.has(c.toLowerCase()));
  });

  readonly unallocated = computed(() => {
    const open = this.expenses().filter((e) => !e.allocatedTo);
    return { count: open.length, totalMinor: open.reduce((sum, e) => sum + e.amountMinor, 0) };
  });

  /** Budget totals next to this month's money out, for the summary cards. */
  readonly summary = computed(() => {
    const list = this.budgets() ?? [];
    const budgetedMinor = list.reduce((s, b) => s + b.monthlyLimitMinor, 0);
    const inBudgetsMinor = list.reduce((s, b) => s + b.spentMinor, 0);
    return {
      moneyOutMinor: this.expenses().reduce((s, e) => s + e.amountMinor, 0),
      budgetedMinor,
      inBudgetsMinor,
      leftMinor: budgetedMinor - inBudgetsMinor,
    };
  });

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

  hasBudget(category: string): boolean {
    return (this.budgets() ?? []).some((b) => b.category === category);
  }

  async load(): Promise<void> {
    this.loading.set(true);
    this.error.set('');
    try {
      const d = await firstValueFrom(
        this.http.get<{ budgets: BudgetView[]; expenses?: ExpenseView[] }>('/api/budgets').pipe(timeout(15000)),
      );
      if (!Array.isArray(d?.budgets)) throw new Error('Invalid response');
      this.budgets.set(d.budgets);
      this.expenses.set(Array.isArray(d.expenses) ? d.expenses : []);
    } catch {
      this.budgets.set(null);
      this.error.set('We could not load your budgets. Please try again.');
    } finally {
      this.loading.set(false);
    }
  }

  async save(): Promise<void> {
    this.formError.set('');
    const choice = this.categoryChoice();
    const category = (choice === '__custom' ? this.customCategory() : choice).trim();
    const limit = Number(this.limit());
    if (!category) { this.formError.set('Choose a category.'); return; }
    if (this.limit() === '' || !Number.isFinite(limit) || limit < 0) { this.formError.set('Enter a monthly limit of 0 or more.'); return; }
    this.saving.set(true);
    try {
      await firstValueFrom(
        this.http.post('/api/budgets', { category, monthlyLimit: limit }).pipe(timeout(15000)),
      );
      this.categoryChoice.set('');
      this.customCategory.set('');
      this.limit.set('');
      this.adding.set(false);
      await this.load();
    } catch (error: any) {
      this.formError.set(error?.error?.error || 'The budget could not be saved. Please retry.');
    } finally {
      this.saving.set(false);
    }
  }

  async allocate(expense: ExpenseView, category: string): Promise<void> {
    this.allocateError.set('');
    this.allocateMessage.set('');
    this.allocating.set(expense.id);
    try {
      await firstValueFrom(
        this.http.post('/api/workspace/allocations', { transactionId: expense.id, category }).pipe(timeout(15000)),
      );
      this.allocateMessage.set(category
        ? expense.description + ' (' + (expense.amountMinor / 100).toFixed(2) + ' KES) allocated to ' + category + '.'
        : 'Allocation cleared for ' + expense.description + '.');
      await this.load();
    } catch (error: any) {
      this.allocateError.set(error?.error?.error || 'That allocation could not be saved. Please retry.');
      await this.load();
    } finally {
      this.allocating.set(null);
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
