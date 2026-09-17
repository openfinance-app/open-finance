/**
 * MovementSection Component (manual movement entry)
 *
 * Rendered for EXPENSE transactions in TransactionForm: classifies the expense
 * as a capital improvement or maintenance movement targeting exactly one
 * real-estate property or physical asset, mirroring the backend @AssertTrue
 * coherence rules on TransactionRequest:
 *   - CAPITAL_IMPROVEMENT / MAINTENANCE require realEstateId or assetId
 *   - at most one instrument per transaction (liability XOR property/asset)
 */
import { useTranslation } from 'react-i18next';
import { HelpTooltip } from '@/components/ui/HelpTooltip';
import { useProperties } from '@/hooks/useRealEstate';
import { useAssets } from '@/hooks/useAssets';
import type { MovementType } from '@/types/transaction';

/**
 * Movement types selectable in the manual-entry UI. System-managed types
 * (DISBURSEMENT / REPAYMENT / …) are created by the liability flows and never
 * chosen by hand here.
 */
export type ImprovementMovementType = Extract<MovementType, 'CAPITAL_IMPROVEMENT' | 'MAINTENANCE'>;

const MOVEMENT_TYPE_VALUES: Array<ImprovementMovementType | ''> = [
  '',
  'CAPITAL_IMPROVEMENT',
  'MAINTENANCE',
];

/** Validation messages surfaced by the TransactionForm zod schema. */
export interface MovementSectionErrors {
  movementType?: string;
  realEstateId?: string;
  assetId?: string;
}

interface MovementSectionProps {
  movementType: ImprovementMovementType | undefined;
  realEstateId: number | undefined;
  assetId: number | undefined;
  onMovementTypeChange: (value: ImprovementMovementType | undefined) => void;
  onRealEstateIdChange: (value: number | undefined) => void;
  onAssetIdChange: (value: number | undefined) => void;
  errors: MovementSectionErrors;
  disabled?: boolean;
}

const selectClass =
  'w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150 disabled:opacity-50';

export function MovementSection({
  movementType,
  realEstateId,
  assetId,
  onMovementTypeChange,
  onRealEstateIdChange,
  onAssetIdChange,
  errors,
  disabled,
}: MovementSectionProps) {
  const { t } = useTranslation('transactions');
  const { data: properties = [] } = useProperties();
  const { data: assets = [] } = useAssets();
  // Client-side filter: the target must be a physical asset (kitchen, vehicle…),
  // never a security. AssetResponse.isPhysical is derived from the asset type.
  const physicalAssets = assets.filter(a => a.isPhysical);

  const targetRequired = movementType === 'CAPITAL_IMPROVEMENT' || movementType === 'MAINTENANCE';

  return (
    <div
      data-testid="movement-section"
      className="space-y-3 rounded-lg border border-border bg-surface p-3"
    >
      <div className="flex items-center gap-1 mb-1">
        <label htmlFor="movementType" className="block text-sm font-medium text-text-primary">
          {t('form.movementType')}
        </label>
        <HelpTooltip text={t('form.movementHint')} side="right" />
      </div>
      <select
        id="movementType"
        value={movementType ?? ''}
        onChange={e =>
          onMovementTypeChange(
            e.target.value ? (e.target.value as ImprovementMovementType) : undefined
          )
        }
        disabled={disabled}
        aria-invalid={errors.movementType ? 'true' : 'false'}
        aria-describedby={errors.movementType ? 'movementType-error' : undefined}
        className={selectClass}
      >
        {MOVEMENT_TYPE_VALUES.map(value => (
          <option key={value} value={value}>
            {value === '' ? t('form.movementTypeNone') : t(`form.movementTypes.${value}`)}
          </option>
        ))}
      </select>
      {errors.movementType && (
        <p id="movementType-error" className="mt-1 text-sm text-error" role="alert">
          {errors.movementType}
        </p>
      )}

      {targetRequired && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label
              htmlFor="movement-realEstateId"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.movementProperty')}
            </label>
            <select
              id="movement-realEstateId"
              value={realEstateId ?? ''}
              onChange={e =>
                onRealEstateIdChange(e.target.value ? Number(e.target.value) : undefined)
              }
              disabled={disabled}
              aria-invalid={errors.realEstateId ? 'true' : 'false'}
              className={selectClass}
            >
              <option value="">{t('form.selectProperty')}</option>
              {properties.map(property => (
                <option key={property.id} value={property.id}>
                  {property.name}
                </option>
              ))}
            </select>
            {errors.realEstateId && (
              <p className="mt-1 text-sm text-error" role="alert">
                {errors.realEstateId}
              </p>
            )}
          </div>
          <div>
            <label
              htmlFor="movement-assetId"
              className="block text-sm font-medium text-text-primary mb-1.5"
            >
              {t('form.movementAsset')}
            </label>
            <select
              id="movement-assetId"
              value={assetId ?? ''}
              onChange={e => onAssetIdChange(e.target.value ? Number(e.target.value) : undefined)}
              disabled={disabled}
              aria-invalid={errors.assetId ? 'true' : 'false'}
              className={selectClass}
            >
              <option value="">{t('form.selectPhysicalAsset')}</option>
              {physicalAssets.map(asset => (
                <option key={asset.id} value={asset.id}>
                  {asset.name}
                </option>
              ))}
            </select>
            {errors.assetId && (
              <p className="mt-1 text-sm text-error" role="alert">
                {errors.assetId}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
