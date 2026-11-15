import type { Account } from '@/types/account';
import { decimalValue, ExactDecimal, exactSum } from '@/utils/exact-decimal';

/** Never add native amounts from different currencies or report a partial conversion as total. */
export function accountBalanceTotal(accounts: Account[], currency: string): string | null {
  const amounts = accounts.map(account =>
    account.currency === currency
      ? decimalValue(account, 'balance')
      : account.baseCurrency === currency && account.isConverted
        ? decimalValue(account, 'balanceInBaseCurrency')
        : undefined
  );
  if (amounts.some(amount => amount == null || !new ExactDecimal(amount).isFinite())) return null;
  return exactSum(amounts as (number | string)[]);
}
