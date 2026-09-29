import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom, timeout } from 'rxjs';
import { extractInvoice } from './invoice-extraction';
import { WorkspaceIcon } from './workspace-icon';
import { InvoiceCamera } from './invoice-camera';
import { InvoicePreview } from './invoice-preview';

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
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DatePipe,
    RouterLink,
    WorkspaceIcon,
    InvoiceCamera,
    InvoicePreview,
  ],
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
  readonly deleting = signal<string | null>(null);
  readonly progress = signal('');
  readonly extractionNote = signal('');
  readonly rawText = signal('');
  readonly file = signal<File | null>(null);
  readonly preview = signal('');
  readonly hasMore = signal(false);
  readonly cameraOpen = signal(false);
  readonly previewInvoice = signal<Invoice | null>(null);
  readonly readingClipboard = signal(false);
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
  async paste(event: ClipboardEvent) {
    event.preventDefault();
    if (this.extracting() || this.saving() || this.readingClipboard()) return;
    const files = Array.from(event.clipboardData?.files || []);
    const file = files.find((item) =>
      ['image/png', 'image/jpeg', 'application/pdf'].includes(item.type),
    );
    if (!file) {
      this.error.set(
        'Copy an invoice image or screenshot, then paste it here. For a PDF, use Upload a file. Copied text and links are not invoice files.',
      );
      return;
    }
    await this.useFile(file);
  }
  async pasteFromClipboard() {
    if (this.extracting() || this.saving() || this.readingClipboard()) return;
    this.error.set('');
    if (!navigator.clipboard?.read) {
      this.error.set(
        'Click the paste area and press Ctrl+V. The clipboard button needs a supported browser on HTTPS or localhost.',
      );
      return;
    }
    this.readingClipboard.set(true);
    try {
      const items = await navigator.clipboard.read();
      if (this.disposed) return;
      for (const item of items) {
        const type = ['image/png', 'image/jpeg', 'application/pdf'].find((type) =>
          item.types.includes(type),
        );
        if (!type) continue;
        const blob = await item.getType(type);
        if (this.disposed) return;
        const extension =
          type === 'application/pdf' ? 'pdf' : type === 'image/jpeg' ? 'jpg' : 'png';
        await this.useFile(new File([blob], `pasted-invoice-${Date.now()}.${extension}`, { type }));
        return;
      }
      this.error.set(
        'No invoice image was found on your clipboard. Copy an image or screenshot first, or use Upload a file.',
      );
    } catch {
      this.error.set(
        'Clipboard access was not available. Click the paste area and press Ctrl+V, or upload the file.',
      );
    } finally {
      this.readingClipboard.set(false);
    }
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
  async deleteInvoice(invoice: Invoice) {
    if (this.deleting() || this.loading() || this.saving()) return;
    if (
      !window.confirm(
        `Delete the invoice from ${invoice.vendor}${invoice.invoiceNumber ? ' (' + invoice.invoiceNumber + ')' : ''}? This permanently removes its document and pending transaction. This cannot be undone.`,
      )
    )
      return;
    this.deleting.set(invoice.id);
    this.error.set('');
    this.notice.set('');
    try {
      await firstValueFrom(
        this.http.delete('/api/invoices/' + encodeURIComponent(invoice.id)).pipe(timeout(20000)),
      );
      this.invoices.update((items) => items.filter((item) => item.id !== invoice.id));
      if (this.previewInvoice()?.id === invoice.id) this.previewInvoice.set(null);
      this.notice.set('Invoice and its pending transaction deleted.');
      await this.load();
    } catch (e) {
      this.error.set(this.message(e));
    } finally {
      this.deleting.set(null);
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
