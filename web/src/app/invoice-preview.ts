import {
  afterNextRender,
  Component,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  signal,
  ViewChild,
} from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';

@Component({
  selector: 'app-invoice-preview',
  template: `
    <dialog
      #dialog
      aria-labelledby="invoice-preview-title"
      (cancel)="$event.preventDefault(); dismiss()"
    >
      <header>
        <div>
          <h2 id="invoice-preview-title">Invoice preview</h2>
          <p>{{ filename() }}</p>
        </div>
        <button
          type="button"
          class="button secondary"
          (click)="dismiss()"
          aria-label="Close invoice preview"
        >
          Close
        </button>
      </header>
      @if (loading()) {
        <p class="status" role="status">Loading preview…</p>
      }
      @if (error()) {
        <div class="status" role="alert">
          <p>{{ error() }}</p>
          <button class="button secondary" type="button" (click)="load()">Retry</button>
        </div>
      }
      <div class="document" [hidden]="loading() || !!error()">
        @if (imageUrl()) {
          <img [src]="imageUrl()" [alt]="'Invoice: ' + filename()" (error)="imageFailed()" />
        }
        <canvas
          #canvas
          [hidden]="!!imageUrl()"
          role="img"
          [attr.aria-label]="'Invoice page ' + page() + ' of ' + pages()"
        ></canvas>
      </div>
      @if (pages() > 0 && !loading() && !error()) {
        <nav aria-label="Invoice pages">
          <button
            type="button"
            class="button secondary"
            (click)="changePage(-1)"
            [disabled]="rendering() || page() === 1"
          >
            Previous
          </button>
          <span aria-live="polite"
            >Page {{ page() }} of {{ pages() }}{{ rendering() ? ' · Loading…' : '' }}</span
          >
          <button
            type="button"
            class="button secondary"
            (click)="changePage(1)"
            [disabled]="rendering() || page() === pages()"
          >
            Next
          </button>
        </nav>
      }
      <small>Read-only preview</small>
    </dialog>
  `,
  styles: `
    dialog {
      box-sizing: border-box;
      width: min(1000px, calc(100vw - 24px));
      max-height: calc(100dvh - 24px);
      overflow: auto;
      border: 1px solid var(--ws-line);
      border-radius: 16px;
      padding: 20px;
      background: var(--ws-card);
      color: var(--ws-ink);
    }
    dialog::backdrop {
      background: rgba(5, 18, 35, 0.75);
    }
    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 16px;
    }
    header div {
      min-width: 0;
    }
    h2 {
      font-size: 24px;
      margin: 0 0 8px;
    }
    header p {
      font-size: 13px;
      color: var(--ws-muted);
      margin: 0;
      overflow-wrap: anywhere;
    }
    .document {
      background: #e4e9ef;
      border-radius: 8px;
      padding: 12px;
    }
    .document img,
    canvas {
      display: block;
      max-width: 100%;
      height: auto;
      margin: auto;
      background: white;
    }
    canvas {
      width: 100%;
    }
    .document[hidden],
    canvas[hidden] {
      display: none;
    }
    .status {
      padding: 24px;
      background: var(--ws-soft);
      border-radius: 8px;
    }
    nav {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 16px;
      margin: 16px 0;
    }
    nav span,
    small {
      font-size: 12px;
      color: var(--ws-muted);
    }
    small {
      display: block;
      margin-top: 12px;
    }
    @media (max-width: 600px) {
      dialog {
        padding: 12px;
      }
      h2 {
        font-size: 20px;
      }
      .document {
        padding: 4px;
      }
      nav {
        gap: 8px;
      }
      .button {
        padding: 10px;
        font-size: 12px;
      }
    }
  `,
})
export class InvoicePreview implements OnDestroy {
  readonly invoiceId = input.required<string>();
  readonly filename = input.required<string>();
  readonly closed = output<void>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  @ViewChild('canvas', { static: true }) canvas!: ElementRef<HTMLCanvasElement>;
  private readonly http = inject(HttpClient);
  readonly loading = signal(true);
  readonly rendering = signal(false);
  readonly error = signal('');
  readonly imageUrl = signal('');
  readonly page = signal(1);
  readonly pages = signal(0);
  private request?: Subscription;
  private task?: PDFDocumentLoadingTask;
  private pdf?: PDFDocumentProxy;
  private renderTask?: RenderTask;
  private generation = 0;
  private disposed = false;

