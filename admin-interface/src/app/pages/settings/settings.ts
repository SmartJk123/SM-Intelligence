import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { BANKS } from '../../core/data';
import { AdminProfileService } from '../../core/admin-profile.service';
import { Badge } from '../../shared/badge';
import { BankConnectionPanel } from '../../shared/bank-connection-panel';
import { SettingsField } from '../../shared/settings-field';
import { SettingsInput } from '../../shared/settings-input';
import { SurfaceCard } from '../../shared/surface-card';
import { Toggle } from '../../shared/toggle';
import { ToastService } from '../../core/toast.service';

interface RoleDefinition {
  name: string;
  desc: string;
  color: 'blue' | 'green' | 'gold' | 'red';
  perms: string[];
}

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SurfaceCard, Badge, BankConnectionPanel, SettingsField, SettingsInput, Toggle],
  templateUrl: './settings.html',
  // Delegated so every Save and Update button in the section reports itself
  // without wiring each button individually.
  host: {
    class: 'flex-1 flex flex-col min-h-0',
    '(click)': 'onSectionSave($event)',
  },
})
export class Settings {
  protected readonly profile = inject(AdminProfileService);
  private readonly toasts = inject(ToastService);

  private readonly avatarInput = viewChild<ElementRef<HTMLInputElement>>('avatarInput');

  protected readonly sections = [
    { id: 'General', icon: 'dashboard' },
    { id: 'Admin Profile', icon: 'person' },
    { id: 'Security', icon: 'lock' },
    { id: 'Roles & Permissions', icon: 'admin_panel_settings' },
    { id: 'Notifications', icon: 'notifications' },
    { id: 'Bank Integration Settings', icon: 'account_balance' },
    { id: 'System Configuration', icon: 'settings' },
  ];

  protected readonly active = signal('General');
  protected readonly banks = BANKS;
  protected readonly dateFormats = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD'];
  protected readonly environments = ['Production', 'Sandbox'];

  protected readonly allPermissions = [
    'View All',
    'Edit All',
    'Delete',
    'Settings',
    'Bank Integrations',
    'Edit Users',
    'Edit Orgs',
    'View Transactions',
    'View Reconciliation',
    'Export Reports',
  ];

  protected readonly roles: RoleDefinition[] = [
    {
      name: 'Super Admin',
      desc: 'Full platform access',
      color: 'blue',
      perms: ['View All', 'Edit All', 'Delete', 'Settings', 'Bank Integrations', 'Export Reports', 'Edit Users', 'Edit Orgs'],
    },
    {
      name: 'Admin',
      desc: 'Operational management',
      color: 'green',
      perms: ['View All', 'Edit Users', 'Edit Orgs', 'Bank Integrations', 'Export Reports'],
    },
    {
      name: 'Support',
      desc: 'Read-only with limited actions',
      color: 'gold',
      perms: ['View All', 'Edit Users'],
    },
    {
      name: 'Finance Operations',
      desc: 'Financial monitoring only',
      color: 'red',
      perms: ['View Transactions', 'View Reconciliation', 'Export Reports'],
    },
  ];

  protected readonly sessions = [
    { device: 'Chrome on macOS', ip: '41.90.64.12', location: 'Nairobi, KE', time: 'Current session', current: true },
    { device: 'Chrome on Windows', ip: '41.90.65.88', location: 'Nairobi, KE', time: '2 hrs ago', current: false },
  ];

  protected roleColor(color: RoleDefinition['color']): string {
    return {
      blue: 'var(--primary)',
      green: 'var(--green)',
      gold: 'var(--gold)',
      red: 'var(--red)',
    }[color];
  }

  protected hasPermission(role: RoleDefinition, permission: string): boolean {
    return role.perms.includes(permission);
  }

  protected delayFor(index: number): string {
    return `delay-${(index + 1) * 50}`;
  }

  /** Opens the hidden file picker used for the profile picture. */
  protected pickAvatar(): void {
    this.avatarInput()?.nativeElement.click();
  }

  protected async onAvatarSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    await this.profile.uploadAvatar(file);
    // Reset so selecting the same file again still fires a change event.
    input.value = '';
  }

  protected removeAvatar(): void {
    this.profile.removeAvatar();
  }

  /**
   * Reports a saved section. The profile fields already write straight into
   * AdminProfileService, so this is the confirmation step. When the backend
   * endpoint is connected, send the section payload from here.
   */
  protected onSectionSave(event: Event): void {
    const button = (event.target as HTMLElement | null)?.closest('button');
    if (!button || button.closest('app-bank-connection-panel')) {
      // The bank connection panel confirms its own save.
      return;
    }
    const label = (button.textContent ?? '').trim();
    if (!/^(Save|Update)/.test(label)) {
      return;
    }
    this.toasts.show('Changes saved', 'success', `${label} applied.`);
  }
}
