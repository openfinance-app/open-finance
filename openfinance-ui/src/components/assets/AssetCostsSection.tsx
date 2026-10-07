import { useDateFormatter } from '@/hooks/useDateFormatter';
import { useState, useEffect } from 'react';
import { Button } from '@/components/ui/Button';
/**
 * AssetCostsSection Component (Task 8)
 *
 * Renders the asset's linked costs inside the detail modal Overview tab,
 * split into Capitalized (improvements) and Maintenance lists, fetched via
 * the assetId transaction filter.
 */
import { useTranslation } from 'react-i18next';
import { Hammer, Wrench, AlertCircle } from 'lucide-react';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useTransactions } from '@/hooks/useTransactions';
import type { Asset } from '@/types/asset';
import type { Transaction } from '@/types/transaction';
import { cn } from '@/lib/utils';

const MOVEMENTS_PAGE_SIZE = 200;

function CostRow({ tx, label, currency }: { tx: Transaction; label: string; currency: string }) {
  const { date: formatDate } = useDateFormatter();
  return (
    <div className="flex items-center justify-between px-4 py-3 bg-background hover:bg-surface transition-colors">
      <div className="flex items-center gap-3 min-w-0">
        <div className="min-w-0">
          <div className="text-sm text-foreground truncate flex items-center gap-2">
            <span className="truncate">{tx.description || `Transaction #${tx.id}`}</span>
            <span className="inline-flex items-center px-2 py-0.5 rounded-md bg-surface text-muted-foreground border border-border text-xs font-medium flex-shrink-0">
              {label}
            </span>
          </div>
          <div className="text-xs text-muted-foreground">{formatDate(tx.date)}</div>
        </div>
      </div>
      <div className="text-sm font-mono font-semibold text-foreground ml-4 flex-shrink-0">
        <ConvertedAmount amount={tx.amount} currency={tx.currency || currency} inline />
      </div>
    </div>
  );
}

export function AssetCostsSection({ asset }: { asset: Asset }) {
  const { t } = useTranslation('assets');
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [asset.id]);
  const {
    data: costsPage,
    isLoading,
    error,
  } = useTransactions({
    assetId: asset.id,
    size: MOVEMENTS_PAGE_SIZE,
    page,
    sort: 'date,desc',
  });

  const costs = costsPage?.content ?? [];
  const capitalized = costs.filter(tx => tx.movementType === 'CAPITAL_IMPROVEMENT');
  const maintenance = costs.filter(tx => tx.movementType === 'MAINTENANCE');
  const acquisition = costs.filter(
    tx => !['CAPITAL_IMPROVEMENT', 'MAINTENANCE'].includes(tx.movementType ?? '')
  );

  const groups = [
    {
      key: 'acquisition',
      title: t('costs.acquisition'),
      icon: <Wrench className="h-4 w-4" />,
      transactions: acquisition,
    },
    {
      key: 'capitalized',
      title: t('costs.capitalized'),
      icon: <Hammer className="h-4 w-4 text-muted-foreground" />,
      transactions: capitalized,
    },
    {
      key: 'maintenance',
      title: t('costs.maintenance'),
      icon: <Wrench className="h-4 w-4 text-muted-foreground" />,
      transactions: maintenance,
    },
  ];

  return (
    <div className="bg-background border border-border rounded-lg p-6">
      <h3 className="text-lg font-semibold text-foreground mb-4">{t('costs.title')}</h3>
      {isLoading ? (
        <div className="space-y-2 animate-pulse">
          {[...Array(2)].map((_, i) => (
            <div key={i} className="h-12 bg-surface border border-border rounded-lg" />
          ))}
        </div>
      ) : error ? (
        <div className="flex items-center gap-2 p-4 bg-error/10 border border-error/20 rounded-lg text-error text-sm">
          <AlertCircle className="h-4 w-4 flex-shrink-0" />
          <span>{t('costs.error')}</span>
        </div>
      ) : costs.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('costs.empty')}</p>
      ) : (
        <div className="space-y-4">
          {groups
            .filter(group => group.transactions.length > 0)
            .map(group => (
              <div key={group.key}>
                <h4
                  className={cn(
                    'text-sm font-semibold text-foreground mb-2 flex items-center gap-2'
                  )}
                >
                  {group.icon}
                  {group.title}
                </h4>
                <div className="divide-y divide-border border border-border rounded-lg overflow-hidden">
                  {group.transactions.map(tx => (
                    <CostRow
                      key={tx.id}
                      tx={tx}
                      label={
                        group.key === 'acquisition'
                          ? t('costs.acquisition')
                          : t(`costs.movementTypes.${tx.movementType}`)
                      }
                      currency={asset.currency}
                    />
                  ))}
                </div>
              </div>
            ))}
        </div>
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
