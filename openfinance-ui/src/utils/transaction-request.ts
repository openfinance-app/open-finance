import {
  decimalValue,
  decimalField,
  equalDecimals,
  ExactDecimal,
  exactSum,
  distributeExact,
} from '@/utils/exact-decimal';
import type {
  Transaction,
  TransactionRequest,
  TransactionSplitRequest,
  Category,
  PaymentMethod,
} from '@/types/transaction';
import type { RepaymentPreview } from '@/hooks/useLiabilities';
import { formatDateForInput } from '@/utils/date';
import { getMonetaryScale } from '@/utils/currency';

/**
 * Builds the initial split rows for the form. For a converted transaction (originalCurrency set),
 * the stored split amounts are in the ACCOUNT currency, so each split's original amount is
 * reconstructed by dividing by the stored conversionRate; the last split is set to
 * originalAmount − sum(others) so the reconstructed splits sum exactly to originalAmount.
 */
export function reconstructInitialSplits(transaction?: Transaction): TransactionSplitRequest[] {
  const rows: TransactionSplitRequest[] =
    transaction?.splits?.map(s => ({
      categoryId: s.categoryId,
      amount: decimalValue(s, 'amount') ?? 0,
      description: s.description,
      tags: s.tags ?? [],
    })) ?? [];

  const rate = transaction && decimalValue(transaction, 'conversionRate');
  const originalCurrency = transaction?.originalCurrency;
  const originalAmount = transaction && decimalValue(transaction, 'originalAmount');
  if (
    !rate ||
    Number(rate) <= 0 ||
    !originalCurrency ||
    originalAmount == null ||
    rows.length === 0
  ) {
    return rows;
  }

  const decimals = getMonetaryScale(originalCurrency);
  const reconstructed = rows.map((s, i) =>
    i < rows.length - 1
      ? {
          ...s,
          amount: moneyResult(
            new ExactDecimal(s.amount).div(rate).toDecimalPlaces(decimals),
            originalAmount
          ),
        }
      : { ...s }
  );
  const sumOthers = exactSum(reconstructed.slice(0, -1).map(s => s.amount));
  reconstructed[reconstructed.length - 1] = {
    ...reconstructed[reconstructed.length - 1],
    amount: moneyResult(
      new ExactDecimal(originalAmount).minus(sumOthers).toDecimalPlaces(decimals),
      originalAmount
    ),
  };
  return reconstructed;
}

/**
 * Resolves a seeded repayment-split category (Interest / Insurance) from the user's category
 * list: system categories carry a stable {@code nameKey}; the name fallbacks cover older seeds
 * and localized display names.
 */
function findRepaymentCategoryId(
  categories: Category[],
  nameKey: string,
  nameFallbacks: string[]
): number | undefined {
  const byKey = categories.find(c => c.nameKey === nameKey);
  if (byKey) return byKey.id;
  const lower = nameFallbacks.map(n => n.toLowerCase());
  const byName = categories.find(c => c.name && lower.includes(c.name.toLowerCase()));
  return byName?.id;
}

/** Stored conversion fields carried by the submit payload (undefined when no conversion). */
interface ConversionTriple {
  originalAmount?: number | string;
  originalCurrency?: string;
  conversionRate?: number | string;
}

/** Result of the repayment auto-split payload construction. */
interface RepaymentAutoSplit {
  splits?: TransactionSplitRequest[];
  categoryId?: number;
}

/**
 * Repayment auto-split payload (Task 9): builds the preview legs — interest/insurance
 * categorized, the (uncategorized) principal leg absorbing the remainder so the split sum
 * matches the parent amount exactly. A single surviving categorized row is returned as a
 * parent-level category (no splits[]).
 */
