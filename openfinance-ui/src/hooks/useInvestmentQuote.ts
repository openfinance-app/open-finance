import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import apiClient from '@/services/apiClient';
import { buildEncryptionHeaders } from '@/utils/encryption';
import { decimalField, equalDecimals } from '@/utils/exact-decimal';
import { validInvestmentQuantity } from '@/utils/investment-movement';
import type { InvestmentMovementRequest, InvestmentQuote, Transaction } from '@/types/transaction';

/** Saved estimates remain stable, including when the historical provider is offline. */
export function useInvestmentQuote(
  investment: InvestmentMovementRequest | null,
  amount: string,
  currency: string,
  date: string,
  transaction?: Transaction
) {
  const [settledAmount, setSettledAmount] = useState(amount);
  useEffect(() => {
    const timer = setTimeout(() => setSettledAmount(amount), 300);
    return () => clearTimeout(timer);
  }, [amount]);
  const saved =
    investment?.pricing === 'HISTORICAL' &&
    transaction?.investmentPriceSource === 'HISTORICAL_CLOSE' &&
    investment.assetId === transaction.assetId &&
    date === transaction.investmentPriceDate &&
    currency === (transaction.originalCurrency ?? transaction.currency) &&
    equalDecimals(
      amount,
      decimalField(transaction, transaction.originalAmount == null ? 'amount' : 'originalAmount')
    ) &&
    transaction.investmentPrice != null &&
    transaction.investmentValue != null;
  const stored: InvestmentQuote | undefined = saved
    ? {
        assetId: investment.assetId,
        quantity: decimalField(transaction, 'investmentQuantity').replace(/^-/, ''),
        price: decimalField(transaction, 'investmentPrice'),
        value: decimalField(transaction, 'investmentValue'),
        currency: transaction.investmentCurrency ?? transaction.currency,
        priceDate: transaction.investmentPriceDate!,
        source: transaction.investmentPriceSource!,
      }
    : undefined;
  const query = useQuery<InvestmentQuote>({
    queryKey: ['investmentQuote', investment?.assetId, amount, currency, date, transaction?.id],
    enabled:
      !stored &&
      investment?.pricing === 'HISTORICAL' &&
      equalDecimals(settledAmount, amount) &&
      validInvestmentQuantity(amount) &&
      !!date &&
      !!currency,
    staleTime: 60_000,
    retry: false,
    queryFn: async ({ signal }) => {
      const response = await apiClient.get<InvestmentQuote>(
        `/assets/${investment!.assetId}/investment-quote`,
        {
          params: { amount, currency, date, transactionId: transaction?.id },
          headers: buildEncryptionHeaders(),
          signal,
        }
      );
      return response.data;
    },
  });
  return { ...query, data: stored ?? query.data, isSaved: !!stored };
}
