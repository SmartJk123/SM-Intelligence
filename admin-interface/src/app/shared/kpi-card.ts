import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { AccentColor, Page } from '../core/data';
import { ActionMenu, ActionMenuItem } from './action-menu';

interface AccentTokens {
  ink: string;
  soft: string;
  solid: string;
}

const ACCENTS: Record<AccentColor, AccentTokens> = {
  blue: { ink: 'var(--primary-ink)', soft: 'var(--primary-soft)', solid: 'var(--primary)' },
  green: { ink: 'var(--green-ink)', soft: 'var(--green-soft)', solid: 'var(--green)' },
  gold: { ink: 'var(--gold-ink)', soft: 'var(--gold-soft)', solid: 'var(--gold)' },
  red: { ink: 'var(--red-ink)', soft: 'var(--red-soft)', solid: 'var(--red)' },
};

const DEFAULT_ICONS: Record<AccentColor, string> = {
  blue: 'analytics',
  green: 'check_circle',
  gold: 'hexagon',
  red: 'warning',
};

/**
 * Headline metric. Label, value and an optional change line, matching the
 * reference dashboard: quiet by default, with the number as the loudest element.
 */
@Component({
  selector: 'app-kpi-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ActionMenu],
  template: `
    <div
      [class]="classes()"
      [attr.role]="interactive() ? 'button' : null"
      [attr.tabindex]="interactive() ? 0 : null"
      (click)="activate()"
      (keydown.enter)="activate()"
      (keydown.space)="activate(); $event.preventDefault()"
    >
      <div class="flex items-start justify-between gap-3">
        <p class="text-[13px] font-medium leading-snug" style="color: var(--text-2)">{{ label() }}</p>

        @if (menuItems().length > 0) {
          <app-action-menu [items]="menuItems()" (chosen)="menuAction.emit($event)" />
        } @else if (icon()) {
          <span
            class="icon-tile w-8 h-8"
            [style.background]="accent().soft"
            [style.color]="accent().ink"
          ><span class="icon icon-sm">{{ glyph() }}</span></span>
        }
      </div>

      <p class="mt-3.5 text-[26px] font-bold font-display leading-none tabular" style="color: var(--text-1)">
        {{ value() }}
      </p>

      @if (trend() || sub()) {
        <div class="mt-2.5 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-[12px]">
          @if (trend()) {
            <span class="font-semibold inline-flex items-center gap-0.5" [style.color]="trendColor()">
              <span class="icon icon-sm">{{ trendUp() ? 'arrow_upward' : 'arrow_downward' }}</span> {{ trend() }}
            </span>
          }
          @if (sub()) {
            <span style="color: var(--text-3)">{{ sub() }}</span>
          }
        </div>
      }
    </div>
  `,
})
export class KpiCard {
  private readonly router = inject(Router);

  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly sub = input<string>();
  readonly color = input<AccentColor>('blue');
  readonly icon = input<string>();
  readonly delay = input('');

  /** Change value shown after an arrow, for example "2.5%". */
  readonly trend = input<string>();
  readonly trendUp = input(true);
  /** Overrides the colour of the change line when up is not good news. */
  readonly trendPositive = input<boolean | undefined>(undefined);

  /** Navigates to another admin page when the card is activated. */
  readonly link = input<Page>();

  /** Emits an identifier so the host page can react, for example by filtering. */
  readonly action = input<string>();

  readonly menuItems = input<ActionMenuItem[]>([]);

  readonly selected = output<string>();
  readonly menuAction = output<string>();

  protected readonly accent = computed(() => ACCENTS[this.color()]);
  protected readonly glyph = computed(() => this.icon() ?? DEFAULT_ICONS[this.color()]);

  protected readonly interactive = computed(() => !!this.link() || !!this.action());

  protected readonly trendColor = computed(() =>
    (this.trendPositive() ?? this.trendUp()) ? 'var(--green-ink)' : 'var(--red-ink)',
  );

  protected readonly classes = computed(() => {
    const base = `panel stat-card h-full w-full p-5 ${this.delay()}`;
    return this.interactive() ? `${base} stat-card--interactive cursor-pointer` : base;
  });

  protected activate(): void {
    const link = this.link();
    if (link) {
      void this.router.navigate(['/admin', link]);
      return;
    }
    const action = this.action();
    if (action) {
      this.selected.emit(action);
    }
  }
}
