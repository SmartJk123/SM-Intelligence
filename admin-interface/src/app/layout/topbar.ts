import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { PAGE_META, Page, Theme } from '../core/data';
import { NotificationService } from '../core/notification.service';
import { AdminProfileService } from '../core/admin-profile.service';
import { GlobalSearch } from './global-search';
import { NotifDropdown } from './notif-dropdown';

@Component({
  selector: 'app-topbar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [GlobalSearch, NotifDropdown],
  templateUrl: './topbar.html',
  host: {
    '(document:mousedown)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'closeMenus()',
  },
})
export class TopBar {
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly profile = inject(AdminProfileService);

  readonly page = input.required<Page>();
  readonly theme = input.required<Theme>();

  readonly menuToggle = output<void>();
  readonly themeToggle = output<void>();
  readonly navigate = output<Page>();
  readonly logout = output<void>();
  readonly profileClick = output<void>();

  protected readonly showNotifications = signal(false);
  protected readonly showProfileMenu = signal(false);

  protected readonly unread = this.notifications.unreadCount;

  protected readonly meta = computed(() => PAGE_META[this.page()]);

  protected toggleNotifications(): void {
    this.showProfileMenu.set(false);
    this.showNotifications.update((open) => !open);
  }

  protected closeNotifications(): void {
    this.showNotifications.set(false);
  }

  protected viewAllNotifications(): void {
    this.navigate.emit('notifications');
  }

  protected toggleProfileMenu(): void {
    this.showNotifications.set(false);
    this.showProfileMenu.update((open) => !open);
  }

  protected openProfile(): void {
    this.showProfileMenu.set(false);
    void this.router.navigate(['/admin', 'settings'], { queryParams: { section: 'Admin Profile' } });
  }

  protected openSettings(): void {
    this.showProfileMenu.set(false);
    void this.router.navigate(['/admin', 'settings']);
  }

  protected requestLogout(): void {
    this.showProfileMenu.set(false);
    this.logout.emit();
  }

  protected closeMenus(): void {
    this.showNotifications.set(false);
    this.showProfileMenu.set(false);
  }

  protected onDocumentClick(event: MouseEvent): void {
    const target = event.target as Node;
    if (this.showProfileMenu() && !this.host.nativeElement.contains(target)) {
      this.showProfileMenu.set(false);
    }
  }
}
