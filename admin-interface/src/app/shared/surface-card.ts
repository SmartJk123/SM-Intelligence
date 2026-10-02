import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * The base card for the whole application: a white panel on the lavender page,
 * with a soft shadow and a generous radius.
 */
@Component({
  selector: 'app-surface-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<ng-content />',
  host: {
    class: 'block panel animate-fade-up',
    '[style.animation-delay]': 'animationDelay()',
    '[style.outlineStyle]': 'outline() ? "solid" : null',
    '[style.outlineWidth]': 'outline() ? "2px" : null',
    '[style.outlineColor]': 'outline() ? "var(--primary)" : null',
  },
})
export class SurfaceCard {
  readonly animDelay = input('');
  readonly outline = input(false);

  /** Mirrors the `.delay-*` utility classes as an inline animation-delay. */
  protected readonly animationDelay = computed(() => {
    const match = /delay-(\d+)/.exec(this.animDelay());
    return match ? `${Number(match[1]) / 1000}s` : '';
  });
}
