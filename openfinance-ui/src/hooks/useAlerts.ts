import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { BudgetAlert, CreateAlertRequest, UpdateAlertRequest } from '../types/alert';
import apiClient from '@/services/apiClient';

const API_BASE = '/budgets/alerts';

/**
 * Fetches alerts for a specific budget
 */
export function useAlertsByBudget(budgetId: number | null, _includeProgress = false) {
  return useQuery({
    queryKey: ['budgetAlerts', budgetId, _includeProgress],
    queryFn: async () => {
      if (!budgetId) return [];

      const response = await apiClient.get<BudgetAlert[]>(`${API_BASE}/${budgetId}`);
      return response.data;
    },
    enabled: !!budgetId,
  });
}

/**
 * Fetches all unread alerts for the current user
 */
export function useUnreadAlerts() {
  return useQuery({
    queryKey: ['budgetAlerts', 'unread'],
    queryFn: async () => {
      const response = await apiClient.get<BudgetAlert[]>(`${API_BASE}/unread`);
      return response.data;
    },
    refetchInterval: 60000, // Refetch every minute
  });
}

/**
 * Fetches unread alert count for notification badge
 */
export function useUnreadAlertCount() {
  return useQuery({
    queryKey: ['budgetAlerts', 'unread', 'count'],
    queryFn: async () => {
      const response = await apiClient.get<number>(`${API_BASE}/unread/count`);
      return response.data;
    },
    refetchInterval: 60000, // Refetch every minute
  });
}

/**
 * Creates a new budget alert
 */
export function useCreateAlert() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ budgetId, data }: { budgetId: number; data: CreateAlertRequest }) => {
      const response = await apiClient.post<BudgetAlert>(`${API_BASE}?budgetId=${budgetId}`, data);
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate alerts for this budget
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', variables.budgetId] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread'] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread', 'count'] });
    },
  });
}

/**
 * Updates an existing budget alert
 */
export function useUpdateAlert() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ alertId, data }: { alertId: string; data: UpdateAlertRequest }) => {
      const response = await apiClient.put<BudgetAlert>(`${API_BASE}/${alertId}`, data);
      return response.data;
    },
    onSuccess: data => {
      // Invalidate all related queries
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', data.budgetId] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread'] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread', 'count'] });
    },
  });
}

/**
 * Marks an alert as read
 */
export function useMarkAlertAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (alertId: string) => {
      await apiClient.put(`${API_BASE}/${alertId}/read`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread'] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread', 'count'] });
    },
  });
}

/**
 * Marks all alerts as read
 */
export function useMarkAllAlertsAsRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      const response = await apiClient.put<number>(`${API_BASE}/read-all`);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread'] });
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts', 'unread', 'count'] });
    },
  });
}

/**
 * Deletes a budget alert
 */
export function useDeleteAlert() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (alertId: string) => {
      await apiClient.delete(`${API_BASE}/${alertId}`);
    },
    onSuccess: () => {
      // Invalidate all alert queries
      queryClient.invalidateQueries({ queryKey: ['budgetAlerts'] });
    },
  });
}
