import { useAuthContext } from '@/context/AuthContext';
import { useNotificationReadState, notificationFingerprint } from '@/stores/notificationReadState';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { INotification } from '@/types/notification';
import apiClient from '@/services/apiClient';

const API_BASE = '/notifications';

/**
 * Fetches active system notifications and hides conditions this user has cleared.
 */
export function useNotifications() {
  const { i18n } = useTranslation();
  const { user } = useAuthContext();
  const userId = user ? String(user.id) : null;
  const dismissed = useNotificationReadState(state =>
    userId ? state.dismissed[userId] : undefined
  );
  const dismiss = useNotificationReadState(state => state.dismiss);
  const syncActive = useNotificationReadState(state => state.syncActive);
  const query = useQuery({
    queryKey: ['notifications', userId, i18n.language],
    enabled: !!userId,
    queryFn: async () => {
      const response = await apiClient.get<INotification[]>(API_BASE);
      if (userId) syncActive(userId, response.data);
      return response.data;
    },
    refetchInterval: 60000, // Refetch every minute
  });
  return {
    ...query,
    data: query.data?.filter(
      notification => !dismissed?.includes(notificationFingerprint(notification))
    ),
    dismiss: (notification: INotification) => {
      if (userId) dismiss(userId, [notification]);
    },
    dismissAll: () => {
      if (userId && query.data?.length) dismiss(userId, query.data);
    },
  };
}

/**
 * Fetches notification count for badge display
 */
export function useNotificationCount() {
  const query = useNotifications();
  const { user } = useAuthContext();
  const userId = user ? String(user.id) : null;
  const seen = useNotificationReadState(state => (userId ? state.seen[userId] : undefined));
  return {
    ...query,
    data: query.data?.filter(
      notification => seen?.[notification.type] !== notificationFingerprint(notification)
    ).length,
  };
}

export function useMarkNotificationAsRead() {
  const { user } = useAuthContext();
  const markRead = useNotificationReadState(state => state.markRead);
  return (notification: INotification): void => {
    if (user) markRead(String(user.id), notification);
  };
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
