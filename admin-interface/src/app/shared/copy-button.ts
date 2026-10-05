import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';

/** Copies a value to the clipboard, with a fallback for insecure contexts. */
@Component({
  selector: 'app-copy-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      (click)="copy()"
      [attr.aria-label]="copied() ? 'Copied' : label()"
      class="flex items-center gap-1.5 text-[12px] font-semibold px-3 py-2 rounded-xl border transition-all hover:opacity-80 whitespace-nowrap"
      style="border-color: var(--border-strong); color: var(--text-2); background: var(--surface)"
    >
      <span class="icon icon-sm">{{ copied() ? 'check' : 'content_copy' }}</span>
      {{ copied() ? 'Copied' : label() }}
    </button>
  `,
})
export class CopyButton {
  readonly value = input.required<string>();
  readonly label = input('Copy');

  protected readonly copied = signal(false);
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected async copy(): Promise<void> {
    const text = this.value();
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      } else {
        this.copyWithFallback(text);
      }
    } catch {
      this.copyWithFallback(text);
    }
    this.copied.set(true);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.copied.set(false), 1800);
  }

  private copyWithFallback(text: string): void {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand('copy');
    } catch {
      // Nothing further we can do; the URL remains selectable on screen.
    }
    document.body.removeChild(area);
  }
}
