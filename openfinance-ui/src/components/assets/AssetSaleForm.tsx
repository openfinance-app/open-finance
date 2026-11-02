import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { useAccounts } from '@/hooks/useAccounts';
import { useOwnershipOperation } from '@/hooks/useOwnershipOperation';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { ExactDecimal } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';
import { getToday } from '@/utils/date';
import { apiErrorMessage } from '@/utils/api-error';

export interface SaleTarget {
  id: number;
  name: string;
  currency: string;
  quantity: string;
  currentValue: string;
  purchaseDate: string;
  financial?: boolean;
  accountId?: number;
  property?: boolean;
}
export function AssetSaleForm({ target, onClose }: { target: SaleTarget; onClose: () => void }) {
  const { t } = useTranslation('assets');
  const id = useId();
  const { data: accounts = [] } = useAccounts();
  const eligible = accounts.filter(
    account =>
      account.currency === target.currency && (!target.financial || account.id === target.accountId)
  );
  const [operationId] = useState(() => crypto.randomUUID());
  const [pending, setPending] = useState<object>();
  const sale = useOwnershipOperation();
  const positive = z
    .string()
    .refine(
      value => isValidDecimalString(value) && new ExactDecimal(value).gt(0),
      t('ownership.positiveAmount')
    );
  const schema = z
    .object({
      accountId: z
        .string()
        .refine(
          value => eligible.some(account => account.id === Number(value)),
          t('ownership.chooseAccount')
        ),
      quantity: positive.refine(
        value => isValidDecimalString(value) && new ExactDecimal(value).lte(target.quantity),
        t('ownership.availableQuantity')
      ),
      proceeds: positive,
      fees: z
        .string()
        .refine(
          value => isValidDecimalString(value) && new ExactDecimal(value).gte(0),
          t('ownership.nonNegativeFees')
        ),
      date: z
        .string()
        .refine(
          value => value >= target.purchaseDate && value <= getToday(),
          t('ownership.saleDateHint')
        ),
    })
    .refine(
      value =>
        !isValidDecimalString(value.proceeds) ||
        !isValidDecimalString(value.fees) ||
        new ExactDecimal(value.proceeds).gt(value.fees),
      { path: ['fees'], message: t('ownership.feesTooHigh') }
    );
  const {
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      accountId: target.financial ? String(target.accountId ?? '') : '',
      quantity: target.quantity,
      proceeds: target.currentValue,
      fees: '0',
      date: getToday(),
    },
  });
  const values = watch();
  const parsed = schema.safeParse(values);
  const net = parsed.success ? new ExactDecimal(values.proceeds).minus(values.fees) : null;
  const submit = handleSubmit(async value => {
    if (sale.isPending) return;
    const data = pending ?? {
      ...value,
      operationId,
      accountId: Number(value.accountId),
      description: t('ownership.saleDescription', { name: target.name }),
    };
    setPending(data);
    try {
      await sale.mutateAsync({ path: `/assets/${target.id}/sale`, data });
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
  });
  return (
    <form
      onSubmit={submit}
      noValidate
      className="space-y-4 rounded-xl border border-primary/30 p-4"
    >
      <h3 className="font-semibold">{t('ownership.sellTitle', { name: target.name })}</h3>
      <p className="text-sm text-text-secondary">{t('ownership.saleHint')}</p>
      {target.property && <p className="text-sm text-warning">{t('ownership.debtRetained')}</p>}
      {target.financial && (
        <p className="text-sm text-text-secondary">{t('ownership.investmentAccount')}</p>
      )}
      <fieldset
        disabled={!!pending || sale.isPending}
        className="grid grid-cols-1 gap-4 sm:grid-cols-2"
      >
        <div className="space-y-1 text-sm">
          <label className="block" htmlFor={`${id}-account`}>
            {t('ownership.receivingAccount')}
          </label>
          <Controller
            name="accountId"
            control={control}
            render={({ field }) => (
              <select
                id={`${id}-account`}
                {...field}
                className="h-11 w-full rounded-lg border border-border bg-background p-2 text-base md:h-10 md:text-sm"
              >
                <option value="">{t('ownership.chooseAccount')}</option>
                {eligible.map(account => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))}
              </select>
            )}
          />
        </div>
        <div className="space-y-1 text-sm">
          <label className="block" htmlFor={`${id}-date`}>
            {t('ownership.saleDate')}
          </label>
          <Controller
            name="date"
            control={control}
            render={({ field }) => (
              <DateInput
                className="h-11 text-base md:h-10 md:text-sm"
                id={`${id}-date`}
                {...field}
                min={target.purchaseDate}
                max={getToday()}
              />
            )}
          />
        </div>
        {(['quantity', 'proceeds', 'fees'] as const)
          .filter(key => key !== 'quantity' || target.financial)
          .map(key => (
            <label key={key} className="space-y-1 text-sm">
              <span>{t(`ownership.${key}`)}</span>
              <Controller
                name={key}
                control={control}
                render={({ field }) => (
                  <NumberInput
                    className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
                    {...field}
                    onChange={field.onChange}
                    min={0}
                  />
                )}
              />
            </label>
          ))}
      </fieldset>
      {Object.entries(errors).map(([key, error]) => (
        <p key={key} role="alert" className="text-sm text-error">
          {error.message}
        </p>
      ))}
      {net && (
        <dl className="grid grid-cols-2 gap-2 border-t border-border pt-3 text-sm [&>dd]:text-right">
          <dt>{t('ownership.netProceeds')}</dt>
          <dd>
            <ConvertedAmount
              className="font-mono tabular-nums"
              inline
              amount={net.toNumber()}
              currency={target.currency}
            />
          </dd>
          {target.financial && (
            <>
              <dt>{t('ownership.remainingQuantity')}</dt>
              <dd className="font-mono tabular-nums">
                {new ExactDecimal(target.quantity).minus(values.quantity).toFixed()}
              </dd>
            </>
          )}
          <dt>{t('ownership.netWorthChange')}</dt>
          <dd>
            <ConvertedAmount
              className="font-mono tabular-nums"
              inline
              currency={target.currency}
              amount={net
                .minus(
                  new ExactDecimal(target.currentValue).times(values.quantity).div(target.quantity)
                )
                .toNumber()}
            />
          </dd>
        </dl>
      )}
      {sale.error && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(sale.error, t('ownership.error'))}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={sale.isPending || !!pending}
          onClick={onClose}
        >
          {t('ownership.cancel')}
        </Button>
        <Button type="submit" isLoading={sale.isPending}>
          {t('ownership.confirmSale')}
        </Button>
      </div>
    </form>
  );
}
