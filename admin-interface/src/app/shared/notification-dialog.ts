import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { NOTIFICATION_DESTINATIONS, PAGE_LABELS, SEVERITY_COLORS } from '../core/data';
import { NotificationService } from '../core/notification.service';
import { Badge } from './badge';

/**
 * Message view for a single notification. Opened from the top bar dropdown and
 * from the notification page. Includes an explicit close button, a backdrop
 * click handler and Escape key support.
 */
@Component({
  selector: 'app-notification-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge],
  templateUrl: './notification-dialog.html',
  host: {
    '(document:keydown.escape)': 'close()',
  },
})
export class NotificationDialog {
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly severityColors = SEVERITY_COLORS;
  protected readonly notification = this.notifications.selected;

  protected severityColor(): string {
    const notification = this.notification();
    return notification ? this.severityColors[notification.severity] : 'var(--text-3)';
  }

  protected destination(): string {
    const notification = this.notification();
    if (!notification) {
      return '';
    }
    const page = NOTIFICATION_DESTINATIONS[notification.cat] ?? 'notifications';
    return PAGE_LABELS[page];
  }

  protected openArea(): void {
    const notification = this.notification();
    if (!notification) {
      return;
    }
    const page = NOTIFICATION_DESTINATIONS[notification.cat] ?? 'notifications';
    this.notifications.close();
    void this.router.navigate(['/admin', page]);
  }

  protected close(): void {
    this.notifications.close();
  }
}
