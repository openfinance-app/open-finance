import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type { InterestPeriod } from '@/types/account';

export interface InterestPreviewRequest {
  balance: string;
  rate: string;
  taxRate: string;
  openingDate: string;
  interestPeriod: InterestPeriod;
  currency: string;
}

export interface InterestPreview {
  year: number;
  startDate: string;
  endDate: string;
  grossInterest: number;
  taxAmount: number;
  netInterest: number;
}

/** Keep entered decimals intact and use the saved-account calculation for the form preview. */
export function useInterestPreview(request: InterestPreviewRequest, enabled: boolean) {
  const serialized = JSON.stringify(request);
  const [settled, setSettled] = useState(serialized);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(serialized), 250);
    return () => clearTimeout(timer);
  }, [serialized]);
  return useQuery({
    queryKey: ['accounts', 'interest-preview', serialized],
    queryFn: async ({ signal }): Promise<InterestPreview> => {
      const response = await apiClient.post<InterestPreview>(
        '/accounts/interest-preview',
        request,
        {
          headers: buildEncryptionHeaders(),
          signal,
        }
      );
      return response.data;
    },
    enabled: enabled && settled === serialized,
    retry: false,
    staleTime: 60_000,
  });
}
