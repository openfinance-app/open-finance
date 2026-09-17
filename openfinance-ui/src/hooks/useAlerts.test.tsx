import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useAlertsByBudget,
  useUnreadAlerts,
  useUnreadAlertCount,
  useCreateAlert,
  useUpdateAlert,
  useMarkAlertAsRead,
  useMarkAllAlertsAsRead,
  useDeleteAlert,
} from './useAlerts';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useAlerts hooks', () => {
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

  const mockAlert = {
    id: 'alert-1',
    budgetId: 1,
    type: 'THRESHOLD',
    threshold: 80,
    isRead: false,
    message: 'Budget at 80%',
  };

  // ── Query hooks ──────────────────────────────────────────────────────────
  describe('useAlertsByBudget', () => {
    it('should fetch alerts for a specific budget', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAlert] });

      const { result } = renderHook(() => useAlertsByBudget(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockAlert]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/alerts/1');
    });

    it('should be disabled when budgetId is null', () => {
      const { result } = renderHook(() => useAlertsByBudget(null), { wrapper });
      expect(result.current.isFetched).toBe(false);
    });
  });

  describe('useUnreadAlerts', () => {
    it('should fetch unread alerts', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockAlert] });

      const { result } = renderHook(() => useUnreadAlerts(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith('/budgets/alerts/unread');
    });
  });

  describe('useUnreadAlertCount', () => {
    it('should fetch unread alert count', async () => {
      mockedApiClient.get.mockResolvedValue({ data: 5 });

      const { result } = renderHook(() => useUnreadAlertCount(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toBe(5);
    });
  });

  // ── Mutation hooks ─────────────────────────────────────────────────────────
  describe('useCreateAlert', () => {
    it('should create an alert and invalidate queries', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockAlert });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateAlert(), { wrapper });

      result.current.mutate({
        budgetId: 1,
        data: { type: 'THRESHOLD', threshold: 80 } as any,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/budgets/alerts?budgetId=1',
        expect.any(Object)
      );
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgetAlerts', 1] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgetAlerts', 'unread'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgetAlerts', 'unread', 'count'] });
    });
  });

  describe('useUpdateAlert', () => {
    it('should update an alert and invalidate queries', async () => {
      const updatedAlert = { ...mockAlert, threshold: 90 };
      mockedApiClient.put.mockResolvedValue({ data: updatedAlert });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useUpdateAlert(), { wrapper });

      result.current.mutate({
        alertId: 'alert-1',
        data: { threshold: 90 } as any,
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ['budgetAlerts', updatedAlert.budgetId],
      });
    });
  });

  describe('useMarkAlertAsRead', () => {
    it('should mark alert as read', async () => {
      mockedApiClient.put.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useMarkAlertAsRead(), { wrapper });

      result.current.mutate('alert-1');

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.put).toHaveBeenCalledWith('/budgets/alerts/alert-1/read');
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgetAlerts', 'unread'] });
    });
  });

  describe('useMarkAllAlertsAsRead', () => {
    it('should mark all alerts as read', async () => {
      mockedApiClient.put.mockResolvedValue({ data: 5 });

      const { result } = renderHook(() => useMarkAllAlertsAsRead(), { wrapper });

      result.current.mutate();

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.put).toHaveBeenCalledWith('/budgets/alerts/read-all');
      expect(result.current.data).toBe(5);
    });
  });

  describe('useDeleteAlert', () => {
    it('should delete an alert and invalidate queries', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteAlert(), { wrapper });

      result.current.mutate('alert-1');

      await waitFor(() => expect(result.current.isSuccess).toBe(true));

      expect(mockedApiClient.delete).toHaveBeenCalledWith('/budgets/alerts/alert-1');
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['budgetAlerts'] });
    });
  });
});
