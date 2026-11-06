import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivityGateway, EmailTemplateDto, OutboxEmailDto } from '../../core/activity.gateway';
import {
  AppNotification,
  NOTIFICATION_DESTINATIONS,
  PAGE_LABELS,
  SEVERITY_COLORS,
  Severity,
} from '../../core/data';
import { NotificationService, relativeTime } from '../../core/notification.service';
import { ToastService } from '../../core/toast.service';
import { Badge } from '../../shared/badge';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

type ReadFilter = 'All' | 'Unread';
type SeverityFilter = 'All' | Severity;
type Tab = 'notifications' | 'outbox' | 'templates';

/**
 * Notifications (new sign ups, sign ins, security and system warnings), the
 * outbox of every email the platform sent, and the email templates an
 * administrator can edit. All three come from identity-service.
 */
@Component({
  selector: 'app-notifications',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge],
  templateUrl: './notifications.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class NotificationsPage {
  protected readonly notifications = inject(NotificationService);
  private readonly gateway = inject(ActivityGateway);
  private readonly toasts = inject(ToastService);

  protected readonly tabs: { id: Tab; label: string; icon: string }[] = [
    { id: 'notifications', label: 'Notifications', icon: 'notifications' },
    { id: 'outbox', label: 'Outbox', icon: 'outbox' },
    { id: 'templates', label: 'Templates', icon: 'mail' },
  ];
  protected readonly tab = signal<Tab>('notifications');

  // ---- Notifications ----
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

  // ---- Outbox ----
  protected readonly outbox = signal<OutboxEmailDto[]>([]);
  protected readonly outboxLoaded = signal(false);
  protected readonly outboxError = signal<string | null>(null);
  protected readonly openEmailId = signal<string | null>(null);
  protected readonly outboxQuery = signal('');
  protected readonly shownOutbox = computed(() => {
    const term = this.outboxQuery().trim().toLowerCase();
    return term
      ? this.outbox().filter((email) => `${email.recipient} ${email.subject}`.toLowerCase().includes(term))
      : this.outbox();
  });
  protected readonly sentCount = computed(() => this.outbox().filter((email) => email.status === 'SENT').length);
  protected readonly notSentCount = computed(() => this.outbox().length - this.sentCount());

  // ---- Templates ----
  protected readonly templates = signal<EmailTemplateDto[]>([]);
  protected readonly templatesError = signal<string | null>(null);
  protected readonly selectedKey = signal<string | null>(null);
  protected readonly draftSubject = signal('');
  protected readonly draftBody = signal('');
  protected readonly savingTemplate = signal(false);
  protected readonly selectedTemplate = computed(
    () => this.templates().find((template) => template.key === this.selectedKey()) ?? null,
  );
  protected readonly templateChanged = computed(() => {
    const template = this.selectedTemplate();
    return !!template && (template.subject !== this.draftSubject() || template.body !== this.draftBody());
  });

  constructor() {
    void this.notifications.load();
  }

  protected selectTab(tab: Tab): void {
    this.tab.set(tab);
    if (tab === 'outbox') {
      void this.loadOutbox();
    } else if (tab === 'templates' && this.templates().length === 0) {
      void this.loadTemplates();
    } else if (tab === 'notifications') {
      void this.notifications.load();
    }
  }

  // ---- Notifications ----
  protected open(notification: AppNotification): void {
    this.notifications.open(notification);
  }

  protected markAllRead(): void {
    this.notifications.markAllRead();
  }

  protected destination(category: string): string {
    return PAGE_LABELS[NOTIFICATION_DESTINATIONS[category] ?? 'notifications'];
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

  // ---- Outbox ----
  protected async loadOutbox(): Promise<void> {
    try {
      this.outbox.set(await this.gateway.outbox());
      this.outboxError.set(null);
    } catch {
      this.outboxError.set('The outbox could not be loaded. Check that identity-service and the gateway are running.');
    } finally {
      this.outboxLoaded.set(true);
    }
  }

  protected toggleEmail(id: string): void {
    this.openEmailId.update((current) => (current === id ? null : id));
  }

  protected emailStatus(email: OutboxEmailDto): string {
    return email.status === 'SENT' ? 'Sent' : email.status === 'FAILED' ? 'Failed' : 'Not sent';
  }

  protected emailTime(email: OutboxEmailDto): string {
    return relativeTime(email.createdAt);
  }

  // ---- Templates ----
  protected async loadTemplates(): Promise<void> {
    try {
      this.templates.set(await this.gateway.templates());
      this.templatesError.set(null);
      if (!this.selectedKey() && this.templates().length > 0) {
        this.selectTemplate(this.templates()[0].key);
      }
    } catch {
      this.templatesError.set('Templates could not be loaded. Check that identity-service and the gateway are running.');
    }
  }

  protected selectTemplate(key: string): void {
    const template = this.templates().find((item) => item.key === key);
    if (!template) {
      return;
    }
    this.selectedKey.set(key);
    this.draftSubject.set(template.subject);
    this.draftBody.set(template.body);
  }

  protected async saveTemplate(): Promise<void> {
    const template = this.selectedTemplate();
    if (!template || this.savingTemplate()) {
      return;
    }
    this.savingTemplate.set(true);
    try {
      this.replaceTemplate(await this.gateway.updateTemplate(template.key, this.draftSubject(), this.draftBody()));
      this.toasts.show('Template saved', 'success', `${template.name} emails now use this wording.`);
    } catch (error) {
      this.toasts.show('Template not saved', 'danger', describe(error));
    } finally {
      this.savingTemplate.set(false);
    }
  }

  protected async resetTemplate(): Promise<void> {
    const template = this.selectedTemplate();
    if (!template || this.savingTemplate()) {
      return;
    }
    this.savingTemplate.set(true);
    try {
      this.replaceTemplate(await this.gateway.resetTemplate(template.key));
      this.toasts.show('Template reset', 'info', `${template.name} is back to the built-in wording.`);
    } catch (error) {
      this.toasts.show('Template not reset', 'danger', describe(error));
    } finally {
      this.savingTemplate.set(false);
    }
  }

  protected placeholderLabel(name: string): string {
    return `{{${name}}}`;
  }

  private replaceTemplate(updated: EmailTemplateDto): void {
    this.templates.update((items) => items.map((item) => (item.key === updated.key ? updated : item)));
    this.selectTemplate(updated.key);
  }
}

function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Could not reach identity-service through the gateway.';
    if (error.status === 400) return error.error?.message ?? 'A template needs a subject and a body.';
    return `The backend returned HTTP ${error.status}.`;
  }
  return 'Something went wrong.';
}
