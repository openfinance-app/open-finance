import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NumberInput } from '@/components/ui/NumberInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { ExactDecimal } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';

/** Keep payment, cost basis and market-value changes distinct in the review. */
export function ImprovementValuationFields({
  amount,
  currency,
  cashEffect,
  value,
  onChange,
  error,
  disabled,
}: {
  amount: string;
  currency: string;
  cashEffect?: { amount: number | null; currency: string };
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}) {
  const { t } = useTranslation('assets');
  const id = useId();
  const [adjust, setAdjust] = useState(
    () => isValidDecimalString(value) && new ExactDecimal(value).gt(0)
  );
  const cost = isValidDecimalString(amount) ? new ExactDecimal(amount) : new ExactDecimal(0);
  const change = isValidDecimalString(value) ? new ExactDecimal(value) : new ExactDecimal(0);
  const cash = cashEffect
    ? cashEffect.amount == null
      ? null
      : new ExactDecimal(cashEffect.amount)
    : cost;
  const effects = [
    ['cashChange', cash?.negated(), cashEffect?.currency ?? currency],
    ['costChange', cost, currency],
    ['valueChange', change, currency],
  ] as const;
  return (
    <div className="space-y-3 border-t border-border pt-4">
      <label className="flex min-h-11 items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={adjust}
          disabled={disabled}
          onChange={event => {
            setAdjust(event.target.checked);
            onChange(event.target.checked ? cost.toFixed() : '0');
          }}
          className="h-4 w-4 accent-primary"
        />
        {t('costEntry.adjustValue')}
      </label>
      {adjust && (
        <div>
          <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
            {t('costEntry.valueIncrease', { currency })}
          </label>
          <NumberInput
            id={id}
            value={value}
            onChange={onChange}
            disabled={disabled}
            min="0"
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            error={error}
          />
        </div>
      )}
      <p className="text-sm text-text-secondary">{t('costEntry.independentValue')}</p>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-sm">
        {effects.map(([key, number, effectCurrency]) => (
          <div key={key} className="contents">
            <dt>{t(`costEntry.${key}`)}</dt>
            <dd className="text-right font-mono tabular-nums">
              {number ? (
                <ConvertedAmount
                  inline
                  className="font-mono tabular-nums"
                  amount={number.toNumber()}
                  currency={effectCurrency}
                />
              ) : (
                '—'
              )}
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
