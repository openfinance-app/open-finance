import { useQuery } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type { LiabilityTranche } from '@/types/liability';
import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
/**
 * PropertyMovementsSection Component (Task 8)
 *
 * Renders property-linked movements inside the property detail Overview tab:
 *  - "Costs": transactions linked via realEstateId, split into Capitalized
 *    (capital improvements) and Maintenance lists
 *  - "Loan movements": repayments / disbursements of the property's mortgage
 *    liability
 */
import { useTranslation } from 'react-i18next';
import { Hammer, Wrench, Landmark, AlertCircle } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTransactions } from '@/hooks/useTransactions';
import { useLiabilityTransactions } from '@/hooks/useLiabilities';
import type { RealEstateProperty } from '@/types/realEstate';
import type { Transaction } from '@/types/transaction';

const MOVEMENTS_PAGE_SIZE = 200;

/** Movement classifications shown in the Loan movements list. */
const LOAN_MOVEMENT_TYPES = new Set(['REPAYMENT', 'DISBURSEMENT', 'INTEREST', 'INSURANCE', 'FEE']);

function MovementRow({
  tx,
  label,
  currency,
}: {
  tx: Transaction;
  label: string;
  currency: string;
}) {
  const { i18n } = useTranslation('realEstate');
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-surface hover:bg-surface-elevated transition-colors">
      <div className="flex items-center gap-3 min-w-0">
        <div className="min-w-0">
          <div className="text-sm text-text-primary truncate flex items-center gap-2">
            <span className="truncate">{tx.description || `Transaction #${tx.id}`}</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-surface-elevated text-text-secondary border border-border text-xs font-medium flex-shrink-0">
              {label}
            </span>
          </div>
          <div className="text-xs text-text-tertiary">
            {new Date(tx.date).toLocaleDateString(i18n.language, {
              year: 'numeric',
              month: 'short',
              day: 'numeric',
            })}
          </div>
        </div>
      </div>
      <div className="text-sm font-mono font-semibold text-text-primary ml-4 flex-shrink-0">
        <ConvertedAmount amount={tx.amount} currency={tx.currency || currency} inline />
      </div>
    </div>
  );
}

function MovementList({
  title,
  icon,
  transactions,
  currency,
  movementLabel,
  emptyLabel,
}: {
  title: string;
  icon: React.ReactNode;
  transactions: Transaction[];
  currency: string;
  movementLabel: (tx: Transaction) => string;
  emptyLabel: string;
}) {
  return (
    <Card className="p-4">
      <h4 className="plate-label text-sm font-semibold text-text-primary mb-3 flex items-center gap-2">
        {icon}
        {title}
      </h4>
      {transactions.length === 0 ? (
        <p className="text-xs text-text-tertiary">{emptyLabel}</p>
      ) : (
        <div className="divide-y divide-border border border-border rounded-lg overflow-hidden">
          {transactions.map(tx => (
            <MovementRow key={tx.id} tx={tx} label={movementLabel(tx)} currency={currency} />
          ))}
        </div>
      )}
    </Card>
  );
}

