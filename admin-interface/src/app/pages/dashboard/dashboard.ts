import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BankIntegrationService } from '../../core/bank-integration.service';
import { PlatformEvent } from '../../core/bank-integration.gateway';
import { Page } from '../../core/data';
import { FlowSeries } from '../../core/dashboard-data';
import { ToastService } from '../../core/toast.service';
import { Badge } from '../../shared/badge';
import { GroupedBarChart } from '../../shared/grouped-bar-chart';
import { KpiCard } from '../../shared/kpi-card';
import { SurfaceCard } from '../../shared/surface-card';

interface QuickAction {
  id: string;
  label: string;
  icon: string;
  bg: string;
  fg: string;
}

const QUICK_ACTION_TARGETS: Record<string, Page> = {
  banks: 'bank-integrations',
  reconcile: 'reconciliation',
  notifications: 'notifications',
  reports: 'reports',
};

/**
 * Operations dashboard. Every figure comes from the backend's real statistics,
 * which are computed from bank notifications actually received. There is no
 * sample data: an empty platform shows zero and says so.
 */
@Component({
  selector: 'app-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [KpiCard, SurfaceCard, Badge, GroupedBarChart],
  templateUrl: './dashboard.html',
  host: { class: 'flex-1 flex flex-col min-h-0' },
})
export class Dashboard {
  private readonly integrations = inject(BankIntegrationService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);

  /** True once the Spring Boot service has answered the statistics request. */
  protected readonly backendConnected = this.integrations.backendConnected;
  protected readonly stats = this.integrations.stats;

  protected readonly total = computed(() => this.stats()?.notificationsTotal ?? 0);
  protected readonly today = computed(() => this.stats()?.notificationsToday ?? 0);
  protected readonly failed = computed(() => this.stats()?.failed ?? 0);
  protected readonly processed = computed(() => this.stats()?.processed ?? 0);
  protected readonly pending = computed(() => this.stats()?.pending ?? 0);
  protected readonly successRate = computed(() => this.stats()?.successRate ?? 0);
  protected readonly banksConnected = computed(() => this.stats()?.banksConnected ?? 0);
  protected readonly banksSupported = computed(() => this.stats()?.banksSupported ?? 0);
  protected readonly environment = computed(() => this.integrations.healthFor('stanbic').environment);

  protected readonly hasData = computed(() => this.total() > 0);

  protected readonly lastReceived = computed(() => {
    const minutes = this.stats()?.minutesSinceLastReceived;
    if (minutes === null || minutes === undefined) {
      return 'nothing received yet';
    }
    if (minutes < 1) {
      return 'less than a minute ago';
    }
    if (minutes < 60) {
      return minutes === 1 ? '1 minute ago' : `${minutes} minutes ago`;
    }
    const hours = Math.round(minutes / 60);
    return hours === 1 ? '1 hour ago' : `${hours} hours ago`;
  });

  protected readonly events = computed<PlatformEvent[]>(() => this.stats()?.recent ?? []);

  /**
   * Credit or Debit as the bank stated it, for the operations table. The
   * amount is deliberately not shown on the administrator surface.
   */
  protected flowLabel(event: PlatformEvent): string {
    const value = (event.direction ?? '').trim().toLowerCase();
    if (value.startsWith('c') || value === 'in' || value === 'inflow') {
      return 'Credit';
    }
    if (value.startsWith('d') || value === 'out' || value === 'outflow') {
      return 'Debit';
    }
    return 'Not stated';
  }

  protected flowColor(event: PlatformEvent): string {
    switch (this.flowLabel(event)) {
      case 'Credit':
        return 'var(--green-ink)';
      case 'Debit':
        return 'var(--red-ink)';
      default:
        return 'var(--text-3)';
    }
  }

  protected readonly volumeLabels = computed(() =>
    (this.stats()?.volume ?? []).map((point) => point.date.slice(5)),
  );

  protected readonly volumeSeries = computed<FlowSeries[]>(() => {
    const volume = this.stats()?.volume ?? [];
    return [
      { name: 'Received', color: 'var(--c-indigo)', values: volume.map((point) => point.received) },
      { name: 'Failed', color: 'var(--c-pink)', values: volume.map((point) => point.failed) },
    ];
  });

  protected readonly processingMix = computed(() => {
    const segments = [
      { label: 'Processed', value: this.processed(), color: 'var(--c-indigo)' },
      { label: 'Failed', value: this.failed(), color: 'var(--c-pink)' },
      { label: 'Pending', value: this.pending(), color: 'var(--c-amber)' },
    ].filter((segment) => segment.value > 0);

    const total = segments.reduce((sum, segment) => sum + segment.value, 0);
    return segments.map((segment) => ({
      ...segment,
      percent: total === 0 ? 0 : (segment.value / total) * 100,
    }));
  });

  protected readonly quickActions: QuickAction[] = [
    { id: 'banks', label: 'Banks', icon: '⬡', bg: 'var(--primary-soft)', fg: 'var(--primary-ink)' },
    { id: 'reconcile', label: 'Reconcile', icon: '⊕', bg: 'var(--green-soft)', fg: 'var(--green-ink)' },
    { id: 'notifications', label: 'Alerts', icon: '◎', bg: 'var(--gold-soft)', fg: 'var(--gold-ink)' },
    { id: 'reports', label: 'Reports', icon: '▦', bg: 'var(--violet-soft)', fg: 'var(--violet-ink)' },
  ];

  protected receivedTime(event: PlatformEvent): string {
    if (!event.receivedAt) {
      return 'unknown';
    }
    return new Date(event.receivedAt).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  protected duration(event: PlatformEvent): string {
    if (!event.receivedAt || !event.processedAt) {
      return 'in progress';
    }
    const ms = new Date(event.processedAt).getTime() - new Date(event.receivedAt).getTime();
    return ms < 1000 ? `${Math.max(1, Math.round(ms))} ms` : `${(ms / 1000).toFixed(1)} s`;
  }

  protected signatureLabel(event: PlatformEvent): string {
    if (event.signatureValid === true) {
      return 'Verified';
    }
    return event.signatureValid === false ? 'Invalid' : 'Not checked';
  }

  protected goTo(page: Page): void {
    void this.router.navigate(['/admin', page]);
  }

  protected onQuickAction(id: string): void {
    const page = QUICK_ACTION_TARGETS[id];
    if (page) {
      this.goTo(page);
    }
  }

  protected async refresh(): Promise<void> {
    await this.integrations.loadStats();
    await this.integrations.loadHealth();
    this.toasts.show('Dashboard refreshed', 'info', 'Figures reloaded from the backend.');
  }
}
