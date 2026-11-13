import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type { PropertyType } from '@/types/realEstate';
import { useImportConfig } from '@/hooks/useImportConfig';

export interface PropertyConversionCandidate {
  assetId: number;
  name: string;
  accountName: string;
  closesSourceAccount: boolean;
  currency: string;
  currentValue: number;
  purchasePrice: number;
  purchaseDate: string;
  transactionCount: number;
  valuationCount: number;
  sourceRevision: string;
}

export interface PropertyConversionRequest {
  operationId: string;
  assetId: number;
  sourceRevision: string;
  name: string;
  address: string;
  propertyType: PropertyType;
  mortgageId?: number;
  rentalIncome?: string;
}

/** Only offer conversion once the server confirms that Skrooge features are enabled. */
export function usePropertyConversionEnabled(): boolean {
  const config = useImportConfig();
  return !config.isError && config.data?.skroogeJsonEnabled === true;
}

export function usePropertyConversionCandidates(enabled = true) {
  const conversionEnabled = usePropertyConversionEnabled();
  return useQuery({
    queryKey: ['realEstate', 'conversion-candidates'],
    enabled: enabled && conversionEnabled,
    queryFn: async () =>
      (
        await apiClient.get<PropertyConversionCandidate[]>('/real-estate/conversion/candidates', {
          headers: buildEncryptionHeaders(),
        })
      ).data,
  });
}

export function useConvertProperty() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (request: PropertyConversionRequest) =>
      (
        await apiClient.post<{ propertyId: number }>('/real-estate/conversion', request, {
          headers: buildEncryptionHeaders(),
        })
      ).data,
    onSuccess: async () => {
      await Promise.all(
        [
          'realEstate',
          'assets',
          'accounts',
          'transactions',
          'dashboard',
          'networth',
          'history',
          'session-history-exists',
          'assetFinancing',
        ].map(key => client.invalidateQueries({ queryKey: [key] }))
      );
    },
  });
}
