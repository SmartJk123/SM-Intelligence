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
  const normalized = text.replace(/[\u2010-\u2015]/g, '-').replace(/\u00a0/g, ' ');
  // Read the date immediately after its label, including when PDF text puts the value
  // on the next line. This keeps issue and due dates distinct in multi-column rows.
  const datePattern =
    '(?:20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/]\\d{1,2}[-/]20\\d{2}|\\d{1,2}(?:st|nd|rd|th)?[\\s-]+[a-z]{3,9}\\.?[\\s,-]+20\\d{2}|[a-z]{3,9}\\.?[\\s-]+\\d{1,2}(?:st|nd|rd|th)?[\\s,-]+20\\d{2})';
  const labelledDate = (label: string) => {
    const match = normalized.match(new RegExp(label + '\\s*:?\\s*(' + datePattern + ')\\b', 'im'));
    return match ? parseInvoiceDate(match[1]) : '';
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
    invoiceDate:
      labelledDate(
        '\\b(?:invoice\\s+date|issue(?:d)?\\s+date|date\\s+issued|date\\s+of\\s+issue|issued\\s+on)\\b',
      ) || labelledDate('^\\s*date\\b'),
    dueDate: labelledDate(
      '\\b(?:due\\s+date|date\\s+due|payment\\s+due(?:\\s+date)?|due\\s+on)\\b',
    ),
  };
}

function parseInvoiceDate(value: string): string {
  const months = [
    'january',
    'february',
    'march',
    'april',
    'may',
    'june',
    'july',
    'august',
    'september',
    'october',
    'november',
    'december',
  ];
  const monthNumber = (name: string) => {
    const normalized = name.toLowerCase().replace(/\.$/, '');
    return (
      months.findIndex(
        (month) =>
          month === normalized ||
          month.slice(0, 3) === normalized ||
          (month === 'september' && normalized === 'sept'),
      ) + 1
    );
  };
  const iso = value.match(/^(20\d{2})[-/](\d{1,2})[-/](\d{1,2})$/);
  const numeric = value.match(/^(\d{1,2})[-/](\d{1,2})[-/](20\d{2})$/);
  const dayFirst = value.match(
    /^(\d{1,2})(?:st|nd|rd|th)?[\s-]+([a-z]{3,9}\.?)?[\s,-]+(20\d{2})$/i,
  );
  const monthFirst = value.match(
    /^([a-z]{3,9}\.?)[\s-]+(\d{1,2})(?:st|nd|rd|th)?[\s,-]+(20\d{2})$/i,
  );
  let parts: number[] | null = null;
  if (iso) parts = [Number(iso[1]), Number(iso[2]), Number(iso[3])];
  else if (dayFirst?.[2])
    parts = [Number(dayFirst[3]), monthNumber(dayFirst[2]), Number(dayFirst[1])];
  else if (monthFirst)
    parts = [Number(monthFirst[3]), monthNumber(monthFirst[1]), Number(monthFirst[2])];
  // Keep ambiguous numeric dates blank instead of guessing the invoice's locale.
  else if (numeric && Number(numeric[1]) > 12)
    parts = [Number(numeric[3]), Number(numeric[2]), Number(numeric[1])];
  else if (numeric && Number(numeric[2]) > 12)
    parts = [Number(numeric[3]), Number(numeric[1]), Number(numeric[2])];
  if (!parts) return '';
  const [year, month, day] = parts;
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (
    parsed.getUTCFullYear() !== year ||
    parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day
  )
    return '';
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
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
