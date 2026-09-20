import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-settings-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div>
      <label class="text-[12px] font-semibold mb-1 block uppercase tracking-wider" style="color: var(--text-3)">
        {{ label() }}
      </label>
      <ng-content />
      @if (hint()) {
        <p class="text-[12px] mt-1" style="color: var(--text-3)">{{ hint() }}</p>
      }
    </div>
  `,
})
export class SettingsField {
  readonly label = input.required<string>();
  readonly hint = input<string>();
}
