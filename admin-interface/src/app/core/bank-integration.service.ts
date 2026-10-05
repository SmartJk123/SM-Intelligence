import { Injectable, computed, inject, signal } from '@angular/core';
import {
  BankConnectionSettings,
  BankEnvironment,
  BankHealth,
  CallbackRegistration,
  CredentialStatus,
  DemoMovement,
  DemoMovementRequest,
  DemoSummary,
  PlatformBaseUrlInfo,
  PlatformStats,
  ConnectionTestResult,
  TokenStatus,
  WebhookInfo,
} from './bank-integration.gateway';
import { HttpBankIntegrationGateway } from './http-bank-integration.gateway';
import { BANKS } from './data';

export type { BankHealth, TokenStatus } from './bank-integration.gateway';

const DEFAULT_ENVIRONMENT: BankEnvironment = 'Sandbox';

/**
 * Single source of truth for bank integration configuration and health.
 *
 * Every value here is something the backend owns. When the Spring Boot service
 * is available, replace the gateway provider in `app.config.ts` and this
 * service will read live data without any change to the screens.
 */
@Injectable({ providedIn: 'root' })
export class BankIntegrationService {
  private readonly gateway = inject(HttpBankIntegrationGateway);

  private readonly settingsState = signal<Record<string, BankConnectionSettings>>({});

  private readonly healthState = signal<Record<string, BankHealth>>({});

  /** False until the backend has answered once. Drives the status pill. */
  private readonly connectedState = signal(false);

  private readonly testingState = signal<string | null>(null);
  private readonly resultsState = signal<Record<string, ConnectionTestResult>>({});
  private readonly statsState = signal<PlatformStats | null>(null);
  private readonly webhookState = signal<Record<string, WebhookInfo>>({});
  private readonly publicBaseUrlState = signal<PlatformBaseUrlInfo | null>(null);
  private readonly demoSummaryState = signal<DemoSummary | null>(null);
  private readonly demoMovementsState = signal<DemoMovement[]>([]);

  readonly settings = this.settingsState.asReadonly();
  readonly health = this.healthState.asReadonly();
  /** Whether the Spring Boot service has answered the latest health request. */
  readonly backendConnected = this.connectedState.asReadonly();
  readonly testingBankId = this.testingState.asReadonly();
  readonly results = this.resultsState.asReadonly();
  /** Real platform statistics. Null until the backend answers. */
  readonly stats = this.statsState.asReadonly();
  /** Notification addresses, as the backend reports them from PUBLIC_BASE_URL. */
  readonly webhookInfo = this.webhookState.asReadonly();
  /** The shared base address every bank's webhook URL is built from. */
  readonly publicBaseUrl = this.publicBaseUrlState.asReadonly();
  /** The demonstration account, or null when the backend has not answered. */
  readonly demoSummary = this.demoSummaryState.asReadonly();
  readonly demoMovements = this.demoMovementsState.asReadonly();

  readonly configuredCount = computed(() => Object.keys(this.settingsState()).length);

  constructor() {
    // Pull the current state as soon as a screen needs it. The backend owns all
    // of it, so nothing is seeded locally.
    void this.loadHealth();
    void this.loadSettings();
    void this.loadStats();
    void this.loadDemo();
    void this.loadWebhookInfos();
    void this.loadPublicBaseUrl();
  }

  /** Reloads the shared webhook base address from the backend. */
  async loadPublicBaseUrl(): Promise<void> {
    try {
      this.publicBaseUrlState.set(await this.gateway.loadPublicBaseUrl());
    } catch {
      // Backend not reachable. The field stays unknown rather than guessed.
    }
  }

  /**
   * Saves the webhook base address on the backend, which stores it so it
   * survives a restart, then reloads every bank's webhook address so the panels
   * show what the backend actually holds. A changed address invalidates any
   * earlier connection test.
   */
  async updatePublicBaseUrl(publicBaseUrl: string): Promise<void> {
    const updated = await this.gateway.updatePublicBaseUrl(publicBaseUrl);
    this.publicBaseUrlState.set(updated);
    await this.loadWebhookInfos();
    for (const bank of BANKS) {
      this.clearResult(bank.id);
    }
  }

