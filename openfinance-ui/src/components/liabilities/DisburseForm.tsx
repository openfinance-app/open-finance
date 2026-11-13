/**
 * DisburseForm Component (tranche drawdown action)
 *
 * Shared form behind the per-tranche "Draw" action (TrancheDrawdownsTab) and
 * the generic "Disburse" button on the staged funding tab. Posts to the
 * liability disbursement endpoint via useDisburseLiability: the funds go
 * either to one of the user's accounts (toAccountId) or directly to the
 * seller of the linked property (directRealEstateId). When no tranche is
 * passed the backend auto-picks (or creates) the next tranche.
 */
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import { useAssets } from '@/hooks/useAssets';
import { apiErrorMessage } from '@/utils/api-error';
import { ExactDecimal } from '@/utils/exact-decimal';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useDisburseLiability } from '@/hooks/useLiabilities';
import { useAccounts } from '@/hooks/useAccounts';
import { getToday } from '@/utils/date';
import { isValidDecimalString } from '@/utils/money';
import { decimalField } from '@/utils/exact-decimal';
import type { Liability, LiabilityTranche } from '@/types/liability';

type DisburseRoute = 'account' | 'property';

interface DisburseFormProps {
  liability: Liability;
  /** Tranche to draw; omit for a generic disbursement (backend picks the next tranche). */
  tranche?: LiabilityTranche;
  onDone: () => void;
}

