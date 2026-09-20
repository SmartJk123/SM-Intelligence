import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { CredentialStatus } from '../core/bank-integration.gateway';
import { Bank } from '../core/data';
import { BankEnvironment } from '../core/bank-integration.gateway';
import { BankIntegrationService } from '../core/bank-integration.service';
import { Badge } from './badge';
import { BankLogo } from './bank-logo';
import { CopyButton } from './copy-button';
import { ConnectionSteps } from './connection-steps';
import { Toggle } from './toggle';

type NumericSetting = 'apiTimeoutSeconds' | 'retryAttempts' | 'retryDelaySeconds';

/**
 * The three banks do not use the same vocabulary. KCB and Stanbic issue an
 * OAuth client key and secret and have a registration call, while NCBA has no
 * API of its own: the endpoint credentials are the values handed to the bank on
 * the request letter. The panel keeps one layout and relabels it per bank,
 * because calling a secret key a client key is how an operator ends up looking
 * for the wrong value in the wrong portal.
 */
interface CredentialLabels {
  heading: string;
  description: string;
  key: string;
  secret: string;
  third: string;
  firstPill: string;
  secondPill: string;
}

const DEFAULT_LABELS: CredentialLabels = {
  heading: 'Client credentials',
  description:
    'The client key and client secret are held in the backend and are never sent to the browser. ' +
    'This view reports which values the service has loaded, without revealing them.',
  key: 'Client key',
  secret: 'Client secret',
  third: 'ApiKey field',
  firstPill: 'Token',
  secondPill: 'Callback registration',
};

const LABELS_BY_BANK: Record<string, Partial<CredentialLabels>> = {
  ncba: {
    heading: 'Endpoint credentials given to NCBA',
    description:
      'NCBA has no token endpoint and no registration API. It pushes XML to this service, and ' +
      'these are the values the request letter carries and every notification is checked against. ' +
      'None of them is ever sent to the browser.',
    key: 'Secret key',
    secret: 'Password',
    third: 'Username',
    firstPill: 'Credentials',
    secondPill: 'Public address',
  },
};

/**
 * Connection panel for a single bank. Every value shown here is owned by the
 * backend; the panel only configures and reports.
 */
@Component({
  selector: 'app-bank-connection-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge, BankLogo, CopyButton, ConnectionSteps, Toggle],
  templateUrl: './bank-connection-panel.html',
})
export class BankConnectionPanel {
  private readonly integrations = inject(BankIntegrationService);

  readonly bank = input.required<Bank>();

  protected readonly health = computed(() => this.integrations.healthFor(this.bank().id));
  protected readonly settings = computed(() => this.integrations.settingsFor(this.bank().id));
  protected readonly result = computed(() => this.integrations.resultFor(this.bank().id));
  protected readonly testing = computed(() => this.integrations.isTesting(this.bank().id));
  protected readonly webhookUrl = computed(() => this.integrations.webhookUrlFor(this.bank().id));
  protected readonly saved = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly credentials = signal<CredentialStatus | null>(null);

  protected readonly labels = computed<CredentialLabels>(() => ({
    ...DEFAULT_LABELS,
    ...(LABELS_BY_BANK[this.bank().id] ?? {}),
  }));

  constructor() {
    // Reloads whenever the panel is opened for a different bank.
    effect(() => {
      const bankId = this.bank().id;
      void this.integrations.loadCredentials(bankId).then((status) => this.credentials.set(status));
    });
  }

  protected readonly environments: BankEnvironment[] = ['Sandbox', 'Production'];

  protected setEnvironment(value: string): void {
    this.integrations.updateSettings(this.bank().id, { environment: value as BankEnvironment });
    this.markChanged();
  }

  protected setNumber(key: NumericSetting, value: string): void {
    const parsed = Number.parseInt(value, 10);
    if (Number.isNaN(parsed)) {
      return;
    }
    this.integrations.updateSettings(this.bank().id, { [key]: parsed });
    this.markChanged();
  }

  protected setSignatureVerification(enabled: boolean): void {
    this.integrations.updateSettings(this.bank().id, { signatureVerification: enabled });
    this.markChanged();
  }

  protected setAutomaticRetry(enabled: boolean): void {
    this.integrations.updateSettings(this.bank().id, { automaticRetry: enabled });
    this.markChanged();
  }

  protected async testConnection(): Promise<void> {
    this.error.set('');
    try {
      await this.integrations.testConnection(this.bank().id);
    } catch (error) {
      this.error.set(errorMessage(error));
    }
  }

  protected async save(): Promise<void> {
    this.error.set('');
    this.saving.set(true);
    try {
      await this.integrations.saveSettings(this.bank().id);
      this.saved.set(true);
      setTimeout(() => this.saved.set(false), 2200);
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected clearResult(): void {
    this.integrations.clearResult(this.bank().id);
  }

  private markChanged(): void {
    // A configuration change invalidates the previous test result.
    this.integrations.clearResult(this.bank().id);
  }
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'The backend could not complete the request.';
}
