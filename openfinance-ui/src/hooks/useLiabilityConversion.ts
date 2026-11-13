import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type { Liability, LiabilityRequest } from '@/types/liability';
import { useImportConfig } from '@/hooks/useImportConfig';
import type { LoanComponent } from '@/types/transaction';

export interface LiabilityConversionCandidate {
  exactValues?: Record<string, string>;
  accountId: number;
  name: string;
  currency: string;
  outstandingBalance: number;
  startDate: string;
  transactionCount: number;
  sourceRevision: string;
}

export interface LiabilityConversionRequest {
  operationId: string;
  accountId: number;
  sourceRevision: string;
  fundedPrincipal: string;
  liability: LiabilityRequest;
  payments?: LiabilityConversionPayment[];
}

export interface LiabilityConversionPayment {
  transactionId: number;
  sourceRevision: string;
  component?: LoanComponent;
  splits: Array<{ splitId: number; component: LoanComponent }>;
}

export interface ConversionPaymentSplit {
  splitId: number;
  amount: number;
  exactValues?: Record<string, string>;
  categoryName?: string;
  categoryNameKey?: string;
  suggestedComponent?: LoanComponent;
}

export interface ConversionPaymentCandidate {
  transactionId: number;
  sourceRevision: string;
  date: string;
  description: string;
  notes?: string;
  accountName: string;
  currency: string;
  amount: number;
  exactValues?: Record<string, string>;
  categoryName?: string;
  categoryNameKey?: string;
  suggestedComponent?: LoanComponent;
  splits: ConversionPaymentSplit[];
}

export function useLiabilityConversionPayments(
  accountId: number,
  search: string,
  enabled: boolean
) {
  const conversionEnabled = useLiabilityConversionEnabled();
  return useQuery({
    queryKey: ['liabilities', 'conversion-payments', accountId, search],
    enabled: enabled && conversionEnabled,
    queryFn: async () =>
      (
        await apiClient.get<{ items: ConversionPaymentCandidate[]; truncated: boolean }>(
          `/liabilities/conversion/${accountId}/payments`,
          { params: { search }, headers: buildEncryptionHeaders() }
        )
      ).data,
  });
}

/** Only offer conversion once the server confirms that Skrooge features are enabled. */
export function useLiabilityConversionEnabled(): boolean {
  const config = useImportConfig();
  return !config.isError && config.data?.skroogeJsonEnabled === true;
}

export function useLiabilityConversionCandidates(enabled = true) {
  const conversionEnabled = useLiabilityConversionEnabled();
  return useQuery({
    queryKey: ['liabilities', 'conversion-candidates'],
    enabled: enabled && conversionEnabled,
    queryFn: async () =>
      (
        await apiClient.get<LiabilityConversionCandidate[]>('/liabilities/conversion/candidates', {
          headers: buildEncryptionHeaders(),
        })
      ).data,
  });
}

export function useConvertLiability() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: async (request: LiabilityConversionRequest) =>
      (
        await apiClient.post<Liability>('/liabilities/conversion', request, {
          headers: buildEncryptionHeaders(),
        })
      ).data,
    onSuccess: async () => {
      await Promise.all(
        [
          'realEstate',
          'liabilities',
          'borrowingCapacity',
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
