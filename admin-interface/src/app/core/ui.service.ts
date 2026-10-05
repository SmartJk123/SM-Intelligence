import { Injectable, signal } from '@angular/core';

export type InviteType = 'org' | 'user';

/** Opens the invite form already pointed at one organisation, as its owner. */
export interface InvitePreset {
  organizationId: string;
  organizationName: string;
}

@Injectable({ providedIn: 'root' })
export class UiService {
  readonly sidebarOpen = signal(false);
  readonly logoutOpen = signal(false);
  readonly inviteType = signal<InviteType | null>(null);
  readonly invitePreset = signal<InvitePreset | null>(null);

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  openInvite(type: InviteType, preset: InvitePreset | null = null): void {
    this.invitePreset.set(preset);
    this.inviteType.set(type);
  }

  /** Sends the owner invite for an organisation that has already been saved. */
  openOwnerInvite(organizationId: string, organizationName: string): void {
    this.openInvite('user', { organizationId, organizationName });
  }

  closeInvite(): void {
    this.inviteType.set(null);
    this.invitePreset.set(null);
  }

  openLogout(): void {
    this.logoutOpen.set(true);
  }

  closeLogout(): void {
    this.logoutOpen.set(false);
  }
}
