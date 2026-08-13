/**
 * BudgetWizard Component
 * TASK-8.6.10: 3-step wizard dialog for automatic budget creation from transaction history.
 *
 * Step 1 — Configure Analysis: choose period + lookback window, trigger analysis
 * Step 2 — Review Suggestions: select / de-select suggestions, edit amounts
 * Step 3 — Confirmation: show bulk-create result (created, skipped, errors)
 *
 * REQ-2.9.1.5: Automatic budget creation from transaction history analysis
 */
import { useState } from 'react';
import { CheckSquare, Square, Wand2, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { useAnalyzeBudgets, useBulkCreateBudgets } from '@/hooks/useBudgets';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import type {
  BudgetPeriod,
  BudgetSuggestion,
  BudgetBulkCreateResponse,
} from '@/types/budget';
import { format, addMonths, addQuarters, addYears, addWeeks } from 'date-fns';

// ─── types ───────────────────────────────────────────────────────────────────

interface BudgetWizardProps {
  open: boolean;
  onClose: () => void;
}

type WizardStep = 1 | 2 | 3;

// ─── constants ────────────────────────────────────────────────────────────────

// (Period option labels are set dynamically from i18n inside the component)

// ─── helper ───────────────────────────────────────────────────────────────────

/** Today as ISO "YYYY-MM-DD". */
function today(): string {
  return format(new Date(), 'yyyy-MM-dd');
}

/**
 * End-date for a budget period starting today.
 * Uses addMonths/addQuarters/addYears so the end date is always a full period
 * after the start date, regardless of the day within the month.
 */
function periodEndDate(period: BudgetPeriod): string {
  const start = new Date();
  switch (period) {
    case 'WEEKLY':
      return format(addWeeks(start, 1), 'yyyy-MM-dd');
    case 'MONTHLY':
      return format(addMonths(start, 1), 'yyyy-MM-dd');
    case 'QUARTERLY':
      return format(addQuarters(start, 1), 'yyyy-MM-dd');
    case 'YEARLY':
      return format(addYears(start, 1), 'yyyy-MM-dd');
    default:
      return format(addMonths(start, 1), 'yyyy-MM-dd');
  }
}

// ─── sub-components ──────────────────────────────────────────────────────────

/** A single skeleton row placeholder during loading. */
function SkeletonRow() {
  return (
    <div className="flex items-center gap-3 p-3 rounded-lg bg-surface-elevated animate-pulse">
      <div className="h-4 w-4 rounded bg-border" />
      <div className="flex-1 space-y-2">
        <div className="h-4 w-1/3 rounded bg-border" />
        <div className="h-3 w-1/2 rounded bg-border" />
      </div>
      <div className="h-8 w-24 rounded bg-border" />
    </div>
  );
}

// ─── main component ──────────────────────────────────────────────────────────

/**
 * 3-step wizard dialog for automatic budget creation.
 *
 * Controlled by the `open` / `onClose` props; all state is local to this component
 * and is reset each time the wizard is closed.
 *
 * REQ-2.9.1.5
 */
export function BudgetWizard({ open, onClose }: BudgetWizardProps) {
  // ── wizard state ────────────────────────────────────────────────────────────
  const [step, setStep] = useState<WizardStep>(1);
  const [period, setPeriod] = useState<BudgetPeriod>('MONTHLY');
  const [lookbackMonths, setLookbackMonths] = useState<number>(6);
  const [suggestions, setSuggestions] = useState<BudgetSuggestion[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [editedAmounts, setEditedAmounts] = useState<Record<number, string>>({});
  const [bulkResult, setBulkResult] = useState<BudgetBulkCreateResponse | null>(null);

  // ── hooks ───────────────────────────────────────────────────────────────────
  const analyzeMutation = useAnalyzeBudgets();
  const bulkCreateMutation = useBulkCreateBudgets();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('budgets');

  const PERIOD_OPTIONS: { value: BudgetPeriod; label: string }[] = [
    { value: 'WEEKLY', label: t('form.periods.WEEKLY') },
    { value: 'MONTHLY', label: t('form.periods.MONTHLY') },
    { value: 'QUARTERLY', label: t('form.periods.QUARTERLY') },
    { value: 'YEARLY', label: t('form.periods.YEARLY') },
  ];

  const LOOKBACK_OPTIONS: { value: number; label: string }[] = [
    { value: 3, label: t('wizard.step1.months', { count: 3 }) },
    { value: 6, label: t('wizard.step1.months', { count: 6 }) },
    { value: 12, label: t('wizard.step1.months', { count: 12 }) },
    { value: 24, label: t('wizard.step1.months', { count: 24 }) },
  ];

  // ── handlers ────────────────────────────────────────────────────────────────

  /** Reset all state to initial values. */
  const reset = () => {
    setStep(1);
    setPeriod('MONTHLY');
    setLookbackMonths(6);
    setSuggestions([]);
    setSelectedIds(new Set());
    setEditedAmounts({});
    setBulkResult(null);
    analyzeMutation.reset();
    bulkCreateMutation.reset();
  };

  const handleClose = () => {
    reset();
    onClose();
  };

  /** Step 1 → trigger analysis. */
  const handleAnalyze = async () => {
    try {
      const result = await analyzeMutation.mutateAsync({
        period,
        lookbackMonths,
      });
      setSuggestions(result);
      // Pre-select all suggestions that don't already have a budget
      const preSelected = new Set(
        result
          .filter((s) => !s.hasExistingBudget)
          .map((s) => s.categoryId)
      );
      setSelectedIds(preSelected);
      // Initialise editable amounts from suggested values
      const amounts: Record<number, string> = {};
      result.forEach((s) => {
        amounts[s.categoryId] = String(s.suggestedAmount);
      });
      setEditedAmounts(amounts);
      setStep(2);
    } catch {
      // Error displayed via analyzeMutation.isError
    }
  };

  const toggleSelect = (categoryId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(categoryId)) {
        next.delete(categoryId);
      } else {
        next.add(categoryId);
      }
      return next;
    });
  };

  const selectAll = () => {
    setSelectedIds(new Set(suggestions.map((s) => s.categoryId)));
  };

  const deselectAll = () => {
    setSelectedIds(new Set());
  };

  /** Step 2 → bulk-create selected suggestions. */
  const handleCreate = async () => {
    const selected = suggestions.filter((s) => selectedIds.has(s.categoryId));
    const startDate = today();

    const budgets = selected.map((s) => ({
      categoryId: s.categoryId,
      amount: String(
        parseFloat(editedAmounts[s.categoryId] ?? String(s.suggestedAmount)) || s.suggestedAmount
      ),
      currency: s.currency,
      period: s.period,
      startDate,
      endDate: periodEndDate(s.period),
      rollover: false,
    }));

    try {
      const result = await bulkCreateMutation.mutateAsync({ budgets });
      setBulkResult(result);
      setStep(3);
    } catch {
      // Error displayed via bulkCreateMutation.isError
    }
  };

  // ── render helpers ──────────────────────────────────────────────────────────

  const selectedCount = selectedIds.size;

  // ── render ──────────────────────────────────────────────────────────────────
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Wand2 className="h-5 w-5 text-primary" />
            {t('wizard.title')}
            <span className="ml-auto text-xs font-normal text-text-secondary">
              {t('wizard.step', { step, total: 3 })}
            </span>
          </DialogTitle>
        </DialogHeader>

        {/* ── Step indicator ───────────────────────────────────────────────── */}
        <div className="flex items-center gap-1 mb-4">
          {([1, 2, 3] as WizardStep[]).map((s) => (
            <div
              key={s}
              className={`flex-1 h-1.5 rounded-full transition-colors ${
                s <= step ? 'bg-primary' : 'bg-border'
              }`}
            />
          ))}
        </div>

        {/* ── Step 1 — Configure ───────────────────────────────────────────── */}
        {step === 1 && (
          <div className="space-y-5">
            <p className="text-sm text-text-secondary">
              {t('wizard.step1.description')}
            </p>

            <div className="grid grid-cols-2 gap-4">
              {/* Period */}
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('wizard.step1.budgetPeriod')}
                </label>
                <select
                  value={period}
                  onChange={(e) => setPeriod(e.target.value as BudgetPeriod)}
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                >
                  {PERIOD_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>

              {/* Lookback */}
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('wizard.step1.historyWindow')}
                </label>
                <select
                  value={lookbackMonths}
                  onChange={(e) => setLookbackMonths(Number(e.target.value))}
                  className="w-full h-10 px-3 rounded-lg bg-background border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                >
                  {LOOKBACK_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Analysis error */}
            {analyzeMutation.isError && (
              <div className="p-3 rounded-lg bg-error/10 border border-error/20 text-error text-sm">
                {t('wizard.step1.analyseError')}
              </div>
            )}

            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={handleClose}>
                {t('wizard.cancel')}
              </Button>
              <Button
                variant="primary"
                onClick={handleAnalyze}
                disabled={analyzeMutation.isPending}
              >
                {analyzeMutation.isPending ? t('wizard.step1.analysing') : t('wizard.step1.analyseSpending')}
              </Button>
            </div>
          </div>
        )}

        {/* ── Step 2 — Review Suggestions ─────────────────────────────────── */}
        {step === 2 && (
          <div className="space-y-4">
            {analyzeMutation.isPending ? (
              // Loading skeletons
              <div className="space-y-2">
                {[...Array(4)].map((_, i) => (
                  <SkeletonRow key={i} />
                ))}
              </div>
            ) : suggestions.length === 0 ? (
              // Empty state
              <div className="py-10 text-center text-text-secondary">
                <p className="text-sm">{t('wizard.step2.noData', { months: lookbackMonths })}</p>
                <p className="text-xs mt-1">{t('wizard.step2.noDataHint')}</p>
                <Button variant="outline" className="mt-4" onClick={() => setStep(1)}>
                  {t('wizard.back')}
                </Button>
              </div>
            ) : (
              <>
                {/* Select-all / deselect-all */}
                <div className="flex items-center justify-between">
                  <p className="text-sm text-text-secondary">
                    {t('wizard.step2.suggestionsFound', { count: suggestions.length })} —{' '}
                    <span className="text-text-primary font-medium">
                      {t('wizard.step2.selected', { count: selectedCount })}
                    </span>
                  </p>
                  <div className="flex gap-2">
                    <button
                      className="text-xs text-primary underline hover:no-underline"
                      onClick={selectAll}
                    >
                      {t('wizard.step2.selectAll')}
                    </button>
                    <span className="text-text-tertiary">·</span>
                    <button
                      className="text-xs text-primary underline hover:no-underline"
                      onClick={deselectAll}
                    >
                      {t('wizard.step2.deselectAll')}
                    </button>
                  </div>
                </div>

                {/* Suggestion list */}
                <div className="space-y-2 max-h-72 overflow-y-auto pr-1">
                  {suggestions.map((s) => {
                    const isSelected = selectedIds.has(s.categoryId);
                    return (
                      <div
                        key={s.categoryId}
                        onClick={() => toggleSelect(s.categoryId)}
                        className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-colors ${
                          isSelected
                            ? 'border-primary bg-primary/5'
                            : 'border-border bg-surface hover:border-border/70'
                        }`}
                      >
                        {/* Checkbox */}
                        <div className="mt-0.5 text-primary flex-shrink-0">
                          {isSelected ? (
                            <CheckSquare className="h-4 w-4" />
                          ) : (
                            <Square className="h-4 w-4 text-text-tertiary" />
                          )}
                        </div>

                        {/* Category info */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-medium text-text-primary truncate">
                              {s.categoryName}
                            </span>
                            {s.hasExistingBudget && (
                              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs bg-warning/15 text-warning border border-warning/30">
                                <AlertTriangle className="h-3 w-3" />
                                {t('wizard.step2.budgetExists')}
                              </span>
                            )}
                          </div>
                          <p className="text-xs text-text-secondary mt-0.5">
                            {t('wizard.step2.avg')} {formatCurrency(s.averageSpent, s.currency)} /{' '}
                            {s.period.toLowerCase()} · {t('wizard.step2.transactions', { count: s.transactionCount })}
                          </p>
                        </div>

                        {/* Editable suggested amount */}
                        <div
                          className="flex-shrink-0"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <div className="flex items-center gap-1">
                            <span className="text-xs text-text-tertiary">{s.currency}</span>
                            <input
                              type="number"
                              min="0"
                              step="1"
                              value={editedAmounts[s.categoryId] ?? String(s.suggestedAmount)}
                              onChange={(e) =>
                                setEditedAmounts((prev) => ({
                                  ...prev,
                                  [s.categoryId]: e.target.value,
                                }))
                              }
                              className="w-24 h-8 px-2 text-right text-sm rounded bg-background border border-border text-text-primary focus:outline-none focus:ring-1 focus:ring-primary"
                            />
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {/* Bulk-create error */}
                {bulkCreateMutation.isError && (
                  <div className="p-3 rounded-lg bg-error/10 border border-error/20 text-error text-sm">
                    {t('wizard.step2.createError')}
                  </div>
                )}

                {/* Actions */}
                <div className="flex justify-between gap-2 pt-2">
                  <Button variant="ghost" onClick={() => setStep(1)}>
                    {t('wizard.back')}
                  </Button>
                  <div className="flex gap-2">
                    <Button variant="ghost" onClick={handleClose}>
                      {t('wizard.cancel')}
                    </Button>
                    <Button
                      variant="primary"
                      onClick={handleCreate}
                      disabled={selectedCount === 0 || bulkCreateMutation.isPending}
                    >
                      {bulkCreateMutation.isPending
                        ? t('wizard.step2.creating')
                        : t('wizard.step2.createBudgets', { count: selectedCount })}
                    </Button>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

        {/* ── Step 3 — Result ─────────────────────────────────────────────── */}
        {step === 3 && bulkResult && (
          <div className="space-y-4">
            {/* Success summary */}
            <div className="grid grid-cols-3 gap-3">
              <div className="p-3 rounded-lg bg-success/10 border border-success/20 text-center">
                <p className="text-2xl font-bold text-success">{bulkResult.successCount}</p>
                <p className="text-xs text-text-secondary mt-1">{t('wizard.step3.created')}</p>
              </div>
              <div className="p-3 rounded-lg bg-warning/10 border border-warning/20 text-center">
                <p className="text-2xl font-bold text-warning">{bulkResult.skippedCount}</p>
                <p className="text-xs text-text-secondary mt-1">{t('wizard.step3.skipped')}</p>
              </div>
              <div className="p-3 rounded-lg bg-error/10 border border-error/20 text-center">
                <p className="text-2xl font-bold text-error">{bulkResult.errors.length}</p>
                <p className="text-xs text-text-secondary mt-1">{t('wizard.step3.errors')}</p>
              </div>
            </div>

            {/* Skipped explanation */}
            {bulkResult.skippedCount > 0 && (
              <p className="text-xs text-text-secondary">
                {t('wizard.step3.skippedNote', { count: bulkResult.skippedCount })}
              </p>
            )}

            {/* Error list */}
            {bulkResult.errors.length > 0 && (
              <div className="p-3 rounded-lg bg-error/10 border border-error/20 space-y-1">
                <p className="text-xs font-medium text-error">{t('wizard.step3.errorsLabel')}</p>
                {bulkResult.errors.map((err, idx) => (
                  <p key={idx} className="text-xs text-error">
                    • {err}
                  </p>
                ))}
              </div>
            )}

            {/* Created budgets preview */}
            {bulkResult.created.length > 0 && (
              <div className="space-y-1 max-h-40 overflow-y-auto">
                {bulkResult.created.map((b) => (
                  <div
                    key={b.id}
                    className="flex items-center justify-between px-3 py-2 rounded bg-surface text-sm"
                  >
                    <span className="text-text-primary">{b.categoryName}</span>
                    <span className="text-text-secondary">
                      {formatCurrency(b.amount, b.currency)} / {b.period.toLowerCase()}
                    </span>
                  </div>
                ))}
              </div>
            )}

            <div className="flex justify-end pt-2">
              <Button variant="primary" onClick={handleClose}>
                {t('wizard.done')}
              </Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
