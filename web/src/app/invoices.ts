import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom, timeout } from 'rxjs';
import { extractInvoice } from './invoice-extraction';
import { WorkspaceIcon } from './workspace-icon';
import { InvoiceCamera } from './invoice-camera';

interface Invoice {
  id: string;
  vendor: string;
  invoiceNumber: string;
  amount: number;
  currency: string;
  invoiceDate: string;
  dueDate: string | null;
  filename: string;
  status: 'PENDING';
  source: 'INVOICE';
}

@Component({
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe, RouterLink, WorkspaceIcon, InvoiceCamera],
  templateUrl: './invoices.html',
  styleUrl: './invoices.css',
})
export class Invoices implements OnDestroy {
  private http = inject(HttpClient);
  readonly activity = inject(ActivatedRoute).snapshot.data['page'] === 'transactions';
  private fb = inject(FormBuilder);
  readonly form = this.fb.nonNullable.group({
    vendor: ['', [Validators.required, Validators.maxLength(200)]],
    invoiceNumber: ['', Validators.maxLength(100)],
    amount: [
      '',
      [Validators.required, Validators.pattern(/^\d{1,15}(\.\d{1,4})?$/), Validators.min(0.0001)],
    ],
    currency: ['', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
    invoiceDate: ['', Validators.required],
    dueDate: [''],
    reviewed: [false, Validators.requiredTrue],
  });
  readonly invoices = signal<Invoice[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly extracting = signal(false);
  readonly saving = signal(false);
  readonly progress = signal('');
  readonly extractionNote = signal('');
  readonly rawText = signal('');
  readonly file = signal<File | null>(null);
  readonly preview = signal('');
  readonly hasMore = signal(false);
  readonly cameraOpen = signal(false);
  private page = 0;
  private disposed = false;
  readonly totals = computed(() => {
    const sums = new Map<string, number>();
    for (const item of this.invoices())
      sums.set(item.currency, (sums.get(item.currency) || 0) + Number(item.amount));
    return [...sums].map(([currency, amount]) => ({ currency, amount }));
  });
  constructor() {
    void this.load();
  }
  async load(more = false) {
    this.loading.set(true);
    this.error.set('');
    const page = more ? this.page + 1 : 0;
    try {
      const items = await firstValueFrom(
        this.http.get<Invoice[]>(`/api/invoices?page=${page}`).pipe(timeout(20000)),
      );
      this.invoices.set(more ? [...this.invoices(), ...items] : items);
      this.page = page;
      this.hasMore.set(items.length === 50);
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.loading.set(false);
    }
  }
  async select(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) await this.useFile(file);
  }
  async useFile(file: File) {
    if (!file || this.extracting() || this.saving()) return;
    this.error.set('');
    this.notice.set('');
    if (
      !['image/jpeg', 'image/png', 'application/pdf'].includes(file.type) ||
      file.size === 0 ||
      file.size > 10 * 1024 * 1024
    ) {
      this.error.set(
        'Choose a JPEG, PNG or PDF file up to 10 MB. For HEIC photos, export as JPEG first.',
      );
      return;
    }
    this.clear();
    this.file.set(file);
    if (file.type.startsWith('image/')) this.preview.set(URL.createObjectURL(file));
    this.extracting.set(true);
    this.progress.set('Preparing your invoice…');
    try {
      const result = await extractInvoice(file, (text) => {
        if (!this.disposed) this.progress.set(text);
      });
      if (this.disposed) return;
      this.form.patchValue(result.fields);
      this.rawText.set(result.text);
      this.extractionNote.set(
        result.text.trim()
          ? 'Check every suggested value against your document. Blank or incorrect fields need your input.'
          : 'No readable text found. Try a clearer photo or enter the details below.',
      );
    } catch (e) {
      this.extractionNote.set(
        e instanceof Error && /no more than 5/.test(e.message)
          ? e.message
          : 'Automatic extraction could not finish. You can enter the details below or retry with a clearer file.',
      );
    } finally {
      this.extracting.set(false);
    }
  }
  clear() {
    if (this.preview()) URL.revokeObjectURL(this.preview());
    this.preview.set('');
    this.file.set(null);
    this.rawText.set('');
    this.extractionNote.set('');
    this.form.reset();
  }
  async save() {
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.file() || this.saving() || this.extracting()) return;
    this.error.set('');
    const value = this.form.getRawValue();
    if (!value.vendor.trim()) {
      this.error.set('Enter the vendor name.');
      return;
    }
    if (value.dueDate && value.dueDate < value.invoiceDate) {
      this.error.set('Due date cannot precede invoice date.');
      return;
    }
    const body = new FormData();
    body.append('file', this.file()!);
    for (const key of [
      'vendor',
      'invoiceNumber',
      'amount',
      'currency',
      'invoiceDate',
      'dueDate',
    ] as const)
      if (value[key]) body.append(key, value[key].trim());
    this.saving.set(true);
    try {
      await firstValueFrom(this.http.post<Invoice>('/api/invoices', body).pipe(timeout(30000)));
      this.clear();
      this.notice.set('Invoice saved as a pending transaction. No payment has been recorded.');
      await this.load();
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.saving.set(false);
    }
  }
  private message(e: unknown) {
    return e instanceof HttpErrorResponse && typeof e.error?.error === 'string'
      ? e.error.error
      : 'Could not reach the invoice service. Please retry.';
  }
  ngOnDestroy() {
    this.disposed = true;
    if (this.preview()) URL.revokeObjectURL(this.preview());
  }
}
