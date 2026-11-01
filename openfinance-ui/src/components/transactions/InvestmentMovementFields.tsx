import { useTranslation } from 'react-i18next';
import { NumberInput } from '@/components/ui/NumberInput';
import { Button } from '@/components/ui/Button';
import { decimalField } from '@/utils/exact-decimal';
import { formatDate } from '@/utils/date';
import { formatExactNumber } from '@/utils/format';
import type { Asset } from '@/types/asset';
import type { InvestmentMovementRequest, InvestmentQuote } from '@/types/transaction';

interface Props {
  value: InvestmentMovementRequest | null;
  onChange: (value: InvestmentMovementRequest | null) => void;
  operation: InvestmentMovementRequest['operation'];
  accountId?: number;
  assets: Asset[];
  originalAssetName?: string;
  quote?: InvestmentQuote;
  quoteLoading: boolean;
  quoteError: boolean;
  savedQuote: boolean;
  error?: string;
  disabled?: boolean;
}

const selectClass =
  'h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50';

export function InvestmentMovementFields({
  value,
  onChange,
  operation,
  accountId,
  assets,
  originalAssetName,
  quote,
  quoteLoading,
  quoteError,
  savedQuote,
  error,
  disabled,
}: Props) {
  const { t } = useTranslation('transactions');
  const eligible = assets.filter(
    asset =>
      asset.accountId === accountId &&
      !asset.isPhysical &&
      !['REAL_ESTATE', 'VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE'].includes(
        asset.type
      ) &&
      asset.acquisitionType !== 'PLANNED'
  );
  const missingSelection = value && !eligible.some(asset => asset.id === value.assetId);
  const selected = assets.find(asset => asset.id === value?.assetId);
  return (
    <div className="space-y-4">
      <div className="space-y-1.5">
        <label htmlFor="investmentAsset" className="block text-sm font-medium text-text-secondary">
          {t('investment.holding')}
        </label>
        <select
          id="investmentAsset"
          className={selectClass}
          value={value?.assetId ?? ''}
          disabled={disabled}
          aria-invalid={!value && !!error}
          aria-describedby={!value && error ? 'investment-error' : undefined}
          onChange={event =>
            onChange(
              event.target.value
                ? {
                    assetId: Number(event.target.value),
                    operation,
                    pricing: 'QUANTITY',
                    quantity: '',
                  }
                : null
            )
          }
        >
          <option value="">{t('linkedOperation.selectHolding')}</option>
          {missingSelection && (
            <option value={value.assetId}>
              {selected?.name ?? originalAssetName ?? t('investment.linkedHolding')}
            </option>
          )}
          {eligible.map(asset => (
            <option key={asset.id} value={asset.id}>
              {asset.name}
              {asset.symbol ? ` (${asset.symbol})` : ''}
            </option>
          ))}
        </select>
        {!eligible.length && !value && (
          <p className="text-sm text-text-secondary">{t('investment.noHoldings')}</p>
        )}
      </div>
      {value && (
        <>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label
                htmlFor="investmentPricing"
                className="block text-sm font-medium text-text-secondary"
              >
                {t('investment.quantityMethod')}
              </label>
              <select
                id="investmentPricing"
                className={selectClass}
                value={value.pricing}
                disabled={disabled}
                onChange={event =>
                  onChange({
                    ...value,
                    pricing: event.target.value as InvestmentMovementRequest['pricing'],
                  })
                }
              >
                <option value="QUANTITY">{t('investment.manual')}</option>
                <option value="HISTORICAL">{t('investment.historical')}</option>
              </select>
            </div>
            {value.pricing === 'QUANTITY' && (
              <div className="space-y-1.5">
                <label
                  htmlFor="investmentQuantity"
                  className="block text-sm font-medium text-text-secondary"
                >
                  {t('investment.quantity')}
                </label>
                <NumberInput
                  id="investmentQuantity"
                  value={value.quantity ?? ''}
                  onChange={quantity => onChange({ ...value, quantity })}
                  min="0"
                  disabled={disabled}
                  aria-invalid={!!error}
                  aria-describedby={error ? 'investment-error' : undefined}
                />
              </div>
            )}
          </div>
          {value.pricing === 'HISTORICAL' && (
            <div className="space-y-2" aria-live="polite">
              <p className="text-sm text-text-secondary">{t('investment.estimateHint')}</p>
              {quoteLoading && (
                <p className="text-sm text-text-secondary" role="status">
                  {t('investment.loadingPrice')}
                </p>
              )}
              {quoteError && (
                <p className="text-sm text-error" role="alert">
                  {t('investment.priceUnavailable')}
                </p>
              )}
              {quote && !quoteError && (
                <>
                  <dl className="grid grid-cols-[minmax(0,1fr)_minmax(0,2fr)] gap-x-4 gap-y-1.5 text-sm">
                    <dt className="text-text-secondary">{t('investment.estimatedQuantity')}</dt>
                    <dd className="break-all text-right font-mono tabular-nums text-text-primary">
                      {formatExactNumber(decimalField(quote, 'quantity'))}
                    </dd>
                    {['REMOVE', 'SELL'].includes(value.operation) &&
                      quote.availableQuantity != null && (
                        <>
                          <dt className="text-text-secondary">
                            {t('investment.availableQuantity')}
                          </dt>
                          <dd className="break-all text-right font-mono tabular-nums text-text-primary">
                            {formatExactNumber(decimalField(quote, 'availableQuantity'))}
                          </dd>
                        </>
                      )}
                    <dt className="text-text-secondary">{t('investment.unitPrice')}</dt>
                    <dd className="break-all text-right font-mono tabular-nums text-text-primary">
                      {formatExactNumber(decimalField(quote, 'price'))} {quote.currency}
                    </dd>
                    <dt className="text-text-secondary">{t('investment.priceDate')}</dt>
                    <dd className="text-right text-text-primary">{formatDate(quote.priceDate)}</dd>
                  </dl>
                  <p className="text-xs text-text-secondary">
                    {t(savedQuote ? 'investment.savedEstimate' : 'investment.priceSource')}
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={disabled}
                    onClick={() =>
                      onChange({
                        ...value,
                        pricing: 'QUANTITY',
                        quantity: decimalField(quote, 'quantity'),
                      })
                    }
                  >
                    {t('investment.overrideQuantity')}
                  </Button>
                </>
              )}
            </div>
          )}
          {value.pricing === 'QUANTITY' &&
            ['REMOVE', 'SELL'].includes(value.operation) &&
            quote?.availableQuantity != null && (
              <p className="text-sm text-text-secondary">
                {t('investment.availableQuantity')}:{' '}
                <span className="break-all font-mono tabular-nums text-text-primary">
                  {formatExactNumber(decimalField(quote, 'availableQuantity'))}
                </span>
              </p>
            )}
        </>
      )}
      {error && (
        <p id="investment-error" className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
