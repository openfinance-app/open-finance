/**
 * PhysicalAssetCard Component
 * Task 9.2.6: Create PhysicalAssetCard component
 * 
 * Displays physical asset details with depreciation, condition, and warranty status
 */
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { getConditionBadgeVariant, getAssetTypeBadgeVariant } from '@/hooks/useAssets';
import { 
  Package, 
  Shield, 
  TrendingDown, 
  Calendar,
  Info
} from 'lucide-react';
import type { Asset } from '@/types/asset';

interface PhysicalAssetCardProps {
  asset: Asset;
  onClick?: () => void;
}

export function PhysicalAssetCard({ asset, onClick }: PhysicalAssetCardProps) {
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(asset.currency);
  const isWarrantyValid = asset.isWarrantyValid;

  // Actual market value change: purchase cost vs current market value
  const currentValue = asset.totalValue; // quantity × currentPrice (actual market value)
  const valueLoss = asset.totalCost - currentValue;
  const lossPercent = asset.totalCost > 0 ? (valueLoss / asset.totalCost) * 100 : 0;
  const retainedPercent = asset.totalCost > 0 ? Math.min((currentValue / asset.totalCost) * 100, 100) : 0;
  const hasValueChange = asset.totalCost > 0 && currentValue !== asset.totalCost;

  return (
    <Card
      onClick={onClick}
      className="cursor-pointer hover:shadow-lg transition-shadow duration-200 p-5 space-y-4"
    >
      {/* Header: Type Badge and Condition */}
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <Package className="h-5 w-5 text-text-secondary" />
            <Badge 
              variant={getAssetTypeBadgeVariant(asset.type)} 
              size="sm"
            >
              {asset.type.charAt(0) + asset.type.slice(1).toLowerCase()}
            </Badge>
          </div>
          <h3 className="text-lg font-semibold text-text-primary line-clamp-2">
            {asset.name}
          </h3>
        </div>
        {asset.condition && (
          <Badge 
            variant={getConditionBadgeVariant(asset.condition)} 
            size="md"
            className="ml-2"
          >
            {asset.condition.charAt(0) + asset.condition.slice(1).toLowerCase()}
          </Badge>
        )}
      </div>

      {/* Brand, Model, Serial Number */}
      <div className="space-y-1.5">
        {asset.brand && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-text-secondary font-medium">Brand:</span>
            <span className="text-text-primary">{asset.brand}</span>
          </div>
        )}
        {asset.model && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-text-secondary font-medium">Model:</span>
            <span className="text-text-primary">{asset.model}</span>
          </div>
        )}
        {asset.serialNumber && (
          <div className="flex items-center gap-2 text-sm">
            <span className="text-text-secondary font-medium">Serial:</span>
            <span className="text-text-primary font-mono text-xs">
              {asset.serialNumber.length > 20 
                ? `${asset.serialNumber.substring(0, 20)}...` 
                : asset.serialNumber}
            </span>
          </div>
        )}
      </div>

      {/* Value Change Bar */}
      {hasValueChange && (
        <div className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <div className="flex items-center gap-1.5">
              <TrendingDown className="h-4 w-4 text-text-secondary" />
              <span className="text-text-secondary font-medium">Value Change</span>
            </div>
            <span className={`text-xs font-medium ${valueLoss > 0 ? 'text-red-500' : 'text-green-500'}`}>
              {valueLoss > 0 ? '-' : '+'}{Math.abs(lossPercent).toFixed(0)}%
              {' '}
              ({valueLoss > 0 ? '-' : '+'}
              <ConvertedAmount
                amount={Math.abs(valueLoss)}
                currency={asset.currency}
                isConverted={false}
                inline
              />)
            </span>
          </div>
          
          {/* Progress Bar — green = retained value, gray = loss */}
          <div className="relative h-6 bg-red-100 dark:bg-red-950 rounded-full overflow-hidden border border-border">
            {/* Retained value portion */}
            <div 
              className={`absolute inset-y-0 left-0 transition-all duration-300 ${
                valueLoss > 0
                  ? 'bg-gradient-to-r from-green-500 to-green-600'
                  : 'bg-gradient-to-r from-blue-500 to-blue-600'
              }`}
              style={{ width: `${retainedPercent}%` }}
            />
            
            {/* Labels */}
            <div className="relative h-full flex items-center justify-between px-3 text-xs font-medium">
              <span className="text-white drop-shadow-md">
                <ConvertedAmount
                  amount={currentValue}
                  currency={asset.currency}
                  isConverted={false}
                  inline
                />
              </span>
              {valueLoss > 0 && retainedPercent < 85 && (
                <span className="text-red-600 dark:text-red-400 drop-shadow-sm">
                  <ConvertedAmount
                    amount={asset.totalCost}
                    currency={asset.currency}
                    isConverted={false}
                    inline
                  />
                </span>
              )}
            </div>
          </div>
          
          {/* Legend */}
          <div className="flex items-center gap-4 text-xs text-text-tertiary pt-1">
            <div className="flex items-center gap-1.5">
              <div className={`w-3 h-3 rounded-full ${valueLoss > 0 ? 'bg-green-500' : 'bg-blue-500'}`} />
              <span>Current Value</span>
            </div>
            <div className="flex items-center gap-1.5">
              <div className="w-3 h-3 rounded-full bg-red-200 dark:bg-red-900" />
              <span>Loss</span>
            </div>
          </div>
        </div>
      )}

      {/* Value Summary */}
      <div className="grid grid-cols-2 gap-3 pt-2 border-t border-border">
        <div>
          <div className="text-xs text-text-secondary mb-1">Current Value</div>
          <div className="text-lg font-bold text-text-primary">
            {/* Reference REQ-10.1: Show asset value with base-currency conversion hint */}
             <ConvertedAmount
               amount={asset.totalValue}
               currency={asset.currency}
               convertedAmount={asset.valueInBaseCurrency}
               baseCurrency={asset.baseCurrency}
               exchangeRate={asset.exchangeRate}
               isConverted={asset.isConverted}
               secondaryAmount={asset.valueInSecondaryCurrency}
               secondaryCurrency={asset.secondaryCurrency}
               inline
             />
          </div>
        </div>
        <div>
          <div className="text-xs text-text-secondary mb-1">Purchase Cost</div>
          <div className="text-lg font-semibold text-text-secondary">
            <ConvertedAmount
              amount={asset.totalCost}
              currency={asset.currency}
              isConverted={false}
              secondaryAmount={convert(asset.totalCost)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              inline
            />
          </div>
        </div>
      </div>

      {/* Warranty Status Badge */}
      {asset.warrantyExpiration && (
        <div className="flex items-center gap-2 pt-2 border-t border-border">
          <Shield className={`h-4 w-4 ${isWarrantyValid ? 'text-success' : 'text-error'}`} />
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-text-primary">
                Warranty
              </span>
              <Badge 
                variant={isWarrantyValid ? 'success' : 'error'} 
                size="sm"
              >
                {isWarrantyValid ? 'Active' : 'Expired'}
              </Badge>
            </div>
            <div className="flex items-center gap-1 mt-0.5">
              <Calendar className="h-3 w-3 text-text-tertiary" />
              <span className="text-xs text-text-tertiary">
                {isWarrantyValid ? 'Expires: ' : 'Expired: '}
                {new Date(asset.warrantyExpiration).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric'
                })}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Useful Life Indicator */}
      {asset.usefulLifeYears && (
        <div className="flex items-center gap-2 text-sm text-text-tertiary">
          <Info className="h-4 w-4" />
          <span>
            Useful life: {asset.usefulLifeYears} years
            {!!asset.holdingDays && (
              <span className="ml-1">
                ({Math.floor(asset.holdingDays / 365)} years owned)
              </span>
            )}
          </span>
        </div>
      )}

      {/* Photo Placeholder */}
      {asset.photoPath ? (
        <div className="rounded-lg overflow-hidden border border-border">
          <img 
            src={asset.photoPath} 
            alt={asset.name}
            className="w-full h-32 object-cover"
          />
        </div>
      ) : (
        <div className="flex items-center justify-center h-32 rounded-lg bg-surface-elevated border border-dashed border-border">
          <div className="text-center text-text-tertiary">
            <Package className="h-8 w-8 mx-auto mb-2 opacity-30" />
            <span className="text-xs">No photo</span>
          </div>
        </div>
      )}
    </Card>
  );
}
