import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosError, type AxiosAdapter } from 'axios';
import apiClient from '@/services/apiClient';
import { STORAGE_KEYS } from '@/constants/storage';
import { mockAuthentication } from '@/test/test-utils';

function rejectRequest(status: number, headers: Record<string, string> = {}): AxiosAdapter {
  return config =>
    Promise.reject(
      new AxiosError('Request rejected', 'ERR_BAD_REQUEST', config, undefined, {
        status,
        statusText: status === 401 ? 'Unauthorized' : 'Forbidden',
        data: { status },
        headers,
        config,
      })
    );
}

describe('expired API sessions', () => {
  const navigation = { href: '/accounts' };

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    mockAuthentication();
    sessionStorage.setItem(STORAGE_KEYS.ENCRYPTION_ENABLED, 'true');
    sessionStorage.setItem(STORAGE_KEYS.SESSION_START_TIME, '2026-11-11T10:00:00Z');
    navigation.href = '/accounts';
    vi.stubGlobal('window', { location: navigation });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
    sessionStorage.clear();
  });

  it.each(['persistent', 'session-only'])(
    'clears a rejected %s login and redirects',
    async kind => {
      if (kind === 'session-only') {
        for (const key of [STORAGE_KEYS.AUTH_TOKEN, STORAGE_KEYS.AUTH_USER]) {
          sessionStorage.setItem(key, localStorage.getItem(key)!);
          localStorage.removeItem(key);
        }
      }

      await expect(
        apiClient.get('/users/me/settings', { adapter: rejectRequest(401) })
      ).rejects.toMatchObject({ response: { status: 401 } });

      for (const storage of [localStorage, sessionStorage]) {
        expect(storage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBeNull();
        expect(storage.getItem(STORAGE_KEYS.AUTH_USER)).toBeNull();
      }
      expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION)).toBeNull();
      expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_ENABLED)).toBeNull();
      expect(sessionStorage.getItem(STORAGE_KEYS.SESSION_START_TIME)).toBeNull();
      expect(navigation.href).toBe('/login');
    }
  );

  it('reauthenticates an expired profile update when the server sends a Bearer challenge', async () => {
    await expect(
      apiClient.put(
        '/auth/profile',
        {},
        {
          adapter: rejectRequest(401, { 'www-authenticate': 'Bearer' }),
        }
      )
    ).rejects.toMatchObject({ response: { status: 401 } });

    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBeNull();
    expect(navigation.href).toBe('/login');
  });

  it.each(['/auth/login', '/auth/profile'])('keeps credentials errors local to %s', async url => {
    const token = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
    await expect(
      apiClient.request({
        url,
        method: url === '/auth/login' ? 'post' : 'put',
        adapter: rejectRequest(401),
      })
    ).rejects.toMatchObject({ response: { status: 401 } });

    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(token);
    expect(navigation.href).toBe('/accounts');
  });

  it('keeps an authenticated user signed in after a genuine permission denial', async () => {
    const token = localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
    await expect(
      apiClient.post(
        '/currencies/exchange-rates/update',
        {},
        {
          adapter: rejectRequest(403),
        }
      )
    ).rejects.toMatchObject({ response: { status: 403 } });

    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(token);
    expect(navigation.href).toBe('/accounts');
  });
});
