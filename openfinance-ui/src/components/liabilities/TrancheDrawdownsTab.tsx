import { useReverseDirectDraw } from '@/hooks/useTranches';
/**
 * TrancheDrawdownsTab Component (Task 8)
 *
 * Renders the "Drawdowns" tab of the liability detail dialog: one row per
 * tranche (staged drawdown) with its T{n} label, status badge, planned /
 * drawn / remaining amounts, linked property label and interest-only flag.
 *
 * Since Task 9 it also hosts the "Add tranche" form (planned amount, date and
 * the interest-only phase fields) posting to the tranche endpoint. Each
 * PLANNED tranche carries a "Draw" action opening the shared DisburseForm
 * (amount prefilled with the remaining planned amount, date and route —
 * to an account or directly to the linked property).
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Layers, AlertCircle, Plus, Banknote } from 'lucide-react';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { DisburseForm } from '@/components/liabilities/DisburseForm';
import { useTranches, useCreateTranche, getTrancheLabel } from '@/hooks/useTranches';
import type { Liability, TrancheStatus } from '@/types/liability';
import { cn } from '@/lib/utils';
import { isValidDecimalString } from '@/utils/money';

/** Badge colors per tranche status. */
const statusBadgeClass: Record<TrancheStatus, string> = {
  PLANNED: 'bg-surface-elevated text-text-secondary border-border',
  DRAWN: 'bg-primary/10 text-primary border-primary/30',
  CANCELLED: 'bg-error/10 text-error border-error/30',
};

