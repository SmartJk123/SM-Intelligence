import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal } from '@angular/core';

export interface ActionMenuItem {
  id: string;
  label: string;
  tone?: 'default' | 'danger' | 'primary';
}

/** Row level overflow menu, used on every table. */
@Component({
  selector: 'app-action-menu',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative inline-flex">
      <button
        type="button"
        (click)="toggle($event)"
        [attr.aria-expanded]="open()"
        aria-label="More actions"
        class="w-8 h-8 rounded-lg flex items-center justify-center transition-all hover:opacity-80"
        [style.background]="open() ? 'var(--surface-3)' : 'transparent'"
        style="color: var(--text-2)"
      >
        <span class="icon">more_vert</span>
      </button>

      @if (open()) {
        <div
          class="absolute right-0 top-full mt-1 z-50 min-w-[184px] panel py-1.5 animate-slide-down"
          style="box-shadow: var(--shadow-lg)"
        >
          @for (item of items(); track item.id) {
            <button
              type="button"
              (click)="choose(item, $event)"
              class="w-full text-left px-3.5 py-2 text-[12.5px] font-medium transition-all"
              style="background: transparent"
              [style.color]="itemColor(item)"
              (mouseenter)="$any($event.target).style.background = 'var(--surface-2)'"
              (mouseleave)="$any($event.target).style.background = 'transparent'"
            >
              {{ item.label }}
            </button>
          }
        </div>
      }
    </div>
  `,
  host: {
    '(document:mousedown)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'open.set(false)',
  },
})
export class ActionMenu {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly items = input<ActionMenuItem[]>([]);
  readonly chosen = output<string>();

  protected readonly open = signal(false);

  protected toggle(event: MouseEvent): void {
    event.stopPropagation();
    this.open.update((value) => !value);
  }

  protected choose(item: ActionMenuItem, event: MouseEvent): void {
    event.stopPropagation();
    this.open.set(false);
    this.chosen.emit(item.id);
  }

  protected itemColor(item: ActionMenuItem): string {
    if (item.tone === 'danger') {
      return 'var(--red-ink)';
    }
    return item.tone === 'primary' ? 'var(--primary-ink)' : 'var(--text-1)';
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
