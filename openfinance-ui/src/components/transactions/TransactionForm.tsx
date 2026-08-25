/**
 * TransactionForm Component
 * Task 3.2.14: Create TransactionForm component
 * Task 12.3.5: Add tags to TransactionForm
 *
 * Dynamic form for creating/editing transactions with validation and tags.
 * Attachments are managed in the TransactionDetailModal, not here.
 */
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Sparkles, X, Scissors } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { TagInput } from './TagInput';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import { SplitTransactionForm } from './SplitTransactionForm';
import { usePopularTags } from '@/hooks/useTransactionTags';
import { useActivePayees } from '@/hooks/usePayees';
import { useLiabilities } from '@/hooks/useLiabilities';
import type { Transaction, TransactionRequest, TransactionType, Category, PaymentMethod, TransactionSplitRequest } from '@/types/transaction';
import type { Account } from '@/types/account';
import { formatDateForInput, getToday } from '@/utils/date';
import { DEFAULT_CURRENCY, getCurrencyDecimals } from '@/utils/currency';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { multiply, roundToDecimals, sumToDecimals } from '@/utils/money';

const optionalNumber = z.preprocess((value) => {
  if (value === '' || value === null || value === undefined) {
    return undefined;
  }
  if (typeof value === 'number' && Number.isNaN(value)) {
    return undefined;
  }
  return value;
}, z.number().optional()).optional() as z.ZodType<number | undefined>;

const transactionSchema = (tValidation: (key: string) => string) => z.object({
  accountId: z.preprocess(
    (val) => (val === '' || val === null || val === undefined || (typeof val === 'number' && Number.isNaN(val))) ? undefined : val,
    z.number({ error: tValidation('form.validation.selectAccount') }).min(1, tValidation('form.validation.selectAccount'))
  ),
  toAccountId: optionalNumber,
  type: z.preprocess(
    (val) => (typeof val === 'string' ? val.toUpperCase() : val),
    z.enum(['INCOME', 'EXPENSE', 'TRANSFER'])
  ),
  amount: z.coerce.number().positive(tValidation('form.validation.amountPositive')),
  currency: z.string().length(3, tValidation('form.validation.currencyCode')),
  categoryId: optionalNumber,
  date: z.string().min(1, tValidation('form.validation.dateRequired')),
  description: z.string().max(200, tValidation('form.validation.descriptionTooLong')).optional(),
  notes: z.string().max(1000, tValidation('form.validation.notesTooLong')).optional(),
  payee: z.string().max(100, tValidation('form.validation.payeeTooLong')).optional(),
  tags: z.array(z.string()).optional(),
  paymentMethod: z.enum(['CASH', 'CHEQUE', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'DEPOSIT', 'STANDING_ORDER', 'DIRECT_DEBIT', 'ONLINE', 'OTHER']).optional(),
  // Requirement 3.1: Optional link to a liability (EXPENSE transactions only)
  liabilityId: optionalNumber,
});

type TransactionFormData = z.infer<ReturnType<typeof transactionSchema>>;

