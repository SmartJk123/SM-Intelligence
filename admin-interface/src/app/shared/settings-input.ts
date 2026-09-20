import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'app-settings-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <input
      [type]="type()"
      [value]="defaultValue()"
      [placeholder]="placeholder()"
      (input)="valueChange.emit($any($event.target).value)"
      class="w-full px-3 py-2.5 text-sm rounded-xl border focus:outline-none transition-all"
      style="background: var(--surface-2); border-color: var(--border); color: var(--text-1)"
    />
  `,
})
export class SettingsInput {
  readonly defaultValue = input<string>();
  readonly type = input('text');
  readonly placeholder = input<string>();
  readonly valueChange = output<string>();
}
