import { ChangeDetectionStrategy, Component, input, linkedSignal, output } from '@angular/core';

@Component({
  selector: 'app-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="flex items-center justify-between py-2.5 border-b last:border-0" style="border-color: var(--border)">
      <span class="text-sm" style="color: var(--text-1)">{{ label() }}</span>
      <button
        type="button"
        (click)="toggle()"
        class="w-10 h-5 rounded-full relative transition-all flex-shrink-0"
        [style.background]="on() ? 'var(--green)' : 'var(--border)'"
      >
        <span
          class="absolute top-0.5 transition-all w-4 h-4 rounded-full bg-white shadow"
          [style.left]="on() ? 'calc(100% - 18px)' : '2px'"
        ></span>
      </button>
    </div>
  `,
})
export class Toggle {
  readonly label = input.required<string>();
  readonly defaultChecked = input(false);

  readonly checked = output<boolean>();

  protected readonly on = linkedSignal(() => this.defaultChecked());

  protected toggle(): void {
    const next = !this.on();
    this.on.set(next);
    this.checked.emit(next);
  }
}
