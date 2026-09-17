/**
 * Payee management hooks
 *
 * Provides React Query hooks for payee CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { Payee, PayeeRequest } from '@/types/payee';

/**
 * Fetch all payees
 */
export function usePayees() {
  return useQuery<Payee[]>({
    queryKey: ['payees'],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>('/payees');
      return response.data;
    },
  });
}

/**
 * Fetch all active payees (for transaction form dropdown)
 */
export function useActivePayees() {
  return useQuery<Payee[]>({
    queryKey: ['payees', 'active'],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>('/payees/active');
      return response.data;
    },
  });
}

/**
 * Fetch payees by category
 */
export function usePayeesByCategory(category: string) {
  return useQuery<Payee[]>({
    queryKey: ['payees', 'category', category],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>(`/payees/category/${category}`);
      return response.data;
    },
    enabled: !!category,
  });
}

/**
 * Search payees by name
 */
export function usePayeeSearch(query: string) {
  return useQuery<Payee[]>({
    queryKey: ['payees', 'search', query],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>('/payees/search', {
        params: { query },
      });
      return response.data;
    },
    enabled: query.length >= 2,
  });
}

/**
 * Fetch system payees (default merchants/providers)
 */
export function useSystemPayees() {
  return useQuery<Payee[]>({
    queryKey: ['payees', 'system'],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>('/payees/system');
      return response.data;
    },
  });
}

/**
 * Fetch custom (user-created) payees
 */
export function useCustomPayees() {
  return useQuery<Payee[]>({
    queryKey: ['payees', 'custom'],
    queryFn: async () => {
      const response = await apiClient.get<Payee[]>('/payees/custom');
      return response.data;
    },
  });
}

/**
 * Fetch distinct categories
 */
export function usePayeeCategories() {
  return useQuery<string[]>({
    queryKey: ['payees', 'categories'],
    queryFn: async () => {
      const response = await apiClient.get<string[]>('/payees/categories');
      return response.data;
    },
  });
}

/**
 * Find or create a payee by name
 */
export function useFindOrCreatePayee() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (name: string) => {
      const response = await apiClient.post<Payee>('/payees/find-or-create', null, {
        params: { name },
      });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payees'] });
    },
  });
}

/**
 * Create a new payee
 */
export function useCreatePayee() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: PayeeRequest) => {
      const response = await apiClient.post<Payee>('/payees', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payees'] });
    },
  });
}

/**
 * Update a payee
 */
export function useUpdatePayee() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, data }: { id: number; data: PayeeRequest }) => {
      const response = await apiClient.put<Payee>(`/payees/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payees'] });
    },
  });
}

/**
 * Toggle payee active status
 */
export function useTogglePayeeActive() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const response = await apiClient.patch<Payee>(`/payees/${id}/toggle-active`);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payees'] });
    },
  });
}

/**
 * Delete a payee
 */
export function useDeletePayee() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/payees/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payees'] });
    },
  });
}
