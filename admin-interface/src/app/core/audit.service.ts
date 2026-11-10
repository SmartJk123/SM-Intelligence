import { Injectable, inject, signal } from '@angular/core';
import { ActivityGateway, AuditEventDto } from './activity.gateway';
import { AuditLog } from './data';

/** Readable names for identity-service's audit actions. Unknown actions are shown as they come. */
const ACTION_LABELS: Record<string, string> = {
  SIGN_UP: 'Signed up',
  SIGN_IN: 'Signed in',
  SIGN_IN_FAILED: 'Sign in failed',
  SIGN_IN_BLOCKED: 'Suspended account tried to sign in',
  SIGN_OUT: 'Signed out',
  PASSWORD_RESET_REQUESTED: 'Asked for a password reset',
  PASSWORD_RESET_COMPLETED: 'Reset their password',
  CUSTOMER_CREATED: 'Added a customer',
  USER_SUSPENDED: 'Suspended a customer',
  USER_RESTORED: 'Restored a customer',
  USER_UPDATED: 'Edited a customer',
  ORGANISATION_CREATED: 'Created an organisation',
  MEMBER_INVITED: 'Invited an organisation member',
  EMAIL_TEMPLATE_UPDATED: 'Edited an email template',
  EMAIL_TEMPLATE_RESET: 'Reset an email template',
};

/**
 * The platform's audit trail from identity-service: sign ups, sign ins (and
 * failed ones), sign outs, password resets and every change an administrator
 * makes to customers, organisations and email templates.
 */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly gateway = inject(ActivityGateway);

  private readonly logsState = signal<AuditLog[]>([]);
  private readonly loadedState = signal(false);
  private readonly errorState = signal<string | null>(null);

  readonly logs = this.logsState.asReadonly();
  readonly loaded = this.loadedState.asReadonly();
  readonly loadError = this.errorState.asReadonly();

  async load(): Promise<void> {
    try {
      this.logsState.set((await this.gateway.auditEvents()).map(toAuditLog));
      this.errorState.set(null);
    } catch {
      this.errorState.set('The audit log could not be loaded. Check that identity-service and the gateway are running.');
    } finally {
      this.loadedState.set(true);
    }
  }
}

export function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action.replace(/_/g, ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase());
}

function toAuditLog(event: AuditEventDto): AuditLog {
  const occurred = new Date(event.occurredAt);
  return {
    id: event.id,
    occurredAt: event.occurredAt,
    time: Number.isNaN(occurred.getTime())
      ? event.occurredAt
      : occurred.toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'medium' }),
    user: event.actorEmail ?? 'System',
    role: event.actorRole === 'STAFF' ? 'Staff' : event.actorRole === 'SYSTEM' ? 'System' : 'Customer',
    action: actionLabel(event.action),
    resource: event.targetLabel ?? event.targetId ?? '—',
    detail: event.detail ?? '',
    ip: event.ipAddress ?? '—',
    result: event.outcome === 'SUCCESS' ? 'Success' : 'Failed',
  };
}
