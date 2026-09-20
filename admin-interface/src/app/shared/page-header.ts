import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Page title block. Sits directly under the top bar so every screen states what
 * it is and why it matters, which the previous build left to the breadcrumb.
 */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div class="min-w-0">
        <p class="text-[11px] font-semibold uppercase tracking-[0.14em]" style="color: var(--text-3)">
          {{ eyebrow() }}
        </p>
        <h1 class="mt-1 text-xl font-bold font-display leading-tight" style="color: var(--text-1)">
          {{ title() }}
        </h1>
        @if (subtitle()) {
          <p class="mt-1 text-[13px]" style="color: var(--text-2)">{{ subtitle() }}</p>
        }
      </div>
      <ng-content />
    </header>
  `,
})
export class PageHeader {
  readonly eyebrow = input('');
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
