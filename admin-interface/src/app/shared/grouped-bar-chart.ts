import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FlowSeries } from '../core/dashboard-data';

interface BarRect {
  x: number;
  y: number;
  w: number;
  h: number;
  color: string;
  path: string;
}

interface BarGroup {
  cx: number;
  label: string;
  showLabel: boolean;
  bars: BarRect[];
  values: { name: string; color: string; value: number }[];
  topY: number;
}

const PAD = { top: 12, right: 6, bottom: 28, left: 46 };

/**
 * Grouped bar chart with dashed gridlines and a hover readout.
 * Width is measured from the container so bars stay crisp at any size.
 */
@Component({
  selector: 'app-grouped-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative w-full" #surface>
      <svg [attr.width]="width()" [attr.height]="height()" role="img" [attr.aria-label]="ariaLabel()">
        <!-- Gridlines and value axis -->
        @for (line of geometry().gridLines; track line.y) {
          <line
            [attr.x1]="PAD.left"
            [attr.x2]="width() - PAD.right"
            [attr.y1]="line.y"
            [attr.y2]="line.y"
            stroke="var(--border-strong)"
            stroke-width="1"
            stroke-dasharray="4 5"
            opacity="0.65"
          ></line>
          <text
            [attr.x]="PAD.left - 10"
            [attr.y]="line.y + 4"
            text-anchor="end"
            font-size="10.5"
            fill="var(--text-3)"
          >{{ line.label }}</text>
        }

        <!-- Bars -->
        @for (group of geometry().groups; track group.label; let groupIndex = $index) {
          @for (bar of group.bars; track $index) {
            <path
              [attr.d]="bar.path"
              [attr.fill]="bar.color"
              [attr.opacity]="hoverIndex() === null || hoverIndex() === groupIndex ? 1 : 0.35"
              class="animate-bar"
              [style.animation-delay]="(groupIndex * 18) + 'ms'"
            ></path>
          }

          @if (group.showLabel) {
            <text
              [attr.x]="group.cx"
              [attr.y]="height() - 8"
              text-anchor="middle"
              font-size="10.5"
              fill="var(--text-3)"
            >{{ group.label }}</text>
          }
        }

        <!-- Hover guide -->
        @if (hoverGroup(); as hovered) {
          <line
            [attr.x1]="hovered.cx"
            [attr.x2]="hovered.cx"
            [attr.y1]="PAD.top"
            [attr.y2]="height() - PAD.bottom"
            stroke="var(--border-strong)"
            stroke-width="1"
          ></line>
        }

        <!-- Pointer capture layer -->
        <rect
          [attr.x]="PAD.left"
          [attr.y]="PAD.top"
          [attr.width]="Math.max(0, width() - PAD.left - PAD.right)"
          [attr.height]="Math.max(0, height() - PAD.top - PAD.bottom)"
          fill="transparent"
          (mousemove)="onMove($event)"
          (mouseleave)="hoverIndex.set(null)"
        ></rect>
      </svg>

      @if (hoverGroup(); as hovered) {
        <div
          class="absolute z-20 pointer-events-none rounded-xl px-3 py-2 animate-fade-in"
          style="background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-lg); transform: translate(-50%, -100%)"
          [style.left.px]="tooltipLeft(hovered.cx)"
          [style.top.px]="hovered.topY - 12"
        >
          <p class="text-[11px] font-semibold mb-1" style="color: var(--text-3)">{{ hovered.label }}</p>
          @for (value of hovered.values; track value.name) {
            <p class="flex items-center gap-2 text-[12px] whitespace-nowrap">
              <span class="w-2 h-2 rounded-full" [style.background]="value.color"></span>
              <span style="color: var(--text-2)">{{ value.name }}</span>
              <span class="ml-auto font-semibold tabular" style="color: var(--text-1)">
                {{ value.value.toLocaleString('en-US') }}k
              </span>
            </p>
          }
        </div>
      }
    </div>
  `,
})
export class GroupedBarChart implements OnDestroy {
  private readonly host = inject(ElementRef<HTMLElement>);
  private observer?: ResizeObserver;

  readonly labels = input.required<string[]>();
  readonly series = input.required<FlowSeries[]>();
  readonly labelEvery = input(1);
  readonly height = input(264);
  readonly ariaLabel = input('Grouped bar chart');

  protected readonly PAD = PAD;
  protected readonly Math = Math;
  protected readonly width = signal(720);
  protected readonly hoverIndex = signal<number | null>(null);

  constructor() {
    afterNextRender(() => {
      const element = this.host.nativeElement as HTMLElement;
      this.width.set(Math.max(320, Math.round(element.clientWidth)));
      this.observer = new ResizeObserver((entries) => {
        const measured = Math.round(entries[0]?.contentRect.width ?? 0);
        if (measured > 0) {
          this.width.set(Math.max(320, measured));
        }
      });
      this.observer.observe(element);
    });
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  protected readonly geometry = computed(() => {
    const width = this.width();
    const height = this.height();
    const labels = this.labels();
    const series = this.series();

    const plotWidth = Math.max(10, width - PAD.left - PAD.right);
    const plotHeight = Math.max(10, height - PAD.top - PAD.bottom);

    const all = series.flatMap((item) => item.values);
    const rawMax = all.length > 0 ? Math.max(...all) : 1;
    const step = niceStep(rawMax / 4);
    const max = Math.max(step * 4, rawMax);

    const groupWidth = labels.length > 0 ? plotWidth / labels.length : plotWidth;
    const barGap = 3;
    const clusterWidth = Math.min(groupWidth * 0.62, 54);
    const barWidth = Math.max(4, (clusterWidth - barGap * (series.length - 1)) / series.length);

    const groups: BarGroup[] = labels.map((label, index) => {
      const clusterStart = PAD.left + groupWidth * index + (groupWidth - clusterWidth) / 2;
      const bars: BarRect[] = series.map((item, seriesIndex) => {
        const value = item.values[index] ?? 0;
        const barHeight = Math.max(2, (value / max) * plotHeight);
        const x = clusterStart + seriesIndex * (barWidth + barGap);
        const y = PAD.top + (plotHeight - barHeight);
        return {
          x,
          y,
          w: barWidth,
          h: barHeight,
          color: item.color,
          path: roundedTopBar(x, y, barWidth, barHeight, Math.min(6, barWidth / 2)),
        };
      });

      const topY = bars.length > 0 ? Math.min(...bars.map((bar) => bar.y)) : PAD.top;

      return {
        cx: PAD.left + groupWidth * (index + 0.5),
        label,
        showLabel: index % Math.max(1, this.labelEvery()) === 0,
        bars,
        topY,
        values: series.map((item, seriesIndex) => ({
          name: item.name,
          color: item.color,
          value: item.values[index] ?? 0,
        })),
      };
    });

    const gridLines = [0, 1, 2, 3, 4].map((index) => {
      const value = (max / 4) * (4 - index);
      return { y: PAD.top + (plotHeight / 4) * index, label: compact(value) };
    });

    return { groups, gridLines, plotWidth };
  });

  protected readonly hoverGroup = computed(() => {
    const index = this.hoverIndex();
    return index === null ? null : this.geometry().groups[index] ?? null;
  });

  protected onMove(event: MouseEvent): void {
    const bounds = (event.currentTarget as SVGRectElement).getBoundingClientRect();
    const offset = event.clientX - bounds.left;
    const plotWidth = Math.max(1, bounds.width);
    const groupWidth = plotWidth / Math.max(1, this.labels().length);
    const index = Math.min(this.labels().length - 1, Math.max(0, Math.floor(offset / groupWidth)));
    this.hoverIndex.set(index);
  }

  /** Keeps the tooltip inside the chart area. */
  protected tooltipLeft(cx: number): number {
    return Math.min(Math.max(cx, 90), Math.max(90, this.width() - 90));
  }
}

function roundedTopBar(x: number, y: number, w: number, h: number, r: number): string {
  const radius = Math.max(0, Math.min(r, w / 2, h));
  if (radius <= 0.5) {
    return `M${x},${y} h${w} v${h} h${-w} Z`;
  }
  return [
    `M${x},${y + h}`,
    `V${y + radius}`,
    `Q${x},${y} ${x + radius},${y}`,
    `H${x + w - radius}`,
    `Q${x + w},${y} ${x + w},${y + radius}`,
    `V${y + h}`,
    'Z',
  ].join(' ');
}

function niceStep(value: number): number {
  if (value <= 0) {
    return 1;
  }
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  const normalised = value / magnitude;
  const nice = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
  return nice * magnitude;
}

function compact(value: number): string {
  if (value >= 1000) {
    const thousands = value / 1000;
    return `${thousands % 1 === 0 ? thousands.toFixed(0) : thousands.toFixed(1)}k`;
  }
  return value.toFixed(0);
}
