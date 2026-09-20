import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const AVATAR_GRADIENTS = [
  'linear-gradient(to bottom right,#2563eb,#4f46e5)',
  'linear-gradient(to bottom right,#059669,#0d9488)',
  'linear-gradient(to bottom right,#d97706,#dc2626)',
  'linear-gradient(to bottom right,#7c3aed,#2563eb)',
];

@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      [class]="classes()"
      [style.background]="gradient()"
    >
      {{ initials() }}
    </div>
  `,
})
export class Avatar {
  readonly name = input.required<string>();
  readonly size = input<'sm' | 'md'>('sm');

  protected readonly initials = computed(() =>
    this.name()
      .split(' ')
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase(),
  );

  protected readonly classes = computed(() => {
    const size = this.size() === 'md' ? 'w-9 h-9 text-[13px]' : 'w-7 h-7 text-[12px]';
    return `rounded-full flex items-center justify-center text-white font-bold flex-shrink-0 ${size}`;
  });

  protected readonly gradient = computed(() => {
    const name = this.name();
    const index = name.length > 0 ? name.charCodeAt(0) % AVATAR_GRADIENTS.length : 0;
    return AVATAR_GRADIENTS[index];
  });
}