interface TransactionFormProps {
  transaction?: Transaction;
  accounts: Account[];
  categories: Category[];
  onSubmit: (data: TransactionRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

const TRANSACTION_TYPES: TransactionType[] = ['INCOME', 'EXPENSE', 'TRANSFER'];
const PAYMENT_METHOD_VALUES: Array<PaymentMethod | ''> = [
  '', 'CASH', 'CHEQUE', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER',
  'DEPOSIT', 'STANDING_ORDER', 'DIRECT_DEBIT', 'ONLINE', 'OTHER',
];

/**
 * Builds the initial split rows for the form. For a converted transaction (originalCurrency set),
 * the stored split amounts are in the ACCOUNT currency, so each split's original amount is
 * reconstructed by dividing by the stored conversionRate; the last split is set to
 * originalAmount − sum(others) so the reconstructed splits sum exactly to originalAmount.
 */
export function reconstructInitialSplits(transaction?: Transaction): TransactionSplitRequest[] {
  const rows: TransactionSplitRequest[] =
    transaction?.splits?.map((s) => ({
      categoryId: s.categoryId,
      amount: s.amount,
      description: s.description,
    })) ?? [];

  const rate = transaction?.conversionRate;
  const originalCurrency = transaction?.originalCurrency;
  const originalAmount = transaction?.originalAmount;
  if (!rate || rate <= 0 || !originalCurrency || originalAmount == null || rows.length === 0) {
    return rows;
  }

  const decimals = getCurrencyDecimals(originalCurrency);
  const reconstructed = rows.map((s, i) =>
    i < rows.length - 1 ? { ...s, amount: roundToDecimals(s.amount / rate, decimals) } : { ...s },
  );
  const sumOthers = sumToDecimals(
    reconstructed.slice(0, -1).map((s) => s.amount),
    decimals,
  );
  reconstructed[reconstructed.length - 1] = {
    ...reconstructed[reconstructed.length - 1],
    amount: roundToDecimals(originalAmount - sumOthers, decimals),
  };
  return reconstructed;
}

export function TransactionForm({
  transaction,
  accounts,
  categories,
  onSubmit,
  onCancel,
  isLoading,
}: TransactionFormProps) {
  const isEditing = !!transaction;
  const { t } = useTranslation('transactions');

  // Fetch popular tags for autocomplete
  const { data: popularTags = [] } = usePopularTags();

  // State for tags (not managed by react-hook-form due to complex interaction)
  const [tags, setTags] = useState<string[]>(transaction?.tags || []);

  // State for split mode — REQ-SPL-1.5, REQ-SPL-3.1
  const [splitMode, setSplitMode] = useState<boolean>(
    !!(transaction?.hasSplits && transaction.splits && transaction.splits.length > 0),
  );
  const [splits, setSplits] = useState<TransactionSplitRequest[]>(() =>
    reconstructInitialSplits(transaction),
  );

  // State for auto-filled category from payee
  const [autoFilledCategory, setAutoFilledCategory] = useState<number | null>(null);
  const [autoFilledFromPayee, setAutoFilledFromPayee] = useState<string | null>(null);

  // Fetch liabilities for the liability selector (Requirement 3.1)
  const { data: liabilities = [] } = useLiabilities();

  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
    setError,
    clearErrors,
    control,
  } = useForm<TransactionFormData>({
    resolver: zodResolver(transactionSchema(t)) as any,
    mode: 'onChange',
    defaultValues: transaction
      ? {
        accountId: transaction.accountId,
        toAccountId: transaction.toAccountId,
        type: transaction.type,
        amount: transaction.originalCurrency
          ? (transaction.originalAmount ?? transaction.amount)
          : transaction.amount,
        currency: transaction.originalCurrency ?? transaction.currency,
        categoryId: transaction.categoryId,
        date: formatDateForInput(transaction.date),
        description: transaction.description || '',
        notes: transaction.notes || '',
        payee: transaction.payee || '',
        tags: transaction.tags || [],
        paymentMethod: transaction.paymentMethod || undefined,
        liabilityId: transaction.liabilityId,
      }
      : {
        accountId: undefined as any,
        toAccountId: undefined,
        type: 'EXPENSE',
        amount: 0,
        currency: DEFAULT_CURRENCY,
        categoryId: undefined,
        date: getToday(),
        description: '',
        notes: '',
        payee: '',
        tags: [],
        paymentMethod: undefined,
        liabilityId: undefined,
      },
  });

  // Log validation errors for debugging
  useEffect(() => {
    if (Object.keys(errors).length > 0) {
      console.warn('TransactionForm validation errors:', errors);
    }
  }, [errors]);

  const selectedType = watch('type');
  const selectedAccountId = watch('accountId');
  const currentCategoryId = watch('categoryId');

  const inputCurrency = watch('currency');
  const amountValue = watch('amount');

  const selectedAccount = accounts.find((a) => a.id === selectedAccountId);
  const accountCurrency = selectedAccount?.currency ?? DEFAULT_CURRENCY;

  // TRANSFER always keeps its current behavior (amount in the source account currency).
  const needsConversion =
    selectedType !== 'TRANSFER' &&
    !!inputCurrency &&
    !!accountCurrency &&
    inputCurrency !== accountCurrency;

  // When editing a previously-converted transaction, reuse the STORED historical rate as long as
  // the user hasn't changed the currency pair from what was saved; otherwise fetch the latest rate.
  const storedOriginalCurrency = transaction?.originalCurrency;
  const storedConversionRate = transaction?.conversionRate;
  const storedAccountCurrency = transaction?.currency;
  const useStoredRate =
    isEditing &&
    !!storedOriginalCurrency &&
    storedConversionRate != null &&
    inputCurrency === storedOriginalCurrency &&
    accountCurrency === storedAccountCurrency;

  const { data: liveExchangeRate } = useLatestExchangeRate(
    inputCurrency,
    accountCurrency,
    needsConversion && !useStoredRate ? 1 : 0,
    needsConversion && !useStoredRate,
  );

  const effectiveRate = useStoredRate ? storedConversionRate : liveExchangeRate?.rate;

  const convertedPreview =
    needsConversion && effectiveRate != null && Number.isFinite(amountValue) && amountValue > 0
      ? multiply(amountValue, effectiveRate)
      : undefined;

  // Clear ONLY the manual "rate unavailable" error we set, once conversion is no longer needed or
  // the rate loads. Scoped to type === 'manual' so it never masks a zod validation error.
  useEffect(() => {
    if (errors.currency?.type === 'manual' && (!needsConversion || effectiveRate != null)) {
      clearErrors('currency');
    }
  }, [errors.currency?.type, needsConversion, effectiveRate, clearErrors]);

  // Get payees for auto-fill logic
  const { data: payees = [] } = useActivePayees();

  // Get selected payee
  const selectedPayeeName = watch('payee');
  const selectedPayee = payees.find(p => p.name === selectedPayeeName);

  // Auto-fill category from payee when payee changes
  useEffect(() => {
    if (selectedPayee && selectedPayee.categoryId && !transaction) {
      // Only auto-fill if no category has been manually set (or if it was auto-filled before)
      if (!currentCategoryId || autoFilledCategory) {
        // Check if the payee's category matches the transaction type
        const category = categories.find(c => c.id === selectedPayee.categoryId);
        if (category && category.type === selectedType) {
          setValue('categoryId', selectedPayee.categoryId, { shouldValidate: true });
          setAutoFilledCategory(selectedPayee.categoryId);
          setAutoFilledFromPayee(selectedPayee.name);
        }
      }
    }
    // When payee is cleared, remove auto-fill state
    if (!selectedPayeeName && autoFilledCategory) {
      setAutoFilledCategory(null);
      setAutoFilledFromPayee(null);
      setValue('categoryId', undefined, { shouldValidate: true });
    }
  }, [selectedPayeeName, selectedPayee, selectedType, categories, setValue, transaction, autoFilledCategory, currentCategoryId]);

  // Default the input currency to the selected account's currency, but only when the account
  // actually changes — so a manual currency choice is not clobbered on unrelated re-renders.
  const prevAccountIdRef = useRef<number | undefined>(selectedAccountId);
  useEffect(() => {
    if (selectedAccountId && selectedAccountId !== prevAccountIdRef.current) {
      const account = accounts.find((a) => a.id === selectedAccountId);
      if (account) {
        setValue('currency', account.currency);
      }
    }
    prevAccountIdRef.current = selectedAccountId;
  }, [selectedAccountId, accounts, setValue]);

  const handleFormSubmit = (data: TransactionFormData) => {
    // The backend requires transaction.currency === account.currency for INCOME/EXPENSE, so convert
    // the entered amount (and any split amounts) into the account currency before submitting.
    const rate = needsConversion ? effectiveRate : undefined;

    // Block submit when a conversion is required but the rate is not available yet.
    if (needsConversion && !rate) {
      setError('currency', { type: 'manual', message: t('form.validation.rateUnavailable') });
      return;
    }

    const decimals = getCurrencyDecimals(accountCurrency);
    const convert = (value: number): number =>
      needsConversion && rate ? roundToDecimals(multiply(value, rate), decimals) : Number(value);

    const inSplit = splitMode && splits.length > 0;
    const submitSplits = inSplit
      ? splits.map((s) => ({ ...s, amount: convert(s.amount) }))
      : undefined;
    // On the conversion path, set the parent to the sum of converted splits so they reconcile
    // exactly with the parent (the backend requires an exact split sum). Otherwise keep today's
    // behavior of submitting the (converted) entered amount.
    const submitAmount =
      needsConversion && rate && inSplit
        ? sumToDecimals(submitSplits!.map((s) => s.amount), decimals)
        : convert(data.amount);

    // The user-entered (pre-conversion) total, in the input currency, persisted for edit restore.
    const originalAmountForSubmit =
      needsConversion && rate
        ? inSplit
          ? sumToDecimals(
              splits.map((s) => Number(s.amount) || 0),
              getCurrencyDecimals(inputCurrency),
            )
          : Number(data.amount)
        : undefined;

    onSubmit({
      accountId: data.accountId,
      toAccountId: data.toAccountId,
      type: data.type,
      amount: submitAmount,
      // Always submit in the account's currency (backend requires currency === account currency for
      // INCOME/EXPENSE). For TRANSFER this normalizes any stale input-currency selection back to the
      // source account's currency; needsConversion is false for TRANSFER so the amount is unchanged.
      currency: accountCurrency,
      originalAmount: originalAmountForSubmit,
      originalCurrency: needsConversion && rate ? inputCurrency : undefined,
      conversionRate: needsConversion && rate ? rate : undefined,
      // REQ-SPL-1.5: hide parent category when split mode is active
      categoryId: splitMode ? undefined : data.categoryId,
      date: data.date,
      description: data.description || '',
      notes: data.notes || '',
      payee: data.payee || undefined,
      tags: tags.length > 0 ? tags : undefined,
      paymentMethod: data.paymentMethod || undefined,
      // Requirement 3.1: Only include liabilityId for EXPENSE transactions
      liabilityId: data.type === 'EXPENSE' ? data.liabilityId : undefined,
      // REQ-SPL-2.1, REQ-SPL-2.2: include splits when split mode is active
      splits: submitSplits,
    });
  };

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
      {/* Row 1: Type & Date */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Transaction Type */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.type')} <span aria-label="required">*</span>
          </label>
          <select
            id="type"
            {...register('type')}
            aria-required="true"
            aria-invalid={errors.type ? 'true' : 'false'}
            aria-describedby={errors.type ? 'type-error' : undefined}
            className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {TRANSACTION_TYPES.map((type) => (
              <option key={type} value={type}>
                {t(`form.types.${type}`)}
              </option>
            ))}
          </select>
          {errors.type && <p id="type-error" className="mt-1 text-sm text-error" role="alert">{errors.type.message}</p>}
        </div>

        {/* Date */}
        <div>
          <label htmlFor="date" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.date')} <span aria-label="required">*</span>
          </label>
          <Controller
            name="date"
            control={control}
            render={({ field }) => (
              <DateInput
                id="date"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errors.date?.message}
              />
            )}
          />
        </div>
      </div>

      {/* Row 2: Currency & Amount (mirrors the Account form's Currency + Balance row) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Currency — hidden for TRANSFER (amount stays in the source account currency) */}
        {selectedType !== 'TRANSFER' && (
          <div>
            <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.currency')} <span aria-label="required">*</span>
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
              <p className="mt-1 text-sm text-error" role="alert">{errors.currency.message}</p>
            )}
            {needsConversion && (
              <div className="mt-1.5">
                <ExchangeRateInline
                  from={inputCurrency}
                  to={accountCurrency}
                  rate={useStoredRate ? storedConversionRate : undefined}
                  hint={useStoredRate ? t('form.rateAtTransactionTime') : undefined}
                />
              </div>
            )}
          </div>
        )}

        {/* Amount */}
        <div className={selectedType === 'TRANSFER' ? 'md:col-span-2' : undefined}>
          <label htmlFor="amount" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.amount')} <span aria-label="required">*</span>
          </label>
          <Input
            id="amount"
            type="number"
            step="any"
            min="0"
            {...register('amount', { valueAsNumber: true })}
            onFocus={(e) => e.target.select()}
            placeholder="0.00"
            error={errors.amount?.message}
            required
          />
          {convertedPreview !== undefined && (
            <p className="text-xs text-text-secondary mt-1">
              ≈{' '}
              <ConvertedAmount amount={convertedPreview} currency={accountCurrency} inline />
            </p>
          )}
        </div>
      </div>

      {/* Row 3: Accounts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Account */}
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {selectedType === 'TRANSFER' ? t('form.fromAccount') : t('form.account')} <span aria-label="required">*</span>
          </label>
          <Controller
            name="accountId"
            control={control}
            render={({ field }) => (
              <AccountSelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.account')}
                allowNone={false}
              />
            )}
          />
          {errors.accountId && <p id="accountId-error" className="mt-1 text-sm text-error" role="alert">{errors.accountId.message}</p>}
        </div>

        {/* To Account (for transfers) */}
        {selectedType === 'TRANSFER' && (
          <div>
            <label className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.toAccount')} <span aria-label="required">*</span>
            </label>
            <Controller
              name="toAccountId"
              control={control}
              render={({ field }) => (
                <AccountSelector
                  value={field.value}
                  onValueChange={field.onChange}
                  placeholder={t('form.account')}
                  allowNone={false}
                />
              )}
            />
            {errors.toAccountId && <p id="toAccountId-error" className="mt-1 text-sm text-error" role="alert">{errors.toAccountId.message}</p>}
          </div>
        )}
      </div>

      {/* Row 4: Payee & Payment Method */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Payee */}
        <div>
          <label htmlFor="payee" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.payee')}
          </label>
          <Controller
            name="payee"
            control={control}
            render={({ field }) => (
              <PayeeSelector
                value={field.value?.toString()}
                onValueChange={field.onChange}
                placeholder={t('form.payeePlaceholder')}
                allowNewPayee={true}
              />
            )}
          />
          <p className="mt-1 text-xs text-text-tertiary">
            {t('form.payeeHint')}
          </p>
        </div>

        {/* Payment Method */}
        <div>
          <label htmlFor="paymentMethod" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.paymentMethod')}
          </label>
          <Controller
            name="paymentMethod"
            control={control}
            render={({ field }) => (
              <select
                id="paymentMethod"
                value={field.value || ''}
                onChange={field.onChange}
                className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                {PAYMENT_METHOD_VALUES.map((method) => (
                  <option key={method} value={method}>
                    {method === '' ? t('form.selectPaymentMethod') : t(`form.paymentMethods.${method}`)}
                  </option>
                ))}
              </select>
            )}
          />
          <p className="mt-1 text-xs text-text-tertiary">
            {t('form.paymentMethodHint')}
          </p>
        </div>
      </div>

      {/* Category / Split section */}
      {selectedType !== 'TRANSFER' && (
        <div className="space-y-3">
          {/* Split toggle button */}
          <div className="flex items-center justify-between">
            <label className="block text-sm font-medium text-text-primary">
              {splitMode ? t('form.splitTransaction') : t('form.category')}
            </label>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className={`flex items-center gap-1.5 text-xs ${splitMode
                ? 'text-primary bg-primary/10 hover:bg-primary/20'
                : 'text-text-secondary hover:text-primary'
                }`}
              onClick={() => {
                const entering = !splitMode;
                setSplitMode(entering);
                if (entering && splits.length === 0) {
                  setSplits([
                    { categoryId: undefined, amount: 0, description: undefined },
                    { categoryId: undefined, amount: 0, description: undefined },
                  ]);
                }
                if (!entering) {
                  setSplits([]);
                }
              }}
              aria-pressed={splitMode}
              title={splitMode ? 'Switch back to single category' : 'Split this transaction across categories'}
            >
              <Scissors className="h-3.5 w-3.5" />
              {splitMode ? t('form.removeSplit') : t('form.splitTransaction')}
            </Button>
          </div>

          {/* When NOT in split mode: standard category picker */}
          {!splitMode && (
            <div>
              <Controller
                name="categoryId"
                control={control}
                render={({ field }) => (
                  <CategorySelect
                    value={field.value}
                    onValueChange={(value) => {
                      // Clear auto-fill indicator if user manually changes category
                      if (autoFilledCategory && value !== autoFilledCategory) {
                        setAutoFilledCategory(null);
                        setAutoFilledFromPayee(null);
                      }
                      field.onChange(value);
                    }}
                    placeholder={t('form.selectCategory')}
                    type={selectedType}
                    allowNone={true}
                    allowCreateNew={true}
                    allowCreateInline
                    inferredType={selectedType ?? 'EXPENSE'}
                    onCreateNew={() => {
                      // Could open a dialog to create new category
                      // For now, just clear the category
                    }}
                  />
                )}
              />
              {/* Auto-fill indicator */}
              {autoFilledFromPayee && (
                <div className="mt-1.5 flex items-center gap-1.5 rounded-md bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1.5">
                  <Sparkles className="h-3.5 w-3.5 text-emerald-500 shrink-0" />
                  <p className="text-xs text-emerald-500 flex-1">
                    {t('form.autoFilledFrom')} <span className="font-medium">{autoFilledFromPayee}</span>
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setAutoFilledCategory(null);
                      setAutoFilledFromPayee(null);
                      setValue('categoryId', undefined);
                    }}
                    className="rounded-sm hover:bg-emerald-500/20 p-0.5 transition-colors"
                    aria-label="Clear auto-filled category"
                  >
                    <X className="h-3 w-3 text-emerald-500" />
                  </button>
                </div>
              )}
              {errors.categoryId && (
                <p id="categoryId-error" className="mt-1 text-sm text-error" role="alert">
                  {errors.categoryId.message}
                </p>
              )}
            </div>
          )}

          {/* When in split mode: SplitTransactionForm */}
          {splitMode && (
            <SplitTransactionForm
              totalAmount={watch('amount') || 0}
              currency={inputCurrency || DEFAULT_CURRENCY}
              accountCurrency={accountCurrency}
              exchangeRate={needsConversion ? effectiveRate : undefined}
              transactionType={selectedType}
              splits={splits}
              onChange={setSplits}
            />
          )}
        </div>
      )}

      {/* Liability Selector - only for EXPENSE transactions (Requirement 3.1) */}
      {selectedType === 'EXPENSE' && liabilities.length > 0 && (
        <div>
          <label htmlFor="liabilityId" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.linkedLiability')}
          </label>
          <Controller
            name="liabilityId"
            control={control}
            render={({ field }) => (
              <LiabilitySelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.selectLiability')}
              />
            )}
          />
          <p className="mt-1 text-xs text-text-tertiary">
            {t('form.linkedLiabilityHint')}
          </p>
        </div>
      )}

      {/* Row 5: Description and Tags */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Description */}
        <div>
          <label htmlFor="description" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.description')}
          </label>
          <Input
            id="description"
            {...register('description')}
            placeholder={t('form.descriptionPlaceholder')}
            error={errors.description?.message}
          />
        </div>

        {/* Tags */}
        <div>
          <label className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.tags')}
          </label>
          <TagInput
            value={tags}
            onChange={setTags}
            suggestions={popularTags}
            placeholder={t('form.tagsPlaceholder')}
            maxTags={10}
            disabled={isLoading}
          />
          <p className="mt-1 text-xs text-muted-foreground">
            {t('form.tagsHint')}
          </p>
        </div>
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
          aria-invalid={errors.notes ? 'true' : 'false'}
          aria-describedby={errors.notes ? 'notes-error' : undefined}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
        />
        {errors.notes && <p id="notes-error" className="mt-1 text-sm text-error" role="alert">{errors.notes.message}</p>}
      </div>

      {/* Actions */}
      <div className="flex justify-end gap-3 pt-4">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button variant="primary" type="submit" isLoading={isLoading}>
          {isEditing ? t('form.updateTransaction') : t('form.createTransaction')}
        </Button>
      </div>
    </form>
  );
}
