/**
 * Where each bank issues the credentials and subscriptions this platform runs on.
 *
 * These are developer portals, not consumer banking. The admin surface belongs
 * to the operator, who registers applications, copies a Token URL and generates
 * keys here. Consumer banking, app store listings and USSD codes belong to the
 * business user interface, which is a different application for a different
 * person.
 *
 * Two addresses come from this repository's own configuration, so they are
 * marked as sourced. NCBA hands its endpoint credentials over in writing and has
 * no portal on record here, and Equity has no connector yet.
 */
export interface BankPortal {
  bankId: string;
  name: string;
  /** The developer portal, or null when this repository has no address for it. */
  portal: string | null;
  /** What the operator does there. */
  purpose: string;
  /** True when the address comes from this repository's configuration. */
  fromConfig: boolean;
}

export const BANK_PORTALS: BankPortal[] = [
  {
    bankId: 'kcb',
    name: 'KCB',
    portal: 'https://buni.kcbgroup.com/',
    purpose: 'BUNI, where the sandbox Key and Secret are generated and the callback address is set.',
    fromConfig: true,
  },
  {
    bankId: 'stanbic',
    name: 'Stanbic',
    portal: 'https://sandbox.stanbicbank.co.ke/',
    purpose: 'The developer sandbox, where the Token URL, client key and client secret are issued.',
    fromConfig: true,
  },
  {
    bankId: 'ncba',
    name: 'NCBA',
    portal: null,
    purpose:
      'The endpoint credentials are sent to NCBA in writing rather than fetched from a portal. Set NCBA_PORTAL_URL once the bank names one.',
    fromConfig: false,
  },
  {
    bankId: 'equity',
    name: 'Equity',
    portal: null,
    purpose: 'No connector yet. The platform lists the bank and nothing calls it.',
    fromConfig: false,
  },
];
