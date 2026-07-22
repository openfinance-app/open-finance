/**
 * RecurringTransactionForm Component
 * Task 12.2.9: Add recurring option to TransactionForm (standalone recurring form)
 * 
 * Form for creating/editing recurring transactions with frequency and end date options
 */
import { useEffect, useMemo } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { useAuthContext } from '@/context/AuthContext';
import { useActivePayees } from '@/hooks/usePayees';
import { useLatestExchangeRate, useCurrencyFormat } from '@/hooks/useCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { multiply } from '@/utils/money';
import type {
  RecurringTransaction,
  RecurringTransactionRequest,
  RecurringFrequency,
} from '@/types/recurringTransaction';
import type { TransactionType, Category } from '@/types/transaction';
import type { Account } from '@/types/account';

const optionalNumber = z.preprocess((value) => {
  if (value === '' || value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'number' && Number.isNaN(value)) {
    return undefined;
  }
  return value;
}, z.number().optional()).optional() as z.ZodType<number | undefined>;

// Static schema used only for TypeScript type inference
const _recurringTransactionSchemaShape = z.object({
  accountId: z.number(),
  toAccountId: optionalNumber,
  type: z.enum(['INCOME', 'EXPENSE', 'TRANSFER']),
  amount: z.number(),
  currency: z.string(),
  categoryId: optionalNumber,
  payee: z.string().optional(),
  description: z.string(),
  notes: z.string().optional(),
  frequency: z.enum(['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']),
  nextOccurrence: z.string(),
  endDate: z.string().optional(),
});

type RecurringTransactionFormData = z.infer<typeof _recurringTransactionSchemaShape>;

function createRecurringTransactionSchema(t: (key: string) => string) {
  return z.object({
    accountId: z.number({ message: t('form.validation.accountRequired') }),
    toAccountId: optionalNumber,
    type: z.enum(['INCOME', 'EXPENSE', 'TRANSFER']),
    amount: z.number().positive(t('form.validation.amountPositive')),
    currency: z.string().length(3, t('form.validation.currencyLength')),
    categoryId: optionalNumber,
    payee: z.string().optional(),
    description: z.string().min(1, t('form.validation.descriptionRequired')).max(200, t('form.validation.descriptionTooLong')),
    notes: z.string().max(1000, t('form.validation.notesTooLong')).optional(),
    frequency: z.enum(['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']),
    nextOccurrence: z.string().min(1, t('form.validation.nextOccurrenceRequired')),
    endDate: z.string().optional(),
  }).superRefine((data, ctx) => {
    if (data.type === 'TRANSFER' && !data.toAccountId) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: t('form.validation.toAccountRequired'),
        path: ['toAccountId'],
      });
    }
    if (data.endDate && data.nextOccurrence && data.endDate < data.nextOccurrence) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: t('form.validation.endDateAfterNext'),
        path: ['endDate'],
      });
    }
  });
}

interface RecurringTransactionFormProps {
  recurringTransaction?: RecurringTransaction | null;
  accounts: Account[];
  categories: Category[];
  onSubmit: (data: RecurringTransactionRequest) => void;
  onCancel: () => void;
  isSubmitting?: boolean;
  error?: string | null;
}

