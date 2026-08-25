/**
 * BudgetForm Component
 * TASK-8.2.8: Create BudgetForm component with validation
 * 
 * Form for creating and editing budgets with Zod validation
 */
import { useEffect, useMemo } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { useAuthContext } from '@/context/AuthContext';
import { isValidDecimalString } from '@/utils/money';
import type { BudgetRequest, BudgetResponse, BudgetPeriod } from '@/types/budget';

const BUDGET_PERIODS: BudgetPeriod[] = ['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY'];

function createBudgetSchema(t: (key: string) => string) {
  return z
    .object({
      categoryId: z.number().min(1, t('validation.categoryRequired')),
      amount: z.string().min(1, t('validation.amountInvalid')).refine(isValidDecimalString, t('validation.amountInvalid')).refine((v) => Number(v) > 0, t('validation.amountPositive')),
      currency: z.string().length(3, t('validation.currencyRequired')),
      period: z.enum(['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']),
      startDate: z.string().min(1, t('validation.startDateRequired')),
      endDate: z.string().min(1, t('validation.endDateRequired')),
      rollover: z.boolean(),
      notes: z.string().max(500, t('validation.notesTooLong')).optional(),
    })
    .refine(
      (data) => {
        const start = new Date(data.startDate);
        const end = new Date(data.endDate);
        // end must be at least 1 day after start (same-day not allowed)
        return end.getTime() - start.getTime() >= 86400000;
      },
      { message: t('validation.endDateAfterStart'), path: ['endDate'] }
    );
}

type BudgetFormData = z.infer<ReturnType<typeof createBudgetSchema>>;

interface BudgetFormProps {
  budget?: BudgetResponse;
  onSubmit: (data: BudgetRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
  serverError?: string | null;
}

export function BudgetForm({ budget, onSubmit, onCancel, isLoading, serverError }: BudgetFormProps) {
  const isEditing = !!budget;
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('budgets');

  const budgetSchema = useMemo(() => createBudgetSchema(t), [t]);

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    formState: { errors },
  } = useForm<BudgetFormData>({
    resolver: zodResolver(budgetSchema),
    reValidateMode: 'onChange',
    values: budget
      ? {
        categoryId: budget.categoryId,
        amount: String(budget.amount),
        currency: budget.currency,
        period: budget.period,
        startDate: budget.startDate,
        endDate: budget.endDate,
        rollover: budget.rollover,
        notes: budget.notes || '',
      }
      : {
        categoryId: 0,
        amount: '0',
        currency: baseCurrency,
        period: 'MONTHLY',
        startDate: new Date().toISOString().split('T')[0],
        endDate: '',
        rollover: false,
        notes: '',
      },
  });

  const watchedPeriod = watch('period');
  const watchedStartDate = watch('startDate');

  // Auto-compute end date from start date + period (only for new budgets)
  useEffect(() => {
    if (isEditing || !watchedStartDate) return;
    const start = new Date(watchedStartDate);
    if (isNaN(start.getTime())) return;

    const end = new Date(start);
    switch (watchedPeriod) {
      case 'WEEKLY':
        end.setDate(end.getDate() + 7);
        break;
      case 'MONTHLY':
        end.setMonth(end.getMonth() + 1);
        break;
      case 'QUARTERLY':
        end.setMonth(end.getMonth() + 3);
        break;
      case 'YEARLY':
        end.setFullYear(end.getFullYear() + 1);
        break;
    }
    setValue('endDate', end.toISOString().split('T')[0], { shouldValidate: true });
  }, [watchedPeriod, watchedStartDate, isEditing, setValue]);

  const handleFormSubmit = handleSubmit((data: BudgetFormData) => {
    onSubmit({
      categoryId: data.categoryId,
      amount: data.amount.trim(),
      currency: data.currency,
      period: data.period,
      startDate: data.startDate,
      endDate: data.endDate,
      rollover: data.rollover,
      notes: data.notes || undefined,
    });
  });

  return (
    <form onSubmit={handleFormSubmit} className="space-y-4">
      {serverError && (
        <div className="p-3 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
          {serverError}
        </div>
      )}
      {/* Row 1: Category and Period */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Category */}
        <div>
          <label htmlFor="categoryId" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.category')} *
          </label>
          <Controller
            name="categoryId"
            control={control}
            render={({ field }) => (
              <CategorySelect
                value={field.value || undefined}
                onValueChange={(value) => field.onChange(value ?? 0)}
                placeholder={t('form.categoryPlaceholder')}
                searchPlaceholder={t('form.categorySearchPlaceholder')}
                type="EXPENSE"
                allowNone={false}
                className="w-full"
              />
            )}
          />
          {errors.categoryId && (
            <p className="mt-1 text-sm text-error">{errors.categoryId.message}</p>
          )}
        </div>

        {/* Period */}
        <div>
          <label htmlFor="period" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.budgetPeriod')} *
          </label>
          <select
            id="period"
            {...register('period')}
            className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {BUDGET_PERIODS.map((period) => (
              <option key={period} value={period}>
                {t(`form.periods.${period}`)}
              </option>
            ))}
          </select>
          {errors.period && <p className="mt-1 text-sm text-error">{errors.period.message}</p>}
        </div>
      </div>

      {/* Row 2: Amount and Currency */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Amount */}
        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.budgetAmount')} *
          </label>
          <Controller
            name="amount"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="amount"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="0.00"
                error={errors.amount?.message}
                min="0"
              />
            )}
          />
        </div>

        {/* Currency */}
        <div>
          <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.currency')} *
          </label>
          <Controller
            name="currency"
            control={control}
            render={({ field }) => (
              <CurrencySelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.currency')}
                className="w-full"
              />
            )}
          />
          {errors.currency && (
            <p className="mt-1 text-sm text-error">{errors.currency.message}</p>
          )}
        </div>
      </div>

      {/* Row 3: Date Range and Rollover */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label htmlFor="startDate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.startDate')} *
          </label>
          <Controller
            name="startDate"
            control={control}
            render={({ field }) => (
              <DateInput
                id="startDate"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.startDate?.message}
              />
            )}
          />
        </div>
        <div>
          <label htmlFor="endDate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.endDate')} *
          </label>
          <Controller
            name="endDate"
            control={control}
            render={({ field }) => (
              <DateInput
                id="endDate"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.endDate?.message}
              />
            )}
          />
        </div>
      </div>

      {/* Rollover checkbox */}
      <div className="flex items-center gap-2 pt-2">
        <input
          id="rollover"
          type="checkbox"
          {...register('rollover')}
          className="h-4 w-4 rounded border-border bg-surface text-primary focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background"
        />
        <label htmlFor="rollover" className="text-sm text-text-primary">
          {t('form.rollover')}
        </label>
      </div>

      {/* Notes */}
      <div>
        <label htmlFor="notes" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.notes')}
        </label>
        <textarea
          id="notes"
          {...register('notes')}
          rows={3}
          placeholder={t('form.notesPlaceholder')}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
        />
        {errors.notes && <p className="mt-1 text-sm text-error">{errors.notes.message}</p>}
      </div>

      {/* Actions */}
      <div className="flex justify-end gap-3 pt-4">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button variant="primary" type="submit" isLoading={isLoading}>
          {isEditing ? t('form.updateBudget') : t('form.createBudget')}
        </Button>
      </div>
    </form>
  );
}
