import { Injectable, signal } from '@angular/core';

export type InviteType = 'org' | 'user';

@Injectable({ providedIn: 'root' })
export class UiService {
  readonly sidebarOpen = signal(false);
  readonly logoutOpen = signal(false);
  readonly inviteType = signal<InviteType | null>(null);

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }

  openInvite(type: InviteType): void {
    this.inviteType.set(type);
  }

  closeInvite(): void {
    this.inviteType.set(null);
  }

  openLogout(): void {
    this.logoutOpen.set(true);
  }

  closeLogout(): void {
    this.logoutOpen.set(false);
  }
}
