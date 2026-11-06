import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NAV, NAV_GROUPS, Page } from '../core/data';
import { NotificationService } from '../core/notification.service';
import { StaffAccessService } from '../core/staff-access.service';

@Component({
  selector: 'app-sidebar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './sidebar.html',
})
export class Sidebar {
  private readonly notifications = inject(NotificationService);

  readonly active = input.required<Page>();
  /** ?type= of the open page, so a sub-item (Individuals, Organisations) can be highlighted. */
  readonly activeType = input('');
  readonly open = input(false);

  readonly closed = output<void>();
  readonly logout = output<void>();

  private readonly staffAccess = inject(StaffAccessService);

  /** Only the pages the administrator's staff role allows; empty groups are left out. */
  protected readonly navGroups = computed(() => {
    this.staffAccess.access();
    return NAV_GROUPS.map((name) => ({
      name,
      items: NAV.filter((item) => item.group === name && this.staffAccess.can(item.id)),
    })).filter((group) => group.items.length > 0);
  });

  /** Parents whose sub-items are shown (Customers opens Individuals and Organisations). */
  protected readonly expanded = signal<ReadonlySet<Page>>(new Set());

  constructor() {
    void this.staffAccess.load();
    // Opening a page with sub-items, from anywhere, unfolds it.
    effect(() => {
      const active = this.active();
      if (NAV.some((item) => item.id === active && item.children)) {
        this.expanded.update((open) => new Set(open).add(active));
      }
    });
  }

  protected isExpanded(item: Page): boolean {
    return this.expanded().has(item);
  }

  protected toggle(item: Page, event: Event): void {
    event.preventDefault();
    event.stopPropagation();
    this.expanded.update((open) => {
      const next = new Set(open);
      if (next.has(item)) next.delete(item);
      else next.add(item);
      return next;
    });
  }
  protected readonly unread = this.notifications.unreadCount;

  protected isActive(item: Page): boolean {
    return this.active() === item;
  }

  protected isChildActive(item: Page, query: string): boolean {
    return this.active() === item && this.activeType() === query;
  }

  /** Inline styles win over utility classes, so hover is applied directly. */
  protected hoverIn(event: Event): void {
    (event.currentTarget as HTMLElement).style.background = 'var(--surface-2)';
  }

  protected hoverOut(event: Event): void {
    (event.currentTarget as HTMLElement).style.background = 'transparent';
  }
}
