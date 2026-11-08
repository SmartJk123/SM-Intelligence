import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { toDataURL } from 'qrcode';
import {
  AdminAccountGateway,
  AdminSession,
  MfaSetup,
  SettingsSection,
  StaffMember,
  StaffRole,
} from '../../core/admin-account.gateway';
import { BANKS } from '../../core/data';
import { AdminProfileService } from '../../core/admin-profile.service';
import { AuthService } from '../../core/auth.service';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { StaffAccessService } from '../../core/staff-access.service';
import { environment } from '../../../environments/environment';
import { Badge } from '../../shared/badge';
import { BankConnectionPanel } from '../../shared/bank-connection-panel';
import { SettingsField } from '../../shared/settings-field';
import { SurfaceCard } from '../../shared/surface-card';
import { Toggle } from '../../shared/toggle';
import { ToastService } from '../../core/toast.service';

/** Short names other pages use to open a settings section directly (?section=...). */
const SECTION_ALIASES: Record<string, string> = {
  'bank-integration': 'Bank Integration Settings',
  'bank-integrations': 'Bank Integration Settings',
  profile: 'Admin Profile',
};

type Values = Record<string, string | number | boolean>;

/**
 * Settings. Every section reads from and saves to identity-service (saved
 * settings, staff and roles, the administrator's own profile, password,
 * two-factor and sessions), except the bank panels, which save to
 * bank-integration-service. Nothing here is kept only in the browser.
 */
