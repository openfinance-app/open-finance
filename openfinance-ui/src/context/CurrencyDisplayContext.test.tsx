import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { CurrencyDisplayProvider, useCurrencyDisplay } from '@/context/CurrencyDisplayContext';

const state = vi.hoisted(() => ({
  userId: 1 as number | undefined,
  settings: undefined as
    | {
        userId: number;
        amountDisplayMode: 'base' | 'native' | 'both';
        secondaryCurrency: string | null;
      }
    | undefined,
  save: vi.fn(),
}));
vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({ user: { id: state.userId }, isAuthenticated: !!state.userId }),
}));
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: state.settings }),
  useUpdateUserSettings: () => ({ mutateAsync: state.save }),
}));
const wrapper = ({ children }: { children: ReactNode }) => (
  <CurrencyDisplayProvider>{children}</CurrencyDisplayProvider>
);

describe('Currency display settings ownership', () => {
  beforeEach(() => {
    localStorage.clear();
    state.userId = 1;
    state.settings = undefined;
    state.save.mockReset();
  });

  it('ignores unscoped legacy preferences while settings load', () => {
    localStorage.setItem('open_finance_amount_display_mode', 'native');
    localStorage.setItem('open_finance_secondary_currency', 'GBP');
    const { result } = renderHook(useCurrencyDisplay, { wrapper });
    expect(result.current.displayMode).toBe('base');
    expect(result.current.secondaryCurrency).toBeNull();
  });
  it('hydrates both preferences without visiting settings', () => {
    const { result, rerender } = renderHook(useCurrencyDisplay, { wrapper });
    state.settings = { userId: 1, amountDisplayMode: 'both', secondaryCurrency: 'GBP' };
    rerender();
    expect(result.current.displayMode).toBe('both');
    expect(result.current.secondaryCurrency).toBe('GBP');
  });
  it('does not expose old settings after logout or a user switch', () => {
    state.settings = { userId: 1, amountDisplayMode: 'native', secondaryCurrency: 'GBP' };
    const { result, rerender } = renderHook(useCurrencyDisplay, { wrapper });
    state.userId = undefined;
    rerender();
    expect(result.current.secondaryCurrency).toBeNull();
    state.userId = 2;
    rerender();
    expect(result.current.displayMode).toBe('base');
    expect(result.current.secondaryCurrency).toBeNull();
    state.settings = { userId: 2, amountDisplayMode: 'both', secondaryCurrency: null };
    rerender();
    expect(result.current.displayMode).toBe('both');
  });
  it('saves mode and normalized secondary selections to the server', async () => {
    const { result } = renderHook(useCurrencyDisplay, { wrapper });
    await act(async () => {
      await result.current.setDisplayMode('native');
      await result.current.setSecondaryCurrency(' GBP ');
      await result.current.setSecondaryCurrency(null);
    });
    expect(state.save.mock.calls).toEqual([
      [{ amountDisplayMode: 'native' }],
      [{ secondaryCurrency: 'GBP' }],
      [{ secondaryCurrency: '' }],
    ]);
  });
  it('keeps saved preferences and reports failed saves to the caller', async () => {
    state.settings = { userId: 1, amountDisplayMode: 'base', secondaryCurrency: 'GBP' };
    state.save.mockRejectedValue(new Error('Offline'));
    const { result } = renderHook(useCurrencyDisplay, { wrapper });
    await expect(result.current.setDisplayMode('native')).rejects.toThrow('Offline');
    expect(result.current.displayMode).toBe('base');
    expect(result.current.secondaryCurrency).toBe('GBP');
  });
  it('requires a provider', () => {
    expect(() => renderHook(useCurrencyDisplay)).toThrow(
      'useCurrencyDisplay must be used within a CurrencyDisplayProvider'
    );
  });
});
