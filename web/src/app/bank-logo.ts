import { Component, input } from '@angular/core';

const BANK_LOGOS: Record<string, { file: string; name: string; key: string }> = {
  KCB: { file: 'kcb.png', name: 'KCB', key: 'kcb' },
  NCBA: { file: 'ncba.png', name: 'NCBA', key: 'ncba' },
  Equity: { file: 'equity.png', name: 'Equity', key: 'equity' },
  Stanbic: { file: 'stanbic.png', name: 'Stanbic Bank', key: 'stanbic' },
};

@Component({
  selector: 'app-bank-logo',
  template: `
    @if (logo; as item) {
      <span class="bank-logo-frame">
        <span class="bank-logo-art" [attr.data-bank]="item.key">
          <img [src]="'/assets/logos/' + item.file" [alt]="item.name" width="150" height="64" />
        </span>
      </span>
    } @else {
      <span class="bank-logo-fallback">{{ bank() }}</span>
    }
  `,
})
export class BankLogo {
  readonly bank = input.required<string>();
  get logo() {
    return BANK_LOGOS[this.bank()];
  }
}
