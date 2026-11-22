import { LockKeyhole, Wallet } from 'lucide-react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { ROUTES } from '@/constants/routes';
import { useAccounts } from '@/hooks/useAccounts';
import { useVisibility } from '@/context/VisibilityContext';
import { accountBalanceTotal } from '@/utils/account-summary';
import { ExactDecimal } from '@/utils/exact-decimal';
import { formatPercentage } from '@/utils/format';
import { percentage } from '@/utils/money';

export default function AccountLiquidityCard({ baseCurrency }: { baseCurrency: string }) {
  const { t } = useTranslation('dashboard');
  const { isAmountsVisible } = useVisibility();
  const { data: accounts, isLoading, isError } = useAccounts();
  const active = (accounts ?? []).filter(account => account.isActive);
  const available = accountBalanceTotal(
    active.filter(account => account.type !== 'LOCKED_SAVINGS'),
    baseCurrency
  );
  const locked = accountBalanceTotal(
    active.filter(account => account.type === 'LOCKED_SAVINGS'),
    baseCurrency
  );
  let availableShare: number | null = null;
  let showMeter = false;
  if (isAmountsVisible && available !== null && locked !== null) {
    const liquid = new ExactDecimal(available);
    const restricted = new ExactDecimal(locked);
    const total = liquid.plus(restricted);
    if (!total.isZero()) {
      availableShare = percentage(available, total.toFixed());
      showMeter = liquid.gte(0) && restricted.gte(0);
    }
  }

  return (
    <section
      aria-label={t('cards.accountLiquidity.label')}
      className="@container flex h-full flex-col overflow-y-auto rounded-[var(--radius-card)] border border-border bg-surface p-5"
    >
      <header className="flex items-center gap-2 pe-5">
        <h3 className="plate-label">{t('cards.accountLiquidity.label')}</h3>
        <HelpTooltip text={t('accountLiquidity.help')} />
      </header>
      {isLoading || isError ? (
        <p role="status" className="my-auto text-sm text-text-secondary">
          {t(isError ? 'accountLiquidity.loadError' : 'accountLiquidity.loading')}
        </p>
      ) : (
        <div className="my-auto pt-3">
          {showMeter && availableShare !== null && (
            <div
              role="meter"
              aria-label={t('accountLiquidity.available')}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={availableShare}
              className="mb-3 h-1.5 overflow-hidden rounded-full bg-text-tertiary"
            >
              <div className="h-full bg-primary" style={{ width: `${availableShare}%` }} />
            </div>
          )}
          <dl className="grid gap-3 @md:grid-cols-2">
            {(
              [
                ['available', Wallet, available, availableShare],
                [
                  'locked',
                  LockKeyhole,
                  locked,
                  availableShare === null ? null : 100 - availableShare,
                ],
              ] as const
            ).map(([key, Icon, value, share]) => (
              <div key={key} className="group relative min-w-0 rounded-md hover:bg-muted/50">
                <dt className="flex items-center gap-2 text-sm text-text-secondary">
                  <Icon
                    aria-hidden="true"
                    className={`h-4 w-4 shrink-0 ${key === 'available' ? 'text-primary' : 'text-text-tertiary'}`}
                  />
                  <Link
                    to={`${ROUTES.ACCOUNTS}?capital=${key}`}
                    className="underline decoration-border-strong underline-offset-4 after:absolute after:inset-0 after:rounded-md group-hover:text-primary focus-visible:outline-none focus-visible:after:ring-2 focus-visible:after:ring-primary"
                  >
                    {t(`accountLiquidity.${key}`)}
                  </Link>
                  {share !== null && (
                    <span className="font-mono text-xs tabular-nums">
                      {formatPercentage(share)}
                    </span>
                  )}
                </dt>
                <dd className="mt-1">
                  {value === null ? (
                    <span className="text-sm text-text-secondary">
                      {t('accountLiquidity.missingRate')}
                    </span>
                  ) : (
                    <ConvertedAmount
                      amount={value}
                      currency={baseCurrency}
                      className="font-mono text-2xl font-semibold tabular-nums [overflow-wrap:anywhere]"
                    />
                  )}
                </dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </section>
  );
}
