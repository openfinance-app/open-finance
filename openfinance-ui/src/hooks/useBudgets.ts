/**
 * Budget service hooks
 * TASK-8.2.10: Create budget service hooks
 * TASK-8.6: Add hooks for auto budget creation (REQ-2.9.1.5)
 *
 * Provides React Query hooks for budget CRUD operations and progress tracking
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  BudgetRequest,
  BudgetResponse,
  BudgetProgressResponse,
  BudgetSummaryResponse,
  BudgetHistoryResponse,
  BudgetPeriod,
  BudgetSuggestion,
  BudgetSuggestionRequest,
  BudgetBulkCreateRequest,
  BudgetBulkCreateResponse,
} from '@/types/budget';

/**
 * Fetch all budgets with optional period filter
 */
export function useBudgets(period?: BudgetPeriod) {
  return useQuery<BudgetResponse[]>({
    queryKey: ['budgets', period],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const params = period ? `?period=${period}` : '';
      const response = await apiClient.get<BudgetResponse[]>(`/budgets${params}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
  });
}

/**
 * Fetch a single budget by ID
 */
export function useBudget(budgetId: number | null) {
  return useQuery<BudgetResponse>({
    queryKey: ['budgets', budgetId],
    queryFn: async () => {
      if (!budgetId) throw new Error('Budget ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<BudgetResponse>(`/budgets/${budgetId}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    enabled: !!budgetId,
  });
}

/**
 * Fetch budget progress tracking
 */
export function useBudgetProgress(budgetId: number | null) {
  return useQuery<BudgetProgressResponse>({
    queryKey: ['budgets', budgetId, 'progress'],
    queryFn: async () => {
      if (!budgetId) throw new Error('Budget ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<BudgetProgressResponse>(
        `/budgets/${budgetId}/progress`,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    enabled: !!budgetId,
  });
}

/**
 * Fetch budget summary with aggregate data
 */
export function useBudgetSummary(period?: BudgetPeriod) {
  return useQuery<BudgetSummaryResponse>({
    queryKey: ['budgets', 'summary', period],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const params = period ? `?period=${period}` : '';
      const response = await apiClient.get<BudgetSummaryResponse>(
        `/budgets/summary${params}`,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
  });
}

/**
 * Create a new budget
 */
export function useCreateBudget() {
  const queryClient = useQueryClient();

  return useMutation<BudgetResponse, Error, BudgetRequest>({
    mutationFn: async (budgetData: BudgetRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<BudgetResponse>('/budgets', budgetData, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all budget-related queries
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Update an existing budget
 */
export function useUpdateBudget() {
  const queryClient = useQueryClient();

  return useMutation<BudgetResponse, Error, { id: number; data: BudgetRequest }>({
    mutationFn: async ({ id, data }) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.put<BudgetResponse>(`/budgets/${id}`, data, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate budget queries
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      queryClient.invalidateQueries({ queryKey: ['budgets', variables.id] });
    },
  });
}

/**
 * Delete a budget
 */
export function useDeleteBudget() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (budgetId: number) => {
      // Note: DELETE endpoint doesn't require encryption key
      await apiClient.delete(`/budgets/${budgetId}`);
    },
    onSuccess: () => {
      // Invalidate budget queries
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Fetch the per-sub-period spending history for a budget.
 *
 * Calls GET /api/v1/budgets/{id}/history and returns a BudgetHistoryResponse
 * containing metadata about the budget plus an ordered list of sub-period
 * entries (e.g. 12 monthly rows for a yearly "Food" budget).
 *
 * REQ-2.9.1.4: Budget history per sub-period breakdown
 */
export function useBudgetHistory(budgetId: number | null) {
  return useQuery<BudgetHistoryResponse>({
    queryKey: ['budgets', budgetId, 'history'],
    queryFn: async () => {
      if (!budgetId) throw new Error('Budget ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<BudgetHistoryResponse>(
        `/budgets/${budgetId}/history`,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    enabled: !!budgetId,
  });
}

/**
 * Mutation hook: analyse past EXPENSE transactions and return budget suggestions.
 *
 * Calls POST /api/v1/budgets/suggestions.
 * REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 */
export function useAnalyzeBudgets() {
  return useMutation<BudgetSuggestion[], Error, BudgetSuggestionRequest>({
    mutationFn: async (request: BudgetSuggestionRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<BudgetSuggestion[]>(
        '/budgets/suggestions',
        request,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
  });
}

/**
 * Mutation hook: bulk-create budgets from user-confirmed suggestions.
 *
 * Calls POST /api/v1/budgets/bulk and invalidates the ['budgets'] cache on success.
 * REQ-2.9.1.5: Bulk budget creation from user-confirmed suggestions
 */
export function useBulkCreateBudgets() {
  const queryClient = useQueryClient();

  return useMutation<BudgetBulkCreateResponse, Error, BudgetBulkCreateRequest>({
    mutationFn: async (request: BudgetBulkCreateRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<BudgetBulkCreateResponse>(
        '/budgets/bulk',
        request,
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all budget-related queries so the BudgetsPage refreshes
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}
