import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  BankConnectionSettings,
  BankHealth,
  ConnectionTestResult,
  CredentialStatus,
  WebhookInfo,
} from '../../core/bank-integration.gateway';
import { Bank } from '../../core/data';
import { formatTimestamp, formatTimestampShort } from '../../core/format';
import { Badge } from '../../shared/badge';
import { BankLogo } from '../../shared/bank-logo';
import { ConnectionSteps } from '../../shared/connection-steps';
import { CopyButton } from '../../shared/copy-button';

type Tone = 'default' | 'danger' | 'success';

interface DetailRow {
  label: string;
  value: string;
  tone: Tone;
}

interface DetailSection {
  title: string;
  hint?: string;
  rows: DetailRow[];
}

/**
 * Slide over detail view for one bank integration.
 *
 * It answers the three questions an operator has about a bank: is it connected,
 * is it receiving notifications, and what exactly is configured. Every value is
 * something the backend reported, so a bank with nothing configured says so
 * rather than showing a dash that could mean anything.
 */
@Component({
  selector: 'app-bank-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge, BankLogo, ConnectionSteps, CopyButton],
  templateUrl: './bank-detail.html',
  host: { '(document:keydown.escape)': 'closed.emit()' },
})
export class BankDetail {
  readonly bank = input.required<Bank>();
  readonly health = input.required<BankHealth>();
  readonly settings = input.required<BankConnectionSettings>();
  readonly webhook = input<WebhookInfo | null>(null);
  readonly result = input<ConnectionTestResult | null>(null);
  readonly credentials = input<CredentialStatus | null>(null);
  readonly testing = input(false);

  readonly closed = output<void>();
  readonly test = output<void>();
  readonly register = output<void>();
  readonly openSettings = output<void>();

  /** Four headline figures for this bank alone. */
  protected readonly tiles = computed<DetailRow[]>(() => {
    const health = this.health();
    return [
      { label: 'Notifications', value: String(health.notificationsTotal), tone: 'default' },
      { label: 'Today', value: String(health.notificationsToday), tone: 'default' },
      {
        label: 'Errors, 24h',
        value: String(health.errorsLast24h),
        tone: health.errorsLast24h > 5 ? 'danger' : 'default',
      },
      {
        label: 'Latency',
        value: health.latencyMs === null ? 'Not measured' : `${health.latencyMs} ms`,
        tone: 'default',
      },
    ];
  });

  protected readonly signatureEnabled = computed(
    () => this.settings().signatureVerification,
  );

  /** The address to register, or empty when the backend has not reported one. */
  protected readonly webhookUrl = computed(() => this.webhook()?.webhookUrl ?? '');

  /**
   * A bank can only deliver to a public HTTPS address. While the tunnel is not
   * running, the address resolves to localhost, which is useful to see during
   * development but cannot be registered with a bank. The backend also reports
   * false when the address is public in form but its hostname no longer resolves,
   * which is what a stopped tunnel looks like.
   */
  protected readonly webhookWarning = computed(() => {
    const webhook = this.webhook();
    if (!webhook) {
      return 'The backend has not reported a notification address. Confirm the service is running.';
    }
    if (!webhook.publiclyReachable) {
      return (
        'A bank cannot reach this address, so no notification can arrive. Start the tunnel, put ' +
        'its current address in PUBLIC_BASE_URL, then restart the service. A quick tunnel is ' +
        'given a new address every time it starts, so an address that worked yesterday may not ' +
        'resolve today.'
      );
    }
    return '';
  });

