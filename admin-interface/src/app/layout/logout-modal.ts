import { ChangeDetectionStrategy, Component, output } from '@angular/core';

@Component({
  selector: 'app-logout-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="fixed inset-0 z-50 flex items-center justify-center animate-fade-in"
      style="background: rgba(0,0,0,.5)"
    >
      <div
        class="w-80 rounded-2xl border p-6 animate-fade-up text-center"
        style="background: var(--surface); border-color: var(--border); box-shadow: var(--shadow-lg)"
      >
        <div
          class="w-12 h-12 rounded-full flex items-center justify-center mx-auto mb-4 text-xl"
          style="background: var(--red-soft); color: var(--red)"
        >⇥</div>
        <h3 class="text-sm font-bold mb-1 font-display" style="color: var(--text-1)">Confirm Logout</h3>
        <p class="text-[13px] mb-5" style="color: var(--text-2)">
          Are you sure you want to log out of the admin portal?
        </p>
        <div class="flex gap-3">
          <button
            type="button"
            (click)="cancelled.emit()"
            class="flex-1 py-2 rounded-xl text-[13px] font-semibold border"
            style="border-color: var(--border); color: var(--text-2)"
          >
            Cancel
          </button>
          <button
            type="button"
            (click)="confirmed.emit()"
            class="flex-1 py-2 rounded-xl text-[13px] font-semibold text-white"
            style="background: var(--red)"
          >
            Log Out
          </button>
        </div>
      </div>
    </div>
  `,
})
export class LogoutModal {
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
