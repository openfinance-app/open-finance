/**
 * Transaction management hooks
 * Task 3.2.18: Create transaction service hooks
 *
 * Provides React Query hooks for transaction CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  Transaction,
  TransactionRequest,
  TransactionFilters,
  Category,
  TransferUpdateRequest,
  CategoryTreeNode,
} from '@/types/transaction';
import { buildEncryptionHeaders } from '@/utils/encryption';

interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

type TransactionTags = Transaction['tags'] | string | null | undefined;

const normalizeTransactionTags = (tags: TransactionTags): string[] | undefined => {
  if (!tags) return undefined;
  if (Array.isArray(tags)) return tags;
  if (typeof tags === 'string') {
    const parsed = tags
      .split(',')
      .map(tag => tag.trim())
      .filter(tag => tag.length > 0);
    return parsed.length > 0 ? parsed : undefined;
  }
  return undefined;
};

const normalizeTransaction = (transaction: Transaction): Transaction => ({
  ...transaction,
  tags: normalizeTransactionTags(transaction.tags),
});

const normalizeTransactionsResponse = (
  data: PaginatedResponse<Transaction> | Transaction[]
): PaginatedResponse<Transaction> => {
  if (Array.isArray(data)) {
    const normalized = data.map(normalizeTransaction);
    return {
      content: normalized,
      totalElements: normalized.length,
      totalPages: 1,
      number: 0,
      size: normalized.length,
    };
  }

  return {
    ...data,
    content: data.content.map(normalizeTransaction),
  };
};

/**
 * Fetch transactions with filters and pagination
 * Uses the /transactions/search endpoint for proper pagination support
 */
export function useTransactions(filters?: TransactionFilters) {
  return useQuery<PaginatedResponse<Transaction>>({
    queryKey: ['transactions', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.accountId) params.append('accountId', filters.accountId.toString());
      if (filters?.type) params.append('type', filters.type);
      if (filters?.categoryId) params.append('categoryId', filters.categoryId.toString());
      if (filters?.noCategory) params.append('noCategory', 'true');
      if (filters?.payee) params.append('payee', filters.payee);
      if (filters?.noPayee) params.append('noPayee', 'true');
      if (filters?.dateFrom) params.append('dateFrom', filters.dateFrom);
      if (filters?.dateTo) params.append('dateTo', filters.dateTo);
      if (filters?.keyword) params.append('keyword', filters.keyword);
      if (filters?.minAmount !== undefined)
        params.append('amountMin', filters.minAmount.toString());
      if (filters?.maxAmount !== undefined)
        params.append('amountMax', filters.maxAmount.toString());
      if (filters?.page !== undefined) params.append('page', filters.page.toString());
      if (filters?.size) params.append('size', filters.size.toString());
      // Add sorting (default to date descending for newest first)
      if (filters?.sort) {
        params.append('sort', filters.sort);
      } else {
        params.append('sort', 'date,desc');
      }

      // Use tag-specific endpoint if tag filter is active
      const endpoint = filters?.tag
        ? `/transactions/by-tag/${encodeURIComponent(filters.tag)}?${params.toString()}`
        : `/transactions/search?${params.toString()}`;

      const response = await apiClient.get<PaginatedResponse<Transaction> | Transaction[]>(
        endpoint,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return normalizeTransactionsResponse(response.data);
    },
  });
}

/**
 * Fetch a single transaction by ID
 */
export function useTransaction(transactionId: number | null) {
  return useQuery<Transaction>({
    queryKey: ['transactions', transactionId],
    queryFn: async () => {
      if (!transactionId) throw new Error('Transaction ID is required');

      const response = await apiClient.get<Transaction>(`/transactions/${transactionId}`, {
        headers: buildEncryptionHeaders(),
      });
      return normalizeTransaction(response.data);
    },
    enabled: !!transactionId,
  });
}

/**
 * Create a new transaction (INCOME or EXPENSE)
 */