  protected readonly sections = computed<DetailSection[]>(() => {
    const health = this.health();
    const settings = this.settings();

    return [
      {
        title: 'Connection',
        rows: [
          { label: 'Environment', value: health.environment, tone: 'default' },
          {
            label: 'API status',
            value: health.apiStatus,
            tone: statusTone(health.apiStatus),
          },
          {
            label: 'Last request',
            value: health.lastSuccessfulRequest
              ? formatTimestamp(health.lastSuccessfulRequest)
              : 'Not tested yet',
            tone: 'default',
          },
          {
            label: 'Latency',
            value: health.latencyMs === null ? 'Not measured' : `${health.latencyMs} ms`,
            tone: 'default',
          },
          { label: 'API timeout', value: `${settings.apiTimeoutSeconds} seconds`, tone: 'default' },
          {
            label: 'Automatic retry',
            value: settings.automaticRetry
              ? `${settings.retryAttempts} attempts, ${settings.retryDelaySeconds}s apart`
              : 'Off',
            tone: 'default',
          },
        ],
      },
      {
        title: 'Notifications',
        hint: 'What the bank has actually delivered to this platform.',
        rows: [
          {
            label: 'Channel status',
            value: health.webhookStatus,
            tone: statusTone(health.webhookStatus),
          },
          {
            label: 'Last received',
            value: health.lastWebhookReceived
              ? formatTimestamp(health.lastWebhookReceived)
              : 'Nothing received yet',
            tone: 'default',
          },
          {
            label: 'Received, total',
            value: String(health.notificationsTotal),
            tone: 'default',
          },
          {
            label: 'Received today',
            value: String(health.notificationsToday),
            tone: 'default',
          },
          {
            label: 'Failed, 24 hours',
            value: String(health.errorsLast24h),
            tone: health.errorsLast24h > 0 ? 'danger' : 'default',
          },
          {
            label: 'Signature check',
            value: settings.signatureVerification ? 'Enabled' : 'Disabled',
            tone: settings.signatureVerification ? 'success' : 'danger',
          },
        ],
      },
      {
        title: 'Access token',
        hint: 'The credential used to call the bank, not to receive from it.',
        rows: [
          { label: 'Token status', value: health.tokenStatus, tone: 'default' },
          {
            label: 'Expires in',
            value:
              health.tokenExpiresInMinutes === null
                ? 'Unknown'
                : `${health.tokenExpiresInMinutes} minutes`,
            tone: 'default',
          },
          {
            label: 'Last refresh',
            value: health.lastTokenRefresh ? formatTimestamp(health.lastTokenRefresh) : 'Never',
            tone: 'default',
          },
          {
            label: 'Checked at',
            value: health.checkedAt ? formatTimestamp(health.checkedAt) : 'Not tested',
            tone: 'default',
          },
        ],
      },
      this.credentialSection(),
    ];
  });

  protected readonly testSummary = computed(() => {
    const result = this.result();
    if (!result) {
      return null;
    }
    return `${result.summary} Tested ${formatTimestampShort(result.testedAt)}.`;
  });

  protected toneColor(tone: Tone): string {
    if (tone === 'danger') {
      return 'var(--red-ink)';
    }
    return tone === 'success' ? 'var(--green-ink)' : 'var(--text-1)';
  }

  private credentialSection(): DetailSection {
    const credentials = this.credentials();
    if (!credentials) {
      return {
        title: 'Credentials',
        hint: 'The backend reports which values it has loaded, never the secret itself.',
        rows: [{ label: 'State', value: 'Not reported by the backend', tone: 'default' }],
      };
    }

    const rows: DetailRow[] = [
      {
        label: 'Client key',
        value: credentials.clientKeyConfigured
          ? `Configured${credentials.clientKeyHint ? `, ends ${credentials.clientKeyHint}` : ''}`
          : 'Missing',
        tone: credentials.clientKeyConfigured ? 'success' : 'danger',
      },
      {
        label: 'Client secret',
        value: credentials.clientSecretConfigured ? 'Configured' : 'Missing',
        tone: credentials.clientSecretConfigured ? 'success' : 'danger',
      },
      {
        label: 'Token ready',
        value: credentials.tokenReady ? 'Yes' : 'No',
        tone: credentials.tokenReady ? 'success' : 'danger',
      },
    ];

    if (credentials.apiKeySource !== 'not applicable') {
      rows.push({
        label: 'API key',
        value: credentials.apiKeyConfigured
          ? `Configured, ${credentials.apiKeySource}`
          : 'Not separate, falls back to the client key',
        tone: 'default',
      });
    }

    if (credentials.tokenUrl) {
      rows.push({ label: 'Token URL', value: credentials.tokenUrl, tone: 'default' });
    }
    if (credentials.apiBaseUrl) {
      rows.push({ label: 'API base URL', value: credentials.apiBaseUrl, tone: 'default' });
    }
    if (credentials.registrationUrl) {
      rows.push({ label: 'Registration URL', value: credentials.registrationUrl, tone: 'default' });
    }
    if (credentials.accountNumber) {
      rows.push({ label: 'Account', value: credentials.accountNumber, tone: 'default' });
    }

    return {
      title: 'Credentials',
      hint: credentials.note,
      rows,
    };
  }
}

function statusTone(status: string): Tone {
  if (status === 'HEALTHY' || status === 'CONNECTED') {
    return 'success';
  }
  return status === 'WARNING' || status === 'PENDING' || status === 'UNKNOWN'
    ? 'default'
    : 'danger';
}