  /**
   * Loads the stored settings for every bank.
   *
   * Without this the screens fall back to local defaults, which is how a bank
   * with signature checking switched on ends up described as having it off.
   */
  async loadSettings(): Promise<void> {
    for (const bank of BANKS) {
      try {
        const settings = await this.gateway.loadSettings(bank.id);
        this.settingsState.update((current) => ({ ...current, [bank.id]: settings }));
      } catch {
        // Backend not reachable. The local fallback stays in place.
      }
    }
  }

  /**
   * Loads the notification address for every bank.
   *
   * The address depends on PUBLIC_BASE_URL, which only the server knows, so it
   * is asked for rather than assembled here. Assembling it in the browser is how
   * an address that does not exist ends up shown to an operator.
   */
  async loadWebhookInfos(): Promise<void> {
    for (const bank of BANKS) {
      try {
        const info = await this.gateway.loadWebhookInfo(bank.id);
        if (info) {
          this.webhookState.update((current) => ({ ...current, [bank.id]: info }));
        }
      } catch {
        // Backend not reachable. The address stays unknown rather than guessed.
      }
    }
  }

  /** Reloads the platform statistics. */
  async loadStats(): Promise<void> {
    try {
      const stats = await this.gateway.loadStats();
      this.statsState.set(stats);
      this.connectedState.set(true);
    } catch {
      this.connectedState.set(false);
    }
  }

  /** Reloads the demonstration account. Both calls are safe to repeat. */
  async loadDemo(): Promise<void> {
    this.demoSummaryState.set(await this.gateway.loadDemoSummary());
    this.demoMovementsState.set(await this.gateway.loadDemoMovements());
  }

  /**
   * Records one movement and reloads, so the screen shows what the backend
   * stored rather than what the browser hoped it stored. The error is passed
   * on so the caller can show the reason the backend refused it.
   */
  async sendDemoMovement(request: DemoMovementRequest): Promise<DemoMovement> {
    const created = await this.gateway.sendDemoMovement(request);
    await this.loadDemo();
    return created;
  }

  settingsFor(bankId: string): BankConnectionSettings {
    return this.settingsState()[bankId] ?? fallbackSettings(bankId);
  }

  healthFor(bankId: string): BankHealth {
    return this.healthState()[bankId] ?? fallbackHealth(bankId);
  }

  resultFor(bankId: string): ConnectionTestResult | null {
    return this.resultsState()[bankId] ?? null;
  }

  isTesting(bankId: string): boolean {
    return this.testingState() === bankId;
  }

  webhookUrlFor(bankId: string): string {
    return this.webhookState()[bankId]?.webhookUrl ?? '';
  }

  /** Full details for the notification address, including whether it is public. */
  webhookInfoFor(bankId: string): WebhookInfo | null {
    return this.webhookState()[bankId] ?? null;
  }

  updateSettings(bankId: string, patch: Partial<BankConnectionSettings>): void {
    this.settingsState.update((current) => ({
      ...current,
      [bankId]: { ...this.settingsFor(bankId), ...patch, bankId },
    }));
  }

  /** Persists the safe, non-secret settings owned by the admin interface. */
  async saveSettings(bankId: string): Promise<BankConnectionSettings> {
    const saved = await this.gateway.saveSettings(this.settingsFor(bankId));
    this.settingsState.update((current) => ({ ...current, [bankId]: saved }));
    this.clearResult(bankId);
    return saved;
  }

  /** Runs the connection test through the gateway and records the outcome. */
  async testConnection(bankId: string): Promise<ConnectionTestResult> {
    const settings = this.settingsFor(bankId);
    this.testingState.set(bankId);
    try {
      const result = await this.gateway.testConnection(settings);
      this.resultsState.update((current) => ({ ...current, [bankId]: result }));
      this.applyResult(bankId, result);
      return result;
    } finally {
      this.testingState.set(null);
    }
  }

