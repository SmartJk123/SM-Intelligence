import { Injectable, signal } from '@angular/core';
import { Theme } from './data';

const STORAGE_KEY = 'sm-theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly current = signal<Theme>(this.readStoredTheme());

  readonly theme = this.current.asReadonly();

  constructor() {
    this.apply(this.current());
  }

  toggle(): void {
    const next: Theme = this.current() === 'dark' ? 'light' : 'dark';
    this.current.set(next);
    this.apply(next);
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Storage unavailable, for example in private mode. The theme still
      // applies for the current session.
    }
  }

  private readStoredTheme(): Theme {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      // Light is the default because the interface is designed light first.
      // A saved preference still wins, and dark remains available from the top bar.
      return saved === 'light' || saved === 'dark' ? saved : 'light';
    } catch {
      return 'dark';
    }
  }
}
