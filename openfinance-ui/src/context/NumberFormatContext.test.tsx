import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: { numberFormat: '1,234.56' },
    isLoading: false,
  }),
  useUpdateUserSettings: () => ({
    mutate: vi.fn(),
    mutateAsync: vi.fn().mockResolvedValue({}),
  }),
}));

import { NumberFormatProvider, useNumberFormat } from './NumberFormatContext';

function wrapper({ children }: { children: ReactNode }) {
  return (
    <I18nextProvider i18n={i18n}>
      <NumberFormatProvider>{children}</NumberFormatProvider>
    </I18nextProvider>
  );
}

describe('NumberFormatContext', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('provides default number format', () => {
    const { result } = renderHook(() => useNumberFormat(), { wrapper });
    expect(result.current.numberFormat).toBe('1,234.56');
  });

  it('provides setNumberFormat function', () => {
    const { result } = renderHook(() => useNumberFormat(), { wrapper });
    expect(typeof result.current.setNumberFormat).toBe('function');
  });

  it('provides isLoading', () => {
    const { result } = renderHook(() => useNumberFormat(), { wrapper });
    expect(result.current.isLoading).toBe(false);
  });

  it('throws when used outside provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      renderHook(() => useNumberFormat());
    }).toThrow('useNumberFormat must be used within a NumberFormatProvider');
    spy.mockRestore();
  });
});
