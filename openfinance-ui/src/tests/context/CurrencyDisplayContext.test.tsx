import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { CurrencyDisplayProvider, useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import apiClient from '@/services/apiClient';

vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({ user: { id: 7 }, isAuthenticated: true }),
}));
vi.mock('@/services/apiClient', () => ({ default: { get: vi.fn(), put: vi.fn() } }));

function setup() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <CurrencyDisplayProvider>{children}</CurrencyDisplayProvider>
    </QueryClientProvider>
  );
  return { client, ...renderHook(useCurrencyDisplay, { wrapper }) };
}

describe('Currency preferences persistence', () => {
  beforeEach(() => vi.clearAllMocks());
  it('persists display mode and hydrates a fresh session from the saved response', async () => {
    let saved = { userId: 7, amountDisplayMode: 'base', secondaryCurrency: 'GBP' };
    vi.mocked(apiClient.get).mockImplementation(async () => ({ data: saved }));
    vi.mocked(apiClient.put).mockImplementation(async (_url, data) => ({
      data: (saved = { ...saved, ...data }),
    }));
    const first = setup();
    await waitFor(() => expect(first.result.current.secondaryCurrency).toBe('GBP'));
    await act(async () => {
      await first.result.current.setDisplayMode('native');
    });
    expect(apiClient.put).toHaveBeenCalledWith('/users/me/settings', {
      amountDisplayMode: 'native',
    });
    await waitFor(() => expect(first.result.current.displayMode).toBe('native'));
    first.unmount();
    first.client.clear();
    const fresh = setup();
    await waitFor(() => expect(fresh.result.current.displayMode).toBe('native'));
    fresh.unmount();
    fresh.client.clear();
  });
  it('clears secondary preference and invalidates cached entity conversions', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { userId: 7, secondaryCurrency: 'GBP' } });
    vi.mocked(apiClient.put).mockResolvedValue({ data: { userId: 7, secondaryCurrency: null } });
    const view = setup();
    for (const key of [
      'accounts',
      'assets',
      'liabilities',
      'realEstate',
      'budgets',
      'transactions',
    ])
      view.client.setQueryData([key], []);
    await waitFor(() => expect(view.result.current.secondaryCurrency).toBe('GBP'));
    await act(async () => {
      await view.result.current.setSecondaryCurrency(null);
    });
    await waitFor(() => expect(view.result.current.secondaryCurrency).toBeNull());
    for (const key of [
      'accounts',
      'assets',
      'liabilities',
      'realEstate',
      'budgets',
      'transactions',
    ])
      expect(view.client.getQueryState([key])?.isInvalidated).toBe(true);
    view.unmount();
    view.client.clear();
  });
});
