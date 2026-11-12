import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { NumberInput } from '@/components/ui/NumberInput';
import { useAuthContext } from '@/context/AuthContext';
import type { AmountFilterValue } from '@/types/amount-filter';

interface AmountFilterControlsProps extends AmountFilterValue {
  onChange: (value: AmountFilterValue) => void;
  minLabel?: string;
  maxLabel?: string;
  exactLabel?: string;
  allowNegative?: boolean;
  testIdSuffix?: string;
}

/** One currency basis for both range/exact filters and monetary ordering. */
export function AmountFilterControls({
  min,
  max,
  exact,
  amountCurrencyMode,
  onChange,
  minLabel,
  maxLabel,
  exactLabel,
  allowNegative = false,
  testIdSuffix,
}: AmountFilterControlsProps) {
  const { t } = useTranslation('common');
  const { baseCurrency } = useAuthContext();
  const id = useId();
  const updateAmount = (field: 'min' | 'max' | 'exact', canonical: string): void => {
    const value = canonical === '' ? undefined : Number(canonical);
    if (value !== undefined && (!Number.isFinite(value) || (!allowNegative && value < 0))) return;
    onChange({
      min: field === 'exact' && value !== undefined ? undefined : min,
      max: field === 'exact' && value !== undefined ? undefined : max,
      exact: field !== 'exact' && value !== undefined ? undefined : exact,
      amountCurrencyMode,
      [field]: value,
    });
  };

  return (
    <div className="col-span-full grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div className="min-w-0">
        <label
          htmlFor={`${id}-currency`}
          className="mb-1.5 block text-sm font-medium text-text-primary"
        >
          {t('amountFilter.compareIn')}
        </label>
        <select
          id={`${id}-currency`}
          value={amountCurrencyMode ?? 'BASE'}
          onChange={event =>
            onChange({
              min,
              max,
              exact,
              amountCurrencyMode: event.target.value === 'NATIVE' ? 'NATIVE' : undefined,
            })
          }
          className="h-11 w-full rounded-lg border border-border bg-background px-3 text-base text-text-primary shadow-slot transition-colors hover:border-border-strong focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background md:h-10 md:text-sm"
        >
          <option value="BASE">{t('amountFilter.baseCurrency', { currency: baseCurrency })}</option>
          <option value="NATIVE">{t('amountFilter.nativeCurrency')}</option>
        </select>
      </div>
      {(
        [
          ['min', min, minLabel ?? t('amountFilter.minimum')],
          ['max', max, maxLabel ?? t('amountFilter.maximum')],
          ['exact', exact, exactLabel ?? t('amountFilter.exact')],
        ] as const
      ).map(([field, value, label]) => (
        <div key={field} className="min-w-0">
          <label
            htmlFor={`${id}-${field}`}
            className="mb-1.5 block text-sm font-medium text-text-primary"
          >
            {label}
          </label>
          <NumberInput
            id={`${id}-${field}`}
            value={value ?? ''}
            onChange={canonical => updateAmount(field, canonical)}
            placeholder="0.00"
            min={allowNegative ? undefined : 0}
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            data-testid={testIdSuffix ? `filter-${field}-${testIdSuffix}` : undefined}
          />
        </div>
      ))}
    </div>
  );
}
