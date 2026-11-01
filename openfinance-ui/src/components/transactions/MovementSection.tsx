import { useTranslation } from 'react-i18next';
import { useProperties } from '@/hooks/useRealEstate';
import { useAssets } from '@/hooks/useAssets';

interface Target {
  realEstateId?: number;
  assetId?: number;
}

interface Props extends Target {
  movementType?: string;
  date?: string;
  onChange: (target: Target) => void;
  error?: string;
  disabled?: boolean;
}

/** One target for the maintenance or improvement action selected by the parent form. */
export function MovementSection({
  realEstateId,
  assetId,
  onChange,
  error,
  disabled,
  movementType,
  date,
}: Props) {
  const { t } = useTranslation('transactions');
  const { data: properties = [] } = useProperties();
  const { data: assets = [] } = useAssets();
  const physicalAssets = assets.filter(asset => asset.isPhysical);
  const unavailable = (target: {
    acquisitionType?: string;
    isActive?: boolean;
    soldDate?: string;
    purchaseDate: string;
  }) =>
    target.isActive === false || (!!target.soldDate && !!date && date > target.soldDate)
      ? t('linkedOperation.inactiveTarget')
      : (movementType === 'CAPITAL_IMPROVEMENT' || movementType === 'MAINTENANCE') &&
          (target.acquisitionType === 'PLANNED' || (!!date && target.purchaseDate > date))
        ? t('linkedOperation.acquireFirst')
        : undefined;
  const value = realEstateId ? `property:${realEstateId}` : assetId ? `asset:${assetId}` : '';
  return (
    <div className="space-y-1.5">
      <label htmlFor="operationTarget" className="block text-sm font-medium text-text-secondary">
        {t('linkedOperation.target')}
      </label>
      <select
        id="operationTarget"
        value={value}
        disabled={disabled}
        aria-invalid={!!error}
        aria-describedby={error ? 'operation-target-error' : undefined}
        onChange={event => {
          const [kind, id] = event.target.value.split(':');
          onChange(
            kind === 'property'
              ? { realEstateId: Number(id) }
              : kind === 'asset'
                ? { assetId: Number(id) }
                : {}
          );
        }}
        className="h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
      >
        <option value="">{t('linkedOperation.selectTarget')}</option>
        {realEstateId && !properties.some(property => property.id === realEstateId) && (
          <option value={value}>{t('linkedOperation.existingProperty')}</option>
        )}
        {assetId && !physicalAssets.some(asset => asset.id === assetId) && (
          <option value={value}>
            {assets.find(asset => asset.id === assetId)?.name ?? t('linkedOperation.existingAsset')}
          </option>
        )}
        {properties.length > 0 && (
          <optgroup label={t('linkedOperation.properties')}>
            {properties.map(property => (
              <option
                key={property.id}
                value={`property:${property.id}`}
                disabled={!!unavailable(property)}
              >
                {property.name}
                {unavailable(property) ? ` — ${unavailable(property)}` : ''}
              </option>
            ))}
          </optgroup>
        )}
        {physicalAssets.length > 0 && (
          <optgroup label={t('linkedOperation.physicalAssets')}>
            {physicalAssets.map(asset => (
              <option key={asset.id} value={`asset:${asset.id}`} disabled={!!unavailable(asset)}>
                {asset.name}
                {unavailable(asset) ? ` — ${unavailable(asset)}` : ''}
              </option>
            ))}
          </optgroup>
        )}
      </select>
      {error && (
        <p id="operation-target-error" className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
