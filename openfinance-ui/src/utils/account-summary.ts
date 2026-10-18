import type { Account } from '@/types/account';
import { sum } from '@/utils/money';

/** Never add native amounts from different currencies or report a partial conversion as total. */
export function accountBalanceTotal(accounts: Account[], currency: string): number | null {
  const amounts = accounts.map(account =>
    account.currency === currency
      ? account.balance
      : account.baseCurrency === currency && account.isConverted
        ? account.balanceInBaseCurrency
        : undefined
  );
  if (amounts.some(amount => amount == null || !Number.isFinite(amount))) return null;
  return sum(amounts as number[]);
}
