/**
 * AssetList Component
 * Task 5.2.9: Create AssetList component
 * Task 9.2.6: Integrated PhysicalAssetCard for physical asset types
 * 
 * Responsive table/grid displaying assets with calculations and color-coded gains/losses
 * Supports sortable columns on desktop view.
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, ChevronUp, ChevronDown, ChevronsUpDown } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { PhysicalAssetCard } from './PhysicalAssetCard';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { cn } from '@/lib/utils';
import { multiply } from '@/utils/money';
import {
  getAssetTypeName,
  getAssetTypeBadgeVariant,
  formatGainLoss
} from '@/hooks/useAssets';
import type { Asset } from '@/types/asset';

// Helper to check if asset is physical
const isPhysicalAsset = (type: string): boolean => {
  return ['VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE'].includes(type);
};

type SortKey = 'name' | 'type' | 'totalValue' | 'totalCost' | 'gainPercentage';
type SortDirection = 'asc' | 'desc';

interface SortConfig {
  key: SortKey;
  direction: SortDirection;
}

interface AssetListProps {
  assets: Asset[];
  onEdit: (asset: Asset) => void;
  onDelete: (assetId: number) => void;
  onView?: (asset: Asset) => void;
  highlightedId?: number | null;
}

function SortIcon({ columnKey, sortConfig }: { columnKey: SortKey; sortConfig: SortConfig | null }) {
  if (!sortConfig || sortConfig.key !== columnKey) {
    return <ChevronsUpDown className="inline h-3.5 w-3.5 ml-1 text-text-tertiary" />;
  }
  return sortConfig.direction === 'asc'
    ? <ChevronUp className="inline h-3.5 w-3.5 ml-1 text-primary" />
    : <ChevronDown className="inline h-3.5 w-3.5 ml-1 text-primary" />;
}

export function AssetList({ assets, onEdit, onDelete, onView, highlightedId }: AssetListProps) {
  const { t } = useTranslation('assets');
  const { t: tc } = useTranslation('common');
  const [deletingAsset, setDeletingAsset] = useState<Asset | null>(null);
  const [sortConfig, setSortConfig] = useState<SortConfig | null>(null);


  useEffect(() => {
    if (highlightedId && assets.length > 0) {
      const timer = setTimeout(() => {
        const element = document.getElementById(`asset-${highlightedId}`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      }, 150);
      return () => clearTimeout(timer);
    }
  }, [highlightedId, assets]);

  const handleDeleteClick = (asset: Asset) => {
    setDeletingAsset(asset);
  };

  const handleConfirmDelete = () => {
    if (deletingAsset) {
      onDelete(deletingAsset.id);
      setDeletingAsset(null);
    }
  };

  const handleSort = (key: SortKey) => {
    setSortConfig(prev => {
      if (prev?.key === key) {
        return { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' };
      }
      return { key, direction: 'asc' };
    });
  };

  if (assets.length === 0) {
    return (
      <div className="text-center py-12 text-text-secondary">
        No assets found. Create your first investment asset!
      </div>
    );
  }

  // Separate physical and financial assets
  const physicalAssets = assets.filter(asset => isPhysicalAsset(asset.type));
  let financialAssets = assets.filter(asset => !isPhysicalAsset(asset.type));

  // Apply sort to financial assets
  if (sortConfig) {
    financialAssets = [...financialAssets].sort((a, b) => {
      let aVal: string | number;
      let bVal: string | number;
      switch (sortConfig.key) {
        case 'name':
          aVal = a.name.toLowerCase();
          bVal = b.name.toLowerCase();
          break;
        case 'type':
          aVal = a.type.toLowerCase();
          bVal = b.type.toLowerCase();
          break;
        case 'totalValue':
          aVal = a.totalValue;
          bVal = b.totalValue;
          break;
        case 'totalCost':
          aVal = a.totalCost;
          bVal = b.totalCost;
          break;
        case 'gainPercentage':
          aVal = a.gainPercentage;
          bVal = b.gainPercentage;
          break;
        default:
          return 0;
      }
      if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
  }

  const thClass = (align: 'left' | 'right' = 'left') =>
    cn(
      'py-3 px-4 text-sm font-medium text-text-secondary cursor-pointer select-none hover:text-text-primary transition-colors',
      align === 'right' ? 'text-right' : 'text-left'
    );

  return (
    <>
      {/* Physical Assets Grid (Task 9.2.6) */}
      {physicalAssets.length > 0 && (
        <div className="mb-8">
          <h3 className="text-lg font-semibold text-text-primary mb-4">{t('table.physicalAssets')}</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {physicalAssets.map((asset) => (
              <div
                key={asset.id}
                id={`asset-${asset.id}`}
                className={cn(
                  "transition-all duration-300 rounded-lg",
                  highlightedId === asset.id && "ring-2 ring-primary ring-offset-2 bg-primary/5 shadow-lg scale-[1.02] z-30"
                )}
              >
                <PhysicalAssetCard
                  asset={asset}
                  onClick={() => onView?.(asset)}
                />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Financial Assets Section */}
      {financialAssets.length > 0 && (
        <>
          {physicalAssets.length > 0 && (
            <h3 className="text-lg font-semibold text-text-primary mb-4 mt-8">{t('table.financialAssets')}</h3>
          )}

          {/* Desktop Table View */}
          <div className="hidden md:block overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-border">
                  <th className={thClass()} onClick={() => handleSort('name')}>
                    {t('table.name')} <SortIcon columnKey="name" sortConfig={sortConfig} />
                  </th>
                  <th className={thClass()} onClick={() => handleSort('type')}>
                    {t('table.type')} <SortIcon columnKey="type" sortConfig={sortConfig} />
                  </th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-text-secondary">{t('table.symbol')}</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">{t('table.quantity')}</th>
                  <th className={thClass('right')} onClick={() => handleSort('totalValue')}>
                    {t('table.currentValue')} <SortIcon columnKey="totalValue" sortConfig={sortConfig} />
                  </th>
                  <th className={thClass('right')} onClick={() => handleSort('totalCost')}>
                    {t('table.costBasis')} <SortIcon columnKey="totalCost" sortConfig={sortConfig} />
                  </th>
                  <th className={thClass('right')} onClick={() => handleSort('gainPercentage')}>
                    {t('table.gainLoss')} <SortIcon columnKey="gainPercentage" sortConfig={sortConfig} />
                  </th>
                  <th className="text-left py-3 px-4 text-sm font-medium text-text-secondary">{t('table.account')}</th>
                  <th className="text-right py-3 px-4 text-sm font-medium text-text-secondary">{t('table.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {financialAssets.map((asset) => {
                   const { formatted, color } = formatGainLoss(
                     asset.unrealizedGain,
                     asset.gainPercentage * 100,
                     asset.baseCurrency ?? asset.currency
                   );

                   return (
                     <tr
                       key={asset.id}
                       id={`asset-${asset.id}`}
                      className={cn(
                        "border-b border-border hover:bg-surface-elevated transition-all duration-300 cursor-pointer",
                        highlightedId === asset.id && "bg-primary/10 border-primary shadow-sm"
                      )}
                      onClick={() => onView?.(asset)}
                    >
                      <td className="py-3 px-4 text-sm font-medium text-text-primary">
                        {asset.name}
                      </td>
                      <td className="py-3 px-4">
                        <Badge
                          variant={getAssetTypeBadgeVariant(asset.type)}
                          size="sm"
                        >
                          {getAssetTypeName(asset.type)}
                        </Badge>
                      </td>
                      <td className="py-3 px-4 text-sm text-text-secondary font-mono">
                        {asset.symbol || '—'}
                      </td>
                      <td className="py-3 px-4 text-sm text-text-primary text-right font-mono">
                        {asset.quantity.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 8
                        })}
                      </td>
                       <td className="py-3 px-4 text-sm text-text-primary text-right font-mono">
                         {/* REQ-2.2: Show converted base-currency value when available */}
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
                      </td>
                      <td className="py-3 px-4 text-sm text-text-secondary text-right font-mono">
                        {/* Show totalCost in base currency when conversion is available */}
                        <ConvertedAmount
                          inline
                          amount={asset.totalCost}
                          currency={asset.currency}
                          convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.totalCost, asset.exchangeRate) : undefined}
                          baseCurrency={asset.baseCurrency}
                          exchangeRate={asset.exchangeRate}
                          isConverted={asset.isConverted}
                        />
                      </td>
                      <td className={`py-3 px-4 text-sm text-right font-mono font-medium ${color}`}>
                        {formatted}
                      </td>
                      <td className="py-3 px-4 text-sm text-text-secondary">
                        {asset.accountName || '—'}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              onEdit(asset);
                            }}
                            aria-label={tc('aria.editAsset')}
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDeleteClick(asset);
                            }}
                            aria-label={tc('aria.deleteAsset')}
                          >
                            <Trash2 className="h-4 w-4 text-error" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Mobile Card View */}
          <div className="md:hidden space-y-4">
            {financialAssets.map((asset) => {
              const { formatted, color } = formatGainLoss(
                asset.unrealizedGain,
                asset.gainPercentage * 100
              );

              return (
                <div
                  key={asset.id}
                  id={`asset-${asset.id}`}
                  className={cn(
                    "bg-surface border border-border rounded-lg p-4 space-y-3 cursor-pointer hover:bg-surface-elevated transition-all duration-300",
                    highlightedId === asset.id && "ring-2 ring-primary ring-offset-2 bg-primary/5 shadow-lg scale-[1.02] z-30"
                  )}
                  onClick={() => onView?.(asset)}
                >
                  {/* Header */}
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <h3 className="font-medium text-text-primary">{asset.name}</h3>
                      {asset.symbol && (
                        <p className="text-sm text-text-secondary font-mono">{asset.symbol}</p>
                      )}
                    </div>
                    <Badge
                      variant={getAssetTypeBadgeVariant(asset.type)}
                      size="sm"
                    >
                      {getAssetTypeName(asset.type)}
                    </Badge>
                  </div>

                  {/* Values */}
                  <div className="space-y-2">
                     <div className="flex justify-between text-sm">
                       <span className="text-text-secondary">{t('table.currentValue')}:</span>
                       <span className="text-text-primary font-mono font-medium">
                         {/* REQ-2.2: Show converted base-currency value when available */}
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
                       </span>
                     </div>
                     <div className="flex justify-between text-sm">
                       <span className="text-text-secondary">{t('table.costBasis')}:</span>
                       <span className="text-text-secondary font-mono">
                         {/* Show totalCost in base currency when conversion is available */}
                         <ConvertedAmount
                           inline
                           amount={asset.totalCost}
                           currency={asset.currency}
                           convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.totalCost, asset.exchangeRate) : undefined}
                           baseCurrency={asset.baseCurrency}
                           exchangeRate={asset.exchangeRate}
                           isConverted={asset.isConverted}
                         />
                       </span>
                     </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-text-secondary">{t('table.gainLoss')}:</span>
                      <span className={`font-mono font-medium ${color}`}>
                        {formatted}
                      </span>
                    </div>
                    <div className="flex justify-between text-sm">
                      <span className="text-text-secondary">{t('table.quantity')}:</span>
                      <span className="text-text-primary font-mono">
                        {asset.quantity.toLocaleString('en-US', {
                          minimumFractionDigits: 2,
                          maximumFractionDigits: 8
                        })}
                      </span>
                    </div>
                    {asset.accountName && (
                      <div className="flex justify-between text-sm">
                        <span className="text-text-secondary">{t('table.account')}:</span>
                        <span className="text-text-primary">{asset.accountName}</span>
                      </div>
                    )}
                  </div>

                  {/* Actions */}
                  <div className="flex gap-2 pt-2 border-t border-border">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        onEdit(asset);
                      }}
                      className="flex-1"
                    >
                      <Pencil className="h-4 w-4 mr-2" />
                      {tc('edit')}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleDeleteClick(asset);
                      }}
                      className="flex-1 text-error"
                    >
                      <Trash2 className="h-4 w-4 mr-2" />
                      {tc('delete')}
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* Delete Confirmation Dialog */}
      <ConfirmationDialog
        open={!!deletingAsset}
        onOpenChange={(open) => !open && setDeletingAsset(null)}
        onConfirm={handleConfirmDelete}
        title={t('detail.deleteConfirm.title')}
        description={t('detail.deleteConfirm.message', { name: deletingAsset?.name ?? '' })}
        confirmText={t('detail.deleteConfirm.confirm')}
        variant="danger"
      />
    </>
  );
}
