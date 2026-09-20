import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NAV, NAV_GROUPS, Page } from '../core/data';
import { NotificationService } from '../core/notification.service';

@Component({
  selector: 'app-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './sidebar.html',
})
export class Sidebar {
  private readonly notifications = inject(NotificationService);

  readonly active = input.required<Page>();
  readonly open = input(false);

  readonly closed = output<void>();
  readonly logout = output<void>();

  protected readonly navGroups = NAV_GROUPS.map((name) => ({
    name,
    items: NAV.filter((item) => item.group === name),
  }));
  protected readonly unread = this.notifications.unreadCount;

  protected isActive(item: Page): boolean {
    return this.active() === item;
  }

  /** Inline styles win over utility classes, so hover is applied directly. */
  protected hoverIn(event: Event): void {
    (event.currentTarget as HTMLElement).style.background = 'var(--surface-2)';
  }

  protected hoverOut(event: Event): void {
    (event.currentTarget as HTMLElement).style.background = 'transparent';
  }
}
