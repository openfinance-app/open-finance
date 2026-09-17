import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { useAuthContext } from '@/context/AuthContext';
import type { User, UserSettings, UpdateUserSettingsRequest } from '@/types/user';

/**
 * Hook to fetch current user profile
 * @returns User profile data with React Query state
 */
export function useUserProfile() {
  const { isAuthenticated } = useAuthContext();
  return useQuery<User>({
    queryKey: ['user', 'profile'],
    queryFn: async () => {
      const response = await apiClient.get('/users/me');
      return response.data;
    },
    enabled: isAuthenticated,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

/**
 * Hook to fetch user's base currency setting
 * @returns Base currency code (e.g., "USD", "EUR")
 */
export function useBaseCurrency() {
  const { isAuthenticated } = useAuthContext();
  return useQuery<string>({
    queryKey: ['user', 'baseCurrency'],
    queryFn: async () => {
      const response = await apiClient.get('/users/me/base-currency');
      return response.data.baseCurrency;
    },
    enabled: isAuthenticated,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

/**
 * Hook to update user's base currency setting
 * @returns Mutation function to update base currency
 */
export function useUpdateBaseCurrency() {
  const queryClient = useQueryClient();
  const { updateUser } = useAuthContext();

  return useMutation({
    mutationFn: async (baseCurrency: string) => {
      const response = await apiClient.put('/users/me/base-currency', {
        baseCurrency,
      });
      return response.data;
    },
    onSuccess: data => {
      // Persist the new baseCurrency into AuthContext (and underlying storage)
      // so it survives page refreshes without requiring a re-login.
      updateUser({ baseCurrency: data.baseCurrency });

      // Update all user-related queries
      queryClient.invalidateQueries({ queryKey: ['user'] });
      // Refresh dashboard to reflect new base currency
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      // Refresh exchange rates
      queryClient.invalidateQueries({ queryKey: ['exchangeRate'] });
      // Refresh all pages that display currency-dependent data
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['liabilities'] });
      queryClient.invalidateQueries({ queryKey: ['realEstate'] });
      queryClient.invalidateQueries({ queryKey: ['budgets'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['insights'] });
      queryClient.invalidateQueries({ queryKey: ['recurringTransactions'] });

      // Update cached user profile with new base currency
      queryClient.setQueryData(['user', 'profile'], (old: User | undefined) => {
        if (old) {
          return { ...old, baseCurrency: data.baseCurrency };
        }
        return old;
      });

      // Update cached base currency
      queryClient.setQueryData(['user', 'baseCurrency'], data.baseCurrency);
    },
  });
}

/**
 * Hook to fetch user's display and locale settings
 * Auto-creates default settings if none exist
 * @returns UserSettings data with React Query state
 */
export function useUserSettings() {
  const { isAuthenticated } = useAuthContext();
  return useQuery<UserSettings>({
    queryKey: ['user', 'settings'],
    queryFn: async () => {
      const response = await apiClient.get('/users/me/settings');
      return response.data;
    },
    enabled: isAuthenticated,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

/**
 * Hook to update user's display and locale settings
 * Supports partial updates - only provided fields will be updated
 * @returns Mutation function to update settings
 */
export function useUpdateUserSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (settings: UpdateUserSettingsRequest) => {
      const response = await apiClient.put('/users/me/settings', settings);
      return response.data as UserSettings;
    },
    onSuccess: data => {
      // Update settings cache
      queryClient.setQueryData(['user', 'settings'], data);

      // Invalidate user profile in case settings affect other parts
      queryClient.invalidateQueries({ queryKey: ['user', 'profile'] });
      // Refresh dashboard and exchange rates (e.g. secondary currency change)
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['exchangeRate'] });
    },
  });
}