/** Collapsible add-tranche form (Task 9 interest-only UI). */
function AddTrancheForm({ liability, onDone }: { liability: Liability; onDone: () => void }) {
  const { t } = useTranslation('liabilities');
  const createTranche = useCreateTranche();
  const [plannedAmount, setPlannedAmount] = useState('');
  const [plannedDate, setPlannedDate] = useState('');
  const [interestOnly, setInterestOnly] = useState(false);
  const [interestOnlyUntil, setInterestOnlyUntil] = useState('');

  const amount = Number(plannedAmount);
  // The backend treats a missing interestOnlyUntil as an open-ended interest-only phase, so
  // the end date is optional here too.
  const valid = isValidDecimalString(plannedAmount) && amount > 0;

  const submit = async () => {
    if (!valid) return;
    try {
      await createTranche.mutateAsync({
        liabilityId: Number(liability.id),
        request: {
          plannedAmount: plannedAmount.trim(),
          plannedDate: plannedDate || undefined,
          interestOnly,
          interestOnlyUntil: interestOnly && interestOnlyUntil ? interestOnlyUntil : undefined,
          currency: liability.currency,
        },
      });
      onDone();
    } catch {
      // The mutation surfaces its error state; the form stays open for a retry.
    }
  };

  return (
    <div
      className="p-4 mb-3 border border-border rounded-lg bg-surface space-y-3"
      data-testid="add-tranche-form"
    >
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label htmlFor="tranche-planned-amount" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.add.plannedAmount')} *
          </label>
          <NumberInput
            id="tranche-planned-amount"
            value={plannedAmount}
            onChange={setPlannedAmount}
            placeholder="0.00"
            min="0"
          />
        </div>
        <div>
          <label htmlFor="tranche-planned-date" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.add.plannedDate')}
          </label>
          <DateInput id="tranche-planned-date" value={plannedDate} onChange={setPlannedDate} />
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 items-end">
        <div className="flex items-center gap-2">
          <input
            id="tranche-interest-only"
            type="checkbox"
            checked={interestOnly}
            onChange={e => setInterestOnly(e.target.checked)}
            className="h-4 w-4 rounded border-border bg-surface text-primary focus:ring-2 focus:ring-primary"
          />
          <label
            htmlFor="tranche-interest-only"
            className="text-sm text-text-primary cursor-pointer"
          >
            {t('drawdowns.add.interestOnly')}
          </label>
        </div>
        <div>
          <label htmlFor="tranche-interest-only-until" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.add.interestOnlyUntil')}
          </label>
          <DateInput
            id="tranche-interest-only-until"
            value={interestOnlyUntil}
            onChange={setInterestOnlyUntil}
            disabled={!interestOnly}
          />
        </div>
      </div>
      {createTranche.isError && (
        <p role="alert" className="text-sm text-error">
          {t('drawdowns.add.error')}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" type="button" onClick={onDone} disabled={createTranche.isPending}>
          {t('drawdowns.add.cancel')}
        </Button>
        <Button
          variant="primary"
          type="button"
          disabled={!valid}
          isLoading={createTranche.isPending}
          onClick={submit}
        >
          {t('drawdowns.add.submit')}
        </Button>
      </div>
    </div>
  );
}

export function TrancheDrawdownsTab({ liability }: { liability: Liability }) {
  const { t } = useTranslation('liabilities');
  const { data: tranches = [], isLoading, error } = useTranches(liability.id);
  const reverse = useReverseDirectDraw();
  const [reverseId, setReverseId] = useState<number | null>(null);
  const [reverseDate, setReverseDate] = useState(new Date().toISOString().slice(0, 10));
  const [showAddForm, setShowAddForm] = useState(false);
  const [drawTrancheId, setDrawTrancheId] = useState<number | null>(null);
  const drawTranche = tranches.find(tr => tr.id === drawTrancheId) ?? null;

  const addButton = (
    <Button
      variant="outline"
      size="sm"
      type="button"
      onClick={() => setShowAddForm(true)}
      disabled={showAddForm}
    >
      <Plus className="h-3.5 w-3.5 mr-1" />
      {t('drawdowns.add.label')}
    </Button>
  );

  if (isLoading) {
    return (
      <div className="space-y-2 animate-pulse py-4">
        {[...Array(3)].map((_, i) => (
          <div key={i} className="h-12 bg-surface border border-border rounded-lg" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
        <AlertCircle className="h-4 w-4 flex-shrink-0" />
        <span>{t('drawdowns.error')}</span>
      </div>
    );
  }

  if (tranches.length === 0 && !showAddForm) {
    return (
      <div className="text-center py-12">
        <Layers className="h-10 w-10 text-text-tertiary mx-auto mb-3" />
        <p className="text-text-secondary text-sm mb-3">{t('drawdowns.empty')}</p>
        {addButton}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {showAddForm && <AddTrancheForm liability={liability} onDone={() => setShowAddForm(false)} />}
      {drawTranche && (
        <DisburseForm
          liability={liability}
          tranche={drawTranche}
          onDone={() => setDrawTrancheId(null)}
        />
      )}
      <div className="flex justify-end">{addButton}</div>
      <div className="divide-y divide-border border border-border rounded-lg overflow-hidden">
        {tranches.map(tranche => (
          <div
            key={tranche.id}
            className="flex items-center justify-between px-4 py-3 bg-surface hover:bg-surface-elevated transition-colors"
          >
            <div className="flex items-center gap-3 min-w-0">
              <span className="text-sm font-semibold text-text-primary font-mono">
                {getTrancheLabel(tranche.trancheNo)}
              </span>
              <span
                className={cn(
                  'inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium border',
                  statusBadgeClass[tranche.status]
                )}
              >
                {t(`drawdowns.status.${tranche.status}`)}
              </span>
              {tranche.interestOnly && (
                <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-info/10 text-info border border-info/30 text-xs font-medium">
                  {t('drawdowns.interestOnly')}
                  {tranche.interestOnlyUntil
                    ? ` (${new Date(tranche.interestOnlyUntil).toLocaleDateString()})`
                    : ''}
                </span>
              )}
              {tranche.realEstateId != null && (
                <span className="text-xs text-text-tertiary">
                  {t('drawdowns.linkedProperty', { id: tranche.realEstateId })}
                </span>
              )}
            </div>
            <div className="text-xs text-text-secondary ml-4 flex-shrink-0 flex items-center gap-4">
              <span>
                {t('drawdowns.planned')}:{' '}
                <span className="font-mono text-text-primary">
                  <ConvertedAmount
                    amount={tranche.plannedAmount}
                    currency={tranche.currency ?? liability.currency}
                    inline
                  />
                </span>
              </span>
              <span>
                {t('drawdowns.drawn')}:{' '}
                <span className="font-mono text-text-primary">
                  <ConvertedAmount
                    amount={tranche.drawnAmount ?? 0}
                    currency={tranche.currency ?? liability.currency}
                    inline
                  />
                </span>
              </span>
              <span>
                {t('drawdowns.remaining')}:{' '}
                <span className="font-mono font-semibold text-text-primary">
                  <ConvertedAmount
                    amount={tranche.remaining}
                    currency={tranche.currency ?? liability.currency}
                    inline
                  />
                </span>
              </span>
              {tranche.directDisbursement && tranche.status === 'DRAWN' && (
                <Button
                  variant="ghost"
                  disabled={reverse.isPending}
                  onClick={() => setReverseId(tranche.id)}
                >
                  {t('drawdowns.reverse')}
                </Button>
              )}
              {tranche.reversedDate && (
                <p className="text-sm">
                  {t('drawdowns.reversedOn', { date: tranche.reversedDate })}
                </p>
              )}
              {reverseId === tranche.id && (
                <div className="space-y-2">
                  <p className="text-sm">{t('drawdowns.reverseHint')}</p>
                  <label htmlFor={`reverse-date-${tranche.id}`}>{t('drawdowns.reverseDate')}</label>
                  <DateInput
                    id={`reverse-date-${tranche.id}`}
                    value={reverseDate}
                    onChange={setReverseDate}
                  />
                  <Button
                    disabled={reverse.isPending}
                    onClick={() =>
                      reverse.mutate(
                        { trancheId: tranche.id, date: reverseDate },
                        { onSuccess: () => setReverseId(null) }
                      )
                    }
                  >
                    {t('drawdowns.reverse')}
                  </Button>
                  <Button variant="ghost" onClick={() => setReverseId(null)}>
                    {t('drawdowns.add.cancel')}
                  </Button>
                  {reverse.error && (
                    <p role="alert" className="text-error">
                      {t('drawdowns.reverseError')}
                    </p>
                  )}
                </div>
              )}
              {tranche.status === 'PLANNED' && (
                <Button
                  variant="outline"
                  size="sm"
                  type="button"
                  onClick={() => setDrawTrancheId(tranche.id)}
                  disabled={drawTrancheId === tranche.id}
                >
                  <Banknote className="h-3.5 w-3.5 mr-1" />
                  {t('drawdowns.draw')}
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