  /** Clears the stored result, used when settings change. */
  clearResult(bankId: string): void {
    this.resultsState.update((current) => {
      const next = { ...current };
      delete next[bankId];
      return next;
    });
  }

  /** Asks the backend to register our callback URL with the bank. */
  async registerCallback(bankId: string): Promise<CallbackRegistration[]> {
    return this.gateway.registerCallback(bankId);
  }

  /** Reads which credentials the backend has loaded, without exposing secrets. */
  async loadCredentials(bankId: string): Promise<CredentialStatus | null> {
    try {
      return await this.gateway.loadCredentials(bankId);
    } catch {
      return null;
    }
  }

  /**
   * Reloads health from the gateway. With the simulated gateway this mirrors
   * the seeded values; with the HTTP gateway it reads the live backend and
   * keeps the last known state if the call fails.
   */
  async loadHealth(): Promise<void> {
    try {
      const health = await this.gateway.loadHealth();
      if (health.length === 0) {
        this.connectedState.set(true);
        return;
      }
      this.connectedState.set(true);
      this.healthState.update((current) => {
        const next = { ...current };
        for (const item of health) {
          next[item.bankId] = { ...(next[item.bankId] ?? item), ...item };
        }
        return next;
      });
    } catch {
      // Backend not reachable. Every panel falls back to "unknown" rather than
      // to an invented value.
      this.connectedState.set(false);
    }
  }

  private applyResult(bankId: string, result: ConnectionTestResult): void {
    const now = new Date().toISOString();
    this.healthState.update((current) => {
      const previous = current[bankId] ?? fallbackHealth(bankId);
      const step = (key: string) => result.steps.find((item) => item.key === key);

      const webhookStep = step('webhook-registration');
      const accountStep = step('account-probe');
      const tokenStep = step('token');

      return {
        ...current,
        [bankId]: {
          ...previous,
          apiStatus:
            tokenStep?.status === 'fail' || accountStep?.status === 'fail'
              ? 'ERROR'
              : tokenStep?.status === 'warn' || accountStep?.status === 'warn'
                ? 'WARNING'
                : 'HEALTHY',
          webhookStatus:
            webhookStep?.status === 'fail'
              ? 'ERROR'
              : webhookStep?.status === 'warn'
                ? 'WARNING'
                : 'HEALTHY',
          tokenStatus:
            tokenStep?.status === 'ok'
              ? 'VALID'
              : tokenStep?.status === 'fail'
                ? 'UNKNOWN'
                : previous.tokenStatus,
          tokenExpiresInMinutes: tokenStep?.status === 'ok' ? null : previous.tokenExpiresInMinutes,
          lastTokenRefresh: tokenStep?.status === 'ok' ? now : previous.lastTokenRefresh,
          lastSuccessfulRequest: accountStep?.status === 'fail' ? previous.lastSuccessfulRequest : now,
          latencyMs: result.latencyMs,
          checkedAt: now,
          errorsLast24h: webhookStep?.status === 'fail' ? previous.errorsLast24h : 0,
        },
      };
    });
  }
}

function fallbackSettings(bankId: string): BankConnectionSettings {
  return {
    bankId,
    environment: DEFAULT_ENVIRONMENT,
    apiTimeoutSeconds: 30,
    retryAttempts: 3,
    retryDelaySeconds: 5,
    signatureVerification: false,
    automaticRetry: true,
  };
}

function fallbackHealth(bankId: string): BankHealth {
  return {
    bankId,
    environment: DEFAULT_ENVIRONMENT,
    apiStatus: 'UNKNOWN',
    webhookStatus: 'UNKNOWN',
    tokenStatus: 'UNKNOWN',
    tokenExpiresInMinutes: null,
    lastTokenRefresh: null,
    lastWebhookReceived: null,
    lastSuccessfulRequest: null,
    latencyMs: null,
    errorsLast24h: 0,
    notificationsTotal: 0,
    notificationsToday: 0,
    connectorImplemented: false,
    checkedAt: null,
  };
}
