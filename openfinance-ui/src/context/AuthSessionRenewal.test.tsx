import { QueryClient } from '@tanstack/react-query';
import { waitFor } from '@testing-library/react';
import { AxiosError, type AxiosAdapter, type AxiosResponse } from 'axios';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { useAuthContext } from '@/context/AuthContext';
import { STORAGE_KEYS } from '@/constants/storage';
import apiClient from '@/services/apiClient';
import { useViewStateStore } from '@/stores/viewState';
import { useImportDraftStore } from '@/stores/importDraft';
import { act, mockAuthentication, renderWithProviders, screen } from '@/test/test-utils';

const initial = token(100);
const renewed = token(200);

function token(exp: number, sid = 'login-session', tokenVersion = 0): string {
  const claims = { sub: 'testuser', userId: 1, tokenVersion, sid, exp };
  return `header.${btoa(JSON.stringify(claims))}.signature`;
}

function responseAdapter(renewal: string, status = 200): AxiosAdapter {
  return async config => {
    const response: AxiosResponse = {
      data: {},
      status,
      statusText: '',
      config,
      headers: { 'x-auth-token': renewal },
    };
    if (status >= 400) throw new AxiosError('Rejected', undefined, config, undefined, response);
    return response;
  };
}

function SessionDetails() {
  const { token: current, sessionStartTime } = useAuthContext();
  return (
    <>
      <span>{current}</span>
      <span>{sessionStartTime}</span>
    </>
  );
}

describe('sliding authentication sessions', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    mockAuthentication();
    localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, initial);
    sessionStorage.setItem(STORAGE_KEYS.SESSION_START_TIME, 'original-login-time');
    useViewStateStore.getState().clear();
    useImportDraftStore.getState().clear();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    useViewStateStore.getState().clear();
    useImportDraftStore.getState().clear();
  });

  it.each(['persistent', 'session-only'])(
    'renews a %s login in place and uses it on the next request',
    async kind => {
      const storage = kind === 'persistent' ? localStorage : sessionStorage;
      const opposite = kind === 'persistent' ? sessionStorage : localStorage;
      if (kind === 'session-only') {
        for (const key of [STORAGE_KEYS.AUTH_TOKEN, STORAGE_KEYS.AUTH_USER]) {
          storage.setItem(key, localStorage.getItem(key)!);
          localStorage.removeItem(key);
        }
      }
      const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
      renderWithProviders(<SessionDetails />, { queryClient: client });
      await screen.findByText(initial);
      client.setQueryData(['accounts'], ['Private account']);
      useViewStateStore.getState().save('entry', '/accounts', 'filter', 'Private filter');
      useImportDraftStore.getState().save(1, {
        selectedStep: 'review',
        uploadId: 'upload',
        fileName: 'draft.csv',
        accountOverride: 1,
        sessionId: 2,
        categoryMappings: {},
        newCategoryNames: [],
        skipDuplicates: true,
        editedTransactions: [],
      });

      await act(async () => {
        await apiClient.get('/users/me/settings', { adapter: responseAdapter(renewed) });
      });

      await screen.findByText(renewed);
      expect(storage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(renewed);
      expect(opposite.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBeNull();
      expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION)).toBe(
        'mock-encryption-key-67890'
      );
      expect(sessionStorage.getItem(STORAGE_KEYS.SESSION_START_TIME)).toBe('original-login-time');
      expect(client.getQueryData(['accounts'])).toEqual(['Private account']);
      expect(useViewStateStore.getState().entries.entry.filter).toBe('Private filter');
      expect(useImportDraftStore.getState().draft?.fileName).toBe('draft.csv');

      const next = await apiClient.get('/users/me/settings', { adapter: responseAdapter(renewed) });
      expect(next.config.headers.Authorization).toBe(`Bearer ${renewed}`);
    }
  );

  it('syncs renewal from another tab without discarding encryption or cached data', async () => {
    const client = new QueryClient();
    renderWithProviders(<SessionDetails />, { queryClient: client });
    await screen.findByText(initial);
    client.setQueryData(['accounts'], ['Private account']);
    act(() => {
      localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, renewed);
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: STORAGE_KEYS.AUTH_TOKEN,
          oldValue: initial,
          newValue: renewed,
        })
      );
    });
    await screen.findByText(renewed);
    expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION)).toBe(
      'mock-encryption-key-67890'
    );
    expect(client.getQueryData(['accounts'])).toEqual(['Private account']);
  });

  it('still clears cached data and encryption when another tab starts a different login', async () => {
    const client = new QueryClient();
    renderWithProviders(<SessionDetails />, { queryClient: client });
    await screen.findByText(initial);
    client.setQueryData(['accounts'], ['Private account']);
    const different = token(200, 'another-login');
    act(() => {
      localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, different);
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: STORAGE_KEYS.AUTH_TOKEN,
          oldValue: initial,
          newValue: different,
        })
      );
    });
    await screen.findByText(different);
    expect(client.getQueryData(['accounts'])).toBeUndefined();
    expect(sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION)).toBeNull();
  });

  it.each(['logout', 'another login', 'newer renewal'])(
    'ignores a late response after %s',
    async action => {
      let complete: (response: AxiosResponse) => void = () => {};
      let pendingResponse: AxiosResponse | undefined;
      const pending = apiClient.get('/users/me/settings', {
        adapter: config =>
          new Promise(resolve => {
            complete = resolve;
            pendingResponse = {
              data: {},
              status: 200,
              statusText: '',
              config,
              headers: { 'x-auth-token': renewed },
            };
          }),
      });
      await waitFor(() => expect(pendingResponse).toBeDefined());
      const next =
        action === 'logout'
          ? null
          : action === 'another login'
            ? token(300, 'different-session')
            : token(300);
      if (next) localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, next);
      else localStorage.removeItem(STORAGE_KEYS.AUTH_TOKEN);
      complete(pendingResponse!);
      await pending;
      expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(next);
    }
  );

  it('accepts a later expiry from a concurrent request carrying an older token', async () => {
    localStorage.setItem(STORAGE_KEYS.AUTH_TOKEN, renewed);
    await apiClient.get('/users/me/settings', {
      headers: { Authorization: `Bearer ${initial}` },
      adapter: responseAdapter(token(300)),
    });
    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(token(300));
  });

  it.each([
    token(50),
    'invalid-token',
    token(300, 'wrong-session'),
    token(300, 'login-session', 1),
  ])('ignores an older or mismatched token: %s', async replacement => {
    await apiClient.get('/users/me/settings', { adapter: responseAdapter(replacement) });
    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(initial);
  });

  it('treats an authenticated validation error as activity', async () => {
    await expect(
      apiClient.put('/users/me/settings', {}, { adapter: responseAdapter(renewed, 400) })
    ).rejects.toMatchObject({ response: { status: 400 } });
    expect(localStorage.getItem(STORAGE_KEYS.AUTH_TOKEN)).toBe(renewed);
  });
});
