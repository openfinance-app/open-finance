import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useUserProfile,
  useBaseCurrency,
  useUpdateBaseCurrency,
  useUserSettings,
  useUpdateUserSettings,
} from './useUserSettings';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

// Mock useAuthContext for useUpdateBaseCurrency
const mockUpdateUser = vi.fn();
vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({
    updateUser: mockUpdateUser,
  }),
}));

describe('useUserSettings hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useUserProfile ─────────────────────────────────────────────────
  describe('useUserProfile', () => {
    it('should fetch user profile', async () => {
      const mockUser = {
        id: 1,
        username: 'testuser',
        email: 'test@example.com',
        baseCurrency: 'USD',
        createdAt: '2024-01-01T00:00:00Z',
      };
      mockedApiClient.get.mockResolvedValue({ data: mockUser });

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockUser);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/users/me');
    });

    it('should handle fetch error', async () => {
      mockedApiClient.get.mockRejectedValue(new Error('Unauthorized'));

      const { result } = renderHook(() => useUserProfile(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Unauthorized');
    });
  });

  // ── useBaseCurrency ─────────────────────────────────────────────────
  describe('useBaseCurrency', () => {
    it('should fetch base currency', async () => {
      mockedApiClient.get.mockResolvedValue({ data: { baseCurrency: 'EUR' } });

      const { result } = renderHook(() => useBaseCurrency(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toBe('EUR');
      expect(mockedApiClient.get).toHaveBeenCalledWith('/users/me/base-currency');
    });

    it('should handle fetch error', async () => {
      mockedApiClient.get.mockRejectedValue(new Error('Not found'));

      const { result } = renderHook(() => useBaseCurrency(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  // ── useUpdateBaseCurrency ─────────────────────────────────────────────────
  describe('useUpdateBaseCurrency', () => {
    it('should update base currency', async () => {
      const mockResponse = { baseCurrency: 'GBP' };
      mockedApiClient.put.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useUpdateBaseCurrency(), { wrapper });

      await act(async () => {
        result.current.mutate('GBP');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.put).toHaveBeenCalledWith('/users/me/base-currency', {
        baseCurrency: 'GBP',
      });
    });

    it('should call updateUser from auth context on success', async () => {
      const mockResponse = { baseCurrency: 'CHF' };
      mockedApiClient.put.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useUpdateBaseCurrency(), { wrapper });

      await act(async () => {
        result.current.mutate('CHF');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockUpdateUser).toHaveBeenCalledWith({ baseCurrency: 'CHF' });
    });

    it('should invalidate related queries on success', async () => {
      const mockResponse = { baseCurrency: 'JPY' };
      mockedApiClient.put.mockResolvedValue({ data: mockResponse });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateBaseCurrency(), { wrapper });

      await act(async () => {
        result.current.mutate('JPY');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['exchangeRate'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['assets'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['liabilities'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['realEstate'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgets'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['transactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['insights'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recurringTransactions'] });
    });

    it('should handle update error', async () => {
      mockedApiClient.put.mockRejectedValue(new Error('Failed to update'));

      const { result } = renderHook(() => useUpdateBaseCurrency(), { wrapper });

      await act(async () => {
        result.current.mutate('INVALID');
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Failed to update');
    });
  });

  // ── useUserSettings ─────────────────────────────────────────────────
  describe('useUserSettings', () => {
    it('should fetch user settings', async () => {
      const mockSettings = {
        id: 1,
        userId: 1,
        theme: 'dark',
        dateFormat: 'YYYY-MM-DD',
        numberFormat: '1,234.56',
        language: 'en',
        timezone: 'UTC',
        country: 'US',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      };
      mockedApiClient.get.mockResolvedValue({ data: mockSettings });

      const { result } = renderHook(() => useUserSettings(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockSettings);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/users/me/settings');
    });

    it('should handle fetch error', async () => {
      mockedApiClient.get.mockRejectedValue(new Error('Server error'));

      const { result } = renderHook(() => useUserSettings(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  // ── useUpdateUserSettings ─────────────────────────────────────────────────
  describe('useUpdateUserSettings', () => {
    it('should update user settings', async () => {
      const updatedSettings = {
        id: 1,
        userId: 1,
        theme: 'light',
        dateFormat: 'DD/MM/YYYY',
        numberFormat: '1.234,56',
        language: 'fr',
        timezone: 'Europe/Paris',
        country: 'FR',
        createdAt: '2024-01-01',
        updatedAt: '2024-06-01',
      };
      mockedApiClient.put.mockResolvedValue({ data: updatedSettings });

      const { result } = renderHook(() => useUpdateUserSettings(), { wrapper });

      await act(async () => {
        result.current.mutate({ theme: 'light', language: 'fr' });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(updatedSettings);
      expect(mockedApiClient.put).toHaveBeenCalledWith('/users/me/settings', {
        theme: 'light',
        language: 'fr',
      });
    });

    it('should update settings cache on success', async () => {
      const updatedSettings = {
        id: 1,
        userId: 1,
        theme: 'dark',
        dateFormat: 'YYYY-MM-DD',
        numberFormat: '1,234.56',
        language: 'en',
        timezone: 'UTC',
        country: 'US',
        createdAt: '2024-01-01',
        updatedAt: '2024-06-01',
      };
      mockedApiClient.put.mockResolvedValue({ data: updatedSettings });

      const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');

      const { result } = renderHook(() => useUpdateUserSettings(), { wrapper });

      await act(async () => {
        result.current.mutate({ theme: 'dark' });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(setQueryDataSpy).toHaveBeenCalledWith(
        ['user', 'settings', updatedSettings.userId],
        updatedSettings
      );
    });

    it('should invalidate dashboard and exchangeRate queries on success', async () => {
      const updatedSettings = {
        id: 1,
        userId: 1,
        theme: 'dark',
        dateFormat: 'YYYY-MM-DD',
        numberFormat: '1,234.56',
        language: 'en',
        timezone: 'UTC',
        country: 'US',
        createdAt: '2024-01-01',
        updatedAt: '2024-06-01',
      };
      mockedApiClient.put.mockResolvedValue({ data: updatedSettings });

      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateUserSettings(), { wrapper });

      await act(async () => {
        result.current.mutate({ secondaryCurrency: 'GBP' });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['user', 'profile'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['exchangeRate'] });
    });

    it('should handle update error', async () => {
      mockedApiClient.put.mockRejectedValue(new Error('Validation error'));

      const { result } = renderHook(() => useUpdateUserSettings(), { wrapper });

      await act(async () => {
        result.current.mutate({ language: '' });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toBe('Validation error');
    });
  });
});
