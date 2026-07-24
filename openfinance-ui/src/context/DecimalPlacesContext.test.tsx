import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AllProviders, mockAuthentication } from '@/test/test-utils';
import { DecimalPlacesProvider, useDecimalPlaces } from './DecimalPlacesContext';
import { getDecimalPlacesOverride, setDecimalPlacesOverride } from '@/utils/currency';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');

const wrapper = ({ children }: { children: ReactNode }) => (
  <AllProviders>
    <DecimalPlacesProvider>{children}</DecimalPlacesProvider>
  </AllProviders>
);

describe('DecimalPlacesContext', () => {
  beforeEach(() => {
    mockAuthentication();
    localStorage.removeItem('open_finance_decimal_places');
    setDecimalPlacesOverride(null);
    vi.mocked(apiClient.get).mockResolvedValue({ data: undefined });
    vi.mocked(apiClient.put).mockResolvedValue({ data: {} });
  });

  afterEach(() => {
    setDecimalPlacesOverride(null);
    vi.clearAllMocks();
  });

  it('defaults to disabled / 2 places / null effective override', () => {
    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });
    expect(result.current.overrideEnabled).toBe(false);
    expect(result.current.decimalPlaces).toBe(2);
    expect(result.current.effectiveDecimals).toBeNull();
    expect(getDecimalPlacesOverride()).toBeNull();
  });

  it('enabling pushes the effective override into the currency module', async () => {
    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });

    act(() => {
      result.current.setDecimalPlaces(4);
    });
    act(() => {
      result.current.setOverrideEnabled(true);
    });

    await waitFor(() => expect(getDecimalPlacesOverride()).toBe(4));
    expect(result.current.effectiveDecimals).toBe(4);
    expect(apiClient.put).toHaveBeenCalledWith(
      '/users/me/settings',
      expect.objectContaining({ decimalPlacesOverrideEnabled: true, preferredDecimalPlaces: 4 })
    );
  });

  it('disabling clears the effective override but retains the chosen value', async () => {
    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });

    act(() => {
      result.current.setDecimalPlaces(5);
    });
    act(() => {
      result.current.setOverrideEnabled(true);
    });
    await waitFor(() => expect(getDecimalPlacesOverride()).toBe(5));

    act(() => {
      result.current.setOverrideEnabled(false);
    });
    await waitFor(() => expect(getDecimalPlacesOverride()).toBeNull());
    expect(result.current.decimalPlaces).toBe(5);
  });

  it('clamps out-of-range values to 1-8', () => {
    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });
    act(() => {
      result.current.setDecimalPlaces(99);
    });
    expect(result.current.decimalPlaces).toBe(8);
    act(() => {
      result.current.setDecimalPlaces(0);
    });
    expect(result.current.decimalPlaces).toBe(1);
  });

  it('reverts to the persisted value when the update API fails', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        id: 1,
        userId: 1,
        theme: 'dark',
        dateFormat: 'MM/DD/YYYY',
        numberFormat: '1,234.56',
        language: 'en',
        timezone: 'UTC',
        country: 'US',
        amountDisplayMode: 'base',
        decimalPlacesOverrideEnabled: true,
        preferredDecimalPlaces: 3,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    });
    vi.mocked(apiClient.put).mockRejectedValue(new Error('network'));

    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });

    // Wait for backend settings to load (override enabled at 3 decimals).
    await waitFor(() => expect(getDecimalPlacesOverride()).toBe(3));
    expect(result.current.decimalPlaces).toBe(3);

    // Attempt a change; the PUT rejects, so state must revert to 3.
    act(() => {
      result.current.setDecimalPlaces(6);
    });

    await waitFor(() => expect(result.current.decimalPlaces).toBe(3));
    expect(getDecimalPlacesOverride()).toBe(3);
  });

  it('syncs state and the module override from loaded backend settings', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        id: 1,
        userId: 1,
        theme: 'dark',
        dateFormat: 'MM/DD/YYYY',
        numberFormat: '1,234.56',
        language: 'en',
        timezone: 'UTC',
        country: 'US',
        amountDisplayMode: 'base',
        decimalPlacesOverrideEnabled: true,
        preferredDecimalPlaces: 5,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      },
    });

    const { result } = renderHook(() => useDecimalPlaces(), { wrapper });

    await waitFor(() => expect(result.current.overrideEnabled).toBe(true));
    expect(result.current.decimalPlaces).toBe(5);
    expect(result.current.effectiveDecimals).toBe(5);
    expect(getDecimalPlacesOverride()).toBe(5);
  });

  it('throws when used outside the provider', () => {
    expect(() => renderHook(() => useDecimalPlaces())).toThrow(
      'useDecimalPlaces must be used within a DecimalPlacesProvider'
    );
  });
});
