import { LinkedText } from '@/components/ui/LinkedText';
import './import-review.css';
import { decimalField, decimalValue, equalDecimals } from '@/utils/exact-decimal';
/**
 * ImportReview Component
 *
 * Step 3 of the Import Wizard — Review Transactions.
 *
 * Responsibilities:
 * - Display all parsed transactions in a reviewable table
 * - Auto-assign categories and payees based on transaction history patterns
 * - Allow per-row corrections before confirmation
 * - Apply corrections to selected transactions in bulk
 * - Filter view by All / Duplicates / Errors
 * - Expand split transactions inline
 * - Expose category mappings (sourceCategory → categoryId) to the parent wizard
 */
import React, { useState, useMemo, useCallback, useEffect, useId } from 'react';
import { Badge } from '@/components/ui/Badge';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { useTranslation } from 'react-i18next';
import { CategorySelect } from '@/components/ui/CategorySelect';
import { CategoryCombobox } from '@/components/ui/CategorySelect';
import { PayeeCombobox } from '@/components/ui/PayeeSelector';
import {
  AlertCircle,
  ChevronDown,
  ChevronRight,
  Edit2,
  Check,
  X,
  Sparkles,
  Tag,
  Info,
  ListTree,
} from 'lucide-react';
import { useCategories } from '@/hooks/useCategories';
import { useToday } from '@/hooks/useReportingCalendar';
import { useUserSettings, useBaseCurrency } from '@/hooks/useUserSettings';
import { DEFAULT_CURRENCY, getCurrencySymbol } from '@/utils/currency';
import { formatDate as globalFormatDate } from '@/utils/date';
import { translateCategoryName } from '@/utils/categoryTranslation';
import {
  isSplitAmountError,
  MAX_IMPORT_AMOUNT,
  reviewTransferPartners,
  reviewedAmountChanges,
  shareTransferAmount,
  reviewFieldErrorKey,
  updateReviewedDateAndAmount,
} from '@/utils/import-review';
import { categoryPath, matchingCategories, type CategoryPathNode } from '@/utils/category-path';
import { ImportReviewDetails } from '@/components/import/ImportReviewDetails';
import { ImportReviewBulkEdit } from '@/components/import/ImportReviewBulkEdit';
import type { ImportTransactionDTO } from '@/types/import';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ImportReviewProps {
  transactions: ImportTransactionDTO[];
  sourceAccountNames?: Record<string, string>;
  onTransactionsChange: (transactions: ImportTransactionDTO[]) => void;
  /** Category mappings collected from the review step (sourceCategory → categoryId) */
  categoryMappings: Record<string, number>;
  onCategoryMappingsChange: (mappings: Record<string, number>) => void;
  /** Source-category names that don't exist in DB yet and should be created on confirm */
  newCategoryNames: string[];
  onNewCategoryNamesChange: (names: string[]) => void;
}

type FilterType = 'all' | 'duplicates' | 'errors' | 'uncategorized' | 'auto_assigned';

interface EditState {
  transactionDate: string;
  amount: string;
  category: string;
  payee: string;
  memo: string;
  description?: string | null;
}

