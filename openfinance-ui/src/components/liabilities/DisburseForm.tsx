/**
 * DisburseForm Component (tranche drawdown action)
 *
 * Shared form behind the per-tranche "Draw" action (TrancheDrawdownsTab) and
 * the generic "Disburse" button on the liability Overview tab. Posts to the
 * liability disbursement endpoint via useDisburseLiability: the funds go
 * either to one of the user's accounts (toAccountId) or directly to the
 * seller of the linked property (directRealEstateId). When no tranche is
 * passed the backend auto-picks (or creates) the next tranche.
 */
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { useDisburseLiability } from '@/hooks/useLiabilities';
import { useAccounts } from '@/hooks/useAccounts';
import { getToday } from '@/utils/date';
import type { Liability, LiabilityTranche } from '@/types/liability';

type DisburseRoute = 'account' | 'property';

interface DisburseFormProps {
  liability: Liability;
  /** Tranche to draw; omit for a generic disbursement (backend picks the next tranche). */
  tranche?: LiabilityTranche;
  onDone: () => void;
}

export function DisburseForm({ liability, tranche, onDone }: DisburseFormProps) {
  const { t } = useTranslation('liabilities');
  const disburse = useDisburseLiability();
  const { data: accounts = [] } = useAccounts('active');

  const linkedPropertyId = tranche?.realEstateId ?? liability.linkedPropertyId ?? null;
  // Amount prefilled with the remaining planned amount of the tranche.
  const [amount, setAmount] = useState(
    tranche ? String(tranche.remaining ?? tranche.plannedAmount) : ''
  );
  const [date, setDate] = useState(getToday());
  const [route, setRoute] = useState<DisburseRoute>('account');
  const [accountId, setAccountId] = useState<number | undefined>(undefined);

  const numericAmount = Number(amount);
  const valid =
    numericAmount > 0 &&
    (route === 'property' ? linkedPropertyId != null : accountId !== undefined);

  const submit = async () => {
    if (!valid) return;
    try {
      await disburse.mutateAsync({
        liabilityId: Number(liability.id),
        request: {
          trancheId: tranche?.id,
          amount: numericAmount,
          date,
          toAccountId: route === 'account' ? accountId : undefined,
          directRealEstateId: route === 'property' ? (linkedPropertyId ?? undefined) : undefined,
        },
      });
      onDone();
    } catch {
      // The mutation surfaces its error state; the form stays open for a retry.
    }
  };

  const selectClass =
    'w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150';

  return (
    <div
      className="p-4 mb-3 border border-border rounded-lg bg-surface space-y-3"
      data-testid="disburse-form"
    >
      <p className="text-sm font-medium text-text-primary">{t('drawdowns.disburseForm.title')}</p>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label htmlFor="disburse-amount" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.amount')} *
          </label>
          <NumberInput
            id="disburse-amount"
            value={amount}
            onChange={setAmount}
            placeholder="0.00"
            min="0.01"
          />
        </div>
        <div>
          <label htmlFor="disburse-date" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.date')}
          </label>
          <DateInput id="disburse-date" value={date} onChange={setDate} />
        </div>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label htmlFor="disburse-route" className="block text-sm font-medium mb-1.5">
            {t('drawdowns.disburseForm.route')}
          </label>
          <select
            id="disburse-route"
            value={route}
            onChange={e => setRoute(e.target.value as DisburseRoute)}
            className={selectClass}
          >
            <option value="account">{t('drawdowns.disburseForm.routeAccount')}</option>
            {linkedPropertyId != null && (
              <option value="property">{t('drawdowns.disburseForm.routeProperty')}</option>
            )}
          </select>
        </div>
        {route === 'account' && (
          <div>
            <label htmlFor="disburse-account" className="block text-sm font-medium mb-1.5">
              {t('drawdowns.disburseForm.account')} *
            </label>
            <select
              id="disburse-account"
              value={accountId ?? ''}
              onChange={e => setAccountId(e.target.value ? Number(e.target.value) : undefined)}
              className={selectClass}
            >
              <option value="">--</option>
              {accounts.map(account => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
      {disburse.isError && (
        <p role="alert" className="text-sm text-error">
          {t('drawdowns.disburseForm.error')}
        </p>
      )}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" type="button" onClick={onDone} disabled={disburse.isPending}>
          {t('drawdowns.disburseForm.cancel')}
        </Button>
        <Button
          variant="primary"
          type="button"
          disabled={!valid}
          isLoading={disburse.isPending}
          onClick={submit}
        >
          {t('drawdowns.disburseForm.submit')}
        </Button>
      </div>
    </div>
  );
}
