import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CHART_DATA } from '../core/data';

@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="flex items-end gap-2 h-36 px-2">
      @for (point of data; track point.label) {
        <div class="flex-1 flex flex-col items-center gap-1">
          <div class="w-full flex gap-0.5 items-end h-28">
            <div
              class="flex-1 rounded-t-md transition-all duration-500"
              [style.height]="barHeight(point.inc)"
              style="background: var(--green); opacity: .8"
            ></div>
            <div
              class="flex-1 rounded-t-md transition-all duration-500"
              [style.height]="barHeight(point.exp)"
              style="background: var(--red); opacity: .7"
            ></div>
          </div>
          <span class="text-[10px]" style="color: var(--text-3)">{{ point.label }}</span>
        </div>
      }
    </div>
  `,
})
export class BarChart {
  protected readonly data = CHART_DATA;
  private readonly max = Math.max(...CHART_DATA.flatMap((point) => [point.inc, point.exp]));

  protected barHeight(value: number): string {
    return `${(value / this.max) * 100}%`;
  }
}