function buildRepaymentAutoSplit(
  preview: Pick<RepaymentPreview, 'interest' | 'insurance' | 'principal' | 'exactValues'>,
  submitAmount: number | string,
  liabToAccount: number | string,
  categories: Category[],
  decimals: number,
  principalCategoryId?: number
): RepaymentAutoSplit {
  const interestLeg = moneyResult(
    new ExactDecimal(decimalField(preview, 'interest', '0'))
      .times(liabToAccount)
      .toDecimalPlaces(decimals),
    submitAmount
  );
  const insuranceLeg = moneyResult(
    new ExactDecimal(decimalField(preview, 'insurance', '0'))
      .times(liabToAccount)
      .toDecimalPlaces(decimals),
    submitAmount
  );
  const principalLeg = moneyResult(
    new ExactDecimal(decimalField(preview, 'principal', '0'))
      .times(liabToAccount)
      .toDecimalPlaces(decimals),
    submitAmount
  );
  const interestCategoryId = findRepaymentCategoryId(categories, 'category.interest.expense', [
    'interest',
    'intérêts',
    'interets',
  ]);
  const insuranceCategoryId = findRepaymentCategoryId(categories, 'category.insurance', [
    'insurance',
    'assurances',
  ]);
  const rows: TransactionSplitRequest[] = [];
  let principalRow: TransactionSplitRequest | undefined;
  if (new ExactDecimal(principalLeg).gt(0)) {
    principalRow = {
      amount: principalLeg,
      categoryId: principalCategoryId,
      description: undefined,
    };
    rows.push(principalRow);
  }
  if (new ExactDecimal(interestLeg).gt(0)) {
    rows.push({ amount: interestLeg, categoryId: interestCategoryId, description: undefined });
  }
  if (new ExactDecimal(insuranceLeg).gt(0)) {
    rows.push({
      amount: insuranceLeg,
      categoryId: insuranceCategoryId,
      description: undefined,
    });
  }

  if (rows.length >= 2 && rows.some(r => r.categoryId != null)) {
    // Clamp so Σsplits == submitAmount exactly: the principal leg absorbs the remainder when
    // present, otherwise the largest categorized leg does (underpayment with principal 0).
    const sumRows = exactSum(rows.map(r => r.amount));
    const remainder = new ExactDecimal(submitAmount).minus(sumRows).toDecimalPlaces(decimals);
    if (!remainder.isZero()) {
      let absorbIndex = principalRow ? rows.indexOf(principalRow) : -1;
      if (absorbIndex < 0) {
        absorbIndex = rows.reduce(
          (maxIdx, r, i) => (new ExactDecimal(r.amount).gt(rows[maxIdx].amount) ? i : maxIdx),
          0
        );
      }
      rows[absorbIndex] = {
        ...rows[absorbIndex],
        amount: moneyResult(
          ExactDecimal.max(
            new ExactDecimal(rows[absorbIndex].amount).plus(remainder),
            0
          ).toDecimalPlaces(decimals),
          submitAmount
        ),
      };
    }
    return { splits: rows };
  }
  if (rows.length === 1 && rows[0].categoryId != null) {
    return { categoryId: rows[0].categoryId };
  }
  return {};
}

/**
 * Liability FX override (Task 9): when the input is the ACCOUNT currency and the liability
 * currency differs, the stored conversion triple must carry the LIABILITY view
 * (originalAmount × rate ≈ amount) so the backend liability leg moves in the liability
 * currency. When the input IS the liability currency the account-conversion triple already
 * holds that view.
 */
function withLiabilityFxOverride(
  submitAmount: number | string,
  liabilityCurrency: string,
  liabilityRate: number | string
): ConversionTriple {
  return {
    originalAmount: moneyResult(
      new ExactDecimal(submitAmount)
        .times(liabilityRate)
        .toDecimalPlaces(getMonetaryScale(liabilityCurrency)),
      submitAmount
    ),
    originalCurrency: liabilityCurrency,
    conversionRate: moneyResult(
      new ExactDecimal(1).div(liabilityRate).toDecimalPlaces(18),
      submitAmount
    ),
  };
}

interface SubmitGuardParams {
  linkedLiability: boolean;
  liabilityCurrency: string | undefined;
  inputCurrency: string;
  accountCurrency: string;
  needsConversion: boolean;
  rate: number | string | undefined;
  needsLiabilityFx: boolean;
  liabilityRate: number | string | undefined;
}

/** Inputs needed to build the submit payload from the live form state. */
interface SubmitContext {
  data: Omit<TransactionRequest, 'paymentMethod'> & { paymentMethod?: PaymentMethod | '' };
  transaction?: Transaction;
  splits: TransactionSplitRequest[];
  splitMode: boolean;
  tags: string[];
  categories: Category[];
  inputCurrency: string;
  accountCurrency: string;
  needsConversion: boolean;
  rate?: number | string;
  needsLiabilityFx: boolean;
  liabilityCurrency?: string;
  liabilityRate?: number | string;
  applyRepaymentSplit: boolean;
  repaymentPreview?: RepaymentPreview;
  linkedLiability: boolean;
}

