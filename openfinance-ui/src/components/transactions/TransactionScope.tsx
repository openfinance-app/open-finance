import { useTranslation } from 'react-i18next';
import { FilterChip } from '@/components/ui/FilterChip';
import { useAsset } from '@/hooks/useAssets';
import { useLiability } from '@/hooks/useLiabilities';
import { useProperty } from '@/hooks/useRealEstate';
import type { TransactionFilters } from '@/types/transaction';

/** Show applied transaction badges and link drill-downs back to their source. */
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
  return (
    <>
      {(['noCategory', 'noPayee', 'excludeTransfers'] as const).map(
        key =>
          filters[key] && (
            <FilterChip
              key={key}
              label={t(`filterKeys.${key}`)}
              onRemove={() => onChange({ ...filters, [key]: undefined })}
            />
          )
      )}
      {importLabel && (
        <FilterChip
          label={importLabel}
          onRemove={() => onChange({ ...filters, importSessionId: undefined })}
        />
      )}
      {scopes
        .filter(scope => scope.id)
        .map(scope => (
          <FilterChip
            key={scope.key}
            label={`${t(`scope.${scope.label}`)}: ${scope.name ?? `#${scope.id}`}`}
            to={`${scope.path}?highlight=${scope.id}`}
            onRemove={() => onChange({ ...filters, [scope.key]: undefined })}
          />
        ))}
      {filters.tag && (
        <FilterChip
          label={`${t('filterKeys.filteredByTag')} ${filters.tag}`}
          onRemove={() => onChange({ ...filters, tag: undefined })}
        />
      )}
    </>
  );
}