  constructor() {
    afterNextRender(() => {
      this.dialog.nativeElement.showModal();
      this.load();
    });
  }
  load() {
    if (this.disposed) return;
    this.releaseDocument();
    const generation = this.generation;
    this.loading.set(true);
    this.error.set('');
    this.pages.set(0);
    this.page.set(1);
    this.request = this.http
      .get('/api/invoices/' + encodeURIComponent(this.invoiceId()) + '/document', {
        responseType: 'blob',
      })
      .pipe(timeout(20000))
      .subscribe({
        next: (blob) => {
          void this.display(blob, generation);
        },
        error: (e) => {
          if (this.disposed || generation !== this.generation) return;
          this.loading.set(false);
          this.error.set(
            e instanceof HttpErrorResponse && e.status === 401
              ? 'Your session expired. Close this preview and sign in again.'
              : e instanceof HttpErrorResponse && [403, 404].includes(e.status)
                ? 'This invoice is not available to preview.'
                : 'Could not load the preview. Please retry.',
          );
        },
      });
  }
  private async display(blob: Blob, generation: number) {
    try {
      const type = blob.type.split(';')[0];
      if (['image/png', 'image/jpeg'].includes(type)) {
        this.imageUrl.set(URL.createObjectURL(blob));
      } else if (type === 'application/pdf') {
        const data = new Uint8Array(await blob.arrayBuffer());
        const pdfjs = await import('pdfjs-dist');
        if (this.disposed || generation !== this.generation) return;
        pdfjs.GlobalWorkerOptions.workerSrc = '/ocr/pdf.worker.min.mjs';
        this.task = pdfjs.getDocument({ data, standardFontDataUrl: '/ocr/pdf-fonts/' });
        const pdf = await this.task.promise;
        if (this.disposed || generation !== this.generation) return;
        this.pdf = pdf;
        this.pages.set(this.pdf.numPages);
        await this.renderPage(1);
      } else {
        throw new Error('Unsupported document');
      }
    } catch {
      if (!this.disposed && generation === this.generation)
        this.error.set('This document could not be displayed. Please retry.');
    } finally {
      if (!this.disposed && generation === this.generation) this.loading.set(false);
    }
  }
  async changePage(direction: number) {
    const target = this.page() + direction;
    if (this.rendering() || target < 1 || target > this.pages()) return;
    try {
      await this.renderPage(target);
    } catch {
      if (!this.disposed) this.error.set('This page could not be displayed. Please retry.');
    }
  }
  private async renderPage(number: number) {
    if (!this.pdf || this.disposed) return;
    this.rendering.set(true);
    const generation = this.generation;
    try {
      const page = await this.pdf.getPage(number);
      if (this.disposed || generation !== this.generation) return;
      const original = page.getViewport({ scale: 1 });
      const viewport = page.getViewport({
        scale: Math.min(2, 2000 / Math.max(original.width, original.height)),
      });
      const canvas = this.canvas.nativeElement;
      canvas.width = Math.ceil(viewport.width);
      canvas.height = Math.ceil(viewport.height);
      this.renderTask = page.render({ canvas, viewport });
      await this.renderTask.promise;
      if (!this.disposed && generation === this.generation) this.page.set(number);
      page.cleanup();
    } finally {
      if (generation === this.generation) this.rendering.set(false);
    }
  }
  imageFailed() {
    this.error.set('This image could not be displayed. Please retry.');
  }
  dismiss() {
    this.disposed = true;
    this.releaseDocument();
    this.dialog.nativeElement.close();
    this.closed.emit();
  }
  private releaseDocument() {
    ++this.generation;
    this.request?.unsubscribe();
    this.renderTask?.cancel();
    this.renderTask = undefined;
    this.rendering.set(false);
    if (this.task) void this.task.destroy().catch(() => {});
    this.task = undefined;
    this.pdf = undefined;
    if (this.imageUrl()) URL.revokeObjectURL(this.imageUrl());
    this.imageUrl.set('');
    if (this.canvas) {
      this.canvas.nativeElement.width = 0;
      this.canvas.nativeElement.height = 0;
    }
  }
  ngOnDestroy() {
    this.disposed = true;
    this.releaseDocument();
  }
}
