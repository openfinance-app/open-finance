/**
 * Liability management hooks
 * Task 6.2.5: Create useLiabilities hooks
 * 
 * Provides React Query hooks for liability CRUD operations
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import i18next from 'i18next';
import { buildEncryptionHeaders } from '@/utils/encryption';
import type {
  Liability,
  LiabilityRequest,
  LiabilityFilters,
  AmortizationSchedule,
  LiabilityTotals,
  LiabilityBreakdown,
} from '@/types/liability';
import type { Transaction } from '@/types/transaction';

/**
 * Fetch all liabilities for the current user with optional filters
 */
export function useLiabilities(filters?: LiabilityFilters) {
  return useQuery<Liability[]>({
    queryKey: ['liabilities', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filters?.type) params.append('type', filters.type);

      const queryString = params.toString();
      const url = queryString ? `/liabilities?${queryString}` : '/liabilities';

      const response = await apiClient.get<Liability[]>(url, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
  });
}

/**
 * Fetch liabilities with pagination for the current user
 */
export function useLiabilitiesPaged(filters: LiabilityFilters = {}) {
  const { page = 0, size = 20, type, search, sort = 'createdAt,desc' } = filters;

  return useQuery({
    queryKey: ['liabilities', 'paged', filters],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.append('page', String(page));
      params.append('size', String(size));
      params.append('sort', sort);
      if (type) params.append('type', type);
      if (search) params.append('search', search);

      const response = await apiClient.get<{
        content: Liability[];
        totalElements: number;
        totalPages: number;
        number: number;
        size: number;
      }>(`/liabilities/paged?${params.toString()}`, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
  });
}

/**
 * Fetch a single liability by ID
 */
export function useLiability(liabilityId: number | null) {
  return useQuery<Liability>({
    queryKey: ['liabilities', liabilityId],
    queryFn: async () => {
      if (!liabilityId) throw new Error('Liability ID is required');

      const response = await apiClient.get<Liability>(`/liabilities/${liabilityId}`, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    enabled: !!liabilityId,
  });
}

/**
 * Create a new liability
 */
export function useCreateLiability() {
  const queryClient = useQueryClient();

  return useMutation<Liability, Error, LiabilityRequest>({
    mutationFn: async (liabilityData: LiabilityRequest) => {
      const response = await apiClient.post<Liability>('/liabilities', liabilityData, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: () => {
      // Invalidate liabilities and dashboard queries
      queryClient.invalidateQueries({ queryKey: ['liabilities'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['networth'] });
    },
  });
}

/**
 * Update an existing liability
 */
export function useUpdateLiability() {
  const queryClient = useQueryClient();

  return useMutation<Liability, Error, { id: number; data: LiabilityRequest }>({
    mutationFn: async ({ id, data }) => {
      const response = await apiClient.put<Liability>(`/liabilities/${id}`, data, {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
    onSuccess: (_, variables) => {
      // Invalidate both list and individual liability queries
      queryClient.invalidateQueries({ queryKey: ['liabilities'] });
      queryClient.invalidateQueries({ queryKey: ['liabilities', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['amortization', variables.id] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['networth'] });
    },
  });
}

/**
 * Delete a liability
 */
export function useDeleteLiability() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: async (liabilityId: number) => {
      await apiClient.delete(`/liabilities/${liabilityId}`, {
        headers: buildEncryptionHeaders(),
      });
    },
    onSuccess: () => {
      // Invalidate liabilities and dashboard queries
      queryClient.invalidateQueries({ queryKey: ['liabilities'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['networth'] });
    },
  });
}

/**
 * Raw entry shape returned by the backend amortization endpoint.
 * The backend uses principalPortion / interestPortion while the frontend
 * type uses principalPayment / interestPayment — this internal type bridges
 * the gap without changing the shared Liability types file.
 */
interface RawAmortizationEntry {
  paymentNumber: number;
  paymentDate: string;
  paymentAmount: number;
  principalPortion: number;
  interestPortion: number;
  remainingBalance: number;
}

/**
 * Fetch amortization schedule for a liability.
 * Accepts the full Liability object so the hook can populate the schedule's
 * summary fields (name, principal, interestRate, currency …) without an
 * extra round-trip to the server.
 */
export function useAmortizationSchedule(liability: Liability | null) {
  return useQuery<AmortizationSchedule>({
    queryKey: ['amortization', liability?.id],
    queryFn: async () => {
      if (!liability) throw new Error('Liability is required');

      const response = await apiClient.get<RawAmortizationEntry[]>(
        `/liabilities/${liability.id}/amortization`,
        {
          headers: buildEncryptionHeaders(),
        }
      );

      const entries = response.data;

      // Map backend field names to frontend field names
      const payments: import('@/types/liability').AmortizationPayment[] = entries.map((e) => ({
        paymentNumber: e.paymentNumber,
        paymentDate: e.paymentDate,
        paymentAmount: e.paymentAmount,
        principalPayment: e.principalPortion,  // renamed from backend
        interestPayment: e.interestPortion,    // renamed from backend
        remainingBalance: e.remainingBalance,
      }));

      const totalInterest = payments.reduce((sum, p) => sum + p.interestPayment, 0);
      const totalAmount = payments.reduce((sum, p) => sum + p.paymentAmount, 0);
      const monthlyPayment = payments.length > 0 ? payments[0].paymentAmount : 0;

      return {
        liabilityId: liability.id,
        liabilityName: liability.name,
        principal: liability.principal,
        interestRate: liability.interestRate ?? 0,
        termMonths: payments.length,
        monthlyPayment,
        totalPayments: payments.length,
        totalInterest,
        totalAmount,
        currency: liability.currency,
        payments,
      };
    },
    enabled: !!liability,
  });
}

/**
 * Fetch total liabilities summary by currency
 */
export function useLiabilityTotals() {
  return useQuery<LiabilityTotals>({
    queryKey: ['liabilities', 'totals'],
    queryFn: async () => {
      const response = await apiClient.get<LiabilityTotals>('/liabilities/totals', {
        headers: buildEncryptionHeaders(),
      });
      return response.data;
    },
  });
}

/**
 * Fetch detailed cost breakdown for a liability (Requirement 2.1)
 */
export function useLiabilityBreakdown(liabilityId: number | null) {
  return useQuery<LiabilityBreakdown>({
    queryKey: ['liabilities', liabilityId, 'breakdown'],
    queryFn: async () => {
      if (!liabilityId) throw new Error('Liability ID is required');

      const response = await apiClient.get<LiabilityBreakdown>(
        `/liabilities/${liabilityId}/breakdown`,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    enabled: !!liabilityId,
  });
}

/**
 * Fetch transactions linked to a specific liability (Requirement 3.2)
 */
export function useLiabilityTransactions(liabilityId: number | null) {
  return useQuery<Transaction[]>({
    queryKey: ['liabilities', liabilityId, 'transactions'],
    queryFn: async () => {
      if (!liabilityId) throw new Error('Liability ID is required');

      const response = await apiClient.get<Transaction[]>(
        `/liabilities/${liabilityId}/transactions`,
        {
          headers: buildEncryptionHeaders(),
        }
      );
      return response.data;
    },
    enabled: !!liabilityId,
  });
}

/**
 * Get user-friendly liability type name
 */
export const getLiabilityTypeName = (type: string): string => {
  const key = `liabilities:types.${type}`;
  const translated = i18next.t(key);
  return translated !== key ? translated : type;
};

/**
 * Get badge color variant for liability type
 */
export const getLiabilityTypeBadgeVariant = (
  type: string
): 'default' | 'success' | 'info' | 'warning' | 'error' => {
  const variantMap: Record<string, 'default' | 'success' | 'info' | 'warning' | 'error'> = {
    MORTGAGE: 'info',
    LOAN: 'default',
    CREDIT_CARD: 'error',
    STUDENT_LOAN: 'warning',
    AUTO_LOAN: 'info',
    PERSONAL_LOAN: 'default',
    OTHER: 'default',
  };
  return variantMap[type] || 'default';
};

/**
 * Calculate total interest for a liability
 */
export const calculateTotalInterest = (
  _principal: number,
  currentBalance: number,
  interestRate: number | undefined,
  monthsRemaining: number
): number => {
  if (!interestRate || interestRate === 0 || monthsRemaining === 0) {
    return 0;
  }

  // Simple interest calculation for estimation
  // For exact calculation, use the backend amortization endpoint
  const monthlyRate = interestRate / 100 / 12;
  const monthlyPayment = (currentBalance * monthlyRate) / (1 - Math.pow(1 + monthlyRate, -monthsRemaining));
  const totalPayments = monthlyPayment * monthsRemaining;

  return totalPayments - currentBalance;
};

/**
 * Calculate months remaining until end date
 */
export const calculateMonthsRemaining = (endDate: string | undefined): number => {
  if (!endDate) return 0;

  const end = new Date(endDate);
  const now = new Date();
  const months = (end.getFullYear() - now.getFullYear()) * 12 + (end.getMonth() - now.getMonth());

  return Math.max(0, months);
};

/**
 * Format currency with appropriate symbol
 */
export const formatCurrency = (amount: number, currency: string = 'USD'): string => {
  const symbols: Record<string, string> = {
    USD: '$',
    EUR: '€',
    GBP: '£',
    JPY: '¥',
    BTC: '₿',
    ETH: 'Ξ',
  };

  const symbol = symbols[currency] || currency + ' ';
  const formatted = Math.abs(amount).toFixed(2);

  if (amount < 0) {
    return `-${symbol}${formatted}`;
  }
  return `${symbol}${formatted}`;
};