/**
 * Builds the final {@link TransactionRequest}: converts the entered amount and any split
 * amounts into the account currency, resolves the stored conversion triple (with the
 * liability-FX override), applies the repayment auto-split legs when enabled and assembles
 * the payload. Pure with respect to the passed context.
 */
export function buildTransactionRequest(ctx: SubmitContext): TransactionRequest {
  const {
    data,
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
  } = ctx;
  const decimals = getMonetaryScale(accountCurrency);
  const convert = (value: number | string): number | string =>
    needsConversion && rate
      ? moneyResult(new ExactDecimal(value).times(rate).toDecimalPlaces(decimals), value)
      : value;

  const inSplit = splitMode && splits.length > 0;
  const submitAmount = convert(data.amount);
  const convertedAmounts = inSplit ? splits.map(split => convert(split.amount)) : [];
  const allocatedAmounts =
    needsConversion && inSplit
      ? distributeExact(submitAmount, convertedAmounts, decimals).map(value =>
          moneyResult(new ExactDecimal(value), data.amount)
        )
      : convertedAmounts;
  const submitSplits = inSplit
    ? splits.map((split, index) => ({ ...split, amount: allocatedAmounts[index] }))
    : undefined;

  let conversion: ConversionTriple =
    needsConversion && rate
      ? {
          originalAmount: data.amount,
          originalCurrency: inputCurrency,
          conversionRate: rate,
        }
      : {};
  if (needsLiabilityFx && inputCurrency === accountCurrency && liabilityRate && liabilityCurrency) {
    conversion = withLiabilityFxOverride(submitAmount, liabilityCurrency, liabilityRate);
  }

  // Repayment auto-split (Task 9): submit the preview legs when the toggle is on and the
  // user did not enter manual splits.
  let finalSplits = submitSplits;
  let autoSplitCategoryId: number | undefined;
  if (!inSplit && applyRepaymentSplit && repaymentPreview && linkedLiability) {
    const liabToAccount =
      needsLiabilityFx && conversion.conversionRate ? conversion.conversionRate : 1;
    const autoSplit = buildRepaymentAutoSplit(
      repaymentPreview,
      submitAmount,
      liabToAccount,
      categories,
      decimals,
      data.categoryId
    );
    finalSplits = autoSplit.splits;
    autoSplitCategoryId = autoSplit.categoryId;
  }

  const carriesLoan =
    data.type === 'EXPENSE' || (data.type === 'INCOME' && data.movementType === 'DISBURSEMENT');
  const request: TransactionRequest = {
    accountId: data.accountId,
    toAccountId: data.toAccountId,
    type: data.type,
    amount: submitAmount,
    // The amount and its original-currency metadata always describe the same conversion,
    // including when the input belongs to a transfer.
    currency: accountCurrency,
    originalAmount: conversion.originalAmount,
    originalCurrency: conversion.originalCurrency,
    conversionRate: conversion.conversionRate,
    // REQ-SPL-1.5: hide parent category when split mode is active; a lone auto-split
    // categorized leg is applied at the parent level when the user picked no category
    categoryId: finalSplits ? undefined : (data.categoryId ?? autoSplitCategoryId),
    date: data.date,
    description: data.description || '',
    notes: data.notes || '',
    payee: data.payee || undefined,
    // Keep an explicit empty array so editing can remove the last tag.
    tags,
    paymentMethod: data.paymentMethod || undefined,
    // Requirement 3.1: Only include liabilityId for EXPENSE transactions
    liabilityId: carriesLoan ? data.liabilityId : undefined,
    trancheId: carriesLoan && data.liabilityId ? data.trancheId : undefined,
    principalAmount:
      data.liabilityId && data.type === 'EXPENSE'
        ? inSplit
          ? undefined
          : applyRepaymentSplit && repaymentPreview
            ? decimalValue(repaymentPreview, 'principal')
            : data.principalAmount
        : undefined,
    // Manual movement entry: only include the instrument links for EXPENSE
    realEstateId: data.type === 'EXPENSE' ? data.realEstateId : undefined,
    assetId: data.type === 'EXPENSE' ? data.assetId : undefined,
    movementType:
      carriesLoan && data.liabilityId
        ? (data.movementType ?? (data.type === 'INCOME' ? 'DISBURSEMENT' : 'REPAYMENT'))
        : data.type === 'EXPENSE' || (data.type === 'INCOME' && data.movementType === 'INTEREST')
          ? data.movementType
          : undefined,
    // REQ-SPL-2.1, REQ-SPL-2.2: include splits when split mode is active
    splits: finalSplits,
  };
  // Metadata edits must not reconstruct money through an approximate historical FX rate.
  // This also preserves older bookings whose original conversion rate was rounded.
  if (ctx.transaction && hasUnchangedBooking(ctx, ctx.transaction)) {
    const stored = ctx.transaction;
    return {
      ...request,
      amount: decimalValue(stored, 'amount') ?? 0,
      currency: stored.currency,
      originalAmount: decimalValue(stored, 'originalAmount'),
      originalCurrency: stored.originalCurrency,
      conversionRate: decimalValue(stored, 'conversionRate'),
      principalAmount: decimalValue(stored, 'principalAmount'),
      splits: stored.hasSplits
        ? stored.splits?.map((split, index) => ({
            categoryId: split.categoryId,
            amount: decimalValue(split, 'amount') ?? 0,
            description: splits[index]?.description,
            tags: splits[index]?.tags ?? [],
          }))
        : undefined,
    };
  }
  return request;
}

