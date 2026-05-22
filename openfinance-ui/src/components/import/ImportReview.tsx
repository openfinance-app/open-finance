/**
 * ImportReview Component
 *
 * Step 3 of the Import Wizard — Review Transactions.
 *
 * Responsibilities:
 * - Display all parsed transactions in a reviewable table
 * - Auto-assign categories and payees based on transaction history patterns
 * - Allow per-row inline editing of category, payee, and memo
 * - Allow multi-select + bulk-edit of category, payee, and tags
 * - Filter view by All / Duplicates / Errors
 * - Expand split transactions inline
 * - Expose category mappings (sourceCategory → categoryId) to the parent wizard
 */
import React, { useState, useMemo, useCallback, useEffect } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
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
  Trash2,
} from 'lucide-react';
import { useCategories } from '@/hooks/useCategories';
import { useUserSettings, useBaseCurrency } from '@/hooks/useUserSettings';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { formatDate as globalFormatDate } from '@/utils/date';
import { translateCategoryName } from '@/utils/categoryTranslation';
import type { ImportTransactionDTO } from '@/types/import';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ImportReviewProps {
  transactions: ImportTransactionDTO[];
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
  category: string;
  payee: string;
  memo: string;
}

const INFO_PREFIXES = ['AUTO-MATCH:', 'AI_MATCH:', 'CATEGORY_SUGGESTION:', 'CATEGORY_UNKNOWN:', 'DUPLICATE:', 'RULE_MATCH:', 'RULE_SKIP:'];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Common QIF/OFX hierarchical-category → normalized name mappings.
 * Used to bridge imported category names (e.g., "Food:Dining") to the
 * user's own category names (e.g., "Dining Out").
 */
const CATEGORY_NORMALISATION_MAP: Record<string, string[]> = {
  'dining': ['dining out', 'restaurants', 'eating out', 'food & drink', 'food'],
  'groceries': ['groceries', 'supermarket', 'food & groceries'],
  'transport': ['transport', 'transportation', 'commute'],
  'fuel': ['fuel', 'petrol', 'gas', 'transport'],
  'utilities': ['utilities', 'bills', 'energy'],
  'entertainment': ['entertainment', 'leisure', 'fun'],
  'healthcare': ['healthcare', 'medical', 'health', 'pharmacy'],
  'insurance': ['insurance'],
  'salary': ['salary', 'income', 'wages', 'payroll'],
  'rent': ['rent', 'housing', 'mortgage'],
  'shopping': ['shopping', 'clothing', 'retail'],
  'subscriptions': ['subscriptions', 'streaming', 'services'],
};

/**
 * Normalise a raw imported category string into candidate search terms.
 * Handles:
 *  - Colon-separated hierarchical paths (QIF): "Food:Dining" → ["dining", "food"]
 *  - Slash-separated paths: "Food/Dining" → ["dining", "food"]
 *  - Literal text after stripping brackets and special characters
 */
function normaliseCategoryTerms(rawCategory: string): string[] {
  const terms: string[] = [];
  // Split by ':' or '/' to get path segments
  const parts = rawCategory.split(/[:/]/).map((p) => p.trim().toLowerCase()).filter(Boolean);
  // Most-specific segment first (last in the path)
  for (let i = parts.length - 1; i >= 0; i--) {
    const term = parts[i];
    terms.push(term);
    // Also add any canonical aliases from the normalisation map
    const canonical = CATEGORY_NORMALISATION_MAP[term];
    if (canonical) terms.push(...canonical);
  }
  return [...new Set(terms)]; // de-duplicate
}

/**
 * Auto-assign a category to a transaction.
 *
 * Priority order:
 *  1. Transaction already has a category → keep it.
 *  2. Exact (case-insensitive) name match against existing categories.
 *  3. Normalised QIF/OFX path segment match (e.g., "Food:Dining" → "Dining Out").
 *  4. Substring match: category name appears in payee or memo text.
 */
