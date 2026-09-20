import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  BankConnectionSettings,
  BankHealth,
  BankIntegrationGateway,
  CallbackRegistration,
  CredentialStatus,
  PlatformStats,
  ConnectionStep,
  ConnectionTestResult,
  DemoMovement,
  DemoMovementRequest,
  DemoSummary,
  WebhookInfo,
} from './bank-integration.gateway';

/**
 * Live implementation of the integration gateway.
 *
 * Everything goes through the backend, because the client key and client
 * secret must never reach the browser. It is the only gateway: the interface
 * has no simulated mode, so an unreachable service is reported as unreachable.
 */
@Injectable({ providedIn: 'root' })
export class HttpBankIntegrationGateway implements BankIntegrationGateway {
  private readonly http = inject(HttpClient);

  async testConnection(settings: BankConnectionSettings): Promise<ConnectionTestResult> {
    const url = `${API_BASE_URL}/admin/bank-integrations/${settings.bankId}/test`;
    try {
      return await firstValueFrom(this.http.post<ConnectionTestResult>(url, settings));
    } catch (error) {
      return failureResult(settings.bankId, url, error);
    }
  }

  async loadHealth(): Promise<BankHealth[]> {
    return firstValueFrom(this.http.get<BankHealth[]>(`${API_BASE_URL}/admin/bank-integrations`));
  }

  async loadSettings(bankId: string): Promise<BankConnectionSettings> {
    return firstValueFrom(
      this.http.get<BankConnectionSettings>(
        `${API_BASE_URL}/admin/bank-integrations/${bankId}/settings`,
      ),
    );
  }

  async saveSettings(settings: BankConnectionSettings): Promise<BankConnectionSettings> {
    return firstValueFrom(
      this.http.put<BankConnectionSettings>(
        `${API_BASE_URL}/admin/bank-integrations/${settings.bankId}`,
        settings,
      ),
    );
  }

  async registerCallback(bankId: string): Promise<CallbackRegistration[]> {
    return firstValueFrom(
      this.http.post<CallbackRegistration[]>(
        `${API_BASE_URL}/admin/bank-integrations/${bankId}/register-callback`,
        {},
      ),
    );
  }

  async loadCredentials(bankId: string): Promise<CredentialStatus | null> {
    const status = await firstValueFrom(
      this.http.get<CredentialStatus | null>(
        `${API_BASE_URL}/admin/bank-integrations/${bankId}/credentials`,
      ),
    );
    return status ?? null;
  }

  async loadWebhookInfo(bankId: string): Promise<WebhookInfo | null> {
    const info = await firstValueFrom(
      this.http.get<WebhookInfo | null>(
        `${API_BASE_URL}/admin/bank-integrations/${bankId}/webhook-url`,
      ),
    );
    return info ?? null;
  }

  async loadStats(): Promise<PlatformStats | null> {
    const stats = await firstValueFrom(
      this.http.get<PlatformStats | null>(`${API_BASE_URL}/admin/stats`),
    );
    return stats ?? null;
  }

  async loadDemoSummary(): Promise<DemoSummary | null> {
    try {
      const summary = await firstValueFrom(
        this.http.get<DemoSummary | null>(`${API_BASE_URL}/admin/demo/summary`),
      );
      return summary ?? null;
    } catch {
      // A demo that cannot be read is reported as missing, never invented.
      return null;
    }
  }

  async loadDemoMovements(): Promise<DemoMovement[]> {
    try {
      return await firstValueFrom(
        this.http.get<DemoMovement[]>(`${API_BASE_URL}/admin/demo/transactions`),
      );
    } catch {
      return [];
    }
  }

  async sendDemoMovement(request: DemoMovementRequest): Promise<DemoMovement> {
    return firstValueFrom(
      this.http.post<DemoMovement>(`${API_BASE_URL}/admin/demo/transactions`, request),
    );
  }
}

/**
 * Turns a transport failure into the same result shape the panel already
 * renders, so a backend problem is explained on screen instead of appearing as
 * an empty panel.
 */
function failureResult(bankId: string, url: string, error: unknown): ConnectionTestResult {
  const status = error instanceof HttpErrorResponse ? error.status : 0;

  let detail: string;
  if (status === 0) {
    detail =
      `No response from ${url}. Start the Spring Boot service, confirm API_BASE_URL, ` +
      `and check that the backend allows requests from this origin.`;
  } else if (status === 401 || status === 403) {
    detail = 'The backend rejected the request. Sign in again, or check the role on this account.';
  } else if (status === 404) {
    detail = `The backend has no endpoint at ${url} yet.`;
  } else {
    detail = `The backend returned HTTP ${status}. Check the service log for the cause.`;
  }

  const steps: ConnectionStep[] = [
    { key: 'token', name: 'Token request', status: 'fail', detail },
    {
      key: 'account-probe',
      name: 'Account probe',
      status: 'fail',
      detail: 'Skipped because the backend could not be reached.',
    },
    {
      key: 'webhook-registration',
      name: 'Backend connection',
      status: 'warn',
      detail:
        'No figure on this screen can be confirmed until the Spring Boot service answers.',
    },
  ];

  return {
    ok: false,
    summary: `Connection test could not run for ${bankId}.`,
    latencyMs: 0,
    steps,
    testedAt: new Date().toISOString(),
  };
}
