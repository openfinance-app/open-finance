import { describe, expect, it } from 'vitest';
import {
  availableTimezones,
  dateBoundary,
  deviceTimezone,
  resolveTimezone,
} from '@/utils/timezone';
import { formatDate } from '@/utils/date';

describe('Timezone boundaries', () => {
  it.each([
    ['2026-03-29', '2026-03-28T23:00:00.000Z', '2026-03-29T22:00:00.000Z'],
    ['2026-10-25', '2026-10-24T22:00:00.000Z', '2026-10-25T23:00:00.000Z'],
  ])('uses both correct midnight offsets for %s in Paris', (date, start, end) => {
    expect(dateBoundary(date, 'Europe/Paris')).toBe(start);
    expect(dateBoundary(date, 'Europe/Paris', true)).toBe(end);
  });

  it('uses the selected zone for history date filters', () => {
    expect(dateBoundary('2026-11-16', 'America/New_York')).toBe('2026-11-16T05:00:00.000Z');
    expect(dateBoundary('2026-11-16', 'Asia/Tokyo')).toBe('2026-11-15T15:00:00.000Z');
  });

  it.each(['', 'not a date', '2026-02-30'])('ignores an invalid filter %s', date => {
    expect(dateBoundary(date, 'UTC')).toBeUndefined();
  });

  it('resolves only valid overrides and includes UTC and the device in the choices', () => {
    expect(resolveTimezone('Asia/Tokyo', false)).toBe('Asia/Tokyo');
    expect(resolveTimezone('Asia/Tokyo', true)).toBe(deviceTimezone());
    expect(resolveTimezone('Invalid/Timezone', false)).toBe(deviceTimezone());
    expect(availableTimezones()).toEqual(expect.arrayContaining(['UTC', deviceTimezone()]));
  });

  it.each(['America/Los_Angeles', 'Asia/Tokyo', 'Pacific/Kiritimati'])(
    'keeps date-only values unchanged in %s',
    timezone => {
      expect(formatDate('2026-11-16', 'YYYY-MM-DD', timezone)).toBe('2026-11-16');
      expect(formatDate('2026-11-16', 'DD/MM/YYYY', timezone)).toBe('16/11/2026');
    }
  );
});
