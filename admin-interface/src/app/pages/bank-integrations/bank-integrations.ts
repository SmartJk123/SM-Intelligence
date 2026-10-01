import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CallbackRegistration, CredentialStatus } from '../../core/bank-integration.gateway';
import { BANKS, Bank } from '../../core/data';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { CsvExportService } from '../../core/csv-export.service';
import { formatTimestampShort } from '../../core/format';
import { ToastService } from '../../core/toast.service';
import { ActionMenu, ActionMenuItem } from '../../shared/action-menu';
import { Badge } from '../../shared/badge';
import { BankLogo } from '../../shared/bank-logo';
import { BankShortcuts } from '../../shared/bank-shortcuts';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';
import { BankDetail } from './bank-detail';

const EXPORT_HEADERS = [
  'Bank',
  'Environment',
  'API status',
  'Webhook status',
  'Token status',
  'Notifications received',
  'Notifications today',
  'Errors, 24 hours',
  'Latency (ms)',
  'Last webhook received',
];

@Component({
  selector: 'app-bank-integrations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge, BankDetail, BankLogo, BankShortcuts, KpiCard, SurfaceCard, ActionMenu],
  templateUrl: './bank-integrations.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class BankIntegrations {
  private readonly integrations = inject(BankIntegrationService);
  private readonly csv = inject(CsvExportService);
  private readonly toasts = inject(ToastService);
  private readonly router = inject(Router);

  protected readonly banks = BANKS;
  protected readonly selected = signal<string | null>(null);
  private readonly credentialsState = signal<Record<string, CredentialStatus | null>>({});

  /** Actions offered on each provider card. */
  protected readonly bankMenu: ActionMenuItem[] = [
    { id: 'details', label: 'View monitoring', tone: 'primary' },
    { id: 'test', label: 'Test connection' },
    { id: 'register', label: 'Register callback URL' },
    { id: 'webhook', label: 'Copy notification URL' },
    { id: 'settings', label: 'Open integration settings' },
  ];

  protected readonly bank = computed(
    () => BANKS.find((item) => item.id === this.selected()) ?? null,
  );

  /**
   * Everything the detail drawer needs, for the bank currently open. One
   * computed value keeps the template free of repeated service lookups.
   */
  protected readonly detail = computed(() => {
    const bank = this.bank();
    if (!bank) {
      return null;
    }
    return {
      bank,
      health: this.integrations.healthFor(bank.id),
      settings: this.integrations.settingsFor(bank.id),
      result: this.integrations.resultFor(bank.id),
      credentials: this.credentialsState()[bank.id] ?? null,
      testing: this.integrations.isTesting(bank.id),
      webhook: this.integrations.webhookInfoFor(bank.id),
    };
  });

  protected readonly backendConnected = this.integrations.backendConnected;

  protected readonly connectedCount = computed(
    () =>
      BANKS.filter((bank) => {
        const status = this.integrations.healthFor(bank.id).apiStatus;
        return status === 'HEALTHY' || status === 'CONNECTED';
      }).length,
  );

  protected readonly healthyApiCount = computed(
    () => BANKS.filter((bank) => this.integrations.healthFor(bank.id).apiStatus === 'HEALTHY').length,
  );

  protected readonly webhookWarnings = computed(
    () => BANKS.filter((bank) => this.integrations.healthFor(bank.id).webhookStatus === 'WARNING').length,
  );

  protected readonly webhookErrors = computed(
    () => BANKS.filter((bank) => this.integrations.healthFor(bank.id).webhookStatus === 'ERROR').length,
  );

  protected healthFor(bankId: string) {
    return this.integrations.healthFor(bankId);
  }

  /** Last notification time for a bank, in a form worth reading. */
  protected lastWebhook(bankId: string): string {
    const received = this.integrations.healthFor(bankId).lastWebhookReceived;
    return received ? formatTimestampShort(received) : 'None yet';
  }

  protected stats(bank: Bank): [string, string | number][] {
    const health = this.healthFor(bank.id);
    return [
      ['Notifications', health.notificationsTotal],
      ['Today', health.notificationsToday],
      ['Errors 24h', health.errorsLast24h],
      ['Latency', health.latencyMs === null ? 'n/a' : `${health.latencyMs}ms`],
    ];
  }

  /** Opens the detail drawer for a bank and loads what the backend knows about it. */
  protected open(bankId: string): void {
    this.selected.set(bankId);
    void this.loadCredentials(bankId);
  }

  protected closeDetail(): void {
    this.selected.set(null);
  }

  protected errorColor(value: string | number): string {
    return Number(value) > 5 ? 'var(--red-ink)' : 'var(--text-1)';
  }

  protected async testConnection(): Promise<void> {
    const bank = this.bank();
    if (bank) {
      await this.runTest(bank);
    }
  }

  protected async refreshHealth(): Promise<void> {
    await this.integrations.loadHealth();
    await this.integrations.loadSettings();
    await this.integrations.loadWebhookInfos();
    const bank = this.bank();
    if (bank) {
      await this.loadCredentials(bank.id);
    }
    this.toasts.show('Status refreshed', 'info', 'Integration health was reloaded from the source.');
  }

  /** Card menu handler. */
  protected async onBankAction(action: string, bank: Bank): Promise<void> {
    switch (action) {
      case 'details':
        this.open(bank.id);
        break;
      case 'test':
        this.open(bank.id);
        await this.runTest(bank);
        break;
      case 'register':
        this.open(bank.id);
        await this.registerCallback(bank);
        break;
      case 'webhook':
        await this.copyWebhookUrl(bank);
        break;
      case 'settings':
        void this.router.navigate(['/admin', 'settings']);
        break;
      default:
        break;
    }
  }

  /** Writes the integration health report currently on screen. */
  protected exportHealth(): void {
    this.csv.download(
      `smartmoney-bank-integrations-${this.csv.stamp()}.csv`,
      EXPORT_HEADERS,
      BANKS.map((bank) => {
        const health = this.integrations.healthFor(bank.id);
        return [
          bank.full,
          health.environment,
          health.apiStatus,
          health.webhookStatus,
          health.tokenStatus,
          health.notificationsTotal,
          health.notificationsToday,
          health.errorsLast24h,
          health.latencyMs ?? '',
          health.lastWebhookReceived ?? 'Never',
        ];
      }),
    );
    this.toasts.show('Export ready', 'success', 'Bank integration health written to CSV.');
  }

  private async runTest(bank: Bank): Promise<void> {
    try {
      const result = await this.integrations.testConnection(bank.id);
      await this.integrations.loadHealth();
      this.toasts.show(
        result.ok ? 'Connection test passed' : 'Connection test failed',
        result.ok ? 'success' : 'danger',
        `${bank.name}: ${result.summary}`,
      );
    } catch (error) {
      this.toasts.show(
        'Connection test failed',
        'danger',
        error instanceof Error ? error.message : `${bank.name}: the backend could not complete the test.`,
      );
    }
  }

  /**
   * Asks Stanbic to send real-time credit and debit alerts to our callback URL.
   * The bank must have approved the account for this API.
   */
  private async registerCallback(bank: Bank): Promise<void> {
    let outcomes: CallbackRegistration[];
    try {
      outcomes = await this.integrations.registerCallback(bank.id);
      await this.integrations.loadHealth();
    } catch (error) {
      this.toasts.show(
        'Callback registration failed',
        'danger',
        error instanceof Error ? error.message : `${bank.name}: the backend could not complete registration.`,
      );
      return;
    }
    if (outcomes.length === 0) {
      const message =
        bank.id === 'ncba'
          ? `${bank.name} has no registration API. Give them the webhook address from the settings panel in writing instead.`
          : `${bank.name} registration is not implemented.`;
      this.toasts.show(bank.id === 'ncba' ? 'No registration API' : 'Not supported yet', 'warning', message);
      return;
    }
    const accepted = outcomes.filter((outcome) => outcome.accepted);
    const rejected = outcomes.filter((outcome) => !outcome.accepted);

    if (accepted.length > 0) {
      const types = accepted.map((outcome) => outcome.notificationType).join(' and ');
      this.toasts.show(
        'Callback registered',
        'success',
        `${bank.name} will send ${types} alerts to our endpoint.`,
      );
    }
    for (const outcome of rejected) {
      this.toasts.show(
        `Registration failed for ${outcome.notificationType}`,
        'danger',
        outcome.error ?? 'Stanbic rejected the registration.',
      );
    }
  }

  private async copyWebhookUrl(bank: Bank): Promise<void> {
    const url = this.integrations.webhookUrlFor(bank.id);
    if (!url) {
      this.toasts.show(
        'No address reported',
        'warning',
        `The backend has not reported a notification address for ${bank.name}. ` +
          'Confirm the service is running and that PUBLIC_BASE_URL is set.',
      );
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      this.toasts.show('Notification URL copied', 'success', url);
    } catch {
      this.toasts.show('Copy failed', 'danger', 'Your browser blocked clipboard access.');
    }
  }

  /** Reads which credentials the backend has loaded for one bank. */
  private async loadCredentials(bankId: string): Promise<void> {
    const status = await this.integrations.loadCredentials(bankId);
    this.credentialsState.update((current) => ({ ...current, [bankId]: status }));
  }
}
