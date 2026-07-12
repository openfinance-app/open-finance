/**
 * Asset management hooks
 * Task 5.2.10: Create asset service hooks
 *
 * Provides React Query hooks for asset CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { formatCurrency } from '@/utils/currency';
import i18next from 'i18next';
import type { Asset, AssetRequest, AssetFilters } from '@/types/asset';
import { buildEncryptionHeaders } from '@/utils/encryption';

interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/**
 * Fetch all assets for the current user with optional filters
 */
export function useAssets(filters?: AssetFilters) {
  return useQuery<Asset[]>({
    queryKey: ['assets', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.accountId) params.append('accountId', filters.accountId.toString());
      if (filters?.type) params.append('type', filters.type);

      const queryString = params.toString();
      const url = queryString ? `/assets?${queryString}` : '/assets';

      const response = await apiClient.get<Asset[]>(url, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
  });
}

/**
 * Fetch assets with filters and pagination
 * Uses the /assets/search endpoint for proper pagination support
 */
export function useAssetsSearch(filters?: AssetFilters) {
  return useQuery<PaginatedResponse<Asset>>({
    queryKey: ['assets', 'search', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.keyword) params.append('keyword', filters.keyword);
      if (filters?.type) params.append('type', filters.type);
      if (filters?.accountId) params.append('accountId', filters.accountId.toString());
      if (filters?.currency) params.append('currency', filters.currency);
      if (filters?.symbol) params.append('symbol', filters.symbol);
      if (filters?.purchaseDateFrom) params.append('purchaseDateFrom', filters.purchaseDateFrom);
      if (filters?.purchaseDateTo) params.append('purchaseDateTo', filters.purchaseDateTo);
      if (filters?.valueMin !== undefined) params.append('valueMin', filters.valueMin.toString());
      if (filters?.valueMax !== undefined) params.append('valueMax', filters.valueMax.toString());
      if (filters?.page !== undefined) params.append('page', filters.page.toString());
      if (filters?.size) params.append('size', filters.size.toString());
      // Add sorting (default to name ascending)
      if (filters?.sort) {
        params.append('sort', filters.sort);
      } else {
        params.append('sort', 'name,asc');
      }

      const response = await apiClient.get<PaginatedResponse<Asset>>(
        `/assets/search?${params.toString()}`,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
  });
}

/**
 * Fetch a single asset by ID
 */
export function useAsset(assetId: number | null) {
  return useQuery<Asset>({
    queryKey: ['assets', assetId],
    queryFn: async () => {
      if (!assetId) throw new Error('Asset ID is required');

      const response = await apiClient.get<Asset>(`/assets/${assetId}`, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    enabled: !!assetId,
  });
}

/**
 * Create a new asset
 */
export function useCreateAsset() {
  const queryClient = useQueryClient();

  return useMutation<Asset, Error, AssetRequest>({
    mutationFn: async (assetData: AssetRequest) => {
      const response = await apiClient.post<Asset>('/assets', assetData, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate assets query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
  });
}

/**
 * Update an existing asset
 */
export function useUpdateAsset() {
  const queryClient = useQueryClient();

  return useMutation<Asset, Error, { id: number; data: AssetRequest }>({
    mutationFn: async ({ id, data }) => {
      const response = await apiClient.put<Asset>(`/assets/${id}`, data, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate both list and individual asset queries
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['assets', variables.id] });
    },
  });
}

/**
 * Delete an asset
 */
export function useDeleteAsset() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (assetId: number) => {
      await apiClient.delete(`/assets/${assetId}`, {
        headers: buildEncryptionHeaders(),
      });
    },
    onSuccess: () => {
      // Invalidate assets query to refetch the list
      queryClient.invalidateQueries({ queryKey: ['assets'] });
    },
  });
}

/**
 * Format gain/loss with color coding.
 * Uses the correct locale-aware formatCurrency from @/utils/currency.
 *
 * @param unrealizedGain - raw gain/loss amount (may be negative)
 * @param gainPercentage - gain as a percentage
 * @param currency       - ISO 4217 currency code to display (default 'USD')
 */
export const formatGainLoss = (
  unrealizedGain: number,
  gainPercentage: number,
  currency = 'USD'
) => {
  const sign = unrealizedGain >= 0 ? '+' : '';
  const color =
    unrealizedGain > 0 ? 'text-green-500' : unrealizedGain < 0 ? 'text-red-500' : 'text-text-muted';

  const formatted = `${sign}${formatCurrency(Math.abs(unrealizedGain), currency)} (${sign}${gainPercentage.toFixed(2)}%)`;

  return { formatted, color };
};

/**
 * Get user-friendly asset type name
 * Task 9.2.5: Added physical asset types
 */
export const getAssetTypeName = (type: string): string => {
  const key = `assets:types.${type}`;
  if (i18next.exists(key)) {
    return i18next.t(key);
  }
  return type;
};

/**
 * Get badge color variant for asset type
 * Task 9.2.5: Added physical asset types
 */
export const getAssetTypeBadgeVariant = (
  type: string
): 'default' | 'success' | 'info' | 'warning' => {
  const variantMap: Record<string, 'default' | 'success' | 'info' | 'warning'> = {
    STOCK: 'info',
    ETF: 'info',
    CRYPTO: 'warning',
    BOND: 'success',
    MUTUAL_FUND: 'default',
    REAL_ESTATE: 'success',
    COMMODITY: 'warning',
    VEHICLE: 'info',
    JEWELRY: 'warning',
    COLLECTIBLE: 'warning',
    ELECTRONICS: 'info',
    FURNITURE: 'default',
    OTHER: 'default',
  };
  return variantMap[type] || 'default';
};

/**
 * Get condition badge variant
 * Task 9.2.5: Physical asset condition color coding
 */
export const getConditionBadgeVariant = (
  condition: string
): 'default' | 'success' | 'info' | 'warning' | 'error' => {
  const variantMap: Record<string, 'default' | 'success' | 'info' | 'warning' | 'error'> = {
    NEW: 'success',
    EXCELLENT: 'info',
    GOOD: 'warning',
    FAIR: 'warning',
    POOR: 'error',
  };
  return variantMap[condition] || 'default';
};
