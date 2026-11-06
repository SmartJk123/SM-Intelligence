import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { IdleTimeoutService } from '../core/idle-timeout.service';
import { PAGE_LABELS, Page } from '../core/data';
import { ThemeService } from '../core/theme.service';
import { UiService } from '../core/ui.service';
import { InviteModal } from './invite-modal';
import { LogoutModal } from './logout-modal';
import { Sidebar } from './sidebar';
import { TopBar } from './topbar';
import { NotificationDialog } from '../shared/notification-dialog';
import { ToastOutlet } from '../shared/toast-outlet';

function toPage(segment: string): Page {
  return (segment in PAGE_LABELS ? segment : 'dashboard') as Page;
}

@Component({
  selector: 'app-admin-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, Sidebar, TopBar, InviteModal, LogoutModal, NotificationDialog, ToastOutlet],
  templateUrl: './admin-layout.html',
  host: { class: 'block h-full' },
})
export class AdminLayout {
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly theme = inject(ThemeService);
  protected readonly ui = inject(UiService);
  /** Signs out after 15 minutes without interaction, warning a minute before. */
  protected readonly idle = inject(IdleTimeoutService);

  constructor() {
    this.idle.start();
    inject(DestroyRef).onDestroy(() => this.idle.stop());
  }

  private readonly navigationUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly activePage = computed<Page>(() => {
    const segment = this.navigationUrl().split('/')[2] ?? '';
    return toPage(segment);
  });

  protected navigate(page: Page): void {
    void this.router.navigate(['/admin', page]);
  }

  protected goToProfile(): void {
    this.ui.closeSidebar();
    void this.router.navigate(['/admin', 'settings']);
  }

  protected confirmLogout(): void {
    this.idle.stop();
    this.ui.closeLogout();
    this.auth.logout();
    void this.router.navigate(['/admin/login']);
  }
}