export function useCreateTransaction() {
  const queryClient = useQueryClient();

  return useMutation<Transaction, Error, TransactionRequest>({
    mutationFn: async (transactionData: TransactionRequest) => {
      const response = await apiClient.post<Transaction>('/transactions', transactionData, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate transactions and accounts queries
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate all dashboard queries to refresh cash flow, net worth, etc.
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Invalidate budget queries so spent amounts reflect new transactions
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Create a transfer between accounts
 */
export function useCreateTransfer() {
  const queryClient = useQueryClient();

  return useMutation<Transaction, Error, TransactionRequest>({
    mutationFn: async (transferData: TransactionRequest) => {
      const response = await apiClient.post<Transaction>('/transactions/transfer', transferData, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate transactions and accounts queries
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate all dashboard queries to refresh cash flow, net worth, etc.
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Invalidate budget queries so spent amounts reflect new transactions
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Update an existing transaction
 */
export function useUpdateTransaction() {
  const queryClient = useQueryClient();

  return useMutation<Transaction, Error, { id: number; data: TransactionRequest }>({
    mutationFn: async ({ id, data }) => {
      const response = await apiClient.put<Transaction>(`/transactions/${id}`, data, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate queries
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['transactions', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate all dashboard queries to refresh cash flow, net worth, etc.
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Invalidate budget queries so spent amounts reflect updated transactions
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Update an existing transfer transaction atomically.
 * Both sides (source EXPENSE and destination INCOME) are updated together.
 */
export function useUpdateTransfer() {
  const queryClient = useQueryClient();

  return useMutation<Transaction, Error, { transferId: string; data: TransferUpdateRequest }>({
    mutationFn: async ({ transferId, data }) => {
      const response = await apiClient.put<Transaction>(
        `/transactions/transfers/${transferId}`,
        data,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all transaction and account queries since both sides were updated
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate all dashboard queries to refresh cash flow, net worth, etc.
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Invalidate budget queries so spent amounts reflect updated transactions
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/**
 * Delete a transaction (soft delete)
 */
export function useDeleteTransaction() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (transactionId: number) => {
      await apiClient.delete(`/transactions/${transactionId}`, {
        headers: buildEncryptionHeaders(),
      });
    },
    onSuccess: () => {
      // Invalidate transactions and accounts queries
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate all dashboard queries to refresh cash flow, net worth, etc.
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Invalidate budget queries so spent amounts reflect deleted transactions
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
    },
  });
}

/** * Fetch all categories
 */
export function useCategories(type?: 'INCOME' | 'EXPENSE') {
  return useQuery<Category[]>({
    queryKey: ['categories', type],
    queryFn: async () => {
      const params = type ? `?type=${type}` : '';
      const response = await apiClient.get<Category[]>(`/categories${params}`, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
  });
}

/**
 * Create a new category
 */
export function useCreateCategory() {
  const queryClient = useQueryClient();

  return useMutation<Category, Error, Partial<Category>>({
    mutationFn: async categoryData => {
      const response = await apiClient.post<Category>('/categories', categoryData, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });
}

/**
 * Fetch category tree (hierarchical)
 */
export function useCategoryTree() {
  return useQuery<CategoryTreeNode[]>({
    queryKey: ['categories', 'tree'],
    queryFn: async () => {
      const response = await apiClient.get<CategoryTreeNode[]>('/categories/tree', {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000,
  });
}

/**
 * Delete a category
 */
export function useDeleteCategory() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (categoryId: number) => {
      await apiClient.delete(`/categories/${categoryId}`, {
        headers: buildEncryptionHeaders(),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });
}

/**
 * Update a category
 */
export function useUpdateCategory() {
  const queryClient = useQueryClient();

  return useMutation<Category, Error, { id: number; data: Partial<Category> }>({
    mutationFn: async ({ id, data }) => {
      const response = await apiClient.put<Category>(`/categories/${id}`, data, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
  });
}
