import { DestroyRef, ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivityGateway, OutboxEmailDto } from '../../core/activity.gateway';
import { AuditService } from '../../core/audit.service';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { CsvCell, CsvExportService } from '../../core/csv-export.service';
import { AccentColor, BANKS } from '../../core/data';
import { OrganizationService } from '../../core/organization.service';
import { ToastService } from '../../core/toast.service';
import { UserService } from '../../core/user.service';
import { SurfaceCard } from '../../shared/surface-card';

interface ReportDefinition {
  id: string;
  name: string;
  desc: string;
  icon: string;
  color: AccentColor;
  /** Whether the reporting period filters this report's rows. */
  dated: boolean;
  headers: string[];
  rows: CsvCell[][];
}

type Range = 'Last 7 days' | 'Last 30 days' | 'Last 90 days' | 'This year' | 'All time';

/** How often the figures are reloaded while the page is open. */
const REFRESH_MS = 60_000;

/**
 * Reports built from the live platform data the portal already reads: customers
 * and organisations (identity-service), sign-in and admin activity (audit log),
 * emails sent (outbox), and bank notifications, health and demo movements
 * (bank-integration-service). Everything reloads every minute and on demand,
 * and every report downloads as CSV or prints as a PDF.
 */
@Component({
  selector: 'app-reports',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SurfaceCard],
  templateUrl: './reports.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Reports {
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly integrations = inject(BankIntegrationService);
  private readonly users = inject(UserService);
  private readonly organisations = inject(OrganizationService);
  private readonly audit = inject(AuditService);
  private readonly activity = inject(ActivityGateway);

  protected readonly ranges: Range[] = ['Last 7 days', 'Last 30 days', 'Last 90 days', 'This year', 'All time'];
  protected readonly range = signal<Range>('Last 30 days');
  protected readonly refreshing = signal(false);
  protected readonly updatedAt = signal<Date | null>(null);
  private readonly outbox = signal<OutboxEmailDto[]>([]);

  /** Start of the reporting period, or null for all time. */
  private readonly since = computed<Date | null>(() => {
    const now = new Date();
    switch (this.range()) {
      case 'Last 7 days':
        return new Date(now.getTime() - 7 * 86_400_000);
      case 'Last 30 days':
        return new Date(now.getTime() - 30 * 86_400_000);
      case 'Last 90 days':
        return new Date(now.getTime() - 90 * 86_400_000);
      case 'This year':
        return new Date(now.getFullYear(), 0, 1);
      default:
        return null;
    }
  });

  protected readonly reports = computed<ReportDefinition[]>(() => {
    const inRange = (iso: string | null | undefined) => {
      const since = this.since();
      if (!since) return true;
      if (!iso) return false;
      const time = new Date(iso).getTime();
      return !Number.isNaN(time) && time >= since.getTime();
    };
    const customers = this.users.summaries().filter((user) => user.role !== 'PLATFORM_ADMIN');
    const logs = this.audit.logs().filter((log) => inRange(log.occurredAt));
    const stats = this.integrations.stats();
    const events = (stats?.recent ?? []).filter((event) => inRange(event.receivedAt));
    const volume = (stats?.volume ?? []).filter((point) => inRange(point.date));
    const movements = this.integrations.demoMovements().filter((movement) => inRange(movement.bookingDate));
    const emails = this.outbox().filter((email) => inRange(email.createdAt));
    const signIns = logs.filter((log) => /sign/i.test(log.action) && log.role !== 'System');

    return [
      {
        id: 'customer-register',
        name: 'Customer register',
        desc: 'Every individual and organisation customer, with status and sign in recency',
        icon: 'group',
        color: 'blue',
        dated: false,
        headers: ['Name', 'Email', 'Account type', 'Organisation', 'Status', 'Email verified', 'Signed up', 'Last sign in'],
        rows: customers.map((user) => [
          user.name,
          user.emailAddress,
          user.accountType === 'ORGANIZATION' ? 'Organisation' : 'Individual',
          user.organizationName ?? '',
          user.status,
          user.isEmailVerified ? 'Yes' : 'No',
          user.createdAt,
          user.lastLoginAt ?? 'Never',
        ]),
      },
      {
        id: 'new-sign-ups',
        name: 'New sign ups',
        desc: 'Customers who joined in the reporting period',
        icon: 'person_add',
        color: 'green',
        dated: true,
        headers: ['Signed up', 'Name', 'Email', 'Account type', 'Organisation'],
        rows: customers
          .filter((user) => inRange(user.createdAt))
          .map((user) => [
            user.createdAt,
            user.name,
            user.emailAddress,
            user.accountType === 'ORGANIZATION' ? 'Organisation' : 'Individual',
            user.organizationName ?? '',
          ]),
      },
      {
        id: 'organisations',
        name: 'Organisations',
        desc: 'Businesses on the platform with their members and status',
        icon: 'apartment',
        color: 'gold',
        dated: false,
        headers: ['Organisation', 'Business type', 'Members', 'Status', 'Joined'],
        rows: this.organisations.organisations().map((org) => [org.name, org.type, org.users, org.status, org.joined]),
      },
      {
        id: 'sign-in-activity',
        name: 'Sign in activity',
        desc: 'Sign ins, failed attempts and sign outs by customers and staff',
        icon: 'login',
        color: 'blue',
        dated: true,
        headers: ['Timestamp', 'Who', 'Type', 'Action', 'IP', 'Result'],
        rows: signIns.map((log) => [log.occurredAt, log.user, log.role, log.action, log.ip, log.result]),
      },
      {
        id: 'bank-notifications',
        name: 'Bank notifications',
        desc: 'Payment notifications received from the banks and how each was handled',
        icon: 'swap_vert',
        color: 'blue',
        dated: true,
        headers: ['Received', 'Processed', 'Bank', 'Flow', 'Sender', 'Reference', 'Signature', 'Status', 'Error'],
        rows: events.map((event) => [
          event.receivedAt,
          event.processedAt,
          event.bankId.toUpperCase(),
          flow(event.direction),
          event.counterpartyName,
          event.externalEventId,
          event.signatureValid === true ? 'Verified' : event.signatureValid === false ? 'Invalid' : 'Not checked',
          event.status,
          event.errorMessage,
        ]),
      },
      {
        id: 'notification-volume',
        name: 'Daily notification volume',
        desc: 'Notifications received and failed per day',
        icon: 'bar_chart',
        color: 'green',
        dated: true,
        headers: ['Date', 'Received', 'Failed'],
        rows: volume.map((point) => [point.date, point.received, point.failed]),
      },
      {
        id: 'bank-performance',
        name: 'Bank integration performance',
        desc: 'Connection, webhook and token health with error counts per bank',
        icon: 'account_balance',
        color: 'green',
        dated: false,
        headers: ['Bank', 'Environment', 'API status', 'Webhook status', 'Token status', 'Notifications', 'Errors 24h', 'Latency (ms)', 'Last tested', 'Last webhook received'],
        rows: BANKS.map((bank) => {
          const health = this.integrations.healthFor(bank.id);
          return [
            bank.full,
            health.environment,
            health.apiStatus,
            health.webhookStatus,
            health.tokenStatus,
            health.notificationsTotal,
            health.errorsLast24h,
            health.latencyMs ?? '',
            health.lastSuccessfulRequest ?? 'Never',
            health.lastWebhookReceived ?? 'Never',
          ];
        }),
      },
      {
        id: 'demo-movements',
        name: 'Demo account movements',
        desc: 'Credits and debits recorded on the demonstration account',
        icon: 'payments',
        color: 'gold',
        dated: true,
        headers: ['Booked', 'Reference', 'Bank', 'Flow', 'Amount', 'Currency', 'Narration'],
        rows: movements.map((movement) => [
          movement.bookingDate,
          movement.reference,
          movement.bankId.toUpperCase(),
          flow(movement.direction),
          movement.amount,
          movement.currency,
          movement.narration,
        ]),
      },
      {
        id: 'system-activity',
        name: 'System activity',
        desc: 'Everything in the audit log: sign ins, admin changes and password resets',
        icon: 'history',
        color: 'blue',
        dated: true,
        headers: ['Timestamp', 'Who', 'Type', 'Action', 'Resource', 'Detail', 'IP', 'Result'],
        rows: logs.map((log) => [log.occurredAt, log.user, log.role, log.action, log.resource, log.detail, log.ip, log.result]),
      },
      {
        id: 'emails-sent',
        name: 'Emails sent',
        desc: 'Welcome emails, invites and password resets from the outbox',
        icon: 'outbox',
        color: 'green',
        dated: true,
        headers: ['Created', 'Recipient', 'Subject', 'Template', 'Status', 'Error'],
        rows: emails.map((email) => [email.createdAt, email.recipient, email.subject, email.templateKey, email.status, email.error]),
      },
      {
        id: 'platform-summary',
        name: 'Platform summary',
        desc: 'Headline figures for the reporting period',
        icon: 'verified',
        color: 'green',
        dated: true,
        headers: ['Metric', 'Value'],
        rows: [
          ['Reporting period', this.range()],
          ['Customers (all time)', customers.length],
          ['Individual customers', customers.filter((user) => user.accountType !== 'ORGANIZATION').length],
          ['Organisation customers', customers.filter((user) => user.accountType === 'ORGANIZATION').length],
          ['Suspended customers', customers.filter((user) => user.status === 'SUSPENDED').length],
          ['New sign ups in period', customers.filter((user) => inRange(user.createdAt)).length],
          ['Organisations', this.organisations.organisations().length],
          ['Sign ins in period', signIns.filter((log) => log.action === 'Signed in').length],
          ['Failed sign ins in period', signIns.filter((log) => log.result !== 'Success').length],
          ['Bank notifications received (all time)', stats?.notificationsTotal ?? 0],
          ['Bank notifications failed (all time)', stats?.failed ?? 0],
          ['Notification success rate %', stats?.successRate ?? 0],
          ['Banks connected', stats?.banksConnected ?? 0],
          ['Emails sent in period', emails.filter((email) => email.status === 'SENT').length],
          ['Emails not sent in period', emails.filter((email) => email.status !== 'SENT').length],
        ],
      },
    ];
  });

  protected readonly totalRows = computed(() => this.reports().reduce((sum, report) => sum + report.rows.length, 0));

  constructor() {
    void this.refresh(false);
    const timer = setInterval(() => void this.refresh(false), REFRESH_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  /** Reloads every source the reports read. */
  protected async refresh(announce = true): Promise<void> {
    if (this.refreshing()) return;
    this.refreshing.set(true);
    await Promise.allSettled([
      this.users.loadUsers(),
      this.organisations.loadOrganizations(),
      this.audit.load(),
      this.integrations.loadStats(),
      this.integrations.loadHealth(),
      this.integrations.loadDemo(),
      this.activity.outbox(1000).then((emails) => this.outbox.set(emails)),
    ]);
    this.updatedAt.set(new Date());
    this.refreshing.set(false);
    if (announce) {
      this.toasts.show('Reports refreshed', 'info', 'Every report now shows the latest data.');
    }
  }

  protected updatedLabel(): string {
    const at = this.updatedAt();
    return at ? at.toLocaleTimeString('en-KE', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '…';
  }

  /** Downloads the rows behind a report as a CSV file. */
  protected generate(report: ReportDefinition): void {
    if (report.rows.length === 0) {
      this.toasts.show('Nothing to report', 'warning', `${report.name} has no rows${report.dated ? ' for ' + this.range() : ''}.`);
      return;
    }
    this.csv.download(`smartmoney-${report.id}-${this.csv.stamp()}.csv`, report.headers, report.rows);
    this.toasts.show('Report generated', 'success', `${report.name}, ${report.rows.length} rows.`);
  }

  /** Opens the report as a printable table; the print dialog saves it as a PDF. */
  protected printReport(report: ReportDefinition): void {
    if (report.rows.length === 0) {
      this.toasts.show('Nothing to print', 'warning', `${report.name} has no rows${report.dated ? ' for ' + this.range() : ''}.`);
      return;
    }
    const page = window.open('', '_blank');
    if (!page) {
      this.toasts.show('Pop-up blocked', 'danger', 'Allow pop-ups for the admin portal to print reports.');
      return;
    }
    const period = report.dated ? this.range() : 'All records';
    page.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(report.name)}</title>
<style>
  body { font-family: Arial, sans-serif; color: #101322; margin: 24px; }
  h1 { font-size: 20px; margin: 0 0 4px; } p { color: #5b6178; font-size: 12px; margin: 0 0 16px; }
  table { border-collapse: collapse; width: 100%; font-size: 11px; }
  th, td { border: 1px solid #d7dbec; padding: 5px 7px; text-align: left; vertical-align: top; }
  th { background: #f1f3fb; }
</style></head><body>
<h1>SmartMoney Intelligence: ${escapeHtml(report.name)}</h1>
<p>${escapeHtml(report.desc)}. Period: ${escapeHtml(period)}. Generated ${escapeHtml(new Date().toLocaleString('en-KE'))}. ${report.rows.length} rows.</p>
<table><thead><tr>${report.headers.map((h) => `<th>${escapeHtml(h)}</th>`).join('')}</tr></thead>
<tbody>${report.rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join('')}</tr>`).join('')}</tbody></table>
</body></html>`);
    page.document.close();
    page.focus();
    setTimeout(() => page.print(), 300);
  }

  protected periodLabel(report: ReportDefinition): string {
    return report.dated ? this.range() : 'All records';
  }
}

function flow(direction: string | null): string {
  const value = (direction ?? '').trim().toLowerCase();
  if (value.startsWith('c') || value === 'in') return 'Credit';
  if (value.startsWith('d') || value === 'out') return 'Debit';
  return 'Not stated';
}

function escapeHtml(value: CsvCell): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
