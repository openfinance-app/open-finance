import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

const { persistNumberFormat } = vi.hoisted(() => ({ persistNumberFormat: vi.fn() }));
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: { numberFormat: '1,234.56' },
    isLoading: false,
  }),
  useUpdateUserSettings: () => ({
    mutate: vi.fn(),
    mutateAsync: persistNumberFormat,
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
    persistNumberFormat.mockResolvedValue({});
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

  it('rejects a failed save and restores the previous persisted format and cache', async () => {
    const { result } = renderHook(() => useNumberFormat(), { wrapper });
    persistNumberFormat.mockRejectedValueOnce(new Error('offline'));
    await act(async () => {
      await expect(result.current.setNumberFormat('1.234,56')).rejects.toThrow('offline');
    });
    expect(result.current.numberFormat).toBe('1,234.56');
    expect(localStorage.getItem('open_finance_number_format')).toBe('1,234.56');
  });

  it('throws when used outside provider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => {
      renderHook(() => useNumberFormat());
    }).toThrow('useNumberFormat must be used within a NumberFormatProvider');
    spy.mockRestore();
  });
});
