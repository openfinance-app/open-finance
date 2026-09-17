/**
 * Institution management hooks
 *
 * Provides React Query hooks for institution CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { Institution, InstitutionRequest } from '@/types/institution';

/**
 * Fetch all institutions
 */
export function useInstitutions() {
  return useQuery<Institution[]>({
    queryKey: ['institutions'],
    queryFn: async () => {
      const response = await apiClient.get<Institution[]>('/institutions');
      return response.data;
    },
  });
}

/**
 * Fetch institutions by country
 */
export function useInstitutionsByCountry(country: string) {
  return useQuery<Institution[]>({
    queryKey: ['institutions', 'country', country],
    queryFn: async () => {
      const response = await apiClient.get<Institution[]>(`/institutions/country/${country}`);
      return response.data;
    },
    enabled: !!country,
  });
}

/**
 * Search institutions by name
 */
export function useInstitutionSearch(query: string) {
  return useQuery<Institution[]>({
    queryKey: ['institutions', 'search', query],
    queryFn: async () => {
      const response = await apiClient.get<Institution[]>('/institutions/search', {
        params: { query },
      });
      return response.data;
    },
    enabled: query.length >= 2,
  });
}

/**
 * Fetch system institutions (default EU banks)
 */
export function useSystemInstitutions() {
  return useQuery<Institution[]>({
    queryKey: ['institutions', 'system'],
    queryFn: async () => {
      const response = await apiClient.get<Institution[]>('/institutions/system');
      return response.data;
    },
  });
}

/**
 * Fetch custom (user-created) institutions
 */
export function useCustomInstitutions() {
  return useQuery<Institution[]>({
    queryKey: ['institutions', 'custom'],
    queryFn: async () => {
      const response = await apiClient.get<Institution[]>('/institutions/custom');
      return response.data;
    },
  });
}

/**
 * Fetch distinct country codes
 */
export function useInstitutionCountries() {
  return useQuery<string[]>({
    queryKey: ['institutions', 'countries'],
    queryFn: async () => {
      const response = await apiClient.get<string[]>('/institutions/countries');
      return response.data;
    },
  });
}

/**
 * Create a new institution
 */
export function useCreateInstitution() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: InstitutionRequest) => {
      const response = await apiClient.post<Institution>('/institutions', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['institutions'] });
    },
  });
}

/**
 * Update an institution
 */
export function useUpdateInstitution() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, data }: { id: number; data: InstitutionRequest }) => {
      const response = await apiClient.put<Institution>(`/institutions/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['institutions'] });
    },
  });
}

/**
 * Delete an institution
 */
export function useDeleteInstitution() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/institutions/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['institutions'] });
    },
  });
}
