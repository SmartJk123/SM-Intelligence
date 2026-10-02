import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

interface PillStyle {
  bg: string;
  fg: string;
  dot: string;
}

const NEUTRAL: PillStyle = {
  bg: 'var(--surface-3)',
  fg: 'var(--text-2)',
  dot: 'var(--text-3)',
};

const GREEN: PillStyle = {
  bg: 'var(--green-soft)',
  fg: 'var(--green-ink)',
  dot: 'var(--green)',
};

const GOLD: PillStyle = {
  bg: 'var(--gold-soft)',
  fg: 'var(--gold-ink)',
  dot: 'var(--gold)',
};

const RED: PillStyle = {
  bg: 'var(--red-soft)',
  fg: 'var(--red-ink)',
  dot: 'var(--red)',
};

const VIOLET: PillStyle = {
  bg: 'var(--primary-soft)',
  fg: 'var(--primary-ink)',
  dot: 'var(--primary)',
};

const CYAN: PillStyle = {
  bg: 'var(--cyan-soft)',
  fg: 'var(--cyan-ink)',
  dot: 'var(--cyan)',
};

const PILL_STYLES: Record<string, PillStyle> = {
  CONNECTED: GREEN,
  HEALTHY: GREEN,
  Active: GREEN,
  Connected: GREEN,
  Matched: GREEN,
  Processed: GREEN,
  Success: GREEN,
  Reconciled: GREEN,

  WARNING: GOLD,
  Warning: GOLD,
  Pending: GOLD,
  PENDING: GOLD,
  'Needs Review': GOLD,
  Partial: GOLD,
  Inactive: NEUTRAL,

  ERROR: RED,
  DISCONNECTED: RED,
  Failed: RED,
  Duplicate: RED,
  Unmatched: RED,
  Suspended: RED,
  Critical: RED,

  Info: VIOLET,
  New: VIOLET,
  Transfer: CYAN,
};

/** Status pill with a colour dot. Used for every state on the platform. */
@Component({
  selector: 'app-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="pill" [style.background]="style().bg" [style.color]="style().fg">
      <span class="w-1.5 h-1.5 rounded-full flex-shrink-0" [style.background]="style().dot"></span>
      {{ status() }}
    </span>
  `,
})
export class Badge {
  readonly status = input.required<string>();

  protected readonly style = computed(() => PILL_STYLES[this.status()] ?? NEUTRAL);
}