export function PropertyMovementsSection({ property }: { property: RealEstateProperty }) {
  const { t } = useTranslation('realEstate');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [property.id]);
  const {
    data: costsPage,
    isLoading: isLoadingCosts,
    error: costsError,
  } = useTransactions({
    realEstateId: property.id,
    size: MOVEMENTS_PAGE_SIZE,
    page,
    sort: 'date,desc',
  });
  const {
    data: loanTransactions = [],
    isLoading: isLoadingLoans,
    error: loansError,
  } = useLiabilityTransactions(property.mortgageId ?? null);

  const relatedLoans = useQuery<Transaction[]>({
    queryKey: ['realEstate', property.id, 'loan-movements'],
    queryFn: async () =>
      (await apiClient.get<Transaction[]>(`/real-estate/${property.id}/loan-movements`)).data,
  });
  const funding = useQuery<LiabilityTranche[]>({
    queryKey: ['realEstate', property.id, 'drawdowns'],
    queryFn: async () =>
      (await apiClient.get<LiabilityTranche[]>(`/real-estate/${property.id}/drawdowns`)).data,
  });
  const acquisition = (costsPage?.content ?? []).filter(
    tx => !['CAPITAL_IMPROVEMENT', 'MAINTENANCE'].includes(tx.movementType ?? '')
  );
  const isLoading = isLoadingCosts || isLoadingLoans;
  const error = costsError ?? loansError ?? funding.error ?? relatedLoans.error;

  const costs = costsPage?.content ?? [];
  const capitalized = costs.filter(tx => tx.movementType === 'CAPITAL_IMPROVEMENT');
  const maintenance = costs.filter(tx => tx.movementType === 'MAINTENANCE');
  const loanMovements = [
    ...new Map([...loanTransactions, ...(relatedLoans.data ?? [])].map(tx => [tx.id, tx])).values(),
  ].filter(tx => tx.movementType != null && LOAN_MOVEMENT_TYPES.has(tx.movementType));

  const hasNoMovements =
    capitalized.length === 0 &&
    maintenance.length === 0 &&
    loanMovements.length === 0 &&
    acquisition.length === 0 &&
    !funding.data?.length;

  return (
    <div className="space-y-4">
      <h3 className="plate-label ">{t('movements.costsTitle')}</h3>
      {isLoading ? (
        <div className="space-y-2 animate-pulse">
          {[...Array(2)].map((_, i) => (
            <div key={i} className="h-12 bg-surface border border-border rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="flex items-center gap-2 p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
          <AlertCircle className="h-4 w-4 flex-shrink-0" />
          <span>{t('movements.error')}</span>
        </div>
      ) : hasNoMovements ? (
        <p className="text-sm text-text-secondary">{t('movements.empty')}</p>
      ) : (
        <>
          <MovementList
            title={t('movements.acquisition')}
            icon={<Landmark className="h-4 w-4" />}
            transactions={acquisition}
            currency={property.currency}
            movementLabel={() => t('movements.acquisition')}
            emptyLabel={t('movements.empty')}
          />
          {funding.data?.length ? (
            <section className="space-y-2">
              <h4 className="plate-label font-semibold">{t('movements.directFunding')}</h4>
              {funding.data.map(draw => (
                <div
                  key={draw.id}
                  className="flex justify-between gap-3 border border-border rounded p-3 text-sm"
                >
                  <span>
                    T{draw.trancheNo} · {draw.drawnDate}
                    {draw.reversedDate
                      ? ` · ${t('movements.reversed', { date: draw.reversedDate })}`
                      : ''}
                  </span>
                  <ConvertedAmount amount={draw.drawnAmount ?? 0} currency={draw.currency} inline />
                </div>
              ))}
            </section>
          ) : null}
          <MovementList
            title={t('movements.capitalized')}
            icon={<Hammer className="h-4 w-4 text-text-secondary" />}
            transactions={capitalized}
            currency={property.currency}
            movementLabel={tx => t(`movements.movementTypes.${tx.movementType}`)}
            emptyLabel={t('movements.empty')}
          />
          <MovementList
            title={t('movements.maintenance')}
            icon={<Wrench className="h-4 w-4 text-text-secondary" />}
            transactions={maintenance}
            currency={property.currency}
            movementLabel={tx => t(`movements.movementTypes.${tx.movementType}`)}
            emptyLabel={t('movements.empty')}
          />
          {loanMovements.length > 0 && (
            <MovementList
              title={t('movements.loanMovements')}
              icon={<Landmark className="h-4 w-4 text-text-secondary" />}
              transactions={loanMovements}
              currency={property.currency}
              movementLabel={tx => t(`movements.movementTypes.${tx.movementType}`)}
              emptyLabel={t('movements.empty')}
            />
          )}
        </>
      )}

      {(costsPage?.totalPages ?? 0) > 1 && (
        <nav className="flex gap-3 items-center mt-4" aria-label={t('movementPaging.label')}>
          <Button variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t('movementPaging.previous')}
          </Button>
          <span>{t('movementPaging.page', { page: page + 1, total: costsPage?.totalPages })}</span>
          <Button
            variant="ghost"
            disabled={page + 1 >= (costsPage?.totalPages ?? 1)}
            onClick={() => setPage(page + 1)}
          >
            {t('movementPaging.next')}
          </Button>
        </nav>
      )}
    </div>
  );
}
