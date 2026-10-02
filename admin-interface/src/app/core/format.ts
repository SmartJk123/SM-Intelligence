/**
 * Timestamp formatting for values that came from the backend.
 *
 * The API returns ISO 8601 strings. Rendering those directly puts
 * `2026-09-15T12:10:38.959215Z` in front of an operator, so every screen shows
 * the same readable form instead, converted to the viewer's own time zone.
 */

/** Readable date and time, for detail rows. */
export function formatTimestamp(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return value ? String(value) : 'Never';
  }
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Shorter form, for cards and table cells. */
export function formatTimestampShort(value: string | null | undefined): string {
  const date = toDate(value);
  if (!date) {
    return value ? String(value) : 'Never';
  }
  return date.toLocaleString('en-GB', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function toDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
