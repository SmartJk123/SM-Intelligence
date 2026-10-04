import { Injectable, computed, inject, linkedSignal, signal } from '@angular/core';
import { AuthService } from './auth.service';

export const MAX_AVATAR_BYTES = 2 * 1024 * 1024;

export interface AvatarResult {
  ok: boolean;
  error?: string;
}

/**
 * Holds the signed in administrator profile. The avatar is kept as a data URL
 * so it can be previewed immediately. When the Spring Boot backend is connected
 * this service becomes the single place that uploads the image and stores the
 * returned URL.
 */
@Injectable({ providedIn: 'root' })
export class AdminProfileService {
  private readonly session = inject(AuthService).session;

  // Name and email follow whoever is signed in; Settings can still edit them
  // locally, and the next sign-in resets them to that account's own details.
  readonly firstName = linkedSignal(() => splitName(this.session()?.name)[0]);
  readonly lastName = linkedSignal(() => splitName(this.session()?.name)[1]);
  readonly email = linkedSignal(() => this.session()?.email ?? '');
  readonly phone = signal('');
  readonly jobTitle = signal('Platform Administrator');
  readonly role = signal('Platform Admin');

  /** Data URL of the uploaded profile image, or null when none is set. */
  readonly avatar = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly avatarError = signal('');

  readonly fullName = computed(
    () => `${this.firstName()} ${this.lastName()}`.trim() || 'Administrator',
  );

  readonly initials = computed(() => {
    const parts = this.fullName().split(' ').filter((part) => part.length > 0);
    const first = parts.length > 0 ? parts[0].charAt(0) : 'A';
    const second = parts.length > 1 ? parts[1].charAt(0) : '';
    return `${first}${second}`.toUpperCase();
  });

  async uploadAvatar(file: File): Promise<AvatarResult> {
    this.avatarError.set('');

    if (!file.type.startsWith('image/')) {
      const error = 'Select an image file, for example PNG, JPG or WEBP.';
      this.avatarError.set(error);
      return { ok: false, error };
    }

    if (file.size > MAX_AVATAR_BYTES) {
      const error = 'The image must be 2 MB or smaller.';
      this.avatarError.set(error);
      return { ok: false, error };
    }

    this.uploading.set(true);
    try {
      const dataUrl = await readAsDataUrl(file);
      this.avatar.set(dataUrl);
      return { ok: true };
    } catch {
      const error = 'The image could not be read. Try another file.';
      this.avatarError.set(error);
      return { ok: false, error };
    } finally {
      this.uploading.set(false);
    }
  }

  removeAvatar(): void {
    this.avatar.set(null);
    this.avatarError.set('');
  }

  clearError(): void {
    this.avatarError.set('');
  }
}

function splitName(name: string | undefined): [string, string] {
  const parts = (name ?? '').trim().split(/\s+/).filter((part) => part.length > 0);
  if (!parts.length) return ['Administrator', ''];
  return [parts[0], parts.slice(1).join(' ')];
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error('read-failed'));
    reader.readAsDataURL(file);
  });
}