export function DisburseForm({ liability, tranche, onDone }: DisburseFormProps) {
  const { t } = useTranslation('liabilities');
  const disburse = useDisburseLiability();
  const { data: accounts = [] } = useAccounts('active');
  const { data: assets = [] } = useAssets();
  const { data: links = [] } = useQuery<{ assetId: number; relationship: string }[]>({
    queryKey: ['assetFinancing', 'liability', liability.id],
    queryFn: async () => (await apiClient.get(`/liabilities/${liability.id}/asset-links`)).data,
  });
  const eligibleAssets = assets.filter(
    asset =>
      (asset.isPhysical || asset.type === 'REAL_ESTATE') &&
      asset.acquisitionType !== 'PLANNED' &&
      !asset.soldDate &&
      asset.isActive !== false &&
      asset.currency === liability.currency &&
      links.some(link => link.assetId === asset.id && link.relationship === 'FINANCING')
  );
  const [selectedAssetId, setSelectedAssetId] = useState<number | undefined>(
    tranche?.assetId ?? undefined
  );
  const selectedAsset = eligibleAssets.find(asset => asset.id === selectedAssetId);

  const linkedPropertyId = tranche?.realEstateId ?? liability.linkedPropertyId ?? null;
  const linkedPropertyAsset = eligibleAssets.find(asset => asset.realEstateId === linkedPropertyId);
  // Amount prefilled with the remaining planned amount of the tranche.
  const [amount, setAmount] = useState(
    tranche
      ? decimalField(
          tranche,
          tranche.status === 'PLANNED' || tranche.remaining == null ? 'plannedAmount' : 'remaining'
        )
      : ExactDecimal.max(
          new ExactDecimal(
            decimalField(
              liability,
              liability.approvedAmount == null ? 'principal' : 'approvedAmount'
            )
          ).minus(
            decimalField(
              liability,
              liability.fundedAmount == null ? 'currentBalance' : 'fundedAmount'
            )
          ),
          0
        ).toFixed()
  );
  const [date, setDate] = useState(getToday());
  const [route, setRoute] = useState<DisburseRoute>('account');
  const [accountId, setAccountId] = useState<number | undefined>(undefined);
  const [capitalizePropertyCost, setCapitalizePropertyCost] = useState(false);
  const [increasePropertyValue, setIncreasePropertyValue] = useState(false);
  const directPropertyId =
    route === 'property'
      ? (selectedAsset?.realEstateId ?? (selectedAsset ? undefined : linkedPropertyId))
      : undefined;
  const addsPropertyCost = directPropertyId != null && capitalizePropertyCost;
  const addsPropertyValue = addsPropertyCost && increasePropertyValue;
  const firstDrawDate = liability.balanceStartDate ?? liability.startDate;
  const available = ExactDecimal.max(
    new ExactDecimal(
      decimalField(liability, liability.approvedAmount == null ? 'principal' : 'approvedAmount')
    ).minus(
      decimalField(liability, liability.fundedAmount == null ? 'currentBalance' : 'fundedAmount')
    ),
    0
  );
  const maximum = tranche
    ? ExactDecimal.min(available, decimalField(tranche, 'plannedAmount'))
    : available;

  const numericAmount = Number(amount);
  const valid =
    isValidDecimalString(amount) &&
    numericAmount > 0 &&
    new ExactDecimal(amount).lte(maximum) &&
    date >= firstDrawDate &&
    date <= getToday() &&
    (route === 'property'
      ? selectedAsset != null || linkedPropertyId != null
      : accounts.some(
          account => account.id === accountId && account.currency === liability.currency
        ));

  const submit = async () => {
    if (!valid) return;
    try {
      await disburse.mutateAsync({
        liabilityId: Number(liability.id),
        request: {
          trancheId: tranche?.id,
          amount: amount.trim(),
          date,
          toAccountId: route === 'account' ? accountId : undefined,
          directRealEstateId: directPropertyId ?? undefined,
          directAssetId:
            route === 'property' && selectedAsset && selectedAsset.type !== 'REAL_ESTATE'
              ? selectedAsset.id
              : undefined,
          capitalizePropertyCost: addsPropertyCost,
          increasePropertyValue: addsPropertyValue,
        },
      });
      onDone();
    } catch {
      // The mutation surfaces its error state; the form stays open for a retry.
    }
  };

  const selectClass =
    'w-full h-11 md:h-10 px-3 rounded-lg bg-surface border border-border text-text-primary text-base md:text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150';

  return (
    <form
      noValidate
      onSubmit={event => {
        event.preventDefault();
        void submit();
      }}
      className="p-4 mb-3 border border-border rounded-lg bg-surface space-y-3 text-left"
      data-testid="disburse-form"
    >
      <p className="text-sm font-medium text-text-primary">{t('drawdowns.disburseForm.title')}</p>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label htmlFor="disburse-amount" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.amount')} *
          </label>
          <NumberInput
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            id="disburse-amount"
            value={amount}
            onChange={setAmount}
            placeholder="0.00"
            min="0"
            error={
              isValidDecimalString(amount) && new ExactDecimal(amount).gt(maximum)
                ? t('drawdowns.disburseForm.amountLimit')
                : undefined
            }
          />
        </div>
        <div>
          <label htmlFor="disburse-date" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.date')}
          </label>
          <DateInput
            id="disburse-date"
            value={date}
            onChange={setDate}
            min={firstDrawDate}
            max={getToday()}
          />
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label htmlFor="disburse-route" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.route')}
          </label>
          <select
            id="disburse-route"
            value={route}
            onChange={e => {
              setRoute(e.target.value as DisburseRoute);
              setCapitalizePropertyCost(false);
              setIncreasePropertyValue(false);
            }}
            className={selectClass}
          >
            <option value="account">{t('drawdowns.disburseForm.routeAccount')}</option>
            {(linkedPropertyId != null || eligibleAssets.length > 0) && (
              <option value="property">{t('drawdowns.disburseForm.routeProperty')}</option>
            )}
          </select>
        </div>
        {route === 'account' && (
          <div>
            <label htmlFor="disburse-account" className="block text-sm font-medium mb-1.5">
              {t('drawdowns.disburseForm.account')} *
            </label>
            <select
              id="disburse-account"
              value={accountId ?? ''}
              onChange={e => setAccountId(e.target.value ? Number(e.target.value) : undefined)}
              className={selectClass}
            >
              <option value="">--</option>
              {accounts
                .filter(account => account.currency === liability.currency)
                .map(account => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))}
            </select>
          </div>
        )}
      </div>
      {route === 'property' && eligibleAssets.length > 0 && (
        <div className="space-y-1">
          <label htmlFor="disburse-target" className="block text-sm font-medium">
            {t('drawdowns.disburseForm.target')}
          </label>
          <select
            id="disburse-target"
            className={selectClass}
            value={selectedAssetId ?? linkedPropertyAsset?.id ?? ''}
            onChange={event => {
              setSelectedAssetId(Number(event.target.value) || undefined);
              setCapitalizePropertyCost(false);
              setIncreasePropertyValue(false);
            }}
          >
            {!linkedPropertyAsset && (
              <option value="">{liability.linkedPropertyName ?? t('form.noAsset')}</option>
            )}
            {eligibleAssets.map(asset => (
              <option key={asset.id} value={asset.id}>
                {asset.name}
              </option>
            ))}
          </select>
        </div>
      )}
      {directPropertyId != null ? (
        <div className="space-y-3 border-t border-border pt-3">
          <div>
            <label className="flex min-h-10 items-center gap-2 text-sm font-medium">
              <input
                type="checkbox"
                checked={capitalizePropertyCost}
                onChange={event => {
                  setCapitalizePropertyCost(event.target.checked);
                  setIncreasePropertyValue(false);
                }}
                aria-describedby="disburse-capitalized-help"
                className="h-4 w-4 shrink-0 accent-primary"
              />
              {t('drawdowns.disburseForm.capitalizeCost')}
            </label>
            <p id="disburse-capitalized-help" className="pl-6 text-xs text-text-secondary">
              {t('drawdowns.disburseForm.capitalizeCostHelp')}
            </p>
          </div>
          {capitalizePropertyCost && (
            <div className="pl-6">
              <label className="flex min-h-10 items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={increasePropertyValue}
                  onChange={event => setIncreasePropertyValue(event.target.checked)}
                  aria-describedby="disburse-value-help"
                  className="h-4 w-4 shrink-0 accent-primary"
                />
                {t('drawdowns.disburseForm.increaseValue')}
              </label>
              <p id="disburse-value-help" className="pl-6 text-xs text-text-secondary">
                {t('drawdowns.disburseForm.increaseValueHelp')}
              </p>
            </div>
          )}
        </div>
      ) : route === 'property' ? (
        <p className="text-xs text-text-secondary">{t('drawdowns.disburseForm.valuationPolicy')}</p>
      ) : null}
      {valid && (
        <dl className="grid grid-cols-2 gap-x-3 gap-y-2 border-t border-border pt-3 text-sm">
          <dt>{t('recordPayment.cashChange')}</dt>
          <dd className="text-right font-mono tabular-nums">
            <ConvertedAmount
              inline
              amount={route === 'account' ? numericAmount : 0}
              currency={liability.currency}
            />
          </dd>
          <dt>{t('drawdowns.disburseForm.debtAfter')}</dt>
          <dd className="text-right font-mono tabular-nums">
            <ConvertedAmount
              inline
              amount={new ExactDecimal(decimalField(liability, 'currentBalance'))
                .plus(amount)
                .toNumber()}
              currency={liability.currency}
            />
          </dd>
          {route === 'property' && (
            <>
              {directPropertyId != null && (
                <>
                  <dt>{t('drawdowns.disburseForm.costChange')}</dt>
                  <dd className="text-right font-mono tabular-nums">
                    <ConvertedAmount
                      inline
                      amount={addsPropertyCost ? numericAmount : 0}
                      currency={liability.currency}
                    />
                  </dd>
                </>
              )}
              <dt>{t('drawdowns.disburseForm.valueChange')}</dt>
              <dd className="text-right font-mono tabular-nums">
                <ConvertedAmount
                  inline
                  amount={addsPropertyValue ? numericAmount : 0}
                  currency={liability.currency}
                />
              </dd>
            </>
          )}
        </dl>
      )}
      {disburse.isError && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(disburse.error, t('drawdowns.disburseForm.error'))}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" type="button" onClick={onDone} disabled={disburse.isPending}>
          {t('drawdowns.disburseForm.cancel')}
        </Button>
        <Button variant="primary" type="submit" disabled={!valid} isLoading={disburse.isPending}>
          {t('drawdowns.disburseForm.submit')}
        </Button>
      </div>
    </form>
  );
}
