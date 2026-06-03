/**
 * useSplitTransactions hook
 * Task 16 (REQ-SPL-2.5)
 *
 * Fetches the split lines for a given transaction via:
 *   GET /api/v1/transactions/{id}/splits
 */
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { TransactionSplitResponse } from '@/types/transaction';

/**
 * React Query hook that retrieves the split lines for a specific transaction.
 *
 * @param transactionId - The ID of the parent transaction.  Pass `null` to
 *   disable the query (e.g. when no transaction is selected).
 * @returns A React Query result containing the list of {@link TransactionSplitResponse}.
 */
export function useSplitTransactions(transactionId: number | null) {
  return useQuery<TransactionSplitResponse[]>({
    queryKey: ['transactions', transactionId, 'splits'],
    queryFn: async () => {
      if (transactionId === null) throw new Error('Transaction ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<TransactionSplitResponse[]>(
        `/transactions/${transactionId}/splits`,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        },
      );

      return response.data;
    },
    // Only run when a transactionId is provided (null disables the query)
    enabled: transactionId !== null,
    // Splits change infrequently; 30 s stale time avoids redundant network calls
    staleTime: 30_000,
  });
}