export function RecurringTransactionForm({
  recurringTransaction,
  accounts,
  categories,
  onSubmit,
  onCancel,
  isSubmitting,
  error,
}: RecurringTransactionFormProps) {
  const isEditing = !!recurringTransaction;
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('recurring');

  const schema = useMemo(() => createRecurringTransactionSchema(t), [t]);

  const transactionTypes: { value: TransactionType; label: string }[] = [
    { value: 'INCOME', label: t('filters.income') },
    { value: 'EXPENSE', label: t('filters.expense') },
    { value: 'TRANSFER', label: t('filters.transfer') },
  ];

  const frequencies: { value: RecurringFrequency; label: string }[] = [
    { value: 'DAILY', label: t('filters.daily') },
    { value: 'WEEKLY', label: t('filters.weekly') },
    { value: 'BIWEEKLY', label: t('filters.biweekly') },
    { value: 'MONTHLY', label: t('filters.monthly') },
    { value: 'QUARTERLY', label: t('filters.quarterly') },
    { value: 'YEARLY', label: t('filters.yearly') },
  ];

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
    control,
  } = useForm<RecurringTransactionFormData>({
    resolver: zodResolver(schema) as any,
    defaultValues: recurringTransaction
      ? {
        accountId: recurringTransaction.accountId,
        toAccountId: recurringTransaction.toAccountId || undefined,
        type: recurringTransaction.type,
        amount: recurringTransaction.amount,
        currency: recurringTransaction.currency,
        categoryId: recurringTransaction.categoryId || undefined,
        payee: recurringTransaction.payee || undefined,
        description: recurringTransaction.description,
        notes: recurringTransaction.notes || '',
        frequency: recurringTransaction.frequency,
        nextOccurrence: recurringTransaction.nextOccurrence,
        endDate: recurringTransaction.endDate || '',
      }
      : {
        type: 'EXPENSE',
        currency: baseCurrency || DEFAULT_CURRENCY,
        frequency: 'MONTHLY',
        nextOccurrence: new Date().toISOString().split('T')[0],
        amount: 0,
        description: '',
        payee: undefined,
      },
  });

  const selectedType = watch('type');
  const selectedAccountId = watch('accountId');
  const amount = watch('amount');
  const currency = watch('currency');

  // Currency conversion for preview
  const { data: exchangeRate } = useLatestExchangeRate(currency || DEFAULT_CURRENCY, baseCurrency || DEFAULT_CURRENCY);
  const formatBaseCurrency = useCurrencyFormat(baseCurrency || DEFAULT_CURRENCY);
  const convertedAmount = amount && exchangeRate && currency !== baseCurrency ? multiply(amount, exchangeRate.rate) : null;

  // Get payees for auto-fill logic
  const { data: payees = [] } = useActivePayees();

  // Get selected payee
  const selectedPayeeName = watch('payee');
  const selectedPayee = payees.find(p => p.name === selectedPayeeName);

  // Auto-fill category from payee when payee changes
  useEffect(() => {
    if (selectedPayee && selectedPayee.categoryId && !recurringTransaction) {
      if (!watch('categoryId')) {
        const category = categories.find(c => c.id === selectedPayee.categoryId);
        if (category && category.type === selectedType) {
          setValue('categoryId', selectedPayee.categoryId);
        }
      }
    }
  }, [selectedPayee, selectedType, categories, setValue, recurringTransaction]);

  // Set currency from selected account
  useEffect(() => {
    if (selectedAccountId) {
      const account = accounts.find((a) => a.id === selectedAccountId);
      if (account) {
        setValue('currency', account.currency);
      }
    }
  }, [selectedAccountId, accounts, setValue]);

  const handleFormSubmit = (data: RecurringTransactionFormData) => {
    onSubmit({
      accountId: data.accountId,
      toAccountId: data.toAccountId || null,
      type: data.type,
      amount: Number(data.amount),
      currency: data.currency,
      categoryId: data.categoryId || null,
      payee: data.payee || null,
      description: data.description,
      notes: data.notes || null,
      frequency: data.frequency,
      nextOccurrence: data.nextOccurrence,
      endDate: data.endDate || null,
    });
  };

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
      {/* Row 1: Type, Amount, Currency */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.type')} *
          </label>
          <select
            id="type"
            {...register('type')}
            className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {transactionTypes.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
          {errors.type && <p className="mt-1 text-sm text-error">{errors.type.message}</p>}
        </div>

        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.amount')} *
          </label>
          <Input
            id="amount"
            type="number"
            step="0.01"
            {...register('amount', { valueAsNumber: true })}
            error={errors.amount?.message}
          />
          {convertedAmount !== null && (
            <p className="mt-1 text-xs text-text-secondary">
              {t('form.approx')} {formatBaseCurrency(convertedAmount)} ({t('form.inBaseCurrency')})
            </p>
          )}
        </div>

        <div>
          <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.currency')} *
          </label>
          <Input id="currency" {...register('currency')} readOnly error={errors.currency?.message} />
        </div>
      </div>

      {/* Row 2: Account and To Account / Category */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {selectedType === 'TRANSFER' ? t('form.fromAccount') : t('form.account')} *
          </label>
          <Controller
            name="accountId"
            control={control}
            render={({ field }) => (
              <AccountSelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.selectAccount')}
                allowNone={false}
              />
            )}
          />
          {errors.accountId && <p className="mt-1 text-sm text-error">{errors.accountId.message}</p>}
        </div>

        {selectedType === 'TRANSFER' ? (
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.toAccount')} *
            </label>
            <Controller
              name="toAccountId"
              control={control}
              render={({ field }) => (
                <AccountSelector
                  value={field.value}
                  onValueChange={field.onChange}
                  placeholder={t('form.selectAccount')}
                  allowNone={false}
                />
              )}
            />
            {errors.toAccountId && <p className="mt-1 text-sm text-error">{errors.toAccountId.message}</p>}
          </div>
        ) : (
          <div>
            <label htmlFor="payee" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.payee')}
            </label>
            <PayeeSelector
              value={watch('payee')?.toString()}
              onValueChange={(value) => setValue('payee', value || undefined)}
              placeholder={t('form.payeePlaceholder')}
              allowNewPayee={true}
            />
          </div>
        )}
      </div>

      {/* Row 3: Description and Category */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <label htmlFor="description" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.description')} *
          </label>
          <Input
            id="description"
            placeholder={t('form.descriptionPlaceholder')}
            {...register('description')}
            error={errors.description?.message}
          />
        </div>

        {selectedType !== 'TRANSFER' && (
          <div>
            <label htmlFor="categoryId" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.category')}
            </label>
            <CategorySelect
              value={watch('categoryId')}
              onValueChange={(value) => setValue('categoryId', value)}
              placeholder={t('form.selectCategory')}
              type={selectedType}
              allowNone={true}
              allowCreateInline
              inferredType={selectedType ?? 'EXPENSE'}
            />
            {errors.categoryId && <p className="mt-1 text-sm text-error">{errors.categoryId.message}</p>}
          </div>
        )}
      </div>

      {/* Row 4: Frequency, Next Occurrence, End Date */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div>
          <label htmlFor="frequency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.frequency')} *
          </label>
          <select
            id="frequency"
            {...register('frequency')}
            className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {frequencies.map((freq) => (
              <option key={freq.value} value={freq.value}>
                {freq.label}
              </option>
            ))}
          </select>
          {errors.frequency && <p className="mt-1 text-sm text-error">{errors.frequency.message}</p>}
        </div>

        <div>
          <label htmlFor="nextOccurrence" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.nextOccurrence')} *
          </label>
          <Input id="nextOccurrence" type="date" {...register('nextOccurrence')} error={errors.nextOccurrence?.message} />
        </div>

        <div>
          <label htmlFor="endDate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.endDate')}
          </label>
          <Input
            id="endDate"
            type="date"
            {...register('endDate')}
            placeholder={t('form.endDatePlaceholder')}
            error={errors.endDate?.message}
          />
        </div>
      </div>

      {/* Notes */}
      <div>
        <label htmlFor="notes" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.notes')}
        </label>
        <textarea
          id="notes"
          rows={2}
          {...register('notes')}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
          placeholder={t('form.notesPlaceholder')}
        />
        {errors.notes && <p className="mt-1 text-sm text-error">{errors.notes.message}</p>}
      </div>

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Form Actions */}
      <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
        <Button type="button" variant="secondary" onClick={onCancel} disabled={isSubmitting}>
          {t('form.cancel')}
        </Button>
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? t('form.saving') : isEditing ? t('form.update') : t('form.create')}
        </Button>
      </div>
    </form>
  );
}
