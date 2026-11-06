import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';
import { AdminAccountGateway } from '../core/admin-account.gateway';
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

  /** Settings, Security asks staff without two-factor to set it up. */
  protected readonly mfaReminder = signal(false);
  private readonly account = inject(AdminAccountGateway);

  constructor() {
    this.idle.start();
    inject(DestroyRef).onDestroy(() => this.idle.stop());
    void this.applySecuritySettings();
  }

  /** Applies the saved idle limit and the two-factor reminder. The defaults stay if this fails. */
  private async applySecuritySettings(): Promise<void> {
    try {
      const [settings, me] = await Promise.all([this.account.settings(), this.account.me()]);
      const security = settings['security']?.values ?? {};
      this.idle.configure(Number(security['sessionTimeoutMinutes']));
      this.mfaReminder.set(security['requireAdminMfa'] === true && me.mfaEnabled !== true);
    } catch {
      // Older identity-service or unreachable: keep the 15 minute default.
    }
  }

  protected setUpMfa(): void {
    this.mfaReminder.set(false);
    void this.router.navigate(['/admin', 'settings'], { queryParams: { section: 'Security' } });
  }

  private readonly navigationUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly activePage = computed<Page>(() => {
    // Drop ?query and #fragment first: /admin/settings?section=Profile is still Settings.
    const path = this.navigationUrl().split(/[?#]/)[0];
    const segment = path.split('/')[2] ?? '';
    return toPage(segment);
  });

  /** The ?type= of the Customers page (individual or organisation), or '' for all. */
  protected readonly activeType = computed(() => {
    const query = this.navigationUrl().split('#')[0].split('?')[1] ?? '';
    return new URLSearchParams(query).get('type') ?? '';
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
