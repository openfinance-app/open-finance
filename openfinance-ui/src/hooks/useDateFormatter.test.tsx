import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { formatDate } from '@/utils/date';
import { deviceTimezone } from '@/utils/timezone';
import type { UserSettings } from '@/types/user';

let settings: Partial<UserSettings>;
vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: settings }),
}));
vi.mock('@/context/LocaleContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/LocaleContext')>()),
  useLocale: () => ({ locale: 'fr' }),
}));

function Preview({ instant = '2026-11-15T23:52:54.291Z' }: { instant?: string }) {
  const { date, dateTime, timeZone } = useDateFormatter();
  return (
    <>
      <output aria-label="timestamp">{dateTime(instant)}</output>
      <output aria-label="calendar date">{date('2026-11-16')}</output>
      <output aria-label="zone">{timeZone}</output>
    </>
  );
}

describe('User timestamp formatting', () => {
  beforeEach(() => {
    mockAuthentication();
    settings = { dateFormat: 'YYYY-MM-DD', timezone: 'Asia/Tokyo', useDeviceTimezone: true };
  });

  it('uses the device by default even when an override has been saved', () => {
    renderWithProviders(<Preview />);
    expect(screen.getByLabelText('zone')).toHaveTextContent(deviceTimezone());
    expect(screen.getByLabelText('timestamp')).toHaveTextContent(
      formatDate('2026-11-15T23:52:54.291Z', 'YYYY-MM-DD', deviceTimezone())
    );
  });

  it.each([
    ['UTC', '2026-11-15 23:52'],
    ['Europe/Paris', '2026-11-16 00:52'],
    ['America/New_York', '2026-11-15 18:52'],
    ['Asia/Tokyo', '2026-11-16 08:52'],
  ])('converts instants to %s without shifting financial dates', (timezone, expected) => {
    settings = { ...settings, timezone, useDeviceTimezone: false };
    renderWithProviders(<Preview />);
    expect(screen.getByLabelText('timestamp')).toHaveTextContent(expected);
    expect(screen.getByLabelText('calendar date')).toHaveTextContent('2026-11-16');
  });

  it('updates both the date and clock when the account override changes', () => {
    settings = { ...settings, timezone: 'America/New_York', useDeviceTimezone: false };
    const { rerender } = renderWithProviders(<Preview />);
    expect(screen.getByLabelText('timestamp')).toHaveTextContent('2026-11-15 18:52');
    settings = { ...settings, timezone: 'Europe/Paris' };
    rerender(<Preview />);
    expect(screen.getByLabelText('timestamp')).toHaveTextContent('2026-11-16 00:52');
  });

  it('handles the spring daylight-saving transition', () => {
    settings = { ...settings, timezone: 'Europe/Paris', useDeviceTimezone: false };
    const { rerender } = renderWithProviders(<Preview instant="2026-03-29T00:30:00Z" />);
    expect(screen.getByLabelText('timestamp')).toHaveTextContent('2026-03-29 01:30');
    rerender(<Preview instant="2026-03-29T01:30:00Z" />);
    expect(screen.getByLabelText('timestamp')).toHaveTextContent('2026-03-29 03:30');
  });

  it('falls back to the device if an old saved timezone is invalid', () => {
    settings = { ...settings, timezone: 'Invalid/Timezone', useDeviceTimezone: false };
    renderWithProviders(<Preview />);
    expect(screen.getByLabelText('zone')).toHaveTextContent(deviceTimezone());
  });
});
