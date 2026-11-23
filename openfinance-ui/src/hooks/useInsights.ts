/**
 * Insight service hooks
 * TASK-11.4.5: Display AI-Powered Insights in Dashboard
 *
 * Provides React Query hooks for insight operations
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { AI_REQUEST_TIMEOUT_MS } from '@/constants/timing';
import type { Insight } from '@/types/insight';
import { buildEncryptionHeaders } from '@/utils/encryption';

/**
 * Fetch top N insights for dashboard display
 * GET /api/v1/insights/top/{limit}
 */
export function useTopInsights(limit: number = 3) {
  const { i18n } = useTranslation();
  return useQuery({
    queryKey: ['insights', 'top', limit, i18n.resolvedLanguage ?? i18n.language],
    queryFn: async () => {
      const response = await apiClient.get<Insight[]>(`/insights/top/${limit}`, {
        headers: buildEncryptionHeaders(),
      });
      const total = Number(response.headers?.['x-total-count']);
      return {
        insights: response.data,
        total: Number.isSafeInteger(total) && total >= response.data.length ? total : undefined,
      };
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
  const { i18n } = useTranslation();
  return useQuery<Insight[]>({
    queryKey: ['insights', 'all', i18n.resolvedLanguage ?? i18n.language],
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
  const [unavailableSources, setUnavailableSources] = useState<string[]>([]);

  const mutation = useMutation<Insight[], Error, void>({
    onMutate: () => setUnavailableSources([]),
    mutationFn: async () => {
      const response = await apiClient.post<Insight[]>(
        '/insights/generate',
        {},
        {
          timeout: AI_REQUEST_TIMEOUT_MS,
          headers: buildEncryptionHeaders(),
        }
      );
      const unavailable = response.headers?.['x-insights-unavailable-sources'];
      setUnavailableSources(
        typeof unavailable === 'string' ? unavailable.split(',').filter(Boolean) : []
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all insight queries to refetch with new data
      queryClient.invalidateQueries({ queryKey: ['insights'] });
    },
  });
  return { ...mutation, unavailableSources };
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

/** Dismiss the user's complete active list, not just the card's top three insights. */
export function useDismissAllInsights() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, void>({
    mutationFn: async () => {
      await apiClient.post('/insights/dismiss-all');
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['insights'] }),
  });
}
