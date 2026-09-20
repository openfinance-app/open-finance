import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { format, subMonths } from 'date-fns';
import apiClient from '@/services/apiClient';
import { useAuthContext } from '@/context/AuthContext';
import type { Asset } from '@/types/asset';
import { sum, multiply, divide } from '@/utils/money';

interface UserFinancialData {
  totalSavings: number;
  averageMonthlyExpenses: number;
  currency: string;
}

/** Holdings in the reporting currency and dated cash expenses over the last six months. */
export const useUserFinancialData = () => {
  const { baseCurrency, user } = useAuthContext();
  const { t } = useTranslation('tools');
  const query = useQuery<UserFinancialData>({
    queryKey: ['userFinancialData', user?.id, baseCurrency],
    enabled: user != null,
    queryFn: async () => {
      const today = new Date();
      const [assetsResponse, cashFlowResponse] = await Promise.all([
        apiClient.get<Asset[]>('/assets'),
        apiClient.get<{ expenses: number }>('/dashboard/cashflow', {
          params: {
            startDate: format(subMonths(today, 6), 'yyyy-MM-dd'),
            endDate: format(today, 'yyyy-MM-dd'),
          },
        }),
      ]);
      const values = assetsResponse.data
        .filter(asset => asset.acquisitionType !== 'PLANNED')
        .map(asset => {
          if (asset.currency === baseCurrency) {
            return asset.totalValue ?? multiply(asset.quantity, asset.currentPrice);
          }
          if (
            asset.baseCurrency === baseCurrency &&
            asset.isConverted &&
            asset.valueInBaseCurrency != null
          ) {
            return asset.valueInBaseCurrency;
          }
          throw new Error(t('financialData.conversionUnavailable'));
        });
      return {
        totalSavings: sum(values),
        averageMonthlyExpenses: divide(cashFlowResponse.data.expenses, 6),
        currency: baseCurrency,
      };
    },
  });
  return {
    data: query.isError ? null : (query.data ?? null),
    isLoading: query.isPending || query.isFetching,
    error: query.error?.message ?? null,
    refetch: async () => {
      await query.refetch();
    },
  };
};
