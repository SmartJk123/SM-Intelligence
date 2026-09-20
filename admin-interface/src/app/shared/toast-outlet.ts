import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService, ToastTone } from '../core/toast.service';

@Component({
  selector: 'app-toast-outlet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fixed bottom-5 right-5 z-[70] flex flex-col gap-2 w-[min(360px,calc(100vw-2.5rem))]">
      @for (toast of toasts(); track toast.id) {
        <div
          class="panel flex items-start gap-3 px-4 py-3 animate-fade-up"
          style="box-shadow: var(--shadow-lg)"
        >
          <span
            class="w-6 h-6 rounded-full flex items-center justify-center text-[12px] font-bold flex-shrink-0 mt-0.5"
            [style.background]="toneBackground(toast.tone)"
            [style.color]="toneColor(toast.tone)"
          >{{ toneIcon(toast.tone) }}</span>
          <div class="flex-1 min-w-0">
            <p class="text-[13px] font-semibold" style="color: var(--text-1)">{{ toast.title }}</p>
            @if (toast.detail) {
              <p class="text-[12px] mt-0.5 leading-relaxed" style="color: var(--text-2)">{{ toast.detail }}</p>
            }
          </div>
          <button
            type="button"
            (click)="dismiss(toast.id)"
            aria-label="Dismiss"
            class="text-[13px] leading-none flex-shrink-0 mt-0.5"
            style="color: var(--text-3)"
          >✕</button>
        </div>
      }
    </div>
  `,
})
export class ToastOutlet {
  private readonly toastsService = inject(ToastService);

  protected readonly toasts = this.toastsService.toasts;

  protected dismiss(id: number): void {
    this.toastsService.dismiss(id);
  }

  protected toneIcon(tone: ToastTone): string {
    if (tone === 'success') {
      return '✓';
    }
    return tone === 'danger' ? '✕' : tone === 'warning' ? '!' : 'i';
  }

  protected toneColor(tone: ToastTone): string {
    switch (tone) {
      case 'success':
        return 'var(--green-ink)';
      case 'warning':
        return 'var(--gold-ink)';
      case 'danger':
        return 'var(--red-ink)';
      default:
        return 'var(--primary-ink)';
    }
  }

  protected toneBackground(tone: ToastTone): string {
    switch (tone) {
      case 'success':
        return 'var(--green-soft)';
      case 'warning':
        return 'var(--gold-soft)';
      case 'danger':
        return 'var(--red-soft)';
      default:
        return 'var(--primary-soft)';
    }
  }
}
