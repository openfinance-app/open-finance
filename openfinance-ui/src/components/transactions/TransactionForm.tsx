/**
 * TransactionForm Component
 * Task 3.2.14: Create TransactionForm component
 * Task 12.3.5: Add tags to TransactionForm
 *
 * Dynamic form for creating/editing transactions with validation and tags.
 * Attachments are managed in the TransactionDetailModal, not here.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Sparkles, X, Scissors } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
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
import { DEFAULT_CURRENCY } from '@/utils/currency';

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
  const [splits, setSplits] = useState<TransactionSplitRequest[]>(
    transaction?.splits?.map((s) => ({
      categoryId: s.categoryId,
      amount: s.amount,
      description: s.description,
    })) ?? [],
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
    control,
  } = useForm<TransactionFormData>({
    resolver: zodResolver(transactionSchema(t)) as any,
    mode: 'onChange',
    defaultValues: transaction
      ? {
        accountId: transaction.accountId,
        toAccountId: transaction.toAccountId,
        type: transaction.type,
        amount: transaction.amount,
        currency: transaction.currency,
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

  // Set currency from selected account
  useEffect(() => {
    if (selectedAccountId) {
      const account = accounts.find((a) => a.id === selectedAccountId);
      if (account) {
        setValue('currency', account.currency);
      }
    }
  }, [selectedAccountId, accounts, setValue]);

  const handleFormSubmit = (data: TransactionFormData) => {
    onSubmit({
      accountId: data.accountId,
      toAccountId: data.toAccountId,
      type: data.type,
      amount: Number(data.amount),
      currency: data.currency,
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
      splits: splitMode && splits.length > 0 ? splits : undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
      {/* Row 1: Type, Amount, Date */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
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

        {/* Amount */}
        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.amount')} <span aria-label="required">*</span>
          </label>
          <Input
            id="amount"
            type="number"
            step="0.01"
            min="0"
            {...register('amount', { valueAsNumber: true })}
            onFocus={(e) => e.target.select()}
            placeholder="0.00"
            error={errors.amount?.message}
            required
          />
        </div>

        {/* Date */}
        <div>
          <label htmlFor="date" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.date')} <span aria-label="required">*</span>
          </label>
          <Input id="date" type="date" {...register('date')} error={errors.date?.message} required />
        </div>
      </div>

      {/* Row 2: Accounts */}
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

      {/* Row 3: Payee & Payment Method */}
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
              currency={watch('currency') || DEFAULT_CURRENCY}
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

      {/* Row 4: Description and Tags */}
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
