import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const SIZES: Record<'sm' | 'md' | 'lg', string> = {
  sm: 'w-8 h-8',
  md: 'w-12 h-12',
  lg: 'w-16 h-10',
};

@Component({
  selector: 'app-bank-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      [class]="classes()"
      style="border-color: var(--border)"
    >
      <img [src]="src()" [alt]="name() + ' logo'" class="w-full h-full object-contain p-0.5" />
    </div>
  `,
})
export class BankLogo {
  readonly src = input.required<string>();
  readonly name = input.required<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');

  protected readonly classes = computed(
    () =>
      `rounded-xl overflow-hidden bg-white flex items-center justify-center flex-shrink-0 border ${SIZES[this.size()]}`,
  );
}
