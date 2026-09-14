import { Component, input, signal } from '@angular/core';

const BANK_LOGOS: Record<string, { src: string; name: string; key: string }> = {
  KCB: {
    src: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcS7qMLGNZqCiHTP5c2vowvuGbboQQXVXo4ZSWR-ih8qoA&s',
    name: 'KCB',
    key: 'kcb',
  },
  NCBA: {
    src: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTg66xLlgOf6g71XFyvFV0ywn_AKq_oUV-cCULrgiO2IzUWSsh92cZIYzY&s=10',
    name: 'NCBA',
    key: 'ncba',
  },
  Equity: {
    src: 'https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQxCJ2AP_2pi9l45H1s_NyobWdgdbVV_T6WKBpbGBY1eVmB1eP2aihaWbw&s=10',
    name: 'Equity',
    key: 'equity',
  },
  Stanbic: {
    src: 'https://triad.co.ke/wp-content/uploads/2020/10/New-Stanbic-Bank-Logo.jpg',
    name: 'Stanbic Bank',
    key: 'stanbic',
  },
};

@Component({
  selector: 'app-bank-logo',
  template: `
    @if (logo; as item) {
      <span class="bank-logo-frame">
        <span class="bank-logo-art" [attr.data-bank]="item.key">
          @if (failedUrl() !== item.src) {
            <img
              [src]="item.src"
              [alt]="item.name"
              width="150"
              height="64"
              referrerpolicy="no-referrer"
              (error)="failedUrl.set(item.src)"
            />
          } @else {
            <span class="bank-logo-fallback" role="img" [attr.aria-label]="item.name">{{
              bank()
            }}</span>
          }
        </span>
      </span>
    } @else {
      <span class="bank-logo-fallback">{{ bank() }}</span>
    }
  `,
})
export class BankLogo {
  readonly bank = input.required<string>();
  readonly failedUrl = signal('');
  get logo() {
    return BANK_LOGOS[this.bank()];
  }
}
