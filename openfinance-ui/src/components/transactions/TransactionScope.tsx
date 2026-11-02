import { Link } from 'react-router';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { useAsset } from '@/hooks/useAssets';
import { useLiability } from '@/hooks/useLiabilities';
import { useProperty } from '@/hooks/useRealEstate';
import type { TransactionFilters } from '@/types/transaction';

/** Keep linked-operation filters visible and removable even when the filter form is closed. */
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
  if (!scopes.some(scope => scope.id)) return null;
  return (
    <div className="mb-4 flex flex-wrap gap-2" aria-label={t('scope.label')}>
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
