/**
 * Recurring Transaction Management Hooks
 * Task 12.2.11: Create useRecurringTransactions hook
 * 
 * React Query hooks for managing recurring transactions
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  RecurringTransaction,
  RecurringTransactionRequest,
  RecurringTransactionFilters,
  ProcessingResult,
} from '@/types/recurringTransaction';

/**
 * Fetch all recurring transactions with optional filters
 */
export function useRecurringTransactions(filters?: RecurringTransactionFilters) {
  return useQuery<RecurringTransaction[]>({
    queryKey: ['recurringTransactions', filters],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const params = new URLSearchParams();
      if (filters?.accountId) params.append('accountId', filters.accountId.toString());
      if (filters?.type) params.append('type', filters.type);
      if (filters?.frequency) params.append('frequency', filters.frequency);

      // Determine endpoint based on filters
      let endpoint = '/recurring-transactions';
      if (filters?.isActive !== undefined) {
        endpoint = filters.isActive ? '/recurring-transactions/active' : '/recurring-transactions';
      }

      const response = await apiClient.get<RecurringTransaction[]>(
        params.toString() ? `${endpoint}?${params.toString()}` : endpoint,
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
 * Fetch recurring transactions with pagination and optional filters
 */
export function useRecurringTransactionsPaged(filters: RecurringTransactionFilters = {}) {
  const { page = 0, size = 20, type, frequency, isActive, search, sort = 'nextOccurrence,asc' } = filters;
  
  return useQuery({
    queryKey: ['recurringTransactions', 'paged', filters],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const params = new URLSearchParams();
      params.append('page', String(page));
      params.append('size', String(size));
      params.append('sort', sort);
      if (type) params.append('type', type);
      if (frequency) params.append('frequency', frequency);
      if (isActive !== undefined) params.append('isActive', String(isActive));
      if (search) params.append('search', search);

      const response = await apiClient.get<{
        content: RecurringTransaction[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
      }>(`/recurring-transactions/paged?${params.toString()}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
  });
}

/**
 * Fetch active recurring transactions only
 */
export function useActiveRecurringTransactions(filters?: Omit<RecurringTransactionFilters, 'isActive'>) {
  return useRecurringTransactions({ ...filters, isActive: true });
}

/**
 * Fetch due recurring transactions (preview)
 */
export function useDueRecurringTransactions() {
  return useQuery<RecurringTransaction[]>({
    queryKey: ['recurringTransactions', 'due'],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<RecurringTransaction[]>('/recurring-transactions/due', {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
  });
}

/**
 * Fetch a single recurring transaction by ID
 */
export function useRecurringTransaction(id: number | null) {
  return useQuery<RecurringTransaction>({
    queryKey: ['recurringTransactions', id],
    queryFn: async () => {
      if (!id) throw new Error('Recurring transaction ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<RecurringTransaction>(`/recurring-transactions/${id}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    enabled: !!id,
  });
}

/**
 * Create a new recurring transaction
 */
export function useCreateRecurringTransaction() {
  const queryClient = useQueryClient();

  return useMutation<RecurringTransaction, Error, RecurringTransactionRequest>({
    mutationFn: async (data: RecurringTransactionRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<RecurringTransaction>('/recurring-transactions', data, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all recurring transaction queries
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
      // Also invalidate dashboard as it may show upcoming recurring transactions
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Update an existing recurring transaction
 */
export function useUpdateRecurringTransaction() {
  const queryClient = useQueryClient();

  return useMutation<RecurringTransaction, Error, { id: number; data: RecurringTransactionRequest }>({
    mutationFn: async ({ id, data }) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.put<RecurringTransaction>(`/recurring-transactions/${id}`, data, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Delete a recurring transaction
 */
export function useDeleteRecurringTransaction() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (id: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      await apiClient.delete(`/recurring-transactions/${id}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Pause a recurring transaction
 */
export function usePauseRecurringTransaction() {
  const queryClient = useQueryClient();

  return useMutation<RecurringTransaction, Error, number>({
    mutationFn: async (id: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<RecurringTransaction>(
        `/recurring-transactions/${id}/pause`,
        {},
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
    },
  });
}

/**
 * Resume a paused recurring transaction
 */
export function useResumeRecurringTransaction() {
  const queryClient = useQueryClient();

  return useMutation<RecurringTransaction, Error, number>({
    mutationFn: async (id: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<RecurringTransaction>(
        `/recurring-transactions/${id}/resume`,
        {},
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
    },
  });
}

/**
 * Manually trigger recurring transaction processing (admin only)
 */
export function useProcessRecurringTransactions() {
  const queryClient = useQueryClient();

  return useMutation<ProcessingResult, Error, void>({
    mutationFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<ProcessingResult>(
        '/recurring-transactions/process',
        {},
        {
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate transactions and recurring transactions after processing
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
    },
  });
}

/**
 * Combined hook for all recurring transaction operations
 */
export function useRecurringTransactionOperations() {
  const create = useCreateRecurringTransaction();
  const update = useUpdateRecurringTransaction();
  const deleteRt = useDeleteRecurringTransaction();
  const pause = usePauseRecurringTransaction();
  const resume = useResumeRecurringTransaction();
  const process = useProcessRecurringTransactions();

  return {
    create,
    update,
    delete: deleteRt,
    pause,
    resume,
    process,
    isLoading:
      create.isPending ||
      update.isPending ||
      deleteRt.isPending ||
      pause.isPending ||
      resume.isPending ||
      process.isPending,
  };
}
