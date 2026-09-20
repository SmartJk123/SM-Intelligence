import { BankStatus } from './data';

export type IntegrationStatus = BankStatus | 'UNKNOWN';
export type BankEnvironment = 'Sandbox' | 'Production';
export type StepStatus = 'ok' | 'warn' | 'fail';
export type TokenStatus = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'UNKNOWN';

/**
 * Stable step identifiers. The backend must return these exact keys, because
 * the frontend uses them to derive API and webhook health from a test result.
 */
export type StepKey =
  | 'token'
  | 'account-probe'
  | 'webhook-registration'
  | 'signature-verification';

/** State of one bank integration as reported by the backend. */
export interface BankHealth {
  bankId: string;
  environment: BankEnvironment;
  apiStatus: IntegrationStatus;
  webhookStatus: IntegrationStatus;
  tokenStatus: TokenStatus;
  tokenExpiresInMinutes: number | null;
  lastTokenRefresh: string | null;
  lastWebhookReceived: string | null;
  lastSuccessfulRequest: string | null;
  latencyMs: number | null;
  errorsLast24h: number;
  /** Notifications this bank has ever delivered. Zero when none have arrived. */
  notificationsTotal: number;
  /** Notifications delivered since midnight UTC. */
  notificationsToday: number;
  checkedAt: string | null;
}

export interface BankConnectionSettings {
  bankId: string;
  environment: BankEnvironment;
  apiTimeoutSeconds: number;
  retryAttempts: number;
  retryDelaySeconds: number;
  signatureVerification: boolean;
  automaticRetry: boolean;
}

export interface ConnectionStep {
  key: StepKey;
  name: string;
  status: StepStatus;
  detail: string;
}

export interface ConnectionTestResult {
  ok: boolean;
  summary: string;
  latencyMs: number;
  steps: ConnectionStep[];
  testedAt: string;
}

/** Outcome of registering our callback URL with the bank. */
export interface CallbackRegistration {
  notificationType: string;
  accepted: boolean;
  responseCode: string | null;
  responseMessage: string | null;
  referenceId: string;
  callbackUrl: string;
  error: string | null;
}

/** Which credentials the backend has loaded. Never contains a secret. */
export interface CredentialStatus {
  bankId: string;
  clientKeyConfigured: boolean;
  clientKeyHint: string;
  clientSecretConfigured: boolean;
  apiKeyConfigured: boolean;
  apiKeyHint: string;
  apiKeySource: string;
  tokenUrl: string;
  apiBaseUrl: string;
  registrationUrl: string;
  oauthScope: string;
  accountNumber: string;
  tokenReady: boolean;
  registrationReady: boolean;
  apiKeyAdvice: string;
  note: string;
}

/**
 * The notification address the bank should be given, as the backend reports it.
 *
 * The address is derived from PUBLIC_BASE_URL on the server, so the browser
 * never has to guess it. A guess is how a fictional address ends up registered
 * with a bank.
 */
export interface WebhookInfo {
  bankId: string;
  webhookUrl: string;
  /**
   * True only when the address is public HTTPS and its host resolves, so a bank
   * can actually deliver a notification to it. A stopped tunnel reports false.
   */
  publiclyReachable: boolean;
  accountNumber: string | null;
}

/** One day of notification volume. */
export interface PlatformDailyPoint {
  date: string;
  received: number;
  failed: number;
}

/** A received bank notification, metadata only. */
export interface PlatformEvent {
  id: number;
  bankId: string;
  externalEventId: string | null;
  signatureValid: boolean | null;
  status: string;
  receivedAt: string | null;
  processedAt: string | null;
  errorMessage: string | null;
  /** Credit or Debit when the bank stated one. No amount accompanies it. */
  direction: string | null;
  /** True for the demonstration account rather than a delivery from a bank. */
  simulated: boolean;
}

/**
 * One movement on the demonstration account.
 *
 * This is the only place in the admin surface that carries an amount. The
 * account is synthetic, it exists so a demonstration can show money arriving
 * and money leaving, and every movement on it is flagged as simulated.
 */
export interface DemoMovement {
  reference: string;
  bankId: string;
  direction: string | null;
  amount: number;
  currency: string;
  narration: string | null;
  accountNumber: string;
  accountName: string;
  bookingDate: string | null;
  simulated: boolean;
}

/** Money in against money out on the demonstration account. */
export interface DemoSummary {
  accountNumber: string;
  accountName: string;
  currency: string;
  movements: number;
  credits: number;
  debits: number;
  creditTotal: number;
  debitTotal: number;
  netTotal: number;
  creditToday: number;
  debitToday: number;
  netToday: number;
}

/** What to record. A missing body records a credit of the default amount. */
export interface DemoMovementRequest {
  bankId: string;
  direction: 'Credit' | 'Debit';
  amount: number;
  narration?: string;
}

/**
 * Real platform statistics from GET /api/v1/admin/stats. Every figure is
 * computed from what the platform has actually received.
 */
export interface PlatformStats {
  generatedAt: string;
  banksSupported: number;
  banksConnected: number;
  banksWebhookHealthy: number;
  notificationsTotal: number;
  notificationsToday: number;
  processed: number;
  failed: number;
  pending: number;
  successRate: number;
  lastReceivedAt: string | null;
  minutesSinceLastReceived: number | null;
  volume: PlatformDailyPoint[];
  recent: PlatformEvent[];
}

/**
 * Boundary between the admin interface and the Spring Boot integration layer.
 *
 * The browser never talks to a bank and never holds bank credentials. It asks
 * the backend to run the connection test and to report integration health.
 *
 * There is no simulated implementation. An interface that invents bank health,
 * latency or notification counts is worse than one that says the service is
 * unreachable, so a backend problem surfaces as a backend problem.
 */
export interface BankIntegrationGateway {
  testConnection(settings: BankConnectionSettings): Promise<ConnectionTestResult>;
  loadHealth(): Promise<BankHealth[]>;
  loadSettings(bankId: string): Promise<BankConnectionSettings>;
  saveSettings(settings: BankConnectionSettings): Promise<BankConnectionSettings>;
  registerCallback(bankId: string): Promise<CallbackRegistration[]>;
  loadCredentials(bankId: string): Promise<CredentialStatus | null>;
  loadWebhookInfo(bankId: string): Promise<WebhookInfo | null>;
  loadStats(): Promise<PlatformStats | null>;
  /** The demonstration account, or null when the backend is unreachable. */
  loadDemoSummary(): Promise<DemoSummary | null>;
  loadDemoMovements(): Promise<DemoMovement[]>;
  /** Throws when the backend refuses the movement, so the reason can be shown. */
  sendDemoMovement(request: DemoMovementRequest): Promise<DemoMovement>;
}
