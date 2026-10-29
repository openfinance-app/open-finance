import { useId } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { isAxiosError } from 'axios';
import { useAccounts } from '@/hooks/useAccounts';
import { useCreateTransaction } from '@/hooks/useTransactions';
import { Button } from '@/components/ui/Button';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { Input } from '@/components/ui/Input';
import { SimpleSelect } from '@/components/ui/SimpleSelect';
import { NumberInput } from '@/components/ui/NumberInput';
import { DateInput } from '@/components/ui/DateInput';
import { getToday } from '@/utils/date';
import { ExactDecimal } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';

type CostTarget = { id: number; name: string; currency: string; kind: 'asset' | 'property' };

/** A focused expense form: the item and its currency are supplied by the detail view. */
export function RecordCostForm({ target, onClose }: { target: CostTarget; onClose: () => void }) {
  const { t } = useTranslation('assets');
  const id = useId();
  const accounts = useAccounts('active');
  const create = useCreateTransaction();
  const eligibleAccounts = (accounts.data ?? []).filter(a => a.currency === target.currency);
  const schema = z.object({
    accountId: z
      .string()
      .refine(
        value => eligibleAccounts.some(a => a.id === Number(value)),
        t('costEntry.accountRequired')
      ),
    description: z.string().trim().min(1, t('costEntry.descriptionRequired')).max(255),
    amount: z
      .string()
      .refine(
        value => isValidDecimalString(value) && new ExactDecimal(value).gt(0),
        t('costEntry.amountRequired')
      ),
    date: z.string().min(1, t('costEntry.dateRequired')),
    movementType: z.enum(['MAINTENANCE', 'CAPITAL_IMPROVEMENT']),
  });
  const {
    register,
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: {
      accountId: '',
      description: '',
      amount: '',
      date: getToday(),
      movementType: 'MAINTENANCE',
    },
  });
  const submit = handleSubmit(async values => {
    try {
      await create.mutateAsync({
        accountId: Number(values.accountId),
        type: 'EXPENSE',
        amount: values.amount.trim(),
        currency: target.currency,
        date: values.date,
        description: values.description,
        movementType: values.movementType,
        ...(target.kind === 'asset' ? { assetId: target.id } : { realEstateId: target.id }),
      });
      onClose();
    } catch {
      // Keep the entered values available for correction or retry.
    }
  });

  return (
    <form
      onSubmit={submit}
      noValidate
      className="space-y-4 rounded-xl border border-primary/30 bg-surface p-4"
    >
      <div className="flex items-center gap-2">
        <h4 className="font-semibold">{t('costEntry.title', { name: target.name })}</h4>
        <HelpTooltip text={t('costEntry.hint')} />
      </div>
      <fieldset disabled={create.isPending} className="space-y-4">
        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <label htmlFor={`${id}-kind`} className="text-sm font-medium">
              {t('costEntry.kind')}
            </label>
            <HelpTooltip
              text={t(
                watch('movementType') === 'MAINTENANCE'
                  ? 'costEntry.maintenanceHint'
                  : 'costEntry.improvementHint'
              )}
            />
          </div>
          <SimpleSelect id={`${id}-kind`} {...register('movementType')}>
            <option value="MAINTENANCE">{t('costEntry.maintenance')}</option>
            <option value="CAPITAL_IMPROVEMENT">{t('costEntry.improvement')}</option>
          </SimpleSelect>
        </div>
        <div>
          <label htmlFor={`${id}-description`} className="mb-1.5 block text-sm font-medium">
            {t('costEntry.description')}
          </label>
          <Input
            id={`${id}-description`}
            {...register('description')}
            error={errors.description?.message}
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor={`${id}-amount`} className="mb-1.5 block text-sm font-medium">
              {t('costEntry.amount', { currency: target.currency })}
            </label>
            <Controller
              name="amount"
              control={control}
              render={({ field }) => (
                <NumberInput
                  id={`${id}-amount`}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  min="0"
                  error={errors.amount?.message}
                />
              )}
            />
          </div>
          <div>
            <label htmlFor={`${id}-date`} className="mb-1.5 block text-sm font-medium">
              {t('costEntry.date')}
            </label>
            <Controller
              name="date"
              control={control}
              render={({ field }) => (
                <DateInput
                  id={`${id}-date`}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  error={errors.date?.message}
                />
              )}
            />
          </div>
        </div>
        <div>
          <label htmlFor={`${id}-account`} className="mb-1.5 block text-sm font-medium">
            {t('costEntry.account')}
          </label>
          <SimpleSelect
            id={`${id}-account`}
            {...register('accountId')}
            className={errors.accountId ? 'border-error focus:ring-error' : undefined}
            aria-invalid={!!errors.accountId}
            aria-describedby={errors.accountId ? `${id}-account-error` : undefined}
          >
            <option value="">{t('costEntry.selectAccount')}</option>
            {eligibleAccounts.map(account => (
              <option key={account.id} value={account.id}>
                {account.name} · {account.currency}
              </option>
            ))}
          </SimpleSelect>
          {errors.accountId && (
            <p id={`${id}-account-error`} role="alert" className="mt-1 text-sm text-error">
              {errors.accountId.message}
            </p>
          )}
          {accounts.isError && (
            <p role="alert" className="text-sm text-error">
              {t('costEntry.accountsError')}
            </p>
          )}
          {!accounts.isLoading && !accounts.isError && eligibleAccounts.length === 0 && (
            <p role="status" className="mt-1 text-sm text-text-secondary">
              {t('costEntry.noAccount', { currency: target.currency })}{' '}
              <Link to="/accounts" className="text-primary underline">
                {t('costEntry.manageAccounts')}
              </Link>
            </p>
          )}
        </div>
        <div className="flex items-center gap-2 text-xs">
          <Link to="/transactions" className="text-primary underline">
            {t('costEntry.transactions')}
          </Link>
          <HelpTooltip text={t('costEntry.existingHint')} />
        </div>
      </fieldset>
      {create.error && (
        <p role="alert" className="text-sm text-error">
          {isAxiosError<{ message?: string }>(create.error) && create.error.response?.data.message
            ? create.error.response.data.message
            : t('costEntry.error')}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onClose} disabled={create.isPending}>
          {t('form.cancel')}
        </Button>
        <Button
          type="submit"
          isLoading={create.isPending}
          disabled={accounts.isLoading || accounts.isError || !eligibleAccounts.length}
        >
          {t('costEntry.save')}
        </Button>
      </div>
    </form>
  );
}
