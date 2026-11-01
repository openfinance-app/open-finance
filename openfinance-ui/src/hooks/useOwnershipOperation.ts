import { useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';

/** Each ownership event can change cash, holdings, debt and their linked detail views. */
export function useOwnershipOperation<T = void>() {
  const queries = useQueryClient();
  return useMutation({
    mutationFn: async ({ path, data }: { path: string; data: unknown }): Promise<T> =>
      (await apiClient.post<T>(path, data, { headers: buildEncryptionHeaders() })).data,
    onSuccess: () => {
      for (const key of [
        'assets',
        'realEstate',
        'accounts',
        'transactions',
        'liabilities',
        'assetFinancing',
        'networth',
        'dashboard',
        'history',
        'session-history-exists',
      ]) {
        queries.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}
