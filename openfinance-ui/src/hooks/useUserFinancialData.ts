import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { format, subMonths } from 'date-fns';
import apiClient from '@/services/apiClient';
import { useAuthContext } from '@/context/AuthContext';
import type { Asset } from '@/types/asset';
import type { Account } from '@/types/account';
import { sum, multiply, divide } from '@/utils/money';

interface UserFinancialData {
  totalSavings: number;
  averageMonthlyExpenses: number;
  currency: string;
}

/** Cash net of account debt plus investable holdings, excluding property and physical assets. */
export const useUserFinancialData = () => {
  const { baseCurrency, user } = useAuthContext();
  const { t } = useTranslation('tools');
  const query = useQuery<UserFinancialData>({
    queryKey: ['userFinancialData', user?.id, baseCurrency],
    enabled: user != null,
    queryFn: async () => {
      const today = new Date();
      const [assetsResponse, accountsResponse, cashFlowResponse] = await Promise.all([
        apiClient.get<Asset[]>('/assets'),
        apiClient.get<Account[]>('/accounts'),
        apiClient.get<{ expenses: number }>('/dashboard/cashflow', {
          params: {
            startDate: format(subMonths(today, 6), 'yyyy-MM-dd'),
            endDate: format(today, 'yyyy-MM-dd'),
          },
        }),
      ]);
      const values = assetsResponse.data
        .filter(
          asset =>
            asset.acquisitionType !== 'PLANNED' &&
            ['STOCK', 'ETF', 'CRYPTO', 'BOND', 'MUTUAL_FUND', 'COMMODITY'].includes(asset.type)
        )
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
      const cash = accountsResponse.data
        .filter(account => account.isActive)
        .map(account => {
          // balance includes linked holdings; ownBalance prevents counting those assets twice.
          const ownBalance = account.ownBalance;
          if (account.currency === baseCurrency) return ownBalance;
          if (account.baseCurrency === baseCurrency && account.exchangeRate != null) {
            return multiply(ownBalance, account.exchangeRate);
          }
          throw new Error(t('financialData.conversionUnavailable'));
        });
      return {
        totalSavings: Math.max(0, sum([...cash, ...values])),
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
