import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useReportingCalendar } from '@/hooks/useReportingCalendar';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { deviceTimezone } from '@/utils/timezone';
import { formatDate, getDaysAgo, getStartOfMonth, getStartOfYear } from '@/utils/date';

const state = vi.hoisted(() => ({ timezone: 'Europe/Paris', useDeviceTimezone: false }));
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: state }),
}));

function Preview() {
  const { today, timeZone } = useReportingCalendar();
  return (
    <>
      <output aria-label="today">{today}</output>
      <output aria-label="zone">{timeZone}</output>
    </>
  );
}

describe('Reporting calendar', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setTimeout', 'clearTimeout'] });
    vi.setSystemTime(new Date('2026-11-15T23:51:00Z'));
    mockAuthentication();
    state.timezone = 'Europe/Paris';
    state.useDeviceTimezone = false;
  });
  afterEach(() => vi.useRealTimers());

  it.each([
    ['Europe/Paris', '2026-11-16'],
    ['America/New_York', '2026-11-15'],
    ['Pacific/Kiritimati', '2026-11-16'],
    ['Etc/GMT+12', '2026-11-15'],
  ])('uses the account calendar in %s', (zone, expected) => {
    state.timezone = zone;
    renderWithProviders(<Preview />);
    expect(screen.getByLabelText('today')).toHaveTextContent(expected);
  });

  it('defaults to the device and responds to account overrides', () => {
    state.useDeviceTimezone = true;
    const view = renderWithProviders(<Preview />);
    expect(screen.getByLabelText('today')).toHaveTextContent(
      formatDate(new Date(), 'YYYY-MM-DD', deviceTimezone())
    );
    state.useDeviceTimezone = false;
    view.rerender(<Preview />);
    expect(screen.getByLabelText('today')).toHaveTextContent('2026-11-16');
  });

  it.each([
    ['2026-03-28T23:00:00Z', 23, '2026-03-30'],
    ['2026-10-24T22:00:00Z', 25, '2026-10-26'],
    ['2026-12-31T22:59:59Z', 0, '2027-01-01'],
  ])('refreshes at midnight through DST and year boundaries (%s)', (instant, hours, expected) => {
    vi.setSystemTime(new Date(instant));
    const view = renderWithProviders(<Preview />);
    act(() => vi.advanceTimersByTime(hours ? hours * 60 * 60 * 1000 + 100 : 1100));
    expect(screen.getByLabelText('today')).toHaveTextContent(expected);
    view.unmount();
  });

  it('calculates filter boundaries from the selected calendar date', () => {
    expect(getDaysAgo(1, '2027-01-01')).toBe('2026-12-31');
    expect(getStartOfMonth('2027-01-01')).toBe('2027-01-01');
    expect(getStartOfYear('2027-01-01')).toBe('2027-01-01');
  });
});
