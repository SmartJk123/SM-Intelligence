import { ChangeDetectionStrategy, Component, ElementRef, inject, output } from '@angular/core';
import { AppNotification, SEVERITY_BACKGROUNDS, SEVERITY_COLORS } from '../core/data';
import { NotificationService } from '../core/notification.service';

@Component({
  selector: 'app-notif-dropdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notif-dropdown.html',
  host: {
    class:
      'absolute right-0 top-full mt-2 rounded-2xl border z-50 animate-slide-down overflow-hidden block w-[calc(100vw-2rem)] sm:w-[22rem] max-w-[22rem]',
    '[style.background]': '"var(--surface)"',
    '[style.borderColor]': '"var(--border)"',
    '[style.boxShadow]': '"var(--shadow-lg)"',
    '(document:mousedown)': 'onDocumentMousedown($event)',
  },
})
export class NotifDropdown {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly notifications = inject(NotificationService);

  readonly viewAll = output<void>();
  readonly closed = output<void>();

  protected readonly items = this.notifications.preview;
  protected readonly unread = this.notifications.unreadCount;
  protected readonly severityColors = SEVERITY_COLORS;
  protected readonly severityBackgrounds = SEVERITY_BACKGROUNDS;

  protected itemBackground(notification: AppNotification): string {
    return notification.read ? 'transparent' : SEVERITY_BACKGROUNDS[notification.severity];
  }

  protected openNotification(notification: AppNotification): void {
    this.notifications.open(notification);
    this.closed.emit();
  }

  protected onDocumentMousedown(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.closed.emit();
    }
  }

  protected onViewAll(): void {
    this.viewAll.emit();
    this.closed.emit();
  }
}
