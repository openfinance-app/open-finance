/**
 * Account management hooks
 * Task 2.2.12: Create account service hooks
 * 
 * Provides React Query hooks for account CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { Account, AccountRequest, InterestRateVariation, InterestRateVariationRequest, AccountFilters } from '@/types/account';

interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Balance history point for account charts
 */
export interface BalanceHistoryPoint {
  date: string;
  balance: number;
}

/**
 * Fetch all accounts for the current user
 * @param filter Optional filter: "all", "active", or "closed" (default: "active")
 */
export function useAccounts(filter: 'all' | 'active' | 'closed' = 'active') {
  return useQuery<Account[]>({
    queryKey: ['accounts', filter],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<Account[]>('/accounts', {
        params: { filter },
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    refetchOnMount: 'always',
  });
}

/**
 * Fetch accounts with filters and pagination
 * Uses the /accounts/search endpoint for proper pagination support
 */
export function useAccountsSearch(filters?: AccountFilters) {
  return useQuery<PaginatedResponse<Account>>({
    queryKey: ['accounts', 'search', filters],
    queryFn: async () => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const params = new URLSearchParams();
      if (filters?.keyword) params.append('keyword', filters.keyword);
      if (filters?.type) params.append('type', filters.type);
      if (filters?.currency) params.append('currency', filters.currency);
      if (filters?.isActive !== undefined) params.append('isActive', filters.isActive.toString());
      if (filters?.balanceMin !== undefined) params.append('balanceMin', filters.balanceMin.toString());
      if (filters?.balanceMax !== undefined) params.append('balanceMax', filters.balanceMax.toString());
      if (filters?.institution) params.append('institution', filters.institution);
      if (filters?.lowBalance) params.append('lowBalance', 'true');
      if (filters?.page !== undefined) params.append('page', filters.page.toString());
      if (filters?.size) params.append('size', filters.size.toString());
      // Add sorting (default to name ascending)
      if (filters?.sort) {
        params.append('sort', filters.sort);
      } else {
        params.append('sort', 'name,asc');
      }

      const response = await apiClient.get<PaginatedResponse<Account>>(
        `/accounts/search?${params.toString()}`,
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
 * Fetch a single account by ID
 */
export function useAccount(accountId: number | null) {
  return useQuery<Account>({
    queryKey: ['accounts', accountId],
    queryFn: async () => {
      if (!accountId) throw new Error('Account ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<Account>(`/accounts/${accountId}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    enabled: !!accountId,
  });
}

/**
 * Fetch account balance history for charts
 * @param accountId The account ID
 * @param period Time period: "1M", "3M", "6M", "1Y", "ALL"
 */
export function useAccountBalanceHistory(accountId: number | null, period: string = '3M') {
  return useQuery<BalanceHistoryPoint[]>({
    queryKey: ['accounts', accountId, 'balance-history', period],
    queryFn: async () => {
      if (!accountId) throw new Error('Account ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<BalanceHistoryPoint[]>(`/accounts/${accountId}/balance-history`, {
        params: { period },
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    enabled: !!accountId,
  });
}

/**
 * Create a new account
 */
export function useCreateAccount() {
  const queryClient = useQueryClient();

  return useMutation<Account, Error, AccountRequest>({
    mutationFn: async (accountData: AccountRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<Account>('/accounts', accountData, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate accounts query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Update an existing account
 */
export function useUpdateAccount() {
  const queryClient = useQueryClient();

  return useMutation<Account, Error, { id: number; data: AccountRequest }>({
    mutationFn: async ({ id, data }) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.put<Account>(`/accounts/${id}`, data, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate both list and individual account queries
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['accounts', variables.id] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Delete an account (soft delete)
 */
export function useDeleteAccount() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (accountId: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      await apiClient.delete(`/accounts/${accountId}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
    },
    onSuccess: () => {
      // Invalidate accounts query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Close an account (soft delete - sets isActive = false)
 * This preserves all historical data including transactions
 */
export function useCloseAccount() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (accountId: number) => {
      await apiClient.post(`/accounts/${accountId}/close`);
    },
    onSuccess: () => {
      // Invalidate accounts query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Reopen a closed account (sets isActive = true)
 * This makes the account active again
 */
export function useReopenAccount() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (accountId: number) => {
      await apiClient.post(`/accounts/${accountId}/reopen`);
    },
    onSuccess: () => {
      // Invalidate accounts query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Permanently delete an account and all associated transactions
 * WARNING: This is a destructive operation that cannot be undone
 */
export function usePermanentDeleteAccount() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (accountId: number) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      await apiClient.delete(`/accounts/${accountId}/permanent`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
    },
    onSuccess: () => {
      // Invalidate accounts query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      // Invalidate dashboard queries that depend on account data
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/**
 * Fetch interest rate variations for an account
 */
export function useInterestRateVariations(accountId: number | null) {
  return useQuery<InterestRateVariation[]>({
    queryKey: ['accounts', accountId, 'interest-variations'],
    queryFn: async () => {
      if (!accountId) throw new Error('Account ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<InterestRateVariation[]>(`/accounts/${accountId}/interest-variations`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    enabled: !!accountId,
  });
}

/**
 * Create an interest rate variation
 */
export function useCreateVariation() {
  const queryClient = useQueryClient();

  return useMutation<InterestRateVariation, Error, { accountId: number; data: InterestRateVariationRequest }>({
    mutationFn: async ({ accountId, data }) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<InterestRateVariation>(`/accounts/${accountId}/interest-variations`, data, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['accounts', variables.accountId, 'interest-variations'] });
      queryClient.invalidateQueries({ queryKey: ['accounts', variables.accountId, 'interest-estimate'] });
    },
  });
}

/**
 * Delete an interest rate variation
 */
export function useDeleteVariation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { accountId: number; variationId: number }>({
    mutationFn: async ({ accountId, variationId }) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      await apiClient.delete(`/accounts/${accountId}/interest-variations/${variationId}`, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['accounts', variables.accountId, 'interest-variations'] });
      queryClient.invalidateQueries({ queryKey: ['accounts', variables.accountId, 'interest-estimate'] });
    },
  });
}

/**
 * Fetch interest estimate — returns both a 1-year projection and the
 * historically accumulated interest since account creation.
 */
export function useInterestEstimate(accountId: number | null, period: string = '1Y') {
  return useQuery<{ estimate: number; historicalAccumulated: number }>({
    queryKey: ['accounts', accountId, 'interest-estimate', period],
    queryFn: async () => {
      if (!accountId) throw new Error('Account ID is required');

      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.get<{ estimate: number; historicalAccumulated: number }>(
        `/accounts/${accountId}/interest-estimate`,
        {
          params: { period },
          headers: {
            'X-Encryption-Session': encryptionKey,
          },
        }
      );
      return response.data;
    },
    enabled: !!accountId,
  });
}

