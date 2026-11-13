import { useId, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { useAccounts } from '@/hooks/useAccounts';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import { useCreateTransaction } from '@/hooks/useTransactions';
import { Button } from '@/components/ui/Button';
import { NumberInput } from '@/components/ui/NumberInput';
import { DateInput } from '@/components/ui/DateInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { ExactDecimal, decimalField } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';
import { apiErrorMessage } from '@/utils/api-error';
import { getToday } from '@/utils/date';
import type { Liability } from '@/types/liability';

/** Actual statement amounts, never a made-up principal/interest split from an incomplete schedule. */
export function RecordLoanPayment({ liability }: { liability: Liability }) {
  const { t } = useTranslation('liabilities');
  const { date: formatDate } = useDateFormatter();
  const [open, setOpen] = useState(false);
  const id = useId();
  const accounts = useAccounts();
  const create = useCreateTransaction();
  const earliestNewPayment = liability.balanceStartDate ?? liability.startDate;
  const eligible = (accounts.data ?? []).filter(account => account.currency === liability.currency);
  const amount = z
    .string()
    .refine(
      value => isValidDecimalString(value) && new ExactDecimal(value).gte(0),
      t('recordPayment.amountInvalid')
    );
  const schema = z
    .object({
      accountId: z
        .string()
        .refine(
          value => eligible.some(account => account.id === Number(value)),
          t('recordPayment.chooseAccount')
        ),
      amount: amount.refine(
        value => isValidDecimalString(value) && new ExactDecimal(value).gt(0),
        t('recordPayment.amountInvalid')
      ),
      principal: amount,
      date: z
        .string()
        .refine(
          value => value >= earliestNewPayment && value <= getToday(),
          t('recordPayment.dateInvalid', { date: formatDate(earliestNewPayment) })
        ),
    })
    .superRefine((value, context) => {
      if (
        isValidDecimalString(value.principal) &&
        isValidDecimalString(value.amount) &&
        (new ExactDecimal(value.principal).gt(value.amount) ||
          new ExactDecimal(value.principal).gt(decimalField(liability, 'currentBalance')))
      ) {
        context.addIssue({
          code: 'custom',
          path: ['principal'],
          message: t('recordPayment.principalInvalid'),
        });
      }
    });
  const {
    control,
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { accountId: '', amount: '', principal: '', date: getToday() },
  });
  const value = watch();
  const valid = schema.safeParse(value).success;
  if (!open)
    return liability.currentBalance > 0 ? (
      <Button
        variant="secondary"
        onClick={() => {
          create.reset();
          setOpen(true);
        }}
      >
        {t('recordPayment.title')}
      </Button>
    ) : null;
  return (
    <form
      noValidate
      className="w-full basis-full space-y-4 rounded-lg border border-primary/30 p-4"
      onSubmit={handleSubmit(async value => {
        try {
          await create.mutateAsync({
            accountId: Number(value.accountId),
            amount: value.amount,
            date: value.date,
            type: 'EXPENSE',
            currency: liability.currency,
            description: t('recordPayment.description', { name: liability.name }),
            liabilityId: liability.id,
            movementType: 'REPAYMENT',
            principalAmount: value.principal,
          });
          setOpen(false);
          reset();
        } catch {
          /* Keep actual statement amounts for correction. */
        }
      })}
    >
      <h3 className="font-semibold">{t('recordPayment.title')}</h3>
      <p className="text-sm text-text-secondary">{t('recordPayment.hint')}</p>
      <fieldset disabled={create.isPending} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="space-y-1 text-sm">
          <label className="block" htmlFor={`${id}-account`}>
            {t('recordPayment.account')}
          </label>
          <select
            id={`${id}-account`}
            {...register('accountId')}
            className="h-11 w-full rounded-lg border border-border bg-background p-2 text-base md:h-10 md:text-sm"
          >
            <option value="">{t('recordPayment.chooseAccount')}</option>
            {eligible.map(account => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-date`} className="mb-1 block text-sm">
            {t('recordPayment.date')}
          </label>
          <Controller
            name="date"
            control={control}
            render={({ field }) => (
              <DateInput
                className="h-11 text-base md:h-10 md:text-sm"
                id={`${id}-date`}
                {...field}
                min={earliestNewPayment}
                max={getToday()}
              />
            )}
          />
        </div>
        {(['amount', 'principal'] as const).map(key => (
          <div key={key}>
            <label htmlFor={`${id}-${key}`} className="mb-1 block text-sm">
              {t(`recordPayment.${key}`)}
            </label>
            <Controller
              name={key}
              control={control}
              render={({ field }) => (
                <NumberInput
                  className="h-11 font-mono text-base tabular-nums md:h-10 md:text-sm"
                  id={`${id}-${key}`}
                  {...field}
                  min={0}
                />
              )}
            />
          </div>
        ))}
      </fieldset>
      {Object.entries(errors).map(([key, error]) => (
        <p key={key} role="alert" className="text-sm text-error">
          {error.message}
        </p>
      ))}
      {valid && (
        <dl className="grid grid-cols-2 gap-2 text-sm [&>dd]:text-right">
          <dt>{t('recordPayment.cashChange')}</dt>
          <dd>
            <ConvertedAmount
              className="font-mono tabular-nums"
              inline
              amount={-Number(value.amount)}
              currency={liability.currency}
            />
          </dd>
          <dt>{t('recordPayment.debtAfter')}</dt>
          <dd>
            <ConvertedAmount
              className="font-mono tabular-nums"
              inline
              amount={new ExactDecimal(decimalField(liability, 'currentBalance'))
                .minus(value.principal)
                .toNumber()}
              currency={liability.currency}
            />
          </dd>
        </dl>
      )}
      {create.error && (
        <p role="alert" className="text-sm text-error">
          {apiErrorMessage(create.error, t('recordPayment.error'))}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={create.isPending}
          onClick={() => setOpen(false)}
        >
          {t('recordPayment.cancel')}
        </Button>
        <Button type="submit" isLoading={create.isPending}>
          {t('recordPayment.confirm')}
        </Button>
      </div>
    </form>
  );
}
