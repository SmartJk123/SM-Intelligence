import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ConnectionTestResult } from '../core/bank-integration.gateway';

/** Renders the outcome of a bank connection test, step by step. */
@Component({
  selector: 'app-connection-steps',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './connection-steps.html',
})
export class ConnectionSteps {
  readonly result = input.required<ConnectionTestResult>();

  protected stepIcon(status: string): string {
    return status === 'ok' ? 'check' : status === 'warn' ? 'priority_high' : 'close';
  }

  protected stepColor(status: string): string {
    if (status === 'ok') {
      return 'var(--green-ink)';
    }
    return status === 'warn' ? 'var(--gold-ink)' : 'var(--red-ink)';
  }
}
