import { formatTimestamp, formatTimestampShort } from './format';

describe('formatTimestamp', () => {
  it('shows "Never" when there is no value', () => {
    expect(formatTimestamp(null)).toBe('Never');
    expect(formatTimestamp(undefined)).toBe('Never');
    expect(formatTimestamp('')).toBe('Never');
  });

  it('turns an ISO timestamp into a readable date with the year', () => {
    const text = formatTimestamp('2026-09-15T12:10:38.959215Z');
    expect(text).toMatch(/Sept? 2026/);
    expect(text).not.toContain('T12');
  });

  it('shows the raw value when it is not a date, rather than "Invalid Date"', () => {
    expect(formatTimestamp('not a date')).toBe('not a date');
  });
});

describe('formatTimestampShort', () => {
  it('leaves the year out', () => {
    const text = formatTimestampShort('2026-09-15T12:10:38Z');
    expect(text).toMatch(/Sept?/);
    expect(text).not.toContain('2026');
  });

  it('shows "Never" when there is no value', () => {
    expect(formatTimestampShort(null)).toBe('Never');
  });
});
