import { useFormDraft } from '@/hooks/useFormDraft';
import {
  decimalField,
  decimalValue,
  exactSum,
  equalDecimals,
  ExactDecimal,
} from '@/utils/exact-decimal';
import { formatDecimal } from '@/utils/format';
import { CURRENCY_CODE_PATTERN } from '@/utils/currency-code';
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
import { NumberInput } from '@/components/ui/NumberInput';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { TagInput } from './TagInput';
import { PayeeSelector } from '@/components/ui/PayeeSelector';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import { SplitTransactionForm } from './SplitTransactionForm';
import { MovementSection } from '@/components/transactions/MovementSection';
import { ImprovementValuationFields } from '@/components/assets/ImprovementValuationFields';
import { LinkedOperationSection } from '@/components/transactions/LinkedOperationSection';
import {
  initialLinkedOperation,
  isInvestmentOperation,
  linkedOperations,
  type LinkedOperation,
} from '@/utils/linked-operation';
import { InvestmentMovementFields } from '@/components/transactions/InvestmentMovementFields';
import { TransferReceipt } from '@/components/transactions/TransferReceipt';
import { useAssets } from '@/hooks/useAssets';
import { useInvestmentQuote } from '@/hooks/useInvestmentQuote';
import { investmentInstruction, validInvestmentQuantity } from '@/utils/investment-movement';
import { usePopularTags } from '@/hooks/useTransactionTags';
import { useActivePayees } from '@/hooks/usePayees';
import { useLiabilities, useRepaymentPreview } from '@/hooks/useLiabilities';
import type {
  Transaction,
  TransactionRequest,
  TransactionType,
  Category,
  PaymentMethod,
  TransactionSplitRequest,
} from '@/types/transaction';
import type { Account } from '@/types/account';
import { formatDateForInput, getToday } from '@/utils/date';
import { DEFAULT_CURRENCY, getMonetaryScale, getCurrencyDecimals } from '@/utils/currency';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { useExchangeRate } from '@/hooks/useCurrency';
import { multiply, isValidDecimalString } from '@/utils/money';
import {
  buildTransactionRequest,
  reconstructInitialSplits,
  submitGuardError,
} from '@/utils/transaction-request';

const optionalNumber = z
  .preprocess(value => {
    if (value === '' || value === null || value === undefined) {
      return undefined;
    }
    if (typeof value === 'number' && Number.isNaN(value)) {
      return undefined;
    }
    return value;
  }, z.number().optional())
  .optional() as z.ZodType<number | undefined>;

