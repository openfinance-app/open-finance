import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import type { Account } from '@/types/account';
import { multiply } from '@/utils/money';

/** Keep portfolio value distinct from the cash available for a payment. */
export function AccountBalanceBreakdown({ account }: { account: Account }) {
  const { t } = useTranslation('accounts');
  if (account.type !== 'INVESTMENT' && !account.holdingsValue) return null;
  return (
    <dl className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm text-text-secondary">
      {(
        [
          ['cash', account.ownBalance],
          ['holdings', account.holdingsValue ?? 0],
        ] as const
      ).map(([key, amount]) => (
        <div key={key} className="flex flex-wrap items-baseline gap-x-2">
          <dt>{t(`card.${key}`)}</dt>
          <dd className="font-mono">
            <ConvertedAmount
              amount={amount}
              currency={account.currency}
              convertedAmount={
                account.exchangeRate != null ? multiply(amount, account.exchangeRate) : undefined
              }
              baseCurrency={account.baseCurrency}
              exchangeRate={account.exchangeRate}
              isConverted={account.isConverted}
              secondaryAmount={
                account.secondaryExchangeRate != null
                  ? multiply(amount, account.secondaryExchangeRate)
                  : undefined
              }
              secondaryCurrency={account.secondaryCurrency}
              inline
            />
          </dd>
        </div>
      ))}
    </dl>
  );
}
