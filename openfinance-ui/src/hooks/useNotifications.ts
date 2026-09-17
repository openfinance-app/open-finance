import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { INotification } from '../types/notification';
import apiClient from '@/services/apiClient';

const API_BASE = '/notifications';

/**
 * Fetches all notifications for the current user
 */
export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: async () => {
      const response = await apiClient.get<INotification[]>(API_BASE);
      return response.data;
    },
    refetchInterval: 60000, // Refetch every minute
  });
}

/**
 * Fetches notification count for badge display
 */
export function useNotificationCount() {
  return useQuery({
    queryKey: ['notifications', 'count'],
    queryFn: async () => {
      const response = await apiClient.get<number>(`${API_BASE}/count`);
      return response.data;
    },
    refetchInterval: 60000, // Refetch every minute
  });
}

/**
 * Triggers an immediate exchange rate update via the notification action endpoint.
 * On success it invalidates both exchange rate queries and the notification list
 * so the STALE_EXCHANGE_RATES notification disappears immediately.
 */
export function useUpdateExchangeRatesFromNotification() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiClient.post<{ message: string; updatedCount: number }>(
        `${API_BASE}/actions/update-exchange-rates`
      );
      return response.data;
    },
    onSuccess: () => {
      // Dismiss the stale-rates notification
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      // Refresh exchange rate data used elsewhere in the app
      queryClient.invalidateQueries({ queryKey: ['exchangeRate'] });
    },
  });
}
