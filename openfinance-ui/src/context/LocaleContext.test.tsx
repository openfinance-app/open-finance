import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import React from 'react';

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

function createWrapper() {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <LocaleProvider>{children}</LocaleProvider>;
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
});
