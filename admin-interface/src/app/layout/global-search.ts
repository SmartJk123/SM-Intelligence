import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { BANKS, ORGS, Page, TRANSACTIONS, USERS } from '../core/data';

interface SearchResult {
  type: string;
  label: string;
  sub: string;
  page: Page;
  icon: string;
}

const MAX_RESULTS = 7;

@Component({
  selector: 'app-global-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './global-search.html',
  host: {
    class: 'relative hidden sm:block',
    '(document:mousedown)': 'onDocumentMousedown($event)',
  },
})
export class GlobalSearch {
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly query = signal('');
  protected readonly open = signal(false);

  protected readonly results = computed<SearchResult[]>(() => {
    const query = this.query().trim().toLowerCase();
    if (query.length < 2) {
      return [];
    }

    return [
      ...ORGS.filter((org) => org.name.toLowerCase().includes(query)).map<SearchResult>((org) => ({
        type: 'Organisation',
        label: org.name,
        sub: org.type,
        page: 'organisations',
        icon: '◈',
      })),
      ...USERS.filter(
        (user) =>
          user.name.toLowerCase().includes(query) || user.email.toLowerCase().includes(query),
      ).map<SearchResult>((user) => ({
        type: 'User',
        label: user.name,
        sub: user.email,
        page: 'users',
        icon: '◉',
      })),
      ...TRANSACTIONS.filter(
        (tx) =>
          tx.id.toLowerCase().includes(query) ||
          tx.org.toLowerCase().includes(query) ||
          tx.desc.toLowerCase().includes(query),
      ).map<SearchResult>((tx) => ({
        type: 'Transaction',
        label: tx.id,
        sub: tx.org,
        page: 'transactions',
        icon: '↕',
      })),
      ...BANKS.filter(
        (bank) =>
          bank.name.toLowerCase().includes(query) || bank.full.toLowerCase().includes(query),
      ).map<SearchResult>((bank) => ({
        type: 'Bank',
        label: bank.full,
        sub: 'Bank Integration',
        page: 'bank-integrations',
        icon: '⬡',
      })),
    ].slice(0, MAX_RESULTS);
  });

  protected onQueryInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
  }

  protected goTo(result: SearchResult): void {
    void this.router.navigate(['/admin', result.page]);
    this.query.set('');
    this.open.set(false);
  }

  protected onDocumentMousedown(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