function hasUnchangedBooking(ctx: SubmitContext, transaction: Transaction): boolean {
  const { data, splitMode, splits } = ctx;
  const fields = [
    'accountId',
    'toAccountId',
    'categoryId',
    'liabilityId',
    'trancheId',
    'realEstateId',
    'assetId',
  ] as const;
  // Marking an account's income as bank interest changes classification only. Preserve its
  // exact booked amount, including imported FX amounts whose stored rate was rounded.
  const savingsClassificationOnly =
    data.type === 'INCOME' &&
    transaction.type === 'INCOME' &&
    !data.liabilityId &&
    !transaction.liabilityId &&
    !data.assetId &&
    !transaction.assetId &&
    !data.realEstateId &&
    !transaction.realEstateId &&
    (data.movementType == null || data.movementType === 'INTEREST') &&
    (transaction.movementType == null || transaction.movementType === 'INTEREST');
  const initialSplits = reconstructInitialSplits(transaction);
  return (
    !ctx.applyRepaymentSplit &&
    fields.every(field => (data[field] ?? null) === (transaction[field] ?? null)) &&
    (savingsClassificationOnly ||
      (data.movementType ?? null) === (transaction.movementType ?? null)) &&
    data.type === (transaction.transferId ? 'TRANSFER' : transaction.type) &&
    data.date === formatDateForInput(transaction.date) &&
    data.currency === (transaction.originalCurrency ?? transaction.currency) &&
    equalDecimals(
      data.amount,
      decimalField(transaction, transaction.originalAmount == null ? 'amount' : 'originalAmount')
    ) &&
    (data.principalAmount == null || transaction.principalAmount == null
      ? (data.principalAmount ?? null) === (transaction.principalAmount ?? null)
      : equalDecimals(data.principalAmount, decimalField(transaction, 'principalAmount'))) &&
    splitMode === Boolean(transaction.hasSplits && initialSplits.length) &&
    (!splitMode ||
      (splits.length === initialSplits.length &&
        splits.every(
          (split, index) =>
            equalDecimals(split.amount, initialSplits[index].amount) &&
            (split.categoryId ?? null) === (initialSplits[index].categoryId ?? null)
        )))
  );
}

/** Returns the validation message key blocking the submit, or undefined when clear to send. */
export function submitGuardError({
  linkedLiability,
  liabilityCurrency,
  inputCurrency,
  accountCurrency,
  needsConversion,
  rate,
  needsLiabilityFx,
  liabilityRate,
}: SubmitGuardParams): string | undefined {
  // A linked liability's legs move in the liability currency, so the entered amount must be in
  // the account currency or the liability currency — anything else has no coherent view.
  if (
    linkedLiability &&
    liabilityCurrency &&
    inputCurrency !== accountCurrency &&
    inputCurrency !== liabilityCurrency
  ) {
    return 'form.validation.liabilityCurrencyUnsupported';
  }
  if (needsConversion && !rate) {
    return 'form.validation.rateUnavailable';
  }
  // Liability FX: on the account-currency input path the account → liability rate is needed
  // to store the liability view in the conversion fields.
  if (needsLiabilityFx && inputCurrency === accountCurrency && !liabilityRate) {
    return 'form.validation.rateUnavailable';
  }
  return undefined;
}

function moneyResult(
  value: InstanceType<typeof ExactDecimal>,
  source: number | string
): number | string {
  return typeof source === 'number' ? value.toNumber() : value.toFixed();
}