@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SurfaceCard, Badge, BankConnectionPanel, SettingsField, Toggle],
  templateUrl: './settings.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Settings {
  protected readonly profile = inject(AdminProfileService);
  protected readonly integrations = inject(BankIntegrationService);
  protected readonly staffAccess = inject(StaffAccessService);
  private readonly account = inject(AdminAccountGateway);
  private readonly auth = inject(AuthService);
  private readonly toasts = inject(ToastService);

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
  protected readonly gatewayUrl = environment.gatewayUrl || 'http://localhost:8080 (local services)';

  // ---- Saved settings (general, security, notifications, system) ----
  protected readonly saved = signal<Record<string, SettingsSection>>({});
  protected readonly drafts = signal<Record<string, Values>>({});
  protected readonly savingSection = signal<string | null>(null);
  protected readonly settingsError = signal<string | null>(null);

  // ---- Profile and password ----
  protected readonly profileName = signal('');
  protected readonly profilePhone = signal('');
  protected readonly savingProfile = signal(false);
  protected readonly passwords = signal({ current: '', next: '', confirm: '' });
  protected readonly savingPassword = signal(false);

  // ---- Two-factor and sessions ----
  protected readonly mfaEnabled = signal<boolean | null>(null);
  protected readonly mfaSetup = signal<(MfaSetup & { qr: string | null }) | null>(null);
  protected readonly mfaCode = signal('');
  protected readonly mfaPassword = signal('');
  protected readonly mfaBusy = signal(false);
  protected readonly sessions = signal<AdminSession[] | null>(null);

  // ---- Staff and roles ----
  protected readonly staff = signal<StaffMember[] | null>(null);
  protected readonly roles = signal<StaffRole[]>([]);
  protected readonly staffError = signal<string | null>(null);
  protected readonly newStaff = signal({ name: '', email: '', roleId: '' });
  protected readonly addingStaff = signal(false);
  protected readonly roleDraft = signal<{ id?: string; name: string; description: string; permissions: string[] } | null>(null);
  protected readonly savingRole = signal(false);
  protected readonly allPermissions = computed(() => this.staffAccess.access()?.allPermissions ?? []);
  protected readonly canManageStaff = computed(() => {
    const access = this.staffAccess.access();
    return !access || access.fullAccess || access.permissions.includes('settings');
  });

  // ---- Webhook base address (bank-integration-service) ----
  protected readonly platformUrlDraft = signal('');
  protected readonly platformUrlEditing = signal(false);
  protected readonly platformUrlSaving = signal(false);
  protected readonly platformUrlSaved = signal(false);
  protected readonly platformUrlError = signal('');

  private readonly avatarInput = viewChild<ElementRef<HTMLInputElement>>('avatarInput');

  constructor() {
    inject(ActivatedRoute).queryParamMap
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((params) => {
        const requested = params.get('section') ?? '';
        const section = SECTION_ALIASES[requested] ?? requested;
        if (this.sections.some((known) => known.id === section)) {
          this.active.set(section);
        }
      });
    effect(() => {
      const info = this.integrations.publicBaseUrl();
      if (info && !this.platformUrlEditing()) {
        this.platformUrlDraft.set(info.publicBaseUrl);
      }
    });
    void this.staffAccess.load();
    void this.loadSettings();
    void this.loadMe();
    void this.loadSessions();
    void this.loadStaff();
  }

  // ---- Saved settings ----

  private async loadSettings(): Promise<void> {
    try {
      const all = await this.account.settings();
      this.saved.set(all);
      this.drafts.set(Object.fromEntries(Object.entries(all).map(([key, section]) => [key, { ...section.values }])));
      this.settingsError.set(null);
    } catch {
      this.settingsError.set('Saved settings could not be loaded. Check that identity-service and the gateway are running.');
    }
  }

  protected value(section: string, key: string): string {
    const value = this.drafts()[section]?.[key];
    return value === undefined || value === null ? '' : String(value);
  }

  protected flag(section: string, key: string): boolean {
    return this.drafts()[section]?.[key] === true;
  }

  protected set(section: string, key: string, value: string | number | boolean): void {
    this.drafts.update((all) => ({ ...all, [section]: { ...(all[section] ?? {}), [key]: value } }));
  }

  protected changed(section: string): boolean {
    const saved = this.saved()[section]?.values ?? {};
    const draft = this.drafts()[section] ?? {};
    return Object.keys(draft).some((key) => String(draft[key]) !== String(saved[key]));
  }

  protected savedBy(section: string): string {
    const info = this.saved()[section];
    if (!info?.updatedAt) return 'Not saved yet: showing the defaults.';
    return `Last saved ${new Date(info.updatedAt).toLocaleString('en-KE')}${info.updatedBy ? ' by ' + info.updatedBy : ''}.`;
  }

  protected async saveSection(section: string, label: string): Promise<void> {
    if (this.savingSection()) return;
    this.savingSection.set(section);
    try {
      const saved = await this.account.saveSettings(section, this.drafts()[section] ?? {});
      this.saved.update((all) => ({ ...all, [section]: saved }));
      this.drafts.update((all) => ({ ...all, [section]: { ...saved.values } }));
      this.toasts.show('Settings saved', 'success', `${label} saved and applied.`);
    } catch (error) {
      this.toasts.show('Not saved', 'danger', describe(error));
    } finally {
      this.savingSection.set(null);
    }
  }

  // ---- Profile and password ----

  private async loadMe(): Promise<void> {
    try {
      const me = await this.account.me();
      this.profileName.set(me.name);
      this.profilePhone.set(me.phoneNumber ?? '');
      this.profile.phone.set(me.phoneNumber ?? '');
      this.mfaEnabled.set(me.mfaEnabled === true);
    } catch {
      this.mfaEnabled.set(null);
    }
  }

  protected async saveProfile(): Promise<void> {
    const name = this.profileName().trim();
    if (name.length < 2) {
      this.toasts.show('Name needed', 'warning', 'Enter at least 2 characters.');
      return;
    }
    this.savingProfile.set(true);
    try {
      const me = await this.account.updateMe(name, this.profilePhone().trim() || null);
      const [first, ...rest] = me.name.trim().split(/\s+/);
      this.profile.firstName.set(first ?? '');
      this.profile.lastName.set(rest.join(' '));
      this.profile.phone.set(me.phoneNumber ?? '');
      this.toasts.show('Profile updated', 'success', 'Your name and phone number were saved.');
    } catch (error) {
      this.toasts.show('Profile not saved', 'danger', describe(error));
    } finally {
      this.savingProfile.set(false);
    }
  }

  protected setPassword(key: 'current' | 'next' | 'confirm', value: string): void {
    this.passwords.update((p) => ({ ...p, [key]: value }));
  }

  protected async changePassword(): Promise<void> {
    const { current, next, confirm } = this.passwords();
    if (!current || !next) {
      this.toasts.show('Password needed', 'warning', 'Enter your current and new password.');
      return;
    }
    if (next.length < 8) {
      this.toasts.show('Password too short', 'warning', 'The new password needs at least 8 characters.');
      return;
    }
    if (next !== confirm) {
      this.toasts.show('Passwords differ', 'warning', 'The new password and its confirmation do not match.');
      return;
    }
    this.savingPassword.set(true);
    try {
      await this.account.changePassword(current, next);
      this.passwords.set({ current: '', next: '', confirm: '' });
      this.toasts.show('Password changed', 'success', 'Your other devices have been signed out.');
      void this.loadSessions();
    } catch (error) {
      this.toasts.show('Password not changed', 'danger',
        error instanceof HttpErrorResponse && error.status === 401
          ? 'Your current password is not correct.'
          : describe(error));
    } finally {
      this.savingPassword.set(false);
    }
  }

  // ---- Two-factor ----

  protected async startMfa(): Promise<void> {
    this.mfaBusy.set(true);
    try {
      const setup = await this.account.mfaSetup();
      let qr: string | null = null;
      try {
        qr = await toDataURL(setup.otpauthUri, { margin: 1, width: 200 });
      } catch {
        // The key below still works when the QR image cannot be drawn.
      }
      this.mfaSetup.set({ ...setup, qr });
    } catch (error) {
      this.toasts.show('Two-factor setup failed', 'danger', describe(error));
    } finally {
      this.mfaBusy.set(false);
    }
  }

  protected async enableMfa(): Promise<void> {
    this.mfaBusy.set(true);
    try {
      await this.account.mfaEnable(this.mfaCode().replace(/\s/g, ''));
      this.mfaSetup.set(null);
      this.mfaCode.set('');
      this.mfaEnabled.set(true);
      this.toasts.show('Two-factor is on', 'success', 'You will be asked for a code from your app at every sign in.');
    } catch (error) {
      this.toasts.show('Code not accepted', 'danger',
        error instanceof HttpErrorResponse && [400, 401].includes(error.status)
          ? 'That code is not correct. Check the time on your phone and try again.'
          : describe(error));
    } finally {
      this.mfaBusy.set(false);
    }
  }

  protected async disableMfa(): Promise<void> {
    this.mfaBusy.set(true);
    try {
      await this.account.mfaDisable(this.mfaPassword(), this.mfaCode().replace(/\s/g, ''));
      this.mfaEnabled.set(false);
      this.mfaPassword.set('');
      this.mfaCode.set('');
      this.toasts.show('Two-factor is off', 'info', 'Sign in now needs only your password.');
    } catch (error) {
      this.toasts.show('Two-factor still on', 'danger',
        error instanceof HttpErrorResponse && [400, 401].includes(error.status)
          ? 'Your password or code is not correct.'
          : describe(error));
    } finally {
      this.mfaBusy.set(false);
    }
  }

  // ---- Sessions ----

  protected async loadSessions(): Promise<void> {
    try {
      this.sessions.set(await this.account.sessions());
    } catch {
      this.sessions.set([]);
    }
  }

  protected async revokeSession(session: AdminSession): Promise<void> {
    try {
      await this.account.revokeSession(session.id);
      this.toasts.show('Device signed out', 'success', `${session.device} has been signed out.`);
      void this.loadSessions();
    } catch (error) {
      this.toasts.show('Not signed out', 'danger', describe(error));
    }
  }

  protected sessionTime(session: AdminSession): string {
    return new Date(session.signedInAt).toLocaleString('en-KE', { dateStyle: 'medium', timeStyle: 'short' });
  }

  // ---- Staff and roles ----

  protected async loadStaff(): Promise<void> {
    if (!(await this.staffAccess.load().then(() => this.canManageStaff()))) {
      return;
    }
    try {
      const [staff, roles] = await Promise.all([this.account.staff(), this.account.roles()]);
      this.staff.set(staff);
      this.roles.set(roles);
      this.staffError.set(null);
    } catch {
      this.staffError.set('Staff and roles could not be loaded. Check that identity-service and the gateway are running.');
    }
  }

  protected setNewStaff(key: 'name' | 'email' | 'roleId', value: string): void {
    this.newStaff.update((s) => ({ ...s, [key]: value }));
  }

  protected async addStaff(): Promise<void> {
    const { name, email, roleId } = this.newStaff();
    if (!email.trim() || !roleId) {
      this.toasts.show('Details needed', 'warning', 'Enter the email address and choose a role.');
      return;
    }
    this.addingStaff.set(true);
    try {
      const result = await this.account.addStaff(name.trim(), email.trim(), roleId);
      this.newStaff.set({ name: '', email: '', roleId: '' });
      this.toasts.show('Staff member added', result.emailSent ? 'success' : 'warning',
        result.existingAccount
          ? `${email} can now sign in to the admin portal with their usual password${result.emailSent ? '; they were emailed' : ''}.`
          : result.emailSent
            ? `${email} was emailed a temporary password and a link to choose their own.`
            : `${email} was created, but the email could not be sent (SMTP). They can use Forgot password.`);
      void this.loadStaff();
    } catch (error) {
      this.toasts.show('Staff member not added', 'danger', describe(error));
    } finally {
      this.addingStaff.set(false);
    }
  }

  protected async changeStaffRole(member: StaffMember, roleId: string): Promise<void> {
    if (!roleId || roleId === member.roleId) return;
    try {
      await this.account.changeStaffRole(member.id, roleId);
      this.toasts.show('Role changed', 'success', `${member.email} now has the ${this.roleName(roleId)} role.`);
      void this.loadStaff();
    } catch (error) {
      this.toasts.show('Role not changed', 'danger', describe(error));
      void this.loadStaff();
    }
  }

  protected async removeStaff(member: StaffMember): Promise<void> {
    if (!confirm(`Remove admin access for ${member.email}? Their account stays, as a customer account.`)) return;
    try {
      await this.account.removeStaff(member.id);
      this.toasts.show('Access removed', 'success', `${member.email} can no longer sign in to the admin portal.`);
      void this.loadStaff();
    } catch (error) {
      this.toasts.show('Access not removed', 'danger', describe(error));
    }
  }

  protected isSelf(member: StaffMember): boolean {
    return member.id === this.auth.userId();
  }

  protected roleName(roleId: string | null): string {
    return this.roles().find((role) => role.id === roleId)?.name ?? 'Full access';
  }

  protected permissionLabel(key: string): string {
    return this.allPermissions().find((p) => p.key === key)?.label ?? key;
  }

  protected editRole(role?: StaffRole): void {
    this.roleDraft.set(role
      ? { id: role.id, name: role.name, description: role.description ?? '', permissions: [...role.permissions] }
      : { name: '', description: '', permissions: ['dashboard'] });
  }

  protected setRoleField(key: 'name' | 'description', value: string): void {
    this.roleDraft.update((draft) => (draft ? { ...draft, [key]: value } : draft));
  }

  protected togglePermission(key: string): void {
    this.roleDraft.update((draft) => {
      if (!draft) return draft;
      const has = draft.permissions.includes(key);
      return { ...draft, permissions: has ? draft.permissions.filter((p) => p !== key) : [...draft.permissions, key] };
    });
  }

  protected async saveRole(): Promise<void> {
    const draft = this.roleDraft();
    if (!draft) return;
    this.savingRole.set(true);
    try {
      await this.account.saveRole(draft);
      this.roleDraft.set(null);
      this.toasts.show('Role saved', 'success', `${draft.name} saved. Staff with this role get its permissions at once.`);
      void this.loadStaff();
    } catch (error) {
      this.toasts.show('Role not saved', 'danger', describe(error));
    } finally {
      this.savingRole.set(false);
    }
  }

  protected async deleteRole(role: StaffRole): Promise<void> {
    if (!confirm(`Delete the ${role.name} role?`)) return;
    try {
      await this.account.deleteRole(role.id);
      this.toasts.show('Role deleted', 'success', `${role.name} was deleted.`);
      void this.loadStaff();
    } catch (error) {
      this.toasts.show('Role not deleted', 'danger', describe(error));
    }
  }

  // ---- Webhook base address ----

  protected onPlatformUrlInput(value: string): void {
    this.platformUrlEditing.set(true);
    this.platformUrlDraft.set(value);
  }

  protected async savePlatformUrl(): Promise<void> {
    this.platformUrlError.set('');
    this.platformUrlSaving.set(true);
    try {
      await this.integrations.updatePublicBaseUrl(this.platformUrlDraft().trim());
      this.platformUrlEditing.set(false);
      this.platformUrlSaved.set(true);
      setTimeout(() => this.platformUrlSaved.set(false), 2200);
    } catch (error) {
      this.platformUrlError.set(bankSaveError(error));
    } finally {
      this.platformUrlSaving.set(false);
    }
  }

  protected delayFor(index: number): string {
    return `delay-${(index + 1) * 50}`;
  }

  // ---- Avatar (kept in this browser) ----

  protected pickAvatar(): void {
    this.avatarInput()?.nativeElement.click();
  }

  protected async onAvatarSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    await this.profile.uploadAvatar(file);
    input.value = '';
  }

  protected removeAvatar(): void {
    this.profile.removeAvatar();
  }
}

/** The backend's own reason when it gives one. */
function describe(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'Could not reach identity-service through the gateway.';
    if (error.status === 403) return error.error?.message ?? 'Your role does not allow this.';
    if (typeof error.error?.message === 'string') return error.error.message;
    return `The backend returned HTTP ${error.status}.`;
  }
  return error instanceof Error ? error.message : 'Something went wrong.';
}

function bankSaveError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'The bank integration service on port 8090 is not running.';
    if (error.status === 401 || error.status === 403) return 'The bank integration service refused your sign-in. Start it with the same JWT_SECRET as identity-service.';
    if (typeof error.error?.message === 'string') return error.error.message;
  }
  return error instanceof Error ? error.message : 'The backend rejected that address.';
}
