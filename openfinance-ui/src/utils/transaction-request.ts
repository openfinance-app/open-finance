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
import {
  divide,
  distributeRemainder,
  multiply,
  roundToDecimals,
  sumToDecimals,
} from '@/utils/money';

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
      amount: s.amount,
      description: s.description,
    })) ?? [];

  const rate = transaction?.conversionRate;
  const originalCurrency = transaction?.originalCurrency;
  const originalAmount = transaction?.originalAmount;
  if (!rate || rate <= 0 || !originalCurrency || originalAmount == null || rows.length === 0) {
    return rows;
  }

  const decimals = getMonetaryScale(originalCurrency);
  const reconstructed = rows.map((s, i) =>
    i < rows.length - 1
      ? { ...s, amount: roundToDecimals(divide(s.amount, rate), decimals) }
      : { ...s }
  );
  const sumOthers = sumToDecimals(
    reconstructed.slice(0, -1).map(s => s.amount),
    decimals
  );
  reconstructed[reconstructed.length - 1] = {
    ...reconstructed[reconstructed.length - 1],
    amount: roundToDecimals(originalAmount - sumOthers, decimals),
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
  originalAmount?: number;
  originalCurrency?: string;
  conversionRate?: number;
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
  preview: Pick<RepaymentPreview, 'interest' | 'insurance' | 'principal'>,
  submitAmount: number,
  liabToAccount: number,
  categories: Category[],
  decimals: number,
  principalCategoryId?: number
): RepaymentAutoSplit {
  const interestLeg = roundToDecimals(multiply(preview.interest ?? 0, liabToAccount), decimals);
  const insuranceLeg = roundToDecimals(multiply(preview.insurance ?? 0, liabToAccount), decimals);
  const principalLeg = roundToDecimals(multiply(preview.principal ?? 0, liabToAccount), decimals);
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
  if (principalLeg > 0) {
    principalRow = {
      amount: principalLeg,
      categoryId: principalCategoryId,
      description: undefined,
    };
    rows.push(principalRow);
  }
  if (interestLeg > 0) {
    rows.push({ amount: interestLeg, categoryId: interestCategoryId, description: undefined });
  }
  if (insuranceLeg > 0) {
    rows.push({
      amount: insuranceLeg,
      categoryId: insuranceCategoryId,
      description: undefined,
    });
  }

  if (rows.length >= 2 && rows.some(r => r.categoryId != null)) {
    // Clamp so Σsplits == submitAmount exactly: the principal leg absorbs the remainder when
    // present, otherwise the largest categorized leg does (underpayment with principal 0).
    const sumRows = roundToDecimals(
      rows.reduce((acc, r) => acc + r.amount, 0),
      decimals
    );
    const remainder = roundToDecimals(submitAmount - sumRows, decimals);
    if (remainder !== 0) {
      let absorbIndex = principalRow ? rows.indexOf(principalRow) : -1;
      if (absorbIndex < 0) {
        absorbIndex = rows.reduce(
          (maxIdx, r, i) => (r.amount > rows[maxIdx].amount ? i : maxIdx),
          0
        );
      }
      rows[absorbIndex] = {
        ...rows[absorbIndex],
        amount: roundToDecimals(Math.max(rows[absorbIndex].amount + remainder, 0), decimals),
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
  submitAmount: number,
  liabilityCurrency: string,
  liabilityRate: number
): ConversionTriple {
  return {
    originalAmount: roundToDecimals(
      multiply(submitAmount, liabilityRate),
      getMonetaryScale(liabilityCurrency)
    ),
    originalCurrency: liabilityCurrency,
    conversionRate: divide(1, liabilityRate),
  };
}

interface SubmitGuardParams {
  linkedLiability: boolean;
  liabilityCurrency: string | undefined;
  inputCurrency: string;
  accountCurrency: string;
  needsConversion: boolean;
  rate: number | undefined;
  needsLiabilityFx: boolean;
  liabilityRate: number | undefined;
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
  rate?: number;
  needsLiabilityFx: boolean;
  liabilityCurrency?: string;
  liabilityRate?: number;
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
  const convert = (value: number): number =>
    needsConversion && rate ? roundToDecimals(multiply(value, rate), decimals) : Number(value);

  const inSplit = splitMode && splits.length > 0;
  const submitAmount = convert(data.amount);
  const convertedAmounts = inSplit ? splits.map(split => convert(split.amount)) : [];
  const allocatedAmounts =
    needsConversion && inSplit
      ? distributeRemainder(submitAmount, convertedAmounts, decimals)
      : convertedAmounts;
  const submitSplits = inSplit
    ? splits.map((split, index) => ({ ...split, amount: allocatedAmounts[index] }))
    : undefined;

  let conversion: ConversionTriple =
    needsConversion && rate
      ? {
          originalAmount: Number(data.amount),
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
    // Always submit in the account's currency (backend requires currency === account currency
    // for INCOME/EXPENSE). For TRANSFER this normalizes any stale input-currency selection
    // back to the source account's currency; needsConversion is false for TRANSFER so the
    // amount is unchanged.
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
    tags: tags.length > 0 ? tags : undefined,
    paymentMethod: data.paymentMethod || undefined,
    // Requirement 3.1: Only include liabilityId for EXPENSE transactions
    liabilityId: carriesLoan ? data.liabilityId : undefined,
    trancheId: carriesLoan && data.liabilityId ? data.trancheId : undefined,
    principalAmount:
      data.liabilityId && data.type === 'EXPENSE'
        ? inSplit
          ? undefined
          : applyRepaymentSplit && repaymentPreview
            ? repaymentPreview.principal
            : data.principalAmount
        : undefined,
    // Manual movement entry: only include the instrument links for EXPENSE
    realEstateId: data.type === 'EXPENSE' ? data.realEstateId : undefined,
    assetId: data.type === 'EXPENSE' ? data.assetId : undefined,
    movementType:
      carriesLoan && data.liabilityId
        ? (data.movementType ?? (data.type === 'INCOME' ? 'DISBURSEMENT' : 'REPAYMENT'))
        : data.type === 'EXPENSE'
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
      amount: stored.amount,
      currency: stored.currency,
      originalAmount: stored.originalAmount,
      originalCurrency: stored.originalCurrency,
      conversionRate: stored.conversionRate,
      principalAmount: stored.principalAmount,
      splits: stored.hasSplits
        ? stored.splits?.map((split, index) => ({
            categoryId: split.categoryId,
            amount: split.amount,
            description: splits[index]?.description,
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
    'principalAmount',
    'realEstateId',
    'assetId',
    'movementType',
  ] as const;
  const initialSplits = reconstructInitialSplits(transaction);
  return (
    !ctx.applyRepaymentSplit &&
    fields.every(field => (data[field] ?? null) === (transaction[field] ?? null)) &&
    data.type === (transaction.transferId ? 'TRANSFER' : transaction.type) &&
    data.date === formatDateForInput(transaction.date) &&
    data.currency === (transaction.originalCurrency ?? transaction.currency) &&
    Number(data.amount) === (transaction.originalAmount ?? transaction.amount) &&
    splitMode === Boolean(transaction.hasSplits && initialSplits.length) &&
    (!splitMode ||
      (splits.length === initialSplits.length &&
        splits.every(
          (split, index) =>
            Number(split.amount) === initialSplits[index].amount &&
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
