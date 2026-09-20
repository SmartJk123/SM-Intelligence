import { Injectable, signal } from '@angular/core';

export type ToastTone = 'success' | 'info' | 'warning' | 'danger';

export interface ToastMessage {
  id: number;
  title: string;
  detail?: string;
  tone: ToastTone;
}

/** Short confirmations for actions such as export, auto-match and saving. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly messages = signal<ToastMessage[]>([]);
  private nextId = 1;

  readonly toasts = this.messages.asReadonly();

  show(title: string, tone: ToastTone = 'success', detail?: string): void {
    const id = this.nextId++;
    this.messages.update((current) => [...current, { id, title, detail, tone }]);
    setTimeout(() => this.dismiss(id), 4000);
  }

  dismiss(id: number): void {
    this.messages.update((current) => current.filter((toast) => toast.id !== id));
  }
}
