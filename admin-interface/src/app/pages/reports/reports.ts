import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AUDIT_LOGS,
  AccentColor,
  BANKS,
  ORGS,
  RECONCILE,
  TRANSACTIONS,
  USERS,
  money,
} from '../../core/data';
import { CsvCell, CsvExportService } from '../../core/csv-export.service';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { ToastService } from '../../core/toast.service';
import { SurfaceCard } from '../../shared/surface-card';

interface ReportDefinition {
  id: string;
  name: string;
  desc: string;
  icon: string;
  color: AccentColor;
  range: string;
  headers: string[];
  rows: CsvCell[][];
}

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

  protected readonly ranges = ['Last 7 days', 'Last 30 days', 'Last 90 days', 'This year'];
  protected readonly range = signal('Last 30 days');

  /** Built on read so the bank rows always reflect the latest reported health. */
  protected get reports(): ReportDefinition[] {
    return [
    {
      id: 'transaction-volume',
      name: 'Transaction volume',
      desc: 'Every transaction processed, with amount, type and status',
      icon: 'swap_vert',
      color: 'blue',
      range: 'Last 30 days',
      headers: ['Reference', 'Organisation', 'Bank', 'Amount', 'Type', 'Status', 'Date'],
      rows: TRANSACTIONS.map((tx) => [tx.id, tx.org, tx.bank, tx.amount, tx.type, tx.status, tx.date]),
    },
    {
      id: 'bank-performance',
      name: 'Bank integration performance',
      desc: 'Uptime, latency and error counts per provider',
      icon: 'account_balance',
      color: 'green',
      range: 'Last 30 days',
      headers: ['Bank', 'API status', 'Webhook status', 'Token status', 'Notifications', 'Errors 24h', 'Latency (ms)', 'Last webhook received'],
      rows: BANKS.map((b) => {
        const health = this.integrations.healthFor(b.id);
        return [
          b.full,
          health.apiStatus,
          health.webhookStatus,
          health.tokenStatus,
          health.notificationsTotal,
          health.errorsLast24h,
          health.latencyMs ?? '',
          health.lastWebhookReceived ?? 'Never',
        ];
      }),
    },
    {
      id: 'organisation-growth',
      name: 'Organisation growth',
      desc: 'Businesses onboarded, with users and connected accounts',
      icon: 'trending_up',
      color: 'gold',
      range: 'This year',
      headers: ['Organisation', 'Business type', 'Users', 'Accounts', 'Status', 'Joined'],
      rows: ORGS.map((o) => [o.name, o.type, o.users, o.accounts, o.status, o.joined]),
    },
    {
      id: 'user-activity',
      name: 'User activity',
      desc: 'Access, roles and sign in recency across organisations',
      icon: 'group',
      color: 'blue',
      range: 'Last 30 days',
      headers: ['Name', 'Email', 'Organisation', 'Role', 'Status', 'Last login'],
      rows: USERS.map((u) => [u.name, u.email, u.org, u.role, u.status, u.lastLogin]),
    },
    {
      id: 'reconciliation',
      name: 'Reconciliation performance',
      desc: 'Match rates and the records still waiting for review',
      icon: 'rule',
      color: 'gold',
      range: 'Last 30 days',
      headers: ['Record', 'Transaction', 'Organisation', 'Expected reference', 'Amount', 'Status'],
      rows: RECONCILE.map((r) => [r.id, r.tx, r.org, r.exp, r.amount, r.status]),
    },
    {
      id: 'failed-transactions',
      name: 'Failed transactions',
      desc: 'Payments that did not process, with the reason to investigate',
      icon: 'error',
      color: 'red',
      range: 'Last 30 days',
      headers: ['Reference', 'Organisation', 'Bank', 'Description', 'Amount', 'Date'],
      rows: TRANSACTIONS.filter((tx) => tx.status === 'Failed' || tx.status === 'Unmatched').map((tx) => [
        tx.id,
        tx.org,
        tx.bank,
        tx.desc,
        tx.amount,
        tx.date,
      ]),
    },
    {
      id: 'system-activity',
      name: 'System activity',
      desc: 'Administrative actions, logins and configuration changes',
      icon: 'history',
      color: 'blue',
      range: 'Last 30 days',
      headers: ['Timestamp', 'User', 'Role', 'Action', 'Resource', 'IP', 'Result'],
      rows: AUDIT_LOGS.map((log) => [log.time, log.user, log.role, log.action, log.resource, log.ip, log.result]),
    },
    {
      id: 'audit-summary',
      name: 'Audit summary',
      desc: 'Compliance overview of platform activity',
      icon: 'verified',
      color: 'green',
      range: 'Last 30 days',
      headers: ['Metric', 'Value'],
      rows: [
        ['Audit entries', AUDIT_LOGS.length],
        ['Successful actions', AUDIT_LOGS.filter((log) => log.result === 'Success').length],
        ['Failed actions', AUDIT_LOGS.filter((log) => log.result !== 'Success').length],
        ['Distinct administrators', new Set(AUDIT_LOGS.map((log) => log.user)).size],
      ],
    },
    ];
  }

  protected get totalRows(): number {
    return this.reports.reduce((sum, report) => sum + report.rows.length, 0);
  }

  /** Downloads the rows behind a report as a real CSV file. */
  protected generate(report: ReportDefinition): void {
    if (report.rows.length === 0) {
      this.toasts.show('Nothing to report', 'warning', `${report.name} has no rows for ${this.range()}.`);
      return;
    }
    this.csv.download(
      `smartmoney-${report.id}-${this.csv.stamp()}.csv`,
      report.headers,
      report.rows,
    );
    this.toasts.show(
      'Report generated',
      'success',
      `${report.name}, ${report.rows.length} rows, ${this.range()}.`,
    );
  }

  /** Opens the browser print dialog, which can save the report as a PDF. */
  protected printReport(report: ReportDefinition): void {
    this.toasts.show('Print view opened', 'info', `${report.name} sent to the print dialog.`);
    setTimeout(() => window.print(), 250);
  }

  protected reportValue(report: ReportDefinition): string {
    return `${report.rows.length} rows`;
  }

  protected reportSummary(report: ReportDefinition): string {
    if (report.id === 'audit-summary') {
      return `${AUDIT_LOGS.length} entries`;
    }
    if (report.id === 'transaction-volume') {
      return `KES ${money(TRANSACTIONS.reduce((sum, tx) => sum + tx.amount, 0))}`;
    }
    return this.reportValue(report);
  }
}
