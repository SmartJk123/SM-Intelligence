import { ChangeDetectionStrategy, Component } from '@angular/core';
import { BANK_PORTALS, BankPortal } from '../core/bank-links';
import { BankLogo } from './bank-logo';
import { SurfaceCard } from './surface-card';

const BANK_LOGOS: Record<string, string> = {
  kcb: 'banks/kcb.png',
  ncba: 'banks/ncba.png',
  equity: 'banks/equity.png',
  stanbic: 'banks/stanbic.jpg',
};

/**
 * Developer portals, one per bank.
 *
 * These are where the credentials this platform runs on are issued, which is
 * what an operator needs. Consumer banking belongs to the business user
 * interface: the administrator is not the front user.
 */
@Component({
  selector: 'app-bank-shortcuts',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BankLogo, SurfaceCard],
  templateUrl: './bank-shortcuts.html',
})
export class BankShortcuts {
  protected readonly portals = BANK_PORTALS;

  protected logoFor(portal: BankPortal): string {
    return BANK_LOGOS[portal.bankId] ?? '';
  }

  protected open(url: string | null): void {
    if (!url) {
      return;
    }
    window.open(url, '_blank', 'noopener,noreferrer');
  }
}
