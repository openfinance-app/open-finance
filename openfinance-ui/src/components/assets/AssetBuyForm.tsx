import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { NumberInput } from '@/components/ui/NumberInput';
import { DateInput } from '@/components/ui/DateInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useOwnershipOperation } from '@/hooks/useOwnershipOperation';
import { ExactDecimal } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';
import { getToday } from '@/utils/date';
import { apiErrorMessage } from '@/utils/api-error';
import type { SaleTarget } from '@/components/assets/AssetSaleForm';

/** Cash and units are reviewed together; a stable receipt makes uncertain retries safe. */
export function AssetBuyForm({ target, onClose }: { target: SaleTarget; onClose: () => void }) {
  const { t } = useTranslation('assets');
  const [units, setUnits] = useState('');
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(getToday());
  const [operationId] = useState(() => crypto.randomUUID());
  const [pending, setPending] = useState<object>();
  const buy = useOwnershipOperation();
  const valid =
    isValidDecimalString(units) &&
    new ExactDecimal(units).gt(0) &&
    isValidDecimalString(amount) &&
    new ExactDecimal(amount).gt(0) &&
    date >= target.purchaseDate &&
    date <= getToday();
  return (
    <form
      className="w-full space-y-4 border-t border-border pt-4"
      onSubmit={async event => {
        event.preventDefault();
        if (!valid || buy.isPending) return;
        const data = pending ?? { operationId, date, quantity: units, amount };
        setPending(data);
        try {
          await buy.mutateAsync({ path: `/assets/${target.id}/buy`, data });
          onClose();
        } catch (error) {
          if (
            isAxiosError(error) &&
            error.response &&
            error.response.status >= 400 &&
            error.response.status < 500
          )
            setPending(undefined);
        }
      }}
    >
      <h3 className="font-medium">{t('ownership.buyMore')}</h3>
      <p className="text-sm text-text-secondary">{t('ownership.buyMoreHint')}</p>
      <fieldset disabled={!!pending || buy.isPending} className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="buy-units" className="mb-1.5 block text-sm">
            {t('ownership.buyUnits')}
          </label>
          <NumberInput
            id="buy-units"
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            value={units}
            onChange={setUnits}
            min="0"
          />
        </div>
        <div>
          <label htmlFor="buy-amount" className="mb-1.5 block text-sm">
            {t('ownership.buyAmount', { currency: target.currency })}
          </label>
          <NumberInput
            id="buy-amount"
            className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
            value={amount}
            onChange={setAmount}
            min="0"
          />
        </div>
        <div>
          <label htmlFor="buy-date" className="mb-1.5 block text-sm">
            {t('ownership.buyDate')}
          </label>
          <DateInput
            id="buy-date"
            value={date}
            onChange={setDate}
            min={target.purchaseDate}
            max={getToday()}
          />
        </div>
      </fieldset>
      {valid && (
        <dl className="grid grid-cols-2 gap-2 text-sm [&>dd]:text-right">
          <dt>{t('ownership.cashChange')}</dt>
          <dd>
            <ConvertedAmount
              className="font-mono tabular-nums"
              amount={-Number(amount)}
              currency={target.currency}
              inline
            />
          </dd>
          <dt>{t('ownership.unitsAfter')}</dt>
          <dd className="font-mono tabular-nums">
            {new ExactDecimal(target.quantity).plus(units).toFixed()}
          </dd>
        </dl>
      )}
      {buy.error && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(buy.error, t('ownership.error'))}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <Button type="button" variant="secondary" disabled={buy.isPending} onClick={onClose}>
          {t('ownership.cancel')}
        </Button>
        <Button type="submit" disabled={!valid} isLoading={buy.isPending}>
          {t('ownership.confirmPurchase')}
        </Button>
      </div>
    </form>
  );
}
