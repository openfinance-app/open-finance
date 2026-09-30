/**
 * Tranche hooks for staged liabilities (Task 8).
 *
 * TanStack Query wrappers around the liability tranche endpoints.
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type { LiabilityTranche } from '@/types/liability';

/**
 * Request payload of the create-tranche endpoint (Task 9 interest-only UI).
 */
export interface TrancheRequest {
  plannedAmount: number | string;
  plannedDate?: string;
  fee?: number;
  interestOnly?: boolean;
  interestOnlyUntil?: string;
  realEstateId?: number;
  notes?: string;
  currency?: string;
}

/**
 * Fetch the tranches (planned drawdowns) of a liability, ordered by tranche number.
 * The query is disabled while no liability is selected.
 */
export function useTranches(liabilityId: number | null) {
  return useQuery<LiabilityTranche[]>({
    queryKey: ['liabilities', liabilityId, 'tranches'],
    queryFn: async () => {
      if (!liabilityId) throw new Error('Liability ID is required');

      const response = await apiClient.get<LiabilityTranche[]>(
        `/liabilities/${liabilityId}/tranches`,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    enabled: liabilityId != null,
  });
}

/**
 * Add a tranche (planned drawdown) to a staged liability (Task 9 interest-only UI).
 * {@code interestOnly} + {@code interestOnlyUntil} mark an interest-only phase.
 */
export function useCreateTranche() {
  const queryClient = useQueryClient();

  return useMutation<LiabilityTranche, Error, { liabilityId: number; request: TrancheRequest }>({
    mutationFn: async ({ liabilityId, request }) => {
      const response = await apiClient.post<LiabilityTranche>(
        `/liabilities/${liabilityId}/tranches`,
        request,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ['liabilities', variables.liabilityId, 'tranches'],
      });
      queryClient.invalidateQueries({ queryKey: ['liabilities'] });
    },
  });
}

/**
 * Human-readable label of a tranche: T1, T2, …
 */
export const getTrancheLabel = (trancheNo: number | null | undefined): string =>
  trancheNo != null ? `T${trancheNo}` : '—';

export function useReverseDirectDraw() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async ({ trancheId, date }: { trancheId: number; date: string }) =>
      apiClient.post(
        `/tranches/${trancheId}/reverse`,
        { date },
        { headers: buildEncryptionHeaders() }
      ),
    onSuccess: async () => {
      for (const key of [
        'liabilities',
        'realEstate',
        'assets',
        'dashboard',
        'networth',
        'assetFinancing',
      ]) {
        await client.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}
