import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { Asset } from '@/types/asset';
import { buildEncryptionHeaders } from '@/utils/encryption';

export type AssetLogoIdentity = Pick<Asset, 'id' | 'userId' | 'type' | 'symbol' | 'name'>;

/** Resolve separately from the list so a slow or unavailable provider never delays balances. */
export function useAssetLogo(asset: AssetLogoIdentity) {
  return useQuery({
    queryKey: ['asset-logo', asset.userId, asset.id, asset.type, asset.symbol, asset.name],
    queryFn: async ({ signal }): Promise<string | null> => {
      const response = await apiClient.get<{ logo?: string | null }>(`/assets/${asset.id}/logo`, {
        signal,
        headers: buildEncryptionHeaders(),
      });
      return response.data.logo ?? null;
    },
    enabled: !!asset.id && ['STOCK', 'ETF', 'CRYPTO', 'MUTUAL_FUND'].includes(asset.type),
    staleTime: 15 * 60 * 1000,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
