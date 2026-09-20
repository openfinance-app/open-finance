import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useNotifications,
  useNotificationCount,
  useUpdateExchangeRatesFromNotification,
} from './useNotifications';
import { AuthProvider } from '@/context/AuthContext';
import { mockAuthentication } from '@/test/test-utils';
import { useNotificationReadState } from '@/stores/notificationReadState';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useNotifications', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
    mockAuthentication();
    useNotificationReadState.setState({ seen: {} });
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  );

  const mockNotifications = [
    {
      type: 'STALE_EXCHANGE_RATES' as const,
      title: 'Stale Exchange Rates',
      message: 'Exchange rates are outdated',
      count: 5,
      actionUrl: '/settings/currencies',
      actionLabel: 'Update Rates',
      severity: 'WARNING' as const,
    },
    {
      type: 'LOW_BALANCE' as const,
      title: 'Low Balance',
      message: 'Account balance is low',
      count: 1,
      actionUrl: '/accounts/1',
      actionLabel: 'View Account',
      severity: 'CRITICAL' as const,
    },
  ];

  describe('useNotifications', () => {
    it('should fetch notifications successfully', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockNotifications });

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockNotifications);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/notifications');
    });

    it('should handle API error', async () => {
      mockedApiClient.get.mockRejectedValue(new Error('Fetch failed'));

      const { result } = renderHook(() => useNotifications(), { wrapper });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe('Fetch failed');
    });
  });

  describe('useNotificationCount', () => {
    it('should fetch notification count successfully', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockNotifications });

      const { result } = renderHook(() => useNotificationCount(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toBe(2);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/notifications');
    });

    it('should handle zero count', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [] });

      const { result } = renderHook(() => useNotificationCount(), { wrapper });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toBe(0);
    });
  });

  it('acknowledges conditions per user and makes changed or returning conditions unread', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockNotifications });
    const { result } = renderHook(() => useNotificationCount(), { wrapper });
    await waitFor(() => expect(result.current.data).toBe(2));
    act(() => useNotificationReadState.getState().markRead('1', mockNotifications[0]));
    await waitFor(() => expect(result.current.data).toBe(1));
    act(() => useNotificationReadState.getState().markRead('2', mockNotifications[1]));
    expect(result.current.data).toBe(1);
    mockedApiClient.get.mockResolvedValue({
      data: [{ ...mockNotifications[0], count: 6 }, mockNotifications[1]],
    });
    await act(async () => {
      await result.current.refetch();
    });
    await waitFor(() => expect(result.current.data).toBe(2));
    act(() => useNotificationReadState.getState().markRead('1', mockNotifications[1]));
    expect(result.current.data).toBe(1);
    mockedApiClient.get.mockResolvedValue({ data: [] });
    await act(async () => {
      await result.current.refetch();
    });
    await waitFor(() => expect(result.current.data).toBe(0));
    mockedApiClient.get.mockResolvedValue({ data: mockNotifications });
    await act(async () => {
      await result.current.refetch();
    });
    await waitFor(() => expect(result.current.data).toBe(2));
  });

  describe('useUpdateExchangeRatesFromNotification', () => {
    it('should call the update endpoint successfully', async () => {
      const mockResponse = { message: 'Rates updated', updatedCount: 42 };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useUpdateExchangeRatesFromNotification(), { wrapper });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockResponse);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/notifications/actions/update-exchange-rates'
      );
    });

    it('should invalidate queries on success', async () => {
      const invalidateQueriesSpy = vi.spyOn(queryClient, 'invalidateQueries');
      const mockResponse = { message: 'Rates updated', updatedCount: 10 };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useUpdateExchangeRatesFromNotification(), { wrapper });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['notifications'] });
      expect(invalidateQueriesSpy).toHaveBeenCalledWith({ queryKey: ['exchangeRate'] });
    });

    it('should handle mutation error', async () => {
      mockedApiClient.post.mockRejectedValue(new Error('Update failed'));

      const { result } = renderHook(() => useUpdateExchangeRatesFromNotification(), { wrapper });

      result.current.mutate();

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error?.message).toBe('Update failed');
    });
  });
});