const transactionSchema = (tValidation: (key: string) => string) =>
  z
    .object({
      accountId: z.preprocess(
        val =>
          val === '' ||
          val === null ||
          val === undefined ||
          (typeof val === 'number' && Number.isNaN(val))
            ? undefined
            : val,
        z
          .number({ error: tValidation('form.validation.selectAccount') })
          .min(1, tValidation('form.validation.selectAccount'))
      ),
      toAccountId: optionalNumber,
      type: z.preprocess(
        val => (typeof val === 'string' ? val.toUpperCase() : val),
        z.enum(['INCOME', 'EXPENSE', 'TRANSFER'])
      ),
      amount: z
        .string()
        .refine(
          v => isValidDecimalString(v) && new ExactDecimal(v).gt(0),
          tValidation('form.validation.amountPositive')
        ),
      currency: z
        .string()
        .regex(CURRENCY_CODE_PATTERN, tValidation('form.validation.currencyCode')),
      categoryId: optionalNumber,
      date: z.string().min(1, tValidation('form.validation.dateRequired')),
      description: z
        .string()
        .max(200, tValidation('form.validation.descriptionTooLong'))
        .optional(),
      notes: z.string().max(1000, tValidation('form.validation.notesTooLong')).optional(),
      payee: z.string().max(100, tValidation('form.validation.payeeTooLong')).optional(),
      tags: z.array(z.string()).optional(),
      paymentMethod: z
        .enum([
          'CASH',
          'CHEQUE',
          'CREDIT_CARD',
          'DEBIT_CARD',
          'BANK_TRANSFER',
          'DEPOSIT',
          'STANDING_ORDER',
          'DIRECT_DEBIT',
          'ONLINE',
          'OTHER',
        ])
        .optional(),
      // Requirement 3.1: Optional link to a liability (EXPENSE transactions only)
      liabilityId: optionalNumber,
      // Manual movement entry: improvement/maintenance classification with exactly
      // one target (property or physical asset) — mirrors the backend @AssertTrue.
      movementType: z
        .enum([
          'DISBURSEMENT',
          'REPAYMENT',
          'INTEREST',
          'INSURANCE',
          'FEE',
          'CAPITAL_IMPROVEMENT',
          'MAINTENANCE',
          'REVALUATION',
        ])
        .optional(),
      trancheId: optionalNumber,
      valuationChange: z
        .string()
        .refine(value => isValidDecimalString(value) && new ExactDecimal(value).gte(0))
        .optional(),
      principalAmount: z.preprocess(
        v => (v === '' || v == null ? undefined : String(v)),
        z
          .string()
          .refine(v => isValidDecimalString(v) && new ExactDecimal(v).gte(0))
          .optional()
      ),
      realEstateId: optionalNumber,
      assetId: optionalNumber,
    })
    .superRefine((data, ctx) => {
      // Transfers must move funds between two different accounts.
      if (
        data.type === 'TRANSFER' &&
        data.toAccountId !== undefined &&
        data.accountId === data.toAccountId
      ) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['toAccountId'],
          message: tValidation('form.validation.sameAccount'),
        });
      }
      // Movement coherence: an improvement/maintenance movement must target
      // exactly one property or physical asset (backend @AssertTrue mirror).
      const improvementMovement =
        data.movementType === 'CAPITAL_IMPROVEMENT' || data.movementType === 'MAINTENANCE';
      const hasProperty = data.realEstateId !== undefined;
      const hasAsset = data.assetId !== undefined;
      if (improvementMovement && !hasProperty && !hasAsset) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['movementType'],
          message: tValidation('form.validation.movementTargetRequired'),
        });
      }
      if (hasProperty && hasAsset) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['realEstateId'],
          message: tValidation('form.validation.movementSingleTarget'),
        });
      }
      // Multi-instrument guard: a transaction can reference a liability OR a
      // property/asset, never both (backend singleInstrument @AssertTrue mirror).
      if (data.type === 'EXPENSE' && data.liabilityId !== undefined && (hasProperty || hasAsset)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['liabilityId'],
          message: tValidation('form.validation.movementLiabilityExclusive'),
        });
      }
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
  '',
  'CASH',
  'CHEQUE',
  'CREDIT_CARD',
  'DEBIT_CARD',
  'BANK_TRANSFER',
  'DEPOSIT',
  'STANDING_ORDER',
  'DIRECT_DEBIT',
  'ONLINE',
  'OTHER',
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
  const [investment, setInvestment] = useState(() => investmentInstruction(transaction));
  const [linkedOperation, setLinkedOperation] = useState<LinkedOperation>(() =>
    initialLinkedOperation(transaction)
  );
  const { data: investmentAssets = [], isSuccess: holdingsLoaded } = useAssets();

  // State for split mode — REQ-SPL-1.5, REQ-SPL-3.1
  const [splitMode, setSplitMode] = useState<boolean>(
    !!(transaction?.hasSplits && transaction.splits && transaction.splits.length > 0)
  );
  const [splits, setSplits] = useState<TransactionSplitRequest[]>(() =>
    reconstructInitialSplits(transaction)
  );

  // State for auto-filled category from payee
  const [autoFilledCategory, setAutoFilledCategory] = useState<number | null>(null);
  const [autoFilledFromPayee, setAutoFilledFromPayee] = useState<string | null>(null);
  const [isMovementOpen, setIsMovementOpen] = useState(
    initialLinkedOperation(transaction) !== 'NONE'
  );

  // Fetch liabilities for the liability selector (Requirement 3.1)
  const { data: liabilities = [] } = useLiabilities();

  const {
    register,
    handleSubmit,
    formState: { errors, submitCount, isDirty },
    watch,
    setValue,
    setError,
    clearErrors,
    trigger,
    control,
  } = useForm<TransactionFormData>({
    resolver: zodResolver(transactionSchema(t)) as any,
    mode: 'onChange',
    defaultValues: transaction
      ? {
          accountId: transaction.accountId,
          toAccountId: transaction.toAccountId,
          type: transaction.transferId ? 'TRANSFER' : transaction.type,
          amount: decimalField(
            transaction,
            transaction.originalCurrency && transaction.originalAmount != null
              ? 'originalAmount'
              : 'amount'
          ),
          currency: transaction.originalCurrency ?? transaction.currency,
          categoryId: transaction.categoryId,
          date: formatDateForInput(transaction.date),
          description: transaction.description || '',
          notes: transaction.notes || '',
          payee: transaction.payee || '',
          tags: transaction.tags || [],
          paymentMethod: transaction.paymentMethod || undefined,
          liabilityId: transaction.liabilityId,
          movementType: transaction.movementType ?? undefined,
          valuationChange:
            transaction.valuationChange == null
              ? '0'
              : decimalField(transaction, 'valuationChange'),
          trancheId: transaction.trancheId,
          principalAmount:
            transaction.principalAmount == null
              ? undefined
              : decimalField(transaction, 'principalAmount'),
          realEstateId: transaction.realEstateId,
          assetId: transaction.investmentQuantity == null ? transaction.assetId : undefined,
        }
      : {
          accountId: undefined as any,
          toAccountId: undefined,
          type: 'EXPENSE',
          amount: '0',
          currency: DEFAULT_CURRENCY,
          categoryId: undefined,
          date: getToday(),
          description: '',
          notes: '',
          payee: '',
          tags: [],
          paymentMethod: undefined,
          liabilityId: undefined,
          movementType: undefined,
          valuationChange: '0',
          realEstateId: undefined,
          assetId: undefined,
        },
  });

  const hasMovementErrors = !!(
    errors.valuationChange ||
    errors.liabilityId ||
    errors.movementType ||
    errors.realEstateId ||
    errors.assetId ||
    errors.principalAmount
  );

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

  const selectedAccount = accounts.find(a => a.id === selectedAccountId);
  const accountCurrency = selectedAccount?.currency ?? DEFAULT_CURRENCY;

  // Repayment auto-split preview (Task 5): fetched when an EXPENSE is linked to a liability
  // and amount/date are filled. Since Task 9 the FX case is supported: when the paying account's
  // currency differs from the liability currency the total is converted to the liability currency
  // first (client-side, with the same rate the submit payload will store) and the preview rows are
  // shown in the liability currency.
  const liabilityIdValue = selectedType === 'EXPENSE' ? watch('liabilityId') : undefined;
  const selectedLiability = liabilities.find(l => l.id === liabilityIdValue);
  const liabilityCurrency = selectedLiability?.currency;
  const linkedLiability = !!liabilityIdValue && selectedType === 'EXPENSE';
  const needsLiabilityFx =
    linkedLiability && !!liabilityCurrency && accountCurrency !== liabilityCurrency;

  // Debounce the watched amount so rapid keystrokes fire a single preview request.
  const [debouncedAmount, setDebouncedAmount] = useState('0');
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedAmount(Number(amountValue) > 0 ? String(amountValue) : '0');
    }, 400);
    return () => clearTimeout(timer);
  }, [amountValue]);

  // Account-currency → liability-currency rate for the FX preview/payload. Only needed when the
  // input is in the account currency (when the input IS the liability currency the existing
  // account-conversion machinery already stores the liability view).
  const transactionDate = watch('date');
  const investmentQuote = useInvestmentQuote(
    investment,
    amountValue,
    inputCurrency,
    transactionDate,
    transaction
  );
  const selectedHolding = investmentAssets.find(asset => asset.id === investment?.assetId);
  const removalQuantity =
    investment?.pricing === 'HISTORICAL'
      ? investmentQuote.data && decimalField(investmentQuote.data, 'quantity')
      : investment?.quantity;
  const exceedsAvailable =
    investment &&
    ['REMOVE', 'SELL'].includes(investment.operation) &&
    investmentQuote.data?.availableQuantity != null &&
    validInvestmentQuantity(removalQuantity) &&
    new ExactDecimal(removalQuantity!).gt(decimalField(investmentQuote.data, 'availableQuantity'));
  const investmentError = !isInvestmentOperation(linkedOperation)
    ? undefined
    : !investment
      ? t('linkedOperation.holdingRequired')
      : splitMode
        ? t('linkedOperation.splitHint')
        : holdingsLoaded && (!selectedHolding || selectedHolding.accountId !== selectedAccountId)
          ? t('investment.accountMismatch')
          : investment.pricing === 'QUANTITY' && !validInvestmentQuantity(investment.quantity)
            ? t('investment.quantityInvalid')
            : exceedsAvailable
              ? t(
                  investment.pricing === 'HISTORICAL'
                    ? 'investment.estimateExceedsAvailable'
                    : 'investment.quantityExceedsAvailable'
                )
              : undefined;
  const investmentBlocked =
    isInvestmentOperation(linkedOperation) &&
    (!!investmentError ||
      !investment ||
      (investment.pricing === 'HISTORICAL' &&
        (!investmentQuote.data ||
          (!investmentQuote.isSaved && (investmentQuote.isFetching || investmentQuote.isError)))));
  const linkedOperationError =
    linkedOperation === 'LOAN_PAYMENT' && !liabilityIdValue
      ? t('linkedOperation.loanRequired')
      : undefined;
  const operationChoices = linkedOperations(
    selectedType,
    liabilities.length > 0 || linkedOperation === 'LOAN_PAYMENT',
    !!selectedAccount?.isInterestEnabled || linkedOperation === 'INTEREST'
  );
  const historicalQuoteFailed =
    isInvestmentOperation(linkedOperation) &&
    investment?.pricing === 'HISTORICAL' &&
    investmentQuote.isError &&
    !investmentQuote.isSaved;
  useEffect(() => {
    if (hasMovementErrors || investmentError || linkedOperationError || historicalQuoteFailed)
      setIsMovementOpen(true);
  }, [
    hasMovementErrors,
    investmentError,
    linkedOperationError,
    historicalQuoteFailed,
    submitCount,
  ]);
  const { data: liabilityRateData } = useExchangeRate(
    accountCurrency,
    liabilityCurrency ?? '',
    transactionDate,
    !!transactionDate && needsLiabilityFx && inputCurrency === accountCurrency
  );
  const accountToLiabilityRate = liabilityRateData?.rate;

  // The preview total expressed in the liability currency:
  // - input == liability currency → the entered amount as-is
  // - input == account currency ≠ liability → converted with the fetched rate
  // - otherwise no coherent liability view exists → preview stays hidden
  const liabilityInputSupported =
    !liabilityCurrency || inputCurrency === liabilityCurrency || inputCurrency === accountCurrency;
  const liabilityPreviewTotal =
    !liabilityInputSupported || Number(debouncedAmount) <= 0
      ? undefined
      : needsLiabilityFx && inputCurrency === accountCurrency
        ? accountToLiabilityRate
          ? new ExactDecimal(debouncedAmount)
              .times(
                liabilityRateData ? decimalField(liabilityRateData, 'rate') : accountToLiabilityRate
              )
              .toDecimalPlaces(getMonetaryScale(liabilityCurrency!))
              .toFixed()
          : undefined
        : debouncedAmount;

  const {
    data: repaymentPreview,
    error: repaymentPreviewError,
    isFetching: repaymentLoading,
    isPlaceholderData: repaymentStale,
  } = useRepaymentPreview(
    !splitMode &&
      (watch('movementType') ?? 'REPAYMENT') === 'REPAYMENT' &&
      liabilityPreviewTotal != null &&
      Number(liabilityPreviewTotal) > 0
      ? liabilityIdValue
      : undefined,
    liabilityPreviewTotal ?? 0,
    watch('date')
  );

  // Auto-split toggle (Task 9): on by default — the submit includes the preview legs as splits.
  const [applyRepaymentSplit, setApplyRepaymentSplit] = useState(!transaction);
  const repaymentNeedsActual =
    repaymentPreview?.status != null && repaymentPreview.status !== 'READY';
  const enterActualRepayment = () => {
    setApplyRepaymentSplit(false);
    setSplitMode(true);
    setSplits([
      { amount: 0, loanComponent: 'INTEREST' },
      { amount: 0, loanComponent: 'INSURANCE' },
    ]);
    clearErrors('principalAmount');
    setTimeout(() => document.getElementById('loan-component-0')?.focus(), 0);
  };

  // Converted transfer instructions need the same exact source-to-account conversion.
  const needsConversion =
    (selectedType !== 'TRANSFER' || !!transaction?.originalCurrency) &&
    !!inputCurrency &&
    !!accountCurrency &&
    inputCurrency !== accountCurrency;

  // When editing a previously-converted transaction, reuse the STORED historical rate as long as
  // the date and currency pair are unchanged; otherwise fetch the rate for the selected date.
  const storedOriginalCurrency = transaction?.originalCurrency;
  const storedConversionRate = transaction?.conversionRate;
  const storedAccountCurrency = transaction?.currency;
  const useStoredRate =
    isEditing &&
    !!storedOriginalCurrency &&
    storedConversionRate != null &&
    inputCurrency === storedOriginalCurrency &&
    accountCurrency === storedAccountCurrency &&
    transactionDate === transaction?.date;

  const { data: liveExchangeRate } = useExchangeRate(
    inputCurrency,
    accountCurrency,
    transactionDate,
    !!transactionDate && needsConversion && !useStoredRate
  );

  const effectiveRate = useStoredRate ? storedConversionRate : liveExchangeRate?.rate;

  const convertedPreview =
    needsConversion &&
    effectiveRate != null &&
    Number.isFinite(Number(amountValue)) &&
    Number(amountValue) > 0
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
        // A payee's category can be used for either transaction direction
        const category = categories.find(c => c.id === selectedPayee.categoryId);
        if (category) {
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
  }, [
    selectedPayeeName,
    selectedPayee,
    selectedType,
    categories,
    setValue,
    transaction,
    autoFilledCategory,
    currentCategoryId,
  ]);

  function changeLinkedOperation(next: LinkedOperation): void {
    if (next === linkedOperation || (splitMode && isInvestmentOperation(next))) return;
    const keepTarget =
      ['MAINTENANCE', 'CAPITAL_IMPROVEMENT'].includes(next) &&
      ['MAINTENANCE', 'CAPITAL_IMPROVEMENT'].includes(linkedOperation);
    setLinkedOperation(next);
    setIsMovementOpen(true);
    setInvestment(current =>
      isInvestmentOperation(next) && current ? { ...current, operation: next } : null
    );
    clearErrors([
      'liabilityId',
      'trancheId',
      'principalAmount',
      'movementType',
      'realEstateId',
      'assetId',
    ]);
    setValue('liabilityId', undefined, { shouldDirty: true });
    setValue('trancheId', undefined, { shouldDirty: true });
    setValue('principalAmount', undefined, { shouldDirty: true });
    setValue('valuationChange', '0', { shouldDirty: true });
    setValue(
      'movementType',
      next === 'MAINTENANCE' || next === 'CAPITAL_IMPROVEMENT' || next === 'INTEREST'
        ? next
        : undefined,
      { shouldDirty: true }
    );
    if (!keepTarget) {
      setValue('realEstateId', undefined, { shouldDirty: true });
      setValue('assetId', undefined, { shouldDirty: true });
    }
    if (next === 'LOAN_PAYMENT') setApplyRepaymentSplit(!transaction && !splitMode);
  }

  function changeTransactionType(type: TransactionType): void {
    setValue('type', type, { shouldDirty: true, shouldValidate: true });
    if (!linkedOperations(type, true, true).includes(linkedOperation))
      changeLinkedOperation('NONE');
    if (type === 'TRANSFER') {
      setSplitMode(false);
      setSplits([]);
    }
  }

  // Default the input currency to the selected account's currency, but only when the account
  // actually changes — so a manual currency choice is not clobbered on unrelated re-renders.
  const prevAccountIdRef = useRef<number | undefined>(selectedAccountId);
  useEffect(() => {
    if (selectedAccountId && selectedAccountId !== prevAccountIdRef.current) {
      const account = accounts.find(a => a.id === selectedAccountId);
      if (account) {
        setValue('currency', account.currency);
      }
    }
    prevAccountIdRef.current = selectedAccountId;
  }, [selectedAccountId, accounts, setValue]);

  const cancelWithGuard = useFormDraft(
    isDirty ||
      JSON.stringify(tags) !== JSON.stringify(transaction?.tags || []) ||
      JSON.stringify(investment) !== JSON.stringify(investmentInstruction(transaction)) ||
      linkedOperation !== initialLinkedOperation(transaction) ||
      applyRepaymentSplit !== !transaction ||
      splitMode !== !!(transaction?.hasSplits && transaction.splits?.length) ||
      JSON.stringify(splits) !== JSON.stringify(reconstructInitialSplits(transaction)),
    !!isLoading,
    onCancel
  );

  const handleFormSubmit = (data: TransactionFormData) => {
    if (investmentBlocked || linkedOperationError) return;
    if (
      splitMode &&
      (splits.length < 2 ||
        !equalDecimals(exactSum(splits.map(split => split.amount)), data.amount))
    ) {
      setError('amount', { type: 'manual', message: t('form.validation.splitTotalMismatch') });
      return;
    }
    // The backend requires transaction.currency === account.currency for INCOME/EXPENSE, so
    // convert the entered amount (and any split amounts) into the account currency first.
    const rate = needsConversion
      ? useStoredRate && transaction
        ? decimalValue(transaction, 'conversionRate')
        : liveExchangeRate
          ? decimalValue(liveExchangeRate, 'rate')
          : undefined
      : undefined;
    const liabilityRate =
      needsLiabilityFx && inputCurrency === accountCurrency && liabilityRateData
        ? decimalValue(liabilityRateData, 'rate')
        : undefined;

    const guardKey = submitGuardError({
      linkedLiability,
      liabilityCurrency,
      inputCurrency,
      accountCurrency,
      needsConversion,
      rate,
      needsLiabilityFx,
      liabilityRate,
    });
    if (guardKey) {
      setError('currency', { type: 'manual', message: t(guardKey) });
      return;
    }

    if (
      data.liabilityId &&
      data.type === 'EXPENSE' &&
      (data.movementType ?? 'REPAYMENT') === 'REPAYMENT' &&
      applyRepaymentSplit &&
      !splitMode &&
      (!repaymentPreview ||
        repaymentLoading ||
        repaymentStale ||
        repaymentNeedsActual ||
        repaymentPreviewError ||
        repaymentPreview.principal > (selectedLiability?.currentBalance ?? Infinity))
    ) {
      setError('principalAmount', { type: 'manual', message: t('form.repaymentUnavailable') });
      return;
    }
    onSubmit(
      buildTransactionRequest({
        data: {
          ...data,
          investment: investment
            ? {
                ...investment,
                quantity: investment.pricing === 'QUANTITY' ? investment.quantity : undefined,
              }
            : transaction?.investmentQuantity != null
              ? null
              : undefined,
        },
        transaction,
        splits,
        splitMode,
        tags,
        categories,
        inputCurrency,
        accountCurrency,
        needsConversion,
        rate,
        needsLiabilityFx,
        liabilityCurrency,
        liabilityRate,
        applyRepaymentSplit,
        repaymentPreview,
        linkedLiability,
      })
    );
  };

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-5">
      {/* ── Zone 1: essentials — type, date, currency, amount ─────────────── */}
      {/* Row 1: Type & Date */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Transaction Type */}
        <div>
          <label
            htmlFor="transaction-type"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.type')} <span aria-label="required">*</span>
          </label>
          <select
            id="transaction-type"
            {...register('type')}
            onChange={event => changeTransactionType(event.target.value as TransactionType)}
            aria-required="true"
            aria-invalid={errors.type ? 'true' : 'false'}
            aria-describedby={errors.type ? 'transaction-type-error' : undefined}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            {TRANSACTION_TYPES.filter(
              type =>
                !isEditing || (transaction?.transferId ? type === 'TRANSFER' : type !== 'TRANSFER')
            ).map(type => (
              <option key={type} value={type}>
                {t(`form.types.${type}`)}
              </option>
            ))}
          </select>
          {errors.type && (
            <p id="transaction-type-error" className="mt-1 text-sm text-error" role="alert">
              {errors.type.message}
            </p>
          )}
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
        {/* Keep a converted transfer's input currency visible while editing. */}
        {(selectedType !== 'TRANSFER' || !!transaction?.originalCurrency) && (
          <div>
            <label
              htmlFor="currency"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
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
              <p className="mt-1 text-sm text-error" role="alert">
                {errors.currency.message}
              </p>
            )}
            {needsConversion && (
              <div className="mt-1.5">
                <ExchangeRateInline
                  from={inputCurrency}
                  to={accountCurrency}
                  rate={effectiveRate}
                  fetchRate={false}
                  hint={t('form.rateAtTransactionTime')}
                />
              </div>
            )}
          </div>
        )}

        {/* Amount */}
        <div
          className={
            selectedType === 'TRANSFER' && !transaction?.originalCurrency
              ? 'md:col-span-2'
              : undefined
          }
        >
          <label htmlFor="amount" className="block text-sm font-medium text-text-primary mb-1.5">
            {selectedType === 'TRANSFER'
              ? `${t('form.sentAmount')} (${inputCurrency})`
              : t('form.amount')}{' '}
            <span aria-label="required">*</span>
          </label>
          <Controller
            name="amount"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="amount"
                value={
                  field.value !== undefined && !Number.isNaN(field.value) ? String(field.value) : ''
                }
                onChange={val => field.onChange(val)}
                onBlur={field.onBlur}
                placeholder="0.00"
                error={errors.amount?.message}
                required
                min="0"
              />
            )}
          />
          {convertedPreview !== undefined && (
            <p className="text-xs text-text-secondary mt-1">
              ≈ <ConvertedAmount amount={convertedPreview} currency={accountCurrency} inline />
            </p>
          )}
        </div>
      </div>

      {/* ── Zone 2: accounts ──────────────────────────────────────────────── */}
      {/* Row 3: Accounts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Account */}
        <div>
          <label htmlFor="accountId" className="block text-sm font-medium text-text-primary mb-1.5">
            {selectedType === 'TRANSFER' ? t('form.fromAccount') : t('form.account')}{' '}
            <span aria-label="required">*</span>
          </label>
          <Controller
            name="accountId"
            control={control}
            render={({ field }) => (
              <AccountSelector
                id="accountId"
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.account')}
                allowNone={false}
                excludeAccountId={selectedType === 'TRANSFER' ? watch('toAccountId') : undefined}
              />
            )}
          />
          {errors.accountId && (
            <p id="accountId-error" className="mt-1 text-sm text-error" role="alert">
              {errors.accountId.message}
            </p>
          )}
        </div>

        {/* To Account (for transfers) */}
        {selectedType === 'TRANSFER' && (
          <div>
            <label
              htmlFor="toAccountId"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.toAccount')} <span aria-label="required">*</span>
            </label>
            <Controller
              name="toAccountId"
              control={control}
              render={({ field }) => (
                <AccountSelector
                  id="toAccountId"
                  value={field.value}
                  onValueChange={field.onChange}
                  placeholder={t('form.account')}
                  allowNone={false}
                  excludeAccountId={selectedAccountId}
                />
              )}
            />
            {errors.toAccountId && (
              <p id="toAccountId-error" className="mt-1 text-sm text-error" role="alert">
                {errors.toAccountId.message}
              </p>
            )}
          </div>
        )}
      </div>

      {selectedType === 'TRANSFER' && transaction?.transferId && (
        <TransferReceipt transaction={transaction} />
      )}

      {/* ── Zone 3: context — payee, payment, category, linkages ──────────── */}
      <div aria-hidden="true" className="border-t border-border/60" />

      {/* Row 4: Payee & Payment Method */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Payee */}
        <div>
          <div className="flex items-center gap-1 mb-1.5">
            <label htmlFor="payee" className="block text-sm font-medium text-text-secondary">
              {t('form.payee')}
            </label>
            <HelpTooltip text={t('form.payeeHint')} side="right" />
          </div>
          <Controller
            name="payee"
            control={control}
            render={({ field }) => (
              <PayeeSelector
                id="payee"
                value={field.value?.toString()}
                onValueChange={field.onChange}
                placeholder={t('form.payeePlaceholder')}
                allowNewPayee={true}
              />
            )}
          />
        </div>

        {/* Payment Method */}
        <div>
          <div className="flex items-center gap-1 mb-1.5">
            <label
              htmlFor="paymentMethod"
              className="block text-sm font-medium text-text-secondary"
            >
              {t('form.paymentMethod')}
            </label>
            <HelpTooltip text={t('form.paymentMethodHint')} side="right" />
          </div>
          <Controller
            name="paymentMethod"
            control={control}
            render={({ field }) => (
              <select
                id="paymentMethod"
                value={field.value || ''}
                onChange={field.onChange}
                className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
              >
                {PAYMENT_METHOD_VALUES.map(method => (
                  <option key={method} value={method}>
                    {method === ''
                      ? t('form.selectPaymentMethod')
                      : t(`form.paymentMethods.${method}`)}
                  </option>
                ))}
              </select>
            )}
          />
        </div>
      </div>

      {/* Category / Split section */}
      {selectedType !== 'TRANSFER' && (
        <div className="space-y-3">
          {/* Split toggle button */}
          <div className="flex items-center justify-between">
            <label
              htmlFor={splitMode ? undefined : 'categoryId'}
              className="block text-sm font-medium text-text-primary"
            >
              {splitMode ? t('form.splitTransaction') : t('form.category')}
            </label>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              disabled={isInvestmentOperation(linkedOperation)}
              className={`flex items-center gap-1.5 text-xs ${
                splitMode
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
              title={
                splitMode
                  ? 'Switch back to single category'
                  : 'Split this transaction across categories'
              }
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
                    id="categoryId"
                    value={field.value}
                    onValueChange={value => {
                      // Clear auto-fill indicator if user manually changes category
                      if (autoFilledCategory && value !== autoFilledCategory) {
                        setAutoFilledCategory(null);
                        setAutoFilledFromPayee(null);
                      }
                      field.onChange(value);
                    }}
                    placeholder={t('form.selectCategory')}
                    allowNone={true}
                    allowCreateNew={true}
                    allowCreateInline
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
                    {t('form.autoFilledFrom')}{' '}
                    <span className="font-medium">{autoFilledFromPayee}</span>
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setAutoFilledCategory(null);
                      setAutoFilledFromPayee(null);
                    }}
                    className="rounded-sm hover:bg-emerald-500/20 p-0.5 transition-colors"
                    aria-label={t('form.dismissAutoFill')}
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
              loanPayment={
                linkedLiability && (watch('movementType') ?? 'REPAYMENT') === 'REPAYMENT'
              }
              totalAmount={watch('amount') || 0}
              currency={inputCurrency || DEFAULT_CURRENCY}
              accountCurrency={accountCurrency}
              exchangeRate={needsConversion ? effectiveRate : undefined}
              splits={splits}
              tagSuggestions={popularTags}
              onChange={setSplits}
            />
          )}
        </div>
      )}

      <LinkedOperationSection
        type={selectedType}
        operation={linkedOperation}
        options={operationChoices}
        onChange={changeLinkedOperation}
        open={isMovementOpen}
        onOpenChange={setIsMovementOpen}
        targetName={
          investment ? (selectedHolding?.name ?? transaction?.assetName) : selectedLiability?.name
        }
        hasError={
          hasMovementErrors ||
          !!investmentError ||
          !!linkedOperationError ||
          !!historicalQuoteFailed
        }
        splitMode={splitMode}
        disabled={isLoading}
      >
        {isInvestmentOperation(linkedOperation) && (
          <InvestmentMovementFields
            value={investment}
            onChange={setInvestment}
            operation={linkedOperation}
            accountId={selectedAccountId}
            assets={investmentAssets}
            originalAssetName={transaction?.assetName}
            quote={investmentQuote.data}
            quoteLoading={!investmentQuote.isSaved && investmentQuote.isFetching}
            quoteError={!investmentQuote.isSaved && investmentQuote.isError}
            savedQuote={investmentQuote.isSaved}
            error={investmentError}
            disabled={isLoading}
          />
        )}
        {linkedOperation === 'LOAN_PAYMENT' && (
          <div className="space-y-1.5">
            <label htmlFor="liabilityId" className="block text-sm font-medium text-text-secondary">
              {t('linkedOperation.loan')}
            </label>
            <Controller
              name="liabilityId"
              control={control}
              render={({ field }) => (
                <LiabilitySelector
                  id="liabilityId"
                  value={field.value}
                  disabled={isLoading}
                  allowNone={false}
                  onValueChange={value => {
                    if (value !== field.value) {
                      setValue('trancheId', undefined);
                      setValue('principalAmount', undefined);
                      setValue('movementType', undefined);
                      setApplyRepaymentSplit(!transaction && !splitMode);
                    }
                    field.onChange(value);
                  }}
                  placeholder={t('form.selectLiability')}
                />
              )}
            />
            {(errors.liabilityId || linkedOperationError) && (
              <p className="text-sm text-error" role="alert">
                {errors.liabilityId?.message ?? linkedOperationError}
              </p>
            )}
            {liabilityIdValue && (
              <div className="pt-2">
                <label
                  htmlFor="loanPaymentType"
                  className="mb-1.5 block text-sm font-medium text-text-secondary"
                >
                  {t('form.loanPaymentType')}
                </label>
                <select
                  id="loanPaymentType"
                  className="w-full h-10 px-3 rounded-lg border border-border bg-surface text-text-primary focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
                  value={watch('movementType') ?? 'REPAYMENT'}
                  onChange={event => {
                    setValue(
                      'movementType',
                      event.target.value as 'REPAYMENT' | 'INTEREST' | 'INSURANCE' | 'FEE',
                      { shouldDirty: true }
                    );
                    setValue('principalAmount', undefined, { shouldDirty: true });
                    setApplyRepaymentSplit(false);
                  }}
                >
                  {(['REPAYMENT', 'INTEREST', 'INSURANCE', 'FEE'] as const).map(type => (
                    <option key={type} value={type}>
                      {t(`form.loanPaymentTypes.${type}`)}
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>
        )}
        {(linkedOperation === 'MAINTENANCE' ||
          linkedOperation === 'CAPITAL_IMPROVEMENT' ||
          (linkedOperation === 'EXISTING' && (watch('realEstateId') || watch('assetId')))) && (
          <MovementSection
            movementType={watch('movementType')}
            date={watch('date')}
            realEstateId={watch('realEstateId')}
            assetId={watch('assetId')}
            onChange={target => {
              setValue('realEstateId', target.realEstateId, { shouldDirty: true });
              setValue('assetId', target.assetId, { shouldDirty: true });
              void trigger(['movementType', 'realEstateId', 'assetId']);
            }}
            error={
              errors.movementType?.message ??
              errors.realEstateId?.message ??
              errors.assetId?.message
            }
            disabled={isLoading || linkedOperation === 'EXISTING'}
          />
        )}
        {linkedOperation === 'CAPITAL_IMPROVEMENT' && (
          <Controller
            name="valuationChange"
            control={control}
            render={({ field }) => (
              <ImprovementValuationFields
                amount={watch('amount')}
                currency={watch('currency')}
                cashEffect={
                  needsConversion
                    ? { amount: convertedPreview ?? null, currency: accountCurrency }
                    : undefined
                }
                value={field.value ?? '0'}
                onChange={field.onChange}
                disabled={isLoading}
                error={errors.valuationChange?.message}
              />
            )}
          />
        )}
        {linkedOperation === 'EXISTING' && watch('liabilityId') && (
          <div className="space-y-1.5">
            <p className="text-sm text-text-secondary">{t('linkedOperation.loan')}</p>
            <LiabilitySelector value={watch('liabilityId')} onValueChange={() => {}} disabled />
          </div>
        )}
        {(linkedOperation !== 'NONE' || selectedType === 'TRANSFER') && (
          <div
            className="space-y-3 border-t border-border/60 pt-3"
            role="region"
            aria-label={t('linkedOperation.effect')}
          >
            <p className="text-sm text-text-secondary">
              {t(
                selectedType === 'TRANSFER'
                  ? linkedOperation === 'REMOVE'
                    ? 'linkedOperation.effects.holdingTransfer'
                    : 'linkedOperation.effects.cashTransfer'
                  : `linkedOperation.effects.${linkedOperation}`,
                {
                  holding:
                    selectedHolding?.name ??
                    transaction?.assetName ??
                    t('linkedOperation.selectedHolding'),
                  destination:
                    accounts.find(account => account.id === watch('toAccountId'))?.name ??
                    t('linkedOperation.destinationAccount'),
                }
              )}
            </p>
            {/* Repayment auto-split preview (Task 5) — read-only breakdown of the repayment total,
           expressed in the LIABILITY currency (FX totals converted client-side since Task 9).
           When the Apply split toggle is on (default) the submit includes the preview legs as a
           splits[] payload: principal uncategorized, interest/insurance categorized. */}
            {liabilityIdValue &&
              !splitMode &&
              liabilityPreviewTotal != null &&
              (watch('movementType') ?? 'REPAYMENT') === 'REPAYMENT' && (
                <div
                  data-testid="repayment-preview"
                  className="rounded-lg border border-border bg-surface p-3 space-y-1.5"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-medium text-text-primary">
                      {t('form.repaymentPreviewTitle')}
                      {liabilityCurrency ? ` (${liabilityCurrency})` : ''}
                    </p>
                    <label className="flex items-center gap-1.5 text-xs text-text-secondary cursor-pointer">
                      <input
                        type="checkbox"
                        checked={
                          applyRepaymentSplit && !repaymentNeedsActual && !repaymentPreviewError
                        }
                        onChange={e => setApplyRepaymentSplit(e.target.checked)}
                        className="h-3.5 w-3.5 rounded border-border bg-surface text-primary focus:ring-2 focus:ring-primary"
                        disabled={
                          isLoading ||
                          repaymentLoading ||
                          repaymentNeedsActual ||
                          !!repaymentPreviewError
                        }
                      />
                      {t('form.repaymentApplySplit')}
                    </label>
                  </div>
                  <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm text-text-secondary">
                    <dt>{t('form.repaymentPreviewPrincipal')}</dt>
                    <dd className="text-right text-text-primary">
                      {repaymentPreview
                        ? formatDecimal(
                            repaymentPreview?.principal ?? 0,
                            getCurrencyDecimals(liabilityCurrency)
                          )
                        : '—'}
                    </dd>
                    <dt>{t('form.repaymentPreviewInterest')}</dt>
                    <dd className="text-right text-text-primary">
                      {repaymentPreview
                        ? formatDecimal(
                            repaymentPreview?.interest ?? 0,
                            getCurrencyDecimals(liabilityCurrency)
                          )
                        : '—'}
                    </dd>
                    <dt>{t('form.repaymentPreviewInsurance')}</dt>
                    <dd className="text-right text-text-primary">
                      {repaymentPreview
                        ? formatDecimal(
                            repaymentPreview?.insurance ?? 0,
                            getCurrencyDecimals(liabilityCurrency)
                          )
                        : '—'}
                    </dd>
                  </dl>
                  {repaymentNeedsActual && !repaymentLoading && (
                    <p role="status" className="text-sm text-text-secondary">
                      {t(`form.repaymentStatus.${repaymentPreview.status}`)}
                    </p>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={enterActualRepayment}
                    disabled={isLoading}
                  >
                    {t('form.repaymentEnterActual')}
                  </Button>
                  {repaymentPreview &&
                    selectedLiability &&
                    repaymentPreview.principal > selectedLiability.currentBalance && (
                      <p role="alert" className="text-error">
                        {t('form.repaymentExceedsDebt')}
                      </p>
                    )}
                  {repaymentPreviewError && applyRepaymentSplit && (
                    <p role="alert" className="text-sm text-error">
                      {t('form.repaymentUnavailable')}
                    </p>
                  )}
                  {!applyRepaymentSplit && !splitMode && (
                    <div>
                      <label htmlFor="principalAmount">
                        {t('form.manualPrincipal', { currency: liabilityCurrency })}
                      </label>
                      <Input
                        id="principalAmount"
                        type="number"
                        min="0"
                        step="0.01"
                        {...register('principalAmount')}
                        error={errors.principalAmount?.message}
                      />
                      <p className="text-xs text-text-secondary">{t('form.manualPrincipalHint')}</p>
                    </div>
                  )}
                  {errors.principalAmount && applyRepaymentSplit && (
                    <p role="alert" className="text-sm text-error">
                      {errors.principalAmount.message}
                    </p>
                  )}
                </div>
              )}
          </div>
        )}
      </LinkedOperationSection>

      {/* ── Zone 4: free text — description, tags, notes ──────────────────── */}
      <div aria-hidden="true" className="border-t border-border/60" />

      {/* Row 5: Description and Tags */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Description */}
        <div>
          <label
            htmlFor="description"
            className="block text-sm font-medium text-text-secondary mb-1.5"
          >
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
          <div className="flex items-center gap-1 mb-1.5">
            <label className="block text-sm font-medium text-text-secondary">
              {t('form.tags')}
            </label>
            <HelpTooltip text={t('form.tagsHint')} side="right" />
          </div>
          <TagInput
            aria-label={t('form.tags')}
            value={tags}
            onChange={setTags}
            suggestions={popularTags}
            placeholder={t('form.tagsPlaceholder')}
            maxTags={10}
            disabled={isLoading}
          />
        </div>
      </div>

      {/* Notes */}
      <div>
        <label htmlFor="notes" className="block text-sm font-medium text-text-secondary mb-1.5">
          {t('form.notes')}
        </label>
        <textarea
          id="notes"
          {...register('notes')}
          rows={3}
          placeholder={t('form.notesPlaceholder')}
          aria-invalid={errors.notes ? 'true' : 'false'}
          aria-describedby={errors.notes ? 'notes-error' : undefined}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-muted text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150 resize-none"
        />
        {errors.notes && (
          <p id="notes-error" className="mt-1 text-sm text-error" role="alert">
            {errors.notes.message}
          </p>
        )}
      </div>

      {/* Actions */}
      <div className="sticky bottom-[-25px] -mx-6 mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end sm:gap-3 border-t border-border bg-surface px-6 py-4">
        <Button variant="ghost" type="button" onClick={cancelWithGuard} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button
          variant="primary"
          type="submit"
          isLoading={isLoading}
          disabled={investmentBlocked || !!linkedOperationError}
        >
          {isEditing ? t('form.updateTransaction') : t('form.createTransaction')}
        </Button>
      </div>
    </form>
  );
}
