import { beforeEach, describe, expect, it } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { TimezoneSettings } from '@/components/settings/TimezoneSettings';
import { server } from '@/test/mocks/server';
import { createTestQueryClient, mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { UpdateUserSettingsRequest, UserSettings } from '@/types/user';
import { deviceTimezone } from '@/utils/timezone';

describe('Timezone settings', () => {
  let settings: UserSettings;

  beforeEach(() => {
    mockAuthentication();
    settings = {
      id: 1,
      userId: 1,
      theme: 'light',
      dateFormat: 'YYYY-MM-DD',
      numberFormat: '1,234.56',
      language: 'en',
      country: 'US',
      timezone: 'UTC',
      useDeviceTimezone: true,
      createdAt: '2026-11-16T00:00:00Z',
      updatedAt: '2026-11-16T00:00:00Z',
    };
    server.use(http.get('/api/v1/users/me/settings', () => HttpResponse.json(settings)));
  });

  function renderSettings() {
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(['user', 'settings', 1], settings);
    return { ...renderWithProviders(<TimezoneSettings />, { queryClient }), queryClient };
  }

  it('defaults to the device and persists an account override and a return to device time', async () => {
    const updates: UpdateUserSettingsRequest[] = [];
    server.use(
      http.put('/api/v1/users/me/settings', async ({ request }) => {
        const update = (await request.json()) as UpdateUserSettingsRequest;
        updates.push(update);
        settings = { ...settings, ...update };
        return HttpResponse.json(settings);
      })
    );
    const { queryClient } = renderSettings();
    const select = screen.getByRole('combobox', { name: 'Timezone' });
    expect(select).toHaveValue('__device__');
    expect(screen.getByRole('option', { selected: true })).toHaveTextContent('Device timezone');
    expect(screen.getByText(`Current timezone: ${deviceTimezone()}`)).toBeVisible();

    fireEvent.change(select, { target: { value: 'Asia/Tokyo' } });
    await waitFor(() => expect(select).toHaveValue('Asia/Tokyo'));
    expect(screen.getByRole('status')).toHaveTextContent('Timezone preference saved.');
    expect(queryClient.getQueryData(['user', 'settings', 1])).toMatchObject({
      timezone: 'Asia/Tokyo',
      useDeviceTimezone: false,
    });

    fireEvent.change(select, { target: { value: '__device__' } });
    await waitFor(() => expect(select).toHaveValue('__device__'));
    expect(updates).toEqual([
      { timezone: 'Asia/Tokyo', useDeviceTimezone: false },
      { useDeviceTimezone: true },
    ]);
    expect(settings.timezone).toBe('Asia/Tokyo');
  });

  it('keeps the saved selection and reports an error when persistence fails', async () => {
    settings = { ...settings, timezone: 'Europe/Paris', useDeviceTimezone: false };
    server.use(
      http.put('/api/v1/users/me/settings', () =>
        HttpResponse.json({ message: 'Unavailable' }, { status: 500 })
      )
    );
    renderSettings();
    const select = screen.getByRole('combobox', { name: 'Timezone' });
    fireEvent.change(select, { target: { value: 'Asia/Tokyo' } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not save the timezone.');
    expect(select).toHaveValue('Europe/Paris');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('disables the selector until the save finishes', async () => {
    let finishSave: () => void = () => {};
    const pending = new Promise<void>(resolve => {
      finishSave = resolve;
    });
    server.use(
      http.put('/api/v1/users/me/settings', async () => {
        await pending;
        return HttpResponse.json({ ...settings, timezone: 'UTC', useDeviceTimezone: false });
      })
    );
    renderSettings();
    const select = screen.getByRole('combobox', { name: 'Timezone' });
    fireEvent.change(select, { target: { value: 'UTC' } });
    await waitFor(() => expect(select).toBeDisabled());
    finishSave();
    await waitFor(() => expect(select).toBeEnabled());
    expect(select).toHaveValue('UTC');
  });
});
