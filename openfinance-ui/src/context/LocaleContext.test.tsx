import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('@/services/apiClient', () => ({
  default: {
    put: vi.fn().mockResolvedValue({}),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: {
      resolvedLanguage: 'en',
      language: 'en',
      changeLanguage: vi.fn().mockResolvedValue(undefined),
      loadNamespaces: vi.fn().mockResolvedValue(undefined),
    },
    t: (key: string) => key,
  }),
}));

import { LocaleProvider, useLocale } from './LocaleContext';
import apiClient from '@/services/apiClient';

function createWrapper() {
  const queryClient = new QueryClient();
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <LocaleProvider>{children}</LocaleProvider>
      </QueryClientProvider>
    );
  };
}

describe('LocaleContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('provides default locale as en', () => {
    const { result } = renderHook(() => useLocale(), { wrapper: createWrapper() });
    expect(result.current.locale).toBe('en');
  });

  it('provides dateFnsLocale', () => {
    const { result } = renderHook(() => useLocale(), { wrapper: createWrapper() });
    expect(result.current.dateFnsLocale).toBeDefined();
  });

  it('provides setLocale function', () => {
    const { result } = renderHook(() => useLocale(), { wrapper: createWrapper() });
    expect(typeof result.current.setLocale).toBe('function');
  });

  it('isChangingLocale is false by default', () => {
    const { result } = renderHook(() => useLocale(), { wrapper: createWrapper() });
    expect(result.current.isChangingLocale).toBe(false);
  });

  it('awaits language persistence and updates the same settings cache used on remount', async () => {
    localStorage.setItem('auth_token', 'test-token');
    const client = new QueryClient();
    client.setQueryData(['user', 'settings'], { language: 'en' });
    vi.mocked(apiClient.put).mockResolvedValueOnce({
      data: { language: 'fr', dateFormat: 'DD/MM/YYYY' },
    });
    const { result } = renderHook(() => useLocale(), {
      wrapper: ({ children }) => (
        <QueryClientProvider client={client}>
          <LocaleProvider>{children}</LocaleProvider>
        </QueryClientProvider>
      ),
    });
    await act(() => result.current.setLocale('fr'));
    expect(client.getQueryData(['user', 'settings'])).toMatchObject({ language: 'fr' });
    expect(localStorage.getItem('openfinance_language')).toBe('fr');
    expect(apiClient.put).toHaveBeenCalledWith('/users/me/settings', { language: 'fr' });
  });
});
