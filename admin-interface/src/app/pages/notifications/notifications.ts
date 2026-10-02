import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AppNotification,
  NOTIFICATION_DESTINATIONS,
  PAGE_LABELS,
  SEVERITY_COLORS,
  Severity,
} from '../../core/data';
import { NotificationService } from '../../core/notification.service';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

type ReadFilter = 'All' | 'Unread';
type SeverityFilter = 'All' | Severity;

@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge],
  templateUrl: './notifications.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class NotificationsPage {
  private readonly notifications = inject(NotificationService);

  protected readonly severityColors = SEVERITY_COLORS;
  protected readonly readFilters: ReadFilter[] = ['All', 'Unread'];
  protected readonly severityFilters: SeverityFilter[] = ['All', 'Critical', 'Warning', 'Info'];

  protected readonly readFilter = signal<ReadFilter>('All');
  protected readonly severityFilter = signal<SeverityFilter>('All');

  protected readonly total = computed(() => this.notifications.all().length);
  protected readonly unread = this.notifications.unreadCount;
  protected readonly critical = this.notifications.criticalCount;
  protected readonly warnings = this.notifications.warningCount;

  protected readonly shown = computed(() =>
    this.notifications
      .all()
      .filter(
        (notification) =>
          (this.readFilter() === 'All' || !notification.read) &&
          (this.severityFilter() === 'All' || notification.severity === this.severityFilter()),
      ),
  );

  protected open(notification: AppNotification): void {
    this.notifications.open(notification);
  }

  protected markAllRead(): void {
    this.notifications.markAllRead();
  }

  protected destination(category: string): string {
    return PAGE_LABELS[NOTIFICATION_DESTINATIONS[category] ?? 'notifications'];
  }

  protected delayFor(index: number): string {
    return `delay-${(index + 1) * 50}`;
  }

  /** KPI cards act as filters for the list below them. */
  protected applyCardAction(action: string): void {
    switch (action) {
      case 'unread':
        this.readFilter.set('Unread');
        this.severityFilter.set('All');
        break;
      case 'critical':
        this.readFilter.set('All');
        this.severityFilter.set('Critical');
        break;
      case 'warning':
        this.readFilter.set('All');
        this.severityFilter.set('Warning');
        break;
      default:
        this.readFilter.set('All');
        this.severityFilter.set('All');
        break;
    }
  }
}
