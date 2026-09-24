export interface InvoiceFields {
  vendor: string;
  invoiceNumber: string;
  amount: string;
  currency: string;
  invoiceDate: string;
  dueDate: string;
}

// Conservative suggestions only: the user must review every field before saving.
export function parseInvoiceText(text: string): InvoiceFields {
  const lines = text
    .split(/\r?\n/)
    .map((v) => v.trim())
    .filter(Boolean);
  const date = (value: string) => {
    const iso = value.match(/\b(20\d{2})[-/](\d{1,2})[-/](\d{1,2})\b/);
    const local = value.match(/\b(\d{1,2})[-/](\d{1,2})[-/](20\d{2})\b/);
    // Ambiguous dates are left blank for the reviewer.
    const parts = iso
      ? [iso[1], iso[2], iso[3]]
      : local && Number(local[1]) > 12
        ? [local[3], local[2], local[1]]
        : null;
    if (!parts) return '';
    const result = `${parts[0]}-${parts[1].padStart(2, '0')}-${parts[2].padStart(2, '0')}`;
    const parsed = new Date(result);
    return !Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === result
      ? result
      : '';
  };
  const totalLines = lines.filter(
    (l) =>
      /\b(amount due|balance due|grand total|total payable|total)\b/i.test(l) &&
      !/sub\s*total|tax total|total tax/i.test(l),
  );
  const totalLine =
    totalLines.find((l) => /amount due|balance due|grand total|total payable/i.test(l)) ||
    totalLines.at(-1) ||
    '';
  const amountMatch = totalLine.match(/\d[\d, ]*(?:\.\d{1,4})?/g)?.at(-1);
  const amount = amountMatch?.replace(/[, ]/g, '') || '';
  const currency =
    text.match(/\b(KES|USD|EUR|GBP|UGX|TZS|ZAR)\b/i)?.[1].toUpperCase() ||
    (/\bKsh\.?\b/i.test(text) ? 'KES' : '');
  const vendor =
    lines.find((l) => /^(vendor|supplier|from)\s*:/i.test(l))?.replace(/^[^:]+:\s*/, '') ||
    lines.find(
      (l) => /[a-z]{3}/i.test(l) && !/invoice|receipt|tax|date|bill to|ship to/i.test(l),
    ) ||
    '';
  return {
    vendor: vendor.slice(0, 200),
    invoiceNumber:
      text.match(/invoice\s*(?:no\.?|number|#)\s*[:#-]?\s*([a-z0-9][a-z0-9/-]*)/i)?.[1] || '',
    amount,
    currency,
    invoiceDate: date(lines.find((l) => /\bdate\b/i.test(l) && !/due/i.test(l)) || ''),
    dueDate: date(lines.find((l) => /due\s*(date)?/i.test(l)) || ''),
  };
}

export async function extractInvoice(
  file: File,
  progress: (text: string) => void,
): Promise<{ fields: InvoiceFields; text: string }> {
  let worker: Awaited<ReturnType<(typeof import('tesseract.js'))['createWorker']>> | undefined;
  const ocr = async (image: File | HTMLCanvasElement) => {
    if (!worker) {
      const { createWorker } = await import('tesseract.js');
      worker = await createWorker('eng', 1, {
        workerPath: '/ocr/worker.min.js',
        corePath: '/ocr/core',
        logger: (event) => progress(`${event.status} ${Math.round((event.progress || 0) * 100)}%`),
      });
    }
    return (await worker.recognize(image)).data.text;
  };
  try {
    let text = '';
    if (file.type === 'application/pdf' || /\.pdf$/i.test(file.name)) {
      const pdfjs = await import('pdfjs-dist');
      pdfjs.GlobalWorkerOptions.workerSrc = '/ocr/pdf.worker.min.mjs';
      const task = pdfjs.getDocument({ data: new Uint8Array(await file.arrayBuffer()) });
      const pdf = await task.promise;
      try {
        if (pdf.numPages > 5)
          throw new Error('Please upload an invoice with no more than 5 pages.');
        for (let n = 1; n <= pdf.numPages; n++) {
          progress(`Reading page ${n} of ${pdf.numPages}`);
          const page = await pdf.getPage(n);
          const content = await page.getTextContent();
          let pageText = content.items
            .map((item) => ('str' in item ? item.str + (item.hasEOL ? '\n' : ' ') : ''))
            .join('');
          if (pageText.trim().length < 30) {
            const original = page.getViewport({ scale: 1 });
            const viewport = page.getViewport({
              scale: Math.min(2, 2400 / Math.max(original.width, original.height)),
            });
            const canvas = document.createElement('canvas');
            canvas.width = Math.ceil(viewport.width);
            canvas.height = Math.ceil(viewport.height);
            await page.render({ canvas, viewport }).promise;
            pageText = await ocr(canvas);
            canvas.width = canvas.height = 0;
          }
          text += pageText + '\n';
          page.cleanup();
        }
      } finally {
        await task.destroy();
      }
    } else {
      const bitmap = await createImageBitmap(file);
      try {
        const scale = Math.min(1, 2400 / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(bitmap.width * scale);
        canvas.height = Math.round(bitmap.height * scale);
        const context = canvas.getContext('2d')!;
        context.fillStyle = 'white';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        text = await ocr(canvas);
        canvas.width = canvas.height = 0;
      } finally {
        bitmap.close();
      }
    }
    return { fields: parseInvoiceText(text), text };
  } finally {
    await worker?.terminate();
  }
}
