import { Link } from 'react-router';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { useAsset } from '@/hooks/useAssets';
import { useLiability } from '@/hooks/useLiabilities';
import { useProperty } from '@/hooks/useRealEstate';
import type { TransactionFilters } from '@/types/transaction';

/** Show the active drill-down filters with links back to their source where applicable. */
export function TransactionScope({
  filters,
  onChange,
}: {
  filters: TransactionFilters;
  onChange: (filters: TransactionFilters) => void;
}) {
  const { t } = useTranslation('transactions');
  const { data: asset } = useAsset(filters.assetId ?? null);
  const { data: property } = useProperty(filters.realEstateId ?? 0);
  const { data: liability } = useLiability(filters.liabilityId ?? null);
  const importLabel = filters.importSessionId
    ? t('scope.importBatch', { id: filters.importSessionId })
    : undefined;
  const scopes = [
    { key: 'assetId', id: filters.assetId, name: asset?.name, path: '/assets', label: 'asset' },
    {
      key: 'realEstateId',
      id: filters.realEstateId,
      name: property?.name,
      path: '/real-estate',
      label: 'property',
    },
    {
      key: 'liabilityId',
      id: filters.liabilityId,
      name: liability?.name,
      path: '/liabilities',
      label: 'liability',
    },
  ] as const;
  if (!importLabel && !scopes.some(scope => scope.id)) return null;
  return (
    <div className="mb-4 flex flex-wrap gap-2" aria-label={t('scope.label')}>
      {importLabel && (
        <button
          type="button"
          className="inline-flex min-h-11 items-center gap-2 rounded-md border border-primary/50 bg-primary/10 px-3 text-sm font-medium text-primary hover:bg-primary/20 md:min-h-9"
          aria-label={t('scope.clear', { name: importLabel })}
          onClick={() => onChange({ ...filters, importSessionId: undefined })}
        >
          {importLabel}
          <X aria-hidden="true" className="h-3.5 w-3.5" />
        </button>
      )}
      {scopes
        .filter(scope => scope.id)
        .map(scope => (
          <div
            key={scope.key}
            className="flex min-w-0 max-w-full items-center gap-1 rounded-md border border-border bg-surface pl-3 text-sm"
          >
            <Link
              to={`${scope.path}?highlight=${scope.id}`}
              className="min-w-0 break-words py-2 text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {t(`scope.${scope.label}`)}: {scope.name ?? `#${scope.id}`}
            </Link>
            <Button
              variant="ghost"
              size="icon"
              className="shrink-0"
              aria-label={t('scope.clear', { name: scope.name ?? t(`scope.${scope.label}`) })}
              onClick={() => onChange({ ...filters, [scope.key]: undefined })}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        ))}
    </div>
  );
}