const INFO_PREFIXES = [
  'AUTO-MATCH:',
  'AI_MATCH:',
  'AI_UNAVAILABLE:',
  'CATEGORY_SUGGESTION:',
  'CATEGORY_UNKNOWN:',
  'DUPLICATE:',
  'RULE_MATCH:',
  'RULE_SKIP:',
];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Canonicalize supplied categories; preserve the backend's decision to abstain. */
function autoAssignCategory(
  transaction: ImportTransactionDTO,
  categories: CategoryPathNode[]
): string | null {
  if (transaction.category?.trim()) {
    const matches = matchingCategories(categories, transaction.category);
    return matches.length === 1 ? categoryPath(categories, matches[0].id) : transaction.category;
  }

  return null;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ImportReview({
  transactions,
  sourceAccountNames = {},
  onTransactionsChange,
  categoryMappings,
  onCategoryMappingsChange,
  newCategoryNames,
  onNewCategoryNamesChange,
}: ImportReviewProps) {
  const { t } = useTranslation('import');
  const today = useToday();
  const editFormId = useId();
  const showSource = transactions.some(transaction => transaction.sourceUploadId);
  const { t: tCategories } = useTranslation('categories');

  // Translate known backend validation error strings to the current locale
  const translateValidationError = (err: string): string => {
    if (isSplitAmountError(err)) return t('validation.splitInvalid');
    if (err.startsWith('RULE_ACTION_INVALID:')) return t('validation.ruleActionInvalid');
    if (err.startsWith('RULE_CONDITION_INVALID:')) {
      return t('validation.ruleConditionInvalid', {
        rule: err.slice('RULE_CONDITION_INVALID:'.length).trim(),
      });
    }
    const key = reviewFieldErrorKey(err);
    return key ? t(`validation.${key}`) : err;
  };

  const [filter, setFilter] = useState<FilterType>('all');
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  // Multi-edit state
  const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set());
  const [detailRow, setDetailRow] = useState<number | null>(null);
  const partners = useMemo(() => reviewTransferPartners(transactions), [transactions]);

  // Per-row inline editing
  const [editingRow, setEditingRow] = useState<number | null>(null);
  const [editState, setEditState] = useState<EditState>({
    transactionDate: '',
    amount: '',
    category: '',
    payee: '',
    memo: '',
  });
  const edited = editingRow == null ? undefined : transactions[editingRow];
  const keepSourceDate =
    edited?.transactionDate === editState.transactionDate &&
    !edited?.validationErrors.some(error => reviewFieldErrorKey(error) === 'dateFuture');
  const latestEditDate =
    keepSourceDate && editState.transactionDate > today ? editState.transactionDate : today;
  const partnerIndex = editingRow == null ? undefined : partners.get(editingRow);
  const transferMate = partnerIndex == null ? undefined : transactions[partnerIndex];
  const sharedAmount = edited && transferMate && shareTransferAmount(edited, transferMate);

  // Mapping panel: tracks which rows have "map to existing" override open
  const [mappingPanelOverride, setMappingPanelOverride] = useState<Record<string, boolean>>({});

  // Auto-assign notification
  const [autoAssignedCount, setAutoAssignedCount] = useState<number>(0);
  const [autoAssignedIndices, setAutoAssignedIndices] = useState<Set<number>>(new Set());

  const { data: categories = [] } = useCategories();
  const { data: settings } = useUserSettings();
  const { data: baseCurrency = DEFAULT_CURRENCY } = useBaseCurrency();

  // -------------------------------------------------------------------------
  // Auto-expand rows that have validation errors on first load
  // -------------------------------------------------------------------------
  useEffect(() => {
    if (transactions.length === 0) return;
    const errorIndices = transactions
      .map((t, idx) => {
        const hasRealErrors = t.validationErrors?.some(
          e => !INFO_PREFIXES.some(p => e.startsWith(p))
        );
        return hasRealErrors ? idx : -1;
      })
      .filter(idx => idx !== -1);
    if (errorIndices.length > 0) {
      setExpandedRows(new Set(errorIndices));
    }
    // Only run once when transactions first arrive
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [transactions.length > 0 ? 'loaded' : 'empty']);

  // -------------------------------------------------------------------------
  // Auto-assign on first load
  // -------------------------------------------------------------------------
  useEffect(() => {
    if (transactions.length === 0 || categories.length === 0) return;

    let changed = false;
    let count = 0;
    const newIndices = new Set<number>();
    const updated = transactions.map((t, idx) => {
      const suggested = autoAssignCategory(t, categories);
      if (suggested && suggested !== t.category) {
        changed = true;
        if (!t.category) {
          count++;
          newIndices.add(idx);
        }
        return { ...t, category: suggested };
      }
      return t;
    });

    if (changed) {
      onTransactionsChange(updated);
      setAutoAssignedCount(count);
      setAutoAssignedIndices(newIndices);
    }
    // Only run once when categories or transactions first become available
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categories.length > 0 ? 'loaded' : 'empty', transactions.length > 0 ? 'loaded' : 'empty']);

  // -------------------------------------------------------------------------
  // Derive unique source categories and sync mappings
  // -------------------------------------------------------------------------
  const uniqueCategories = useMemo(() => {
    const map = new Map<string, number>();
    transactions.forEach(t => {
      if (t.category) map.set(t.category, (map.get(t.category) ?? 0) + 1);
      if (t.splits?.length) {
        t.splits.forEach(s => {
          if (s.category) map.set(s.category, (map.get(s.category) ?? 0) + 1);
        });
      }
    });
    return Array.from(map.entries()).map(([name, count]) => ({ name, count }));
  }, [transactions]);

  // Keep categoryMappings in sync: when a category name matches an existing category, auto-wire the ID.
  // Also auto-detect unknown categories (no DB match, no existing mapping) → add to newCategoryNames.
  useEffect(() => {
    const newMappings = { ...categoryMappings };
    const newToCreate: string[] = [];
    let mappingChanged = false;

    uniqueCategories.forEach(({ name }) => {
      const matches = matchingCategories(categories, name);
      if (!(name in newMappings)) {
        if (matches.length === 1) {
          newMappings[name] = matches[0].id;
          mappingChanged = true;
        }
      }
      if (!(name in newMappings) && matches.length === 0) newToCreate.push(name);
    });

    if (mappingChanged) onCategoryMappingsChange(newMappings);
    if (JSON.stringify(newToCreate) !== JSON.stringify(newCategoryNames)) {
      onNewCategoryNamesChange(newToCreate);
    }
  }, [
    uniqueCategories,
    categories,
    categoryMappings,
    newCategoryNames,
    onCategoryMappingsChange,
    onNewCategoryNamesChange,
  ]);

  const rememberCategorySelection = (name: string, categoryId?: number) => {
    if (categoryId != null) {
      onCategoryMappingsChange({ ...categoryMappings, [name]: categoryId });
    }
  };

  // -------------------------------------------------------------------------
  // Derived data
  // -------------------------------------------------------------------------
  const transactionsWithIndex = useMemo(
    () => transactions.map((t, idx) => ({ ...t, originalIndex: idx })),
    [transactions]
  );

  const isCategorized = useCallback(
    (transaction: ImportTransactionDTO): boolean => {
      const mapped = (name: string | null) =>
        !!name &&
        (categoryMappings[name] != null ||
          matchingCategories(categories, name).length === 1 ||
          newCategoryNames.includes(name));
      return (
        mapped(transaction.category) ||
        (!!transaction.splits?.length && transaction.splits.every(split => mapped(split.category)))
      );
    },
    [categoryMappings, categories, newCategoryNames]
  );

  const filteredTransactions = useMemo(() => {
    switch (filter) {
      case 'duplicates':
        return transactionsWithIndex.filter(t => t.potentialDuplicate);
      case 'errors':
        return transactionsWithIndex.filter(t =>
          t.validationErrors.some(e => !INFO_PREFIXES.some(p => e.startsWith(p)))
        );
      case 'uncategorized':
        return transactionsWithIndex.filter(t => !isCategorized(t));
      case 'auto_assigned':
        return transactionsWithIndex.filter(t => autoAssignedIndices.has(t.originalIndex));
      default:
        return transactionsWithIndex;
    }
  }, [transactionsWithIndex, filter, autoAssignedIndices, isCategorized]);

  const stats = useMemo(() => {
    const errorCount = transactions.filter(t =>
      t.validationErrors.some(e => !INFO_PREFIXES.some(prefix => e.startsWith(prefix)))
    ).length;

    return {
      total: transactions.length,
      duplicates: transactions.filter(t => t.potentialDuplicate).length,
      errors: errorCount,
      categorized: transactions.filter(isCategorized).length,
    };
  }, [transactions, isCategorized]);

  const allSelected =
    filteredTransactions.length > 0 &&
    filteredTransactions.every(t => selectedRows.has(t.originalIndex));
  const someSelected = selectedRows.size > 0 && !allSelected;

  // -------------------------------------------------------------------------
  // Handlers — selection
  // -------------------------------------------------------------------------
  const handleSelectAll = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.checked) {
      setSelectedRows(new Set(filteredTransactions.map(t => t.originalIndex)));
    } else {
      setSelectedRows(new Set());
    }
  };

  const toggleRowSelection = useCallback((originalIndex: number) => {
    setSelectedRows(prev => {
      const next = new Set(prev);
      if (next.has(originalIndex)) next.delete(originalIndex);
      else next.add(originalIndex);
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Handlers — inline editing
  // -------------------------------------------------------------------------
  const startEditing = (originalIndex: number) => {
    const t = transactions[originalIndex];
    setEditState({
      transactionDate: t.transactionDate ?? '',
      amount: decimalField(t, 'amount'),
      category: t.category ?? '',
      payee: t.payee ?? '',
      memo: t.memo ?? '',
      description: t.description,
    });
    setEditingRow(originalIndex);
  };

  const commitEdit = (originalIndex: number) => {
    const amounts = reviewedAmountChanges(
      transactions,
      partners,
      new Set([originalIndex]),
      editState.amount
    );
    const updated = transactions.map((t, idx) => {
      if (idx !== originalIndex) {
        const amount = amounts.get(idx);
        return amount === undefined ? t : updateReviewedDateAndAmount(t, t.transactionDate, amount);
      }
      return {
        ...updateReviewedDateAndAmount(t, editState.transactionDate || null, editState.amount),
        category: editState.category.trim() || null,
        payee: editState.payee,
        memo: editState.memo || null,
        // An empty description clears it; a missing one falls back to the statement payee.
        description: editState.description,
      };
    });
    onTransactionsChange(updated);
    setEditingRow(null);
  };

  const cancelEdit = () => setEditingRow(null);

  // -------------------------------------------------------------------------
  // Handlers — expand/collapse
  // -------------------------------------------------------------------------
  const toggleRowExpansion = (originalIndex: number) => {
    setExpandedRows(prev => {
      const next = new Set(prev);
      if (next.has(originalIndex)) next.delete(originalIndex);
      else next.add(originalIndex);
      return next;
    });
  };

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------
  const formatDate = (dateStr: string): string => {
    try {
      return globalFormatDate(dateStr, settings?.dateFormat);
    } catch {
      return dateStr;
    }
  };

  const categoryDisplayName = (name: string): string =>
    categoryPath(categories, categoryMappings[name], 'display') ||
    translateCategoryName(tCategories, name);

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <div className="import-review space-y-4">
      {transactions.some(transaction =>
        transaction.validationErrors?.some(error => error.startsWith('AI_UNAVAILABLE:'))
      ) && (
        <p role="status" className="text-sm text-text-secondary">
          {t('review.aiUnavailable')}
        </p>
      )}
      {/* ── Auto-assign notification ──────────────────────────────────────── */}
      {autoAssignedCount > 0 && (
        <button
          onClick={() => setFilter(filter === 'auto_assigned' ? 'all' : 'auto_assigned')}
          className="w-full text-left flex items-start space-x-3 bg-primary/5 border border-primary/20 rounded-lg p-3 text-sm hover:bg-primary/10 transition-colors"
        >
          <Sparkles className="h-4 w-4 text-primary mt-0.5 flex-shrink-0" />
          <div>
            <span className="font-semibold text-text-primary">
              {t('review.autoAssignedCount', { count: autoAssignedCount })}
            </span>{' '}
            <span className="text-text-secondary">{t('review.autoAssignedDescription')}</span>
            <span className="ml-2 text-xs text-primary underline underline-offset-2">
              {filter === 'auto_assigned'
                ? t('review.filters.showAll')
                : t('review.filters.viewThese')}
            </span>
          </div>
        </button>
      )}

      {/* ── Stats bar & filter buttons ────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center space-y-3 sm:space-y-0">
        <div className="text-sm text-text-secondary flex flex-wrap gap-x-3 gap-y-1">
          <span>
            <span className="font-semibold text-text-primary">{stats.total}</span>{' '}
            {t('review.stats.transactions', { count: transactions.length })}
          </span>
          <span className="text-text-tertiary">·</span>
          <span className="flex items-center space-x-1">
            <Tag className="h-3.5 w-3.5 text-text-tertiary" />
            <span>
              <span
                className={
                  stats.categorized === stats.total
                    ? 'text-green-600 font-semibold'
                    : 'font-semibold text-amber-600'
                }
              >
                {stats.categorized}
              </span>
              <span className="text-text-tertiary">/{stats.total}</span>{' '}
              {t('review.stats.categorized')}
            </span>
          </span>
          {stats.duplicates > 0 && (
            <>
              <span className="text-text-tertiary">·</span>
              <span className="font-semibold text-amber-600">
                {stats.duplicates} {t('review.stats.duplicates')}
              </span>
            </>
          )}
          {stats.errors > 0 && (
            <>
              <span className="text-text-tertiary">·</span>
              <span className="font-semibold text-red-600">
                {stats.errors} {t('review.stats.errors')}
              </span>
            </>
          )}
        </div>

        <div className="flex space-x-2 flex-wrap gap-y-2">
          {(['all', 'duplicates', 'errors', 'uncategorized'] as FilterType[]).map(f => {
            const count =
              f === 'duplicates'
                ? stats.duplicates
                : f === 'errors'
                  ? stats.errors
                  : f === 'uncategorized'
                    ? stats.total - stats.categorized
                    : null;
            if (f !== 'all' && count === 0) return null;
            return (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-3 py-1 text-sm rounded-md transition-colors capitalize ${
                  filter === f
                    ? f === 'all'
                      ? 'bg-primary text-white'
                      : f === 'duplicates'
                        ? 'bg-amber-600 text-white'
                        : f === 'errors'
                          ? 'bg-red-600 text-white'
                          : 'bg-amber-500 text-white'
                    : 'bg-surface hover:bg-surface-elevated text-text-secondary'
                }`}
              >
                {f === 'all'
                  ? t('review.filters.all')
                  : f === 'duplicates'
                    ? t('review.filters.duplicates', { count: stats.duplicates })
                    : f === 'errors'
                      ? t('review.filters.errors', { count: stats.errors })
                      : t('review.filters.uncategorized', {
                          count: stats.total - stats.categorized,
                        })}
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Category mapping summary (collapsible info) ───────────────────── */}
      {uniqueCategories.length > 0 && (
        <details className="group border border-border rounded-lg overflow-hidden">
          <summary className="cursor-pointer select-none flex items-center justify-between px-4 py-3 bg-surface-elevated text-sm hover:bg-surface transition-colors">
            <span className="flex items-center space-x-2 font-medium text-text-primary">
              <Info className="h-4 w-4 text-primary" />
              <span>
                {t('review.categoryAssignments.title', { count: uniqueCategories.length })}
              </span>
              {newCategoryNames.filter(n => uniqueCategories.some(u => u.name === n)).length >
                0 && (
                <span className="ml-1.5 text-xs text-primary bg-primary/10 px-1.5 py-0.5 rounded-full">
                  {t('review.labels.toCreate', { count: newCategoryNames.length })}
                </span>
              )}
            </span>
            <ChevronDown className="h-4 w-4 text-text-tertiary group-open:rotate-180 transition-transform" />
          </summary>
          <div className="divide-y divide-border">
            {uniqueCategories.map(({ name, count }) => {
              const mappedId = categoryMappings[name];
              const isMapped = mappedId != null;
              const isToCreate = !isMapped && newCategoryNames.includes(name);
              const showOverride = mappingPanelOverride[name];
              return (
                <div
                  key={name}
                  className={`flex flex-col items-start gap-2 sm:flex-row sm:items-center sm:justify-between px-4 py-2.5 text-sm ${
                    isToCreate ? 'bg-primary/5' : !isMapped ? 'bg-amber-500/5' : ''
                  }`}
                >
                  <div className="flex items-center space-x-2 min-w-0">
                    <span className="font-medium text-text-primary [overflow-wrap:anywhere]">
                      {name}
                    </span>
                    <span className="flex-shrink-0 text-xs text-text-tertiary bg-surface px-1.5 py-0.5 rounded">
                      {count}×
                    </span>
                  </div>
                  <div className="flex w-full min-w-0 items-center gap-2 sm:w-auto">
                    <span className="hidden sm:block text-text-tertiary">→</span>
                    <div className="w-full sm:w-48">
                      {isToCreate && !showOverride ? (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-primary italic flex items-center gap-1">
                            <Sparkles className="h-3 w-3" /> {t('review.labels.willCreate')}
                          </span>
                          <button
                            type="button"
                            onClick={() => setMappingPanelOverride(p => ({ ...p, [name]: true }))}
                            className="text-xs text-text-tertiary hover:text-primary underline underline-offset-2"
                            title={t('review.actions.mapExisting')}
                          >
                            {t('review.labels.mapExisting')}
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1 w-full">
                          <div className="flex-1 min-w-0">
                            <CategorySelect
                              value={mappedId ?? undefined}
                              onValueChange={val => {
                                const next = { ...categoryMappings };
                                if (val == null) {
                                  delete next[name];
                                  // Clearing while in override → revert to "will be created"
                                  if (showOverride) {
                                    setMappingPanelOverride(p => {
                                      const n = { ...p };
                                      delete n[name];
                                      return n;
                                    });
                                    if (!newCategoryNames.includes(name)) {
                                      onNewCategoryNamesChange([...newCategoryNames, name]);
                                    }
                                  }
                                } else {
                                  next[name] = val;
                                  // Remove from "to create" if user mapped to existing
                                  if (newCategoryNames.includes(name)) {
                                    onNewCategoryNamesChange(
                                      newCategoryNames.filter(n => n !== name)
                                    );
                                  }
                                  setMappingPanelOverride(p => {
                                    const n = { ...p };
                                    delete n[name];
                                    return n;
                                  });
                                }
                                onCategoryMappingsChange(next);
                              }}
                              placeholder={t('review.categoryAssignments.notMapped')}
                              allowNone
                              className={`w-full text-xs ${!isMapped && !isToCreate ? 'border-amber-400' : ''}`}
                            />
                          </div>
                          {/* Cancel override — revert to "Will be created" */}
                          {showOverride && (
                            <button
                              type="button"
                              title={t('review.actions.keepNew')}
                              onClick={() =>
                                setMappingPanelOverride(p => {
                                  const n = { ...p };
                                  delete n[name];
                                  return n;
                                })
                              }
                              className="p-0.5 rounded text-text-tertiary hover:text-red-500 hover:bg-red-50 transition-colors flex-shrink-0"
                            >
                              <X className="h-3.5 w-3.5" />
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                    {isMapped ? (
                      <Check className="h-4 w-4 text-green-500 flex-shrink-0" />
                    ) : isToCreate ? (
                      <Sparkles className="h-4 w-4 text-primary flex-shrink-0" />
                    ) : (
                      <AlertCircle className="h-4 w-4 text-amber-500 flex-shrink-0" />
                    )}
                  </div>
                </div>
              );
            })}
          </div>
          <div className="px-4 py-2 bg-surface text-xs text-text-tertiary border-t border-border">
            {t('review.categoryAssignments.footer')}
          </div>
        </details>
      )}

      {/* ── Bulk-edit toolbar ─────────────────────────────────────────────── */}
      {selectedRows.size > 0 && (
        <ImportReviewBulkEdit
          transactions={transactions}
          selected={selectedRows}
          partners={partners}
          today={today}
          baseCurrency={baseCurrency}
          onCategory={rememberCategorySelection}
          onApply={rows => {
            if (editingRow !== null && rows[editingRow] !== transactions[editingRow])
              setEditingRow(null);
            onTransactionsChange(rows);
            setSelectedRows(new Set());
          }}
          onRemove={() => {
            onTransactionsChange(transactions.filter((_, idx) => !selectedRows.has(idx)));
            setEditingRow(null);
            setSelectedRows(new Set());
          }}
          onClear={() => setSelectedRows(new Set())}
        />
      )}

      {/* ── Transactions table ────────────────────────────────────────────── */}
      <div className="review-ledger border border-border rounded-lg">
        <div className="review-scroll">
          <table role="table" className="w-full table-fixed" aria-label={t('wizard.review.title')}>
            <colgroup>
              <col className="review-select" />
              <col className="review-date" />
              <col />
              <col className="review-amount" />
              <col />
              <col className="review-notes" />
              <col />
              {showSource && <col />}
              <col className="review-status" />
              <col className="review-actions" />
            </colgroup>
            <thead
              role="rowgroup"
              className="bg-surface-elevated sticky top-0 z-10 shadow-sm ring-1 ring-border"
            >
              <tr role="row">
                <th role="columnheader" scope="col" className="review-select">
                  <label>
                    <input
                      type="checkbox"
                      checked={allSelected}
                      ref={input => {
                        if (input) input.indeterminate = someSelected;
                      }}
                      onChange={handleSelectAll}
                      className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                      aria-label={t('review.table.selectAll')}
                    />
                    <span className="review-select-label">{t('review.table.selectAll')}</span>
                  </label>
                </th>
                <th role="columnheader" scope="col" className="review-date">
                  {t('review.table.date')}
                </th>
                <th role="columnheader" scope="col" className="review-payee">
                  {t('review.table.payee')}
                </th>
                <th role="columnheader" scope="col" className="review-amount">
                  {t('review.table.amount')}
                </th>
                <th role="columnheader" scope="col" className="review-category">
                  {t('review.table.category')}
                </th>
                <th role="columnheader" scope="col" className="review-notes">
                  {t('review.table.notes')}
                </th>
                <th role="columnheader" scope="col" className="review-description">
                  {t('review.table.description')}
                </th>
                {showSource && (
                  <th role="columnheader" scope="col" className="review-source">
                    {t('review.table.source')}
                  </th>
                )}
                <th role="columnheader" scope="col" className="review-status">
                  {t('review.table.status')}
                </th>
                <th
                  role="columnheader"
                  scope="col"
                  className="review-actions"
                  aria-label={t('common:labels.actions')}
                />
              </tr>
            </thead>

            <tbody role="rowgroup">
              {filteredTransactions.length === 0 ? (
                <tr role="row">
                  <td
                    role="cell"
                    colSpan={showSource ? 10 : 9}
                    className="py-12 text-center text-text-secondary text-sm"
                  >
                    {t('review.table.noResults')}
                  </td>
                </tr>
              ) : (
                filteredTransactions.map(transaction => {
                  const idx = transaction.originalIndex;
                  const description =
                    transaction.description ?? transaction.originalPayee ?? transaction.payee;
                  const currency =
                    transaction.currency || transaction.reviewCurrency || baseCurrency;
                  const isEditing = editingRow === idx;
                  const isExpanded = expandedRows.has(idx);
                  const isSelected = selectedRows.has(idx);
                  const hasSplits = transaction.splits?.length > 0;
                  const hasErrors = transaction.validationErrors.some(
                    e => !INFO_PREFIXES.some(p => e.startsWith(p))
                  );
                  const hasRuleMatch = transaction.validationErrors.some(e =>
                    e.startsWith('RULE_MATCH:')
                  );
                  const hasAIMatch = transaction.validationErrors.some(e =>
                    e.startsWith('AI_MATCH:')
                  );
                  const hasRuleSkip = transaction.validationErrors.some(e =>
                    e.startsWith('RULE_SKIP:')
                  );
                  const isDuplicate = transaction.potentialDuplicate;
                  const invalidZero =
                    isEditing && !transaction.openingBalance && equalDecimals(editState.amount, 0);

                  return (
                    <React.Fragment key={idx}>
                      {/* ── Main row ──────────────────────────────────────── */}
                      <tr
                        role="row"
                        className={[
                          'review-row border-t border-border transition-colors',
                          isEditing ? 'review-row-editing' : '',
                          isSelected
                            ? 'bg-primary/5'
                            : isDuplicate
                              ? 'bg-amber-500/5 hover:bg-amber-500/10'
                              : hasErrors
                                ? 'bg-red-500/5 hover:bg-red-500/10'
                                : 'hover:bg-surface-elevated',
                        ].join(' ')}
                      >
                        {/* Checkbox */}
                        <td role="cell" className="review-select">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleRowSelection(idx)}
                            className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                            aria-label={t('review.actions.selectTransaction', { number: idx + 1 })}
                          />
                        </td>

                        {/* Date */}
                        <td role="cell" className="review-date text-text-secondary">
                          {isEditing ? (
                            <>
                              <label className="sr-only" htmlFor={`${editFormId}-date`}>
                                {t('review.table.date')}
                              </label>
                              <DateInput
                                id={`${editFormId}-date`}
                                form={editFormId}
                                value={editState.transactionDate}
                                max={latestEditDate}
                                required={
                                  !transaction.openingBalance ||
                                  !!transaction.transactionDate ||
                                  transaction.validationErrors.some(
                                    error => reviewFieldErrorKey(error) === 'dateRequired'
                                  )
                                }
                                onChange={transactionDate =>
                                  setEditState(s => ({ ...s, transactionDate }))
                                }
                              />
                            </>
                          ) : transaction.transactionDate ? (
                            formatDate(transaction.transactionDate)
                          ) : (
                            t('review.table.missingDate')
                          )}
                        </td>

                        {/* Payee */}
                        <td role="cell" className="review-payee font-medium text-text-primary">
                          {isEditing ? (
                            <PayeeCombobox
                              value={editState.payee}
                              onValueChange={val => setEditState(s => ({ ...s, payee: val }))}
                              placeholder={t('review.table.payee')}
                              className="w-full"
                            />
                          ) : (
                            <div className="break-words" title={transaction.payee || undefined}>
                              {transaction.payee || (
                                <span className="text-text-tertiary italic">—</span>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Amount */}
                        <td role="cell" className="review-amount font-mono text-right">
                          {isEditing ? (
                            <>
                              <div className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-1.5">
                                <span
                                  title={currency}
                                  className="inline-flex h-10 items-center font-sans text-xs text-text-secondary"
                                >
                                  <span aria-hidden="true">{getCurrencySymbol(currency)}</span>
                                  <span id={`${editFormId}-currency`} className="sr-only">
                                    {currency}
                                  </span>
                                </span>
                                <NumberInput
                                  form={editFormId}
                                  aria-label={t('review.table.amount')}
                                  aria-describedby={`${editFormId}-currency`}
                                  value={editState.amount}
                                  onChange={amount => setEditState(s => ({ ...s, amount }))}
                                  error={invalidZero ? t('validation.amountZero') : undefined}
                                  min={`-${MAX_IMPORT_AMOUNT}`}
                                  max={MAX_IMPORT_AMOUNT}
                                  maxFractionDigits={18}
                                  required
                                  calculable
                                  className="text-right"
                                />
                              </div>
                              {transferMate && (
                                <p className="font-sans text-xs text-text-secondary">
                                  {t(
                                    sharedAmount
                                      ? 'review.table.sharedTransferAmount'
                                      : 'review.table.sharedTransferDirection'
                                  )}
                                </p>
                              )}
                            </>
                          ) : transaction.amount == null ? (
                            <span className="text-text-tertiary">
                              {t('review.table.missingAmount')}
                            </span>
                          ) : (
                            <span
                              className={
                                Number(transaction.amount) >= 0 ? 'text-green-600' : 'text-red-500'
                              }
                            >
                              <ConvertedAmount
                                amount={decimalValue(transaction, 'amount') ?? transaction.amount}
                                currency={currency}
                                inline
                              />
                            </span>
                          )}
                        </td>

                        {/* Category */}
                        <td role="cell" className="review-category text-text-secondary">
                          <span aria-hidden="true" className="review-field-label">
                            {t('review.table.category')}
                          </span>
                          {isEditing ? (
                            <CategoryCombobox
                              value={editState.category}
                              onValueChange={(name, id) => {
                                setEditState(s => ({ ...s, category: name }));
                                rememberCategorySelection(name, id);
                              }}
                              placeholder={t('review.table.noCategory')}
                              className="w-full text-sm"
                            />
                          ) : transaction.category ? (
                            <span className="inline-flex items-baseline gap-1">
                              <Tag className="h-3 w-3 text-text-tertiary flex-shrink-0" />
                              <span className="min-w-0" title={transaction.category}>
                                {categoryDisplayName(transaction.category)}
                              </span>
                            </span>
                          ) : hasSplits && !transaction.openingBalance ? (
                            <button
                              type="button"
                              onClick={() => setDetailRow(idx)}
                              className="text-sm text-primary hover:underline"
                            >
                              {t('review.details.editCategories')}
                            </button>
                          ) : (
                            <button
                              onClick={() => startEditing(idx)}
                              className="text-xs text-amber-600 hover:text-amber-700 underline underline-offset-2 whitespace-nowrap"
                            >
                              {t('review.table.assign')}
                            </button>
                          )}
                        </td>

                        {/* Notes */}
                        <td role="cell" className="review-notes text-text-primary">
                          <span aria-hidden="true" className="review-field-label">
                            {t('review.table.notes')}
                          </span>
                          {isEditing ? (
                            <textarea
                              rows={2}
                              maxLength={1000}
                              aria-label={t('review.table.notes')}
                              value={editState.memo}
                              onChange={e => setEditState(s => ({ ...s, memo: e.target.value }))}
                              className="w-full px-2 py-1 bg-surface border border-primary rounded text-sm focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                          ) : (
                            <span
                              className="block whitespace-pre-line"
                              title={transaction.memo ?? ''}
                            >
                              {transaction.memo ? (
                                <LinkedText text={transaction.memo} />
                              ) : (
                                <span className="italic">—</span>
                              )}
                            </span>
                          )}
                          {isEditing && <LinkedText text={editState.memo} linksOnly />}
                        </td>

                        <td role="cell" className="review-description text-text-secondary">
                          <span aria-hidden="true" className="review-field-label">
                            {t('review.table.description')}
                          </span>
                          {isEditing && !transaction.openingBalance ? (
                            <textarea
                              aria-label={t('review.table.description')}
                              value={editState.description ?? description ?? ''}
                              onChange={e =>
                                setEditState(s => ({ ...s, description: e.target.value }))
                              }
                              rows={2}
                              maxLength={255}
                              className="w-full min-w-0 resize-y rounded border border-primary bg-surface px-2 py-1 text-sm text-text-primary focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                          ) : (
                            <span className="block whitespace-pre-line" title={description ?? ''}>
                              <LinkedText text={description || '—'} />
                            </span>
                          )}
                          {isEditing && !transaction.openingBalance && (
                            <LinkedText text={editState.description ?? description} linksOnly />
                          )}
                          {!!transaction.tags?.length && (
                            <div
                              className="mt-1 flex flex-wrap gap-1"
                              aria-label={t('review.details.tags')}
                            >
                              {transaction.tags.map(tag => (
                                <Badge
                                  key={tag}
                                  variant="info"
                                  size="sm"
                                  className="max-w-full whitespace-normal break-all"
                                >
                                  {tag}
                                </Badge>
                              ))}
                            </div>
                          )}
                        </td>

                        {showSource && (
                          <td role="cell" className="review-source text-text-secondary">
                            <span aria-hidden="true" className="review-field-label">
                              {t('review.table.source')}
                            </span>
                            <p className="break-all">{transaction.sourceFileName}</p>
                            <p className="mt-1 text-xs">
                              {sourceAccountNames[transaction.sourceUploadId ?? ''] ||
                                transaction.accountName}
                            </p>
                          </td>
                        )}

                        {/* Status badges */}
                        <td role="cell" className="review-status">
                          <div className="flex flex-wrap items-center gap-1">
                            {isDuplicate && (
                              <Badge variant="warning" size="sm">
                                {t('review.labels.duplicate')}
                              </Badge>
                            )}
                            {hasErrors && (
                              <button
                                onClick={() => toggleRowExpansion(idx)}
                                className="inline-flex items-center space-x-1 focus:outline-none group"
                                title={t(
                                  isExpanded
                                    ? 'review.actions.hideErrors'
                                    : 'review.actions.showErrors'
                                )}
                              >
                                <Badge variant="error" size="sm">
                                  <span className="inline-flex items-center gap-1">
                                    <AlertCircle className="h-3 w-3" />
                                    <span>{t('review.labels.error')}</span>
                                  </span>
                                </Badge>
                              </button>
                            )}
                            {hasRuleMatch && (
                              <Badge
                                variant="info"
                                size="sm"
                                className="bg-primary/10 text-primary border-primary/20"
                              >
                                <span className="inline-flex items-center gap-1">
                                  <Sparkles className="h-3 w-3" />
                                  <span>{t('review.labels.matched')}</span>
                                </span>
                              </Badge>
                            )}
                            {hasAIMatch && !hasRuleMatch && (
                              <Badge
                                variant="info"
                                size="sm"
                                className="bg-violet-500/10 text-violet-600 border-violet-500/20"
                              >
                                <span className="inline-flex items-center gap-1">
                                  <Sparkles className="h-3 w-3" />
                                  <span>{t('review.labels.ai')}</span>
                                </span>
                              </Badge>
                            )}
                            {hasRuleSkip && (
                              <Badge
                                variant="default"
                                size="sm"
                                className="bg-gray-100 text-gray-500 border-gray-200"
                              >
                                <span>{t('review.labels.skipped')}</span>
                              </Badge>
                            )}
                            {hasSplits && !isDuplicate && !hasErrors && (
                              <Badge variant="default" size="sm">
                                {t('review.labels.split')}
                              </Badge>
                            )}
                          </div>
                        </td>

                        {/* Row actions */}
                        <td role="cell" className="review-actions">
                          <div className="flex flex-wrap items-center justify-end">
                            {isEditing ? (
                              <form
                                id={editFormId}
                                className="contents"
                                onSubmit={event => {
                                  event.preventDefault();
                                  if (!invalidZero) commitEdit(idx);
                                }}
                              >
                                <button
                                  type="submit"
                                  disabled={invalidZero}
                                  className="review-action rounded text-green-600 hover:bg-green-50 transition-colors"
                                  title={t('review.actions.save')}
                                >
                                  <Check className="h-4 w-4" />
                                </button>
                                <button
                                  type="button"
                                  onClick={cancelEdit}
                                  className="review-action rounded text-text-tertiary hover:bg-surface-elevated transition-colors"
                                  title={t('review.actions.cancel')}
                                >
                                  <X className="h-4 w-4" />
                                </button>
                              </form>
                            ) : (
                              <>
                                <button
                                  onClick={() => startEditing(idx)}
                                  className="review-action rounded text-text-tertiary hover:text-primary hover:bg-primary/5 transition-colors"
                                  title={t('review.actions.edit')}
                                >
                                  <Edit2 className="h-3.5 w-3.5" />
                                  <span aria-hidden="true" className="review-action-label">
                                    {t('common:edit')}
                                  </span>
                                </button>
                                {!transaction.openingBalance && (
                                  <button
                                    type="button"
                                    onClick={() => setDetailRow(idx)}
                                    className="review-action rounded text-text-secondary hover:text-primary hover:bg-primary/5"
                                    aria-label={t('review.details.title')}
                                    title={t('review.details.title')}
                                  >
                                    <ListTree className="h-4 w-4" />
                                    <span aria-hidden="true" className="review-action-label">
                                      {t('review.details.title')}
                                    </span>
                                  </button>
                                )}
                                {hasSplits && (
                                  <button
                                    onClick={() => toggleRowExpansion(idx)}
                                    className="review-action rounded text-text-tertiary hover:text-primary hover:bg-primary/5 transition-colors"
                                    title={t(
                                      isExpanded
                                        ? 'review.actions.collapse'
                                        : 'review.actions.expandSplits'
                                    )}
                                  >
                                    {isExpanded ? (
                                      <ChevronDown className="h-4 w-4" />
                                    ) : (
                                      <ChevronRight className="h-4 w-4" />
                                    )}
                                  </button>
                                )}
                              </>
                            )}
                          </div>
                        </td>
                      </tr>

                      {hasErrors && isExpanded && (
                        <tr role="row">
                          <td role="cell" colSpan={showSource ? 10 : 9} className="px-4 py-3">
                            <div className="flex items-start space-x-2 text-sm">
                              <AlertCircle className="h-4 w-4 text-red-500 mt-0.5 flex-shrink-0" />
                              <div>
                                <div className="font-medium text-red-600 mb-1">
                                  {t('review.table.validationErrors')}
                                </div>
                                <ul className="list-disc list-inside text-red-600 space-y-0.5">
                                  {transaction.validationErrors.map((err, i) => (
                                    <li key={i}>{translateValidationError(err)}</li>
                                  ))}
                                </ul>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}

                      {hasSplits && isExpanded && (
                        <tr role="row">
                          <td role="cell" colSpan={showSource ? 10 : 9} className="px-4 py-3">
                            <div className="font-medium text-text-primary text-sm mb-2">
                              {t('review.table.splitLines')}
                            </div>
                            <div className="space-y-1">
                              {transaction.splits.map((split, i) => (
                                <div
                                  key={i}
                                  className="flex flex-wrap items-baseline justify-between text-sm bg-app-bg rounded px-3 py-2 gap-x-4 gap-y-1 [overflow-wrap:anywhere]"
                                >
                                  <span className="text-text-secondary grow">
                                    {split.category ? categoryDisplayName(split.category) : '—'}
                                  </span>
                                  {!!split.tags?.length && (
                                    <div className="flex flex-wrap gap-1">
                                      {split.tags.map(tag => (
                                        <span
                                          key={tag}
                                          className="rounded bg-primary/10 text-primary px-2 py-0.5 text-xs"
                                        >
                                          {tag}
                                        </span>
                                      ))}
                                    </div>
                                  )}
                                  <span className="font-mono text-text-primary whitespace-nowrap">
                                    <ConvertedAmount
                                      amount={decimalValue(split, 'amount') ?? split.amount ?? 0}
                                      currency={
                                        transaction.currency ||
                                        transaction.reviewCurrency ||
                                        baseCurrency
                                      }
                                      inline
                                    />
                                  </span>
                                  {split.memo && (
                                    <span className="text-text-secondary text-xs">
                                      <LinkedText text={split.memo} />
                                    </span>
                                  )}
                                </div>
                              ))}
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {detailRow != null && transactions[detailRow] && (
        <ImportReviewDetails
          transaction={transactions[detailRow]}
          categories={categories}
          currency={
            transactions[detailRow].currency ||
            transactions[detailRow].reviewCurrency ||
            baseCurrency
          }
          onClose={() => setDetailRow(null)}
          onSave={(next, mappings) => {
            if (Object.keys(mappings).length)
              onCategoryMappingsChange({ ...categoryMappings, ...mappings });
            onTransactionsChange(
              transactions.map((row, index) => (index === detailRow ? next : row))
            );
            setDetailRow(null);
          }}
        />
      )}
      {/* ── Footer hint ───────────────────────────────────────────────────── */}
      {stats.categorized < stats.total && (
        <div className="flex items-start space-x-2 text-xs text-text-secondary bg-warning/5 border border-warning/30 rounded-lg px-3 py-2.5">
          <AlertCircle className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
          <span>{t('review.categorizationHint', { count: stats.total - stats.categorized })}</span>
        </div>
      )}
    </div>
  );
}