function autoAssignCategory(
  transaction: ImportTransactionDTO,
  categories: Array<{ id: number; name: string }>
): string | null {
  // 1. Exact name match on the raw category field from the parsed file
  if (transaction.category) {
    const exact = categories.find(
      (c) => c.name.toLowerCase() === transaction.category!.toLowerCase()
    );
    if (exact) return exact.name;
  }

  // 2. Normalised path-segment matching using the imported category string
  // (which may be stored in memo or category field by the parser)
  const rawCategory = transaction.category ?? '';
  if (rawCategory) {
    const terms = normaliseCategoryTerms(rawCategory);
    for (const term of terms) {
      const match = categories.find(
        (c) =>
          c.name.toLowerCase() === term ||
          c.name.toLowerCase().includes(term) ||
          term.includes(c.name.toLowerCase())
      );
      if (match) return match.name;
    }
  }

  // If category was set but no system category matched, preserve the raw value
  if (transaction.category) return transaction.category;

  // 3. Substring match: does any category name appear in payee / memo?
  //    Require word-boundary matching and minimum 3-char category name to avoid
  //    false positives (e.g., category "In" matching every payee containing "in").
  const text = `${transaction.payee ?? ''} ${transaction.memo ?? ''}`.toLowerCase();
  for (const cat of categories) {
    const catName = cat.name.toLowerCase();
    if (catName.length >= 3) {
      const escaped = catName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      if (new RegExp(`\\b${escaped}\\b`).test(text)) return cat.name;
    }
  }

  return null;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ImportReview({
  transactions,
  onTransactionsChange,
  categoryMappings,
  onCategoryMappingsChange,
  newCategoryNames,
  onNewCategoryNamesChange,
}: ImportReviewProps) {
  const { t } = useTranslation('import');
  const { t: tCategories } = useTranslation('categories');

  // Translate known backend validation error strings to the current locale
  const translateValidationError = (err: string): string => {
    const map: Record<string, string> = {
      'Transaction date is required': t('validation.dateRequired'),
      'Transaction amount is required': t('validation.amountRequired'),
      'Transaction amount cannot be zero': t('validation.amountZero'),
      'Transaction date cannot be in the future': t('validation.dateFuture'),
    };
    return map[err] ?? err;
  };

  const [filter, setFilter] = useState<FilterType>('all');
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  // Multi-edit state
  const [selectedRows, setSelectedRows] = useState<Set<number>>(new Set());
  const [bulkCategory, setBulkCategory] = useState<string>('');
  const [bulkPayee, setBulkPayee] = useState<string>('');

  // Per-row inline editing
  const [editingRow, setEditingRow] = useState<number | null>(null);
  const [editState, setEditState] = useState<EditState>({ category: '', payee: '', memo: '' });

  // Mapping panel: tracks which rows have "map to existing" override open
  const [mappingPanelOverride, setMappingPanelOverride] = useState<Record<string, boolean>>({});

  // Auto-assign notification
  const [autoAssignedCount, setAutoAssignedCount] = useState<number>(0);
  const [autoAssignedIndices, setAutoAssignedIndices] = useState<Set<number>>(new Set());

  const { data: categories = [] } = useCategories();
  const { data: settings } = useUserSettings();
  const { data: baseCurrency = 'EUR' } = useBaseCurrency();
  const { format: formatCurrency } = useFormatCurrency();

  // -------------------------------------------------------------------------
  // Auto-expand rows that have validation errors on first load
  // -------------------------------------------------------------------------
  useEffect(() => {
    if (transactions.length === 0) return;
    const errorIndices = transactions
      .map((t, idx) => {
        const hasRealErrors = t.validationErrors?.some(
          (e) => !INFO_PREFIXES.some((p) => e.startsWith(p))
        );
        return hasRealErrors ? idx : -1;
      })
      .filter((idx) => idx !== -1);
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
    transactions.forEach((t) => {
      if (t.category) map.set(t.category, (map.get(t.category) ?? 0) + 1);
      if (t.splitTransaction && t.splits) {
        t.splits.forEach((s) => {
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
    const newToCreate = [...newCategoryNames];
    let mappingChanged = false;
    let createChanged = false;

    uniqueCategories.forEach(({ name }) => {
      if (!(name in newMappings)) {
        const match = categories.find(
          (c) => c.name.toLowerCase() === name.toLowerCase()
        );
        if (match) {
          newMappings[name] = match.id;
          mappingChanged = true;
        } else if (!newToCreate.includes(name)) {
          newToCreate.push(name);
          createChanged = true;
        }
      }
    });

    if (mappingChanged) onCategoryMappingsChange(newMappings);
    if (createChanged) onNewCategoryNamesChange(newToCreate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uniqueCategories.length, categories.length]);

  // -------------------------------------------------------------------------
  // Derived data
  // -------------------------------------------------------------------------
  const transactionsWithIndex = useMemo(
    () => transactions.map((t, idx) => ({ ...t, originalIndex: idx })),
    [transactions]
  );

  const filteredTransactions = useMemo(() => {
    switch (filter) {
      case 'duplicates':
        return transactionsWithIndex.filter((t) => t.potentialDuplicate);
      case 'errors':
        return transactionsWithIndex.filter((t) =>
          t.validationErrors.some((e) => !INFO_PREFIXES.some((p) => e.startsWith(p)))
        );
      case 'uncategorized':
        return transactionsWithIndex.filter((t) => !t.category);
      case 'auto_assigned':
        return transactionsWithIndex.filter((t) => autoAssignedIndices.has(t.originalIndex));
      default:
        return transactionsWithIndex;
    }
  }, [transactionsWithIndex, filter, autoAssignedIndices]);

  const stats = useMemo(() => {
    const errorCount = transactions.filter((t) =>
      t.validationErrors.some((e) => !INFO_PREFIXES.some((prefix) => e.startsWith(prefix)))
    ).length;

    return {
      total: transactions.length,
      duplicates: transactions.filter((t) => t.potentialDuplicate).length,
      errors: errorCount,
      categorized: transactions.filter((t) => {
        if (!t.category) return false;
        return (
          categoryMappings[t.category] != null ||
          categories.some((c) => c.name.toLowerCase() === t.category!.toLowerCase()) ||
          newCategoryNames.includes(t.category)
        );
      }).length,
    };
  }, [transactions, categoryMappings, categories, newCategoryNames]);

  const allSelected =
    filteredTransactions.length > 0 &&
    filteredTransactions.every((t) => selectedRows.has(t.originalIndex));
  const someSelected = selectedRows.size > 0 && !allSelected;

  // -------------------------------------------------------------------------
  // Handlers — selection
  // -------------------------------------------------------------------------
  const handleSelectAll = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.checked) {
      setSelectedRows(new Set(filteredTransactions.map((t) => t.originalIndex)));
    } else {
      setSelectedRows(new Set());
    }
  };

  const toggleRowSelection = useCallback((originalIndex: number) => {
    setSelectedRows((prev) => {
      const next = new Set(prev);
      if (next.has(originalIndex)) next.delete(originalIndex);
      else next.add(originalIndex);
      return next;
    });
  }, []);

  // -------------------------------------------------------------------------
  // Handlers — bulk edit
  // -------------------------------------------------------------------------
  const handleBulkApply = () => {
    if (selectedRows.size === 0 || (!bulkCategory && !bulkPayee)) return;

    const updated = transactions.map((t, idx) => {
      if (!selectedRows.has(idx)) return t;
      return {
        ...t,
        ...(bulkCategory.trim() ? { category: bulkCategory.trim() } : {}),
        ...(bulkPayee.trim() ? { payee: bulkPayee.trim() } : {}),
      };
    });

    onTransactionsChange(updated);
    setSelectedRows(new Set());
    setBulkCategory('');
    setBulkPayee('');
  };

  // -------------------------------------------------------------------------
  // Handlers — inline editing
  // -------------------------------------------------------------------------
  const startEditing = (originalIndex: number) => {
    const t = transactions[originalIndex];
    setEditState({
      category: t.category ?? '',
      payee: t.payee ?? '',
      memo: t.memo ?? '',
    });
    setEditingRow(originalIndex);
  };

  const commitEdit = (originalIndex: number) => {
    const updated = transactions.map((t, idx) => {
      if (idx !== originalIndex) return t;
      return {
        ...t,
        category: editState.category.trim() || null,
        payee: editState.payee,
        memo: editState.memo || null,
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
    setExpandedRows((prev) => {
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

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------
  return (
    <div className="space-y-4">

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
            <span className="text-text-secondary">
              {t('review.autoAssignedDescription')}
            </span>
            <span className="ml-2 text-xs text-primary underline underline-offset-2">
              {filter === 'auto_assigned' ? t('review.filters.showAll') : t('review.filters.viewThese')}
            </span>
          </div>
        </button>
      )}

      {/* ── Stats bar & filter buttons ────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center space-y-3 sm:space-y-0">
        <div className="text-sm text-text-secondary flex flex-wrap gap-x-3 gap-y-1">
          <span>
            <span className="font-semibold text-text-primary">{stats.total}</span> {t('review.stats.transactions')}
          </span>
          <span className="text-text-tertiary">·</span>
          <span className="flex items-center space-x-1">
            <Tag className="h-3.5 w-3.5 text-text-tertiary" />
            <span>
              <span className={stats.categorized === stats.total ? 'text-green-600 font-semibold' : 'font-semibold text-amber-600'}>
                {stats.categorized}
              </span>
              <span className="text-text-tertiary">/{stats.total}</span> {t('review.stats.categorized')}
            </span>
          </span>
          {stats.duplicates > 0 && (
            <>
              <span className="text-text-tertiary">·</span>
              <span className="font-semibold text-amber-600">{stats.duplicates} {t('review.stats.duplicates')}</span>
            </>
          )}
          {stats.errors > 0 && (
            <>
              <span className="text-text-tertiary">·</span>
              <span className="font-semibold text-red-600">{stats.errors} {t('review.stats.errors')}</span>
            </>
          )}
        </div>

        <div className="flex space-x-2 flex-wrap gap-y-2">
          {(['all', 'duplicates', 'errors', 'uncategorized'] as FilterType[]).map((f) => {
            const count =
              f === 'duplicates' ? stats.duplicates
              : f === 'errors' ? stats.errors
              : f === 'uncategorized' ? stats.total - stats.categorized
              : null;
            if (f !== 'all' && count === 0) return null;
            return (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={`px-3 py-1 text-sm rounded-md transition-colors capitalize ${filter === f
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
                      : t('review.filters.uncategorized', { count: stats.total - stats.categorized })}
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
              <span>{t('review.categoryAssignments.title', { count: uniqueCategories.length })}</span>
              {newCategoryNames.filter((n) => uniqueCategories.some((u) => u.name === n)).length > 0 && (
                <span className="ml-1.5 text-xs text-primary bg-primary/10 px-1.5 py-0.5 rounded-full">
                  {newCategoryNames.filter((n) => uniqueCategories.some((u) => u.name === n)).length} to create
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
                  className={`flex items-center justify-between px-4 py-2.5 text-sm ${
                    isToCreate ? 'bg-primary/5' : !isMapped ? 'bg-amber-500/5' : ''
                  }`}
                >
                  <div className="flex items-center space-x-2 min-w-0">
                    <span className="font-medium text-text-primary truncate">{name}</span>
                    <span className="flex-shrink-0 text-xs text-text-tertiary bg-surface px-1.5 py-0.5 rounded">
                      {count}×
                    </span>
                  </div>
                  <div className="flex items-center space-x-2 flex-shrink-0 ml-4">
                    <span className="hidden sm:block text-text-tertiary">→</span>
                    <div className="w-48">
                      {isToCreate && !showOverride ? (
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-primary italic flex items-center gap-1">
                            <Sparkles className="h-3 w-3" /> Will be created
                          </span>
                          <button
                            type="button"
                            onClick={() => setMappingPanelOverride((p) => ({ ...p, [name]: true }))}
                            className="text-xs text-text-tertiary hover:text-primary underline underline-offset-2"
                            title="Map to existing category instead"
                          >
                            Map to existing
                          </button>
                        </div>
                      ) : (
                        <div className="flex items-center gap-1 w-full">
                          <div className="flex-1 min-w-0">
                            <CategorySelect
                              value={mappedId ?? undefined}
                              onValueChange={(val) => {
                                const next = { ...categoryMappings };
                                if (val == null) {
                                  delete next[name];
                                  // Clearing while in override → revert to "will be created"
                                  if (showOverride) {
                                    setMappingPanelOverride((p) => { const n = { ...p }; delete n[name]; return n; });
                                    if (!newCategoryNames.includes(name)) {
                                      onNewCategoryNamesChange([...newCategoryNames, name]);
                                    }
                                  }
                                } else {
                                  next[name] = val;
                                  // Remove from "to create" if user mapped to existing
                                  if (newCategoryNames.includes(name)) {
                                    onNewCategoryNamesChange(newCategoryNames.filter((n) => n !== name));
                                  }
                                  setMappingPanelOverride((p) => { const n = { ...p }; delete n[name]; return n; });
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
                              title="Cancel — keep as will be created"
                              onClick={() =>
                                setMappingPanelOverride((p) => {
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
        <div className="sticky top-0 z-20 bg-surface-elevated border border-primary/30 rounded-lg p-3 shadow-lg flex flex-col sm:flex-row items-center space-y-2 sm:space-y-0 sm:space-x-3 transition-all">
          <span className="text-sm font-semibold whitespace-nowrap text-primary px-2">
            {t('review.bulkAction.selected', { count: selectedRows.size })}
          </span>

          <div className="flex-1 flex flex-col sm:flex-row space-y-2 sm:space-y-0 sm:space-x-2 w-full">
            {/* Bulk Category */}
            <div className="flex-1 min-w-0">
              <CategoryCombobox
                value={bulkCategory}
                onValueChange={setBulkCategory}
                placeholder={t('review.bulkAction.setCategory')}
                className="w-full h-10"
              />
            </div>

            {/* Bulk Payee */}
            <div className="flex-1 min-w-0">
              <PayeeCombobox
                value={bulkPayee}
                onValueChange={setBulkPayee}
                placeholder={t('review.bulkAction.setPayee')}
                className="w-full h-10"
              />
            </div>
          </div>

          <div className="flex items-center space-x-2 w-full sm:w-auto">
            <Button
              variant="primary"
              size="sm"
              onClick={handleBulkApply}
              disabled={!bulkCategory && !bulkPayee}
              className="flex-1 sm:flex-none"
            >
              <Edit2 className="h-4 w-4 mr-1.5" />
              {t('review.bulkAction.applyButton', { count: selectedRows.size })}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                onTransactionsChange(transactions.filter((_, idx) => !selectedRows.has(idx)));
                setSelectedRows(new Set());
              }}
              title="Remove selected from import"
              className="text-red-500 hover:text-red-600 hover:bg-red-50"
            >
              <Trash2 className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setSelectedRows(new Set())}
              title="Clear selection"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}

      {/* ── Transactions table ────────────────────────────────────────────── */}
      <div className="border border-border rounded-lg overflow-hidden flex flex-col">
        <div className="overflow-auto max-h-[50vh]">
          <table className="w-full relative">
            <thead className="bg-surface-elevated sticky top-0 z-10 shadow-sm ring-1 ring-border">
              <tr>
                <th className="py-3 px-4 text-left w-10">
                  <input
                    type="checkbox"
                    checked={allSelected}
                    ref={(input) => {
                      if (input) input.indeterminate = someSelected;
                    }}
                    onChange={handleSelectAll}
                    className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                    aria-label={t('review.table.selectAll')}
                  />
                </th>
                <th className="text-left py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide whitespace-nowrap">
                  {t('review.table.date')}
                </th>
                <th className="text-left py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide">
                  {t('review.table.payee')}
                </th>
                <th className="text-right py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide whitespace-nowrap">
                  {t('review.table.amount')}
                </th>
                <th className="text-left py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide">
                  {t('review.table.category')}
                </th>
                <th className="text-left py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide hidden sm:table-cell">
                  {t('review.table.memo')}
                </th>
                <th className="text-left py-3 px-4 text-xs font-medium text-text-secondary uppercase tracking-wide">
                  {t('review.table.status')}
                </th>
                <th className="py-3 px-3 w-10" aria-label={t('common:labels.actions')} />
              </tr>
            </thead>

            <tbody>
              {filteredTransactions.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-12 text-center text-text-secondary text-sm">
                    {t('review.table.noResults')}
                  </td>
                </tr>
              ) : (
                filteredTransactions.map((transaction) => {
                  const idx = transaction.originalIndex;
                  const isEditing = editingRow === idx;
                  const isExpanded = expandedRows.has(idx);
                  const isSelected = selectedRows.has(idx);
                  const hasSplits = transaction.splitTransaction && transaction.splits.length > 0;
                  const hasErrors = transaction.validationErrors.some((e) =>
                    !INFO_PREFIXES.some((p) => e.startsWith(p))
                  );
                  const hasRuleMatch = transaction.validationErrors.some((e) => e.startsWith('RULE_MATCH:'));
                  const hasAIMatch = transaction.validationErrors.some((e) => e.startsWith('AI_MATCH:'));
                  const hasRuleSkip = transaction.validationErrors.some((e) => e.startsWith('RULE_SKIP:'));
                  const isDuplicate = transaction.potentialDuplicate;

                  return (
                    <React.Fragment key={idx}>
                      {/* ── Main row ──────────────────────────────────────── */}
                      <tr
                        className={[
                          'border-t border-border transition-colors',
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
                        <td className="py-2.5 px-4">
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => toggleRowSelection(idx)}
                            className="rounded border-border text-primary focus:ring-primary w-4 h-4 cursor-pointer"
                            aria-label={`Select transaction ${idx + 1}`}
                          />
                        </td>

                        {/* Date */}
                        <td className="py-2.5 px-4 text-sm text-text-secondary whitespace-nowrap">
                          {formatDate(transaction.transactionDate)}
                        </td>

                         {/* Payee */}
                         <td className="py-2.5 px-4 text-sm text-text-primary font-medium">
                           {isEditing ? (
                             <PayeeCombobox
                               value={editState.payee}
                               onValueChange={(val) => setEditState((s) => ({ ...s, payee: val }))}
                               placeholder={t('review.table.payee')}
                               className="w-full"
                             />
                           ) : (
                            <div
                              className="max-w-[180px] truncate"
                              title={transaction.originalPayee || transaction.payee || undefined}
                            >
                              {transaction.originalPayee || transaction.payee || (
                                <span className="text-text-tertiary italic">—</span>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Amount */}
                        <td className="py-2.5 px-4 text-sm text-right font-mono whitespace-nowrap">
                          <span className={transaction.amount >= 0 ? 'text-green-600' : 'text-red-500'}>
                            {formatCurrency(transaction.amount, transaction.currency || baseCurrency)}
                          </span>
                        </td>

                         {/* Category */}
                         <td className="py-2.5 px-4 text-sm min-w-[160px]">
                           {isEditing ? (
                             <CategoryCombobox
                               value={editState.category}
                               onValueChange={(val) =>
                                 setEditState((s) => ({ ...s, category: val }))
                               }
                               placeholder={t('review.table.noCategory')}
                               className="w-full text-sm"
                             />
                          ) : transaction.category ? (
                            <span className="inline-flex items-center space-x-1">
                              <Tag className="h-3 w-3 text-text-tertiary flex-shrink-0" />
                              <span className="text-text-secondary truncate max-w-[200px]" title={transaction.category}>
                                {translateCategoryName(tCategories, transaction.category)}
                              </span>
                            </span>
                          ) : (
                            <button
                              onClick={() => startEditing(idx)}
                              className="text-xs text-amber-600 hover:text-amber-700 underline underline-offset-2 whitespace-nowrap"
                            >
                              + Assign
                            </button>
                          )}
                        </td>

                        {/* Memo */}
                        <td className="py-2.5 px-4 text-sm text-text-tertiary max-w-[200px] hidden sm:table-cell">
                          {isEditing ? (
                            <input
                              type="text"
                              value={editState.memo}
                              onChange={(e) => setEditState((s) => ({ ...s, memo: e.target.value }))}
                              className="w-full px-2 py-1 bg-surface border border-primary rounded text-sm focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                          ) : (
                            <span className="truncate block" title={transaction.memo ?? ''}>
                              {transaction.memo || <span className="italic">—</span>}
                            </span>
                          )}
                        </td>

                        {/* Status badges */}
                        <td className="py-2.5 px-4">
                          <div className="flex items-center space-x-1.5">
                            {isDuplicate && (
                              <Badge variant="warning" size="sm">Duplicate</Badge>
                            )}
                            {hasErrors && (
                              <button
                                onClick={() => toggleRowExpansion(idx)}
                                className="inline-flex items-center space-x-1 focus:outline-none group"
                                title={isExpanded ? 'Hide errors' : 'Show errors'}
                              >
                                <Badge variant="error" size="sm">
                                  <span className="flex items-center space-x-1">
                                    <AlertCircle className="h-3 w-3" />
                                    <span>Error</span>
                                  </span>
                                </Badge>
                              </button>
                            )}
                            {hasRuleMatch && (
                              <Badge variant="info" size="sm" className="bg-primary/10 text-primary border-primary/20">
                                <span className="flex items-center space-x-1">
                                  <Sparkles className="h-3 w-3" />
                                  <span>Matched</span>
                                </span>
                              </Badge>
                            )}
                            {hasAIMatch && !hasRuleMatch && (
                              <Badge variant="info" size="sm" className="bg-violet-500/10 text-violet-600 border-violet-500/20">
                                <span className="flex items-center space-x-1">
                                  <Sparkles className="h-3 w-3" />
                                  <span>AI</span>
                                </span>
                              </Badge>
                            )}
                            {hasRuleSkip && (
                              <Badge variant="default" size="sm" className="bg-gray-100 text-gray-500 border-gray-200">
                                <span>Skipped</span>
                              </Badge>
                            )}
                            {hasSplits && !isDuplicate && !hasErrors && (
                              <Badge variant="default" size="sm">Split</Badge>
                            )}
                          </div>
                        </td>

                        {/* Row actions */}
                        <td className="py-2.5 px-3">
                          <div className="flex items-center space-x-1">
                            {isEditing ? (
                              <>
                                <button
                                  onClick={() => commitEdit(idx)}
                                  className="p-1 rounded text-green-600 hover:bg-green-50 transition-colors"
                                  title="Save"
                                >
                                  <Check className="h-4 w-4" />
                                </button>
                                <button
                                  onClick={cancelEdit}
                                  className="p-1 rounded text-text-tertiary hover:bg-surface-elevated transition-colors"
                                  title="Cancel"
                                >
                                  <X className="h-4 w-4" />
                                </button>
                              </>
                            ) : (
                              <>
                                <button
                                  onClick={() => startEditing(idx)}
                                  className="p-1 rounded text-text-tertiary hover:text-primary hover:bg-primary/5 transition-colors"
                                  title="Edit transaction"
                                >
                                  <Edit2 className="h-3.5 w-3.5" />
                                </button>
                                {(hasSplits || (hasErrors && hasSplits)) && (
                                  <button
                                    onClick={() => toggleRowExpansion(idx)}
                                    className="p-1 rounded text-text-tertiary hover:text-primary hover:bg-primary/5 transition-colors"
                                    title={isExpanded ? 'Collapse' : 'Expand splits'}
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

                             {hasErrors && (
                               <div className="mb-3">
                                 <div className="flex items-start space-x-2 text-sm">
                                   <AlertCircle className="h-4 w-4 text-red-500 mt-0.5 flex-shrink-0" />
                                   <div>
                                     <div className="font-medium text-red-600 mb-1">{t('review.table.validationErrors')}</div>
                                     <ul className="list-disc list-inside text-red-600 space-y-0.5">
                                       {transaction.validationErrors.map((err, i) => (
                                         <li key={i}>{translateValidationError(err)}</li>
                                       ))}
                                     </ul>
                                   </div>
                                 </div>
                               </div>
                             )}

                             {hasSplits && (
                               <div>
                                 <div className="font-medium text-text-primary text-sm mb-2">{t('review.table.splitLines')}</div>
                                 <div className="space-y-1">
                                   {transaction.splits.map((split, i) => (
                                     <div
                                       key={i}
                                       className="flex items-center justify-between text-sm bg-app-bg rounded px-3 py-2 gap-4"
                                     >
                                        <span className="text-text-secondary flex-1 truncate">
                                          {split.category
                                            ? translateCategoryName(tCategories, split.category)
                                            : '—'}
                                        </span>
                                       <span className="font-mono text-text-primary whitespace-nowrap">
                                         {formatCurrency(split.amount, transaction.currency || baseCurrency)}
                                       </span>
                                       {split.memo && (
                                         <span className="text-text-tertiary text-xs truncate max-w-[160px]">
                                           {split.memo}
                                         </span>
                                       )}
                                     </div>
                                   ))}
                                 </div>
                               </div>
                             )}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* ── Footer hint ───────────────────────────────────────────────────── */}
      {stats.categorized < stats.total && (
        <div className="flex items-start space-x-2 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2.5">
          <AlertCircle className="h-3.5 w-3.5 mt-0.5 flex-shrink-0" />
          <span>
            {t('review.categorizationHint', { count: stats.total - stats.categorized })}
          </span>
        </div>
      )}
    </div>
  );
}
