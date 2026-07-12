/**
 * Insight service hooks
 * TASK-11.4.5: Display AI-Powered Insights in Dashboard
 *
 * Provides React Query hooks for insight operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { Insight } from '@/types/insight';
import { buildEncryptionHeaders } from '@/utils/encryption';

/**
 * Fetch top N insights for dashboard display
 * GET /api/v1/insights/top/{limit}
 */
export function useTopInsights(limit: number = 3) {
  return useQuery<Insight[]>({
    queryKey: ['insights', 'top', limit],
    queryFn: async () => {
      const response = await apiClient.get<Insight[]>(`/insights/top/${limit}`, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // Consider data fresh for 5 minutes
    retry: 2,
  });
}

/**
 * Fetch all active insights
 * GET /api/v1/insights
 */
export function useInsights() {
  return useQuery<Insight[]>({
    queryKey: ['insights'],
    queryFn: async () => {
      const response = await apiClient.get<Insight[]>('/insights', {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
    retry: 2,
  });
}

/**
 * Generate new insights
 * POST /api/v1/insights/generate
 */
export function useGenerateInsights() {
  const queryClient = useQueryClient();

  return useMutation<Insight[], Error, void>({
    mutationFn: async () => {
      const response = await apiClient.post<Insight[]>(
        '/insights/generate',
        {},
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all insight queries to refetch with new data
      queryClient.invalidateQueries({ queryKey: ['insights'] });
    },
  });
}

/**
 * Dismiss an insight
 * POST /api/v1/insights/{id}/dismiss
 */
export function useDismissInsight() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (insightId: number) => {
      await apiClient.post(`/insights/${insightId}/dismiss`);
    },
    onSuccess: () => {
      // Invalidate insight queries to refetch without dismissed insight
      queryClient.invalidateQueries({ queryKey: ['insights'] });
    },
  });
}
