/**
 * AssetPerformanceTable Component
 * Task 5.4.3: Create AssetPerformanceTable component
 * 
 * Sortable table displaying asset performance with gain/loss details
 */
import { useState, useMemo } from 'react';
import { ArrowUpDown, ArrowUp, ArrowDown, TrendingUp, TrendingDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { Asset } from '@/types/asset';
import { formatPercentage, getGainLossColor, getAssetTypeLabel } from '@/utils/portfolio';
import { multiply } from '@/utils/money';
import { LastUpdatedIndicator } from './LastUpdatedIndicator';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useNavigate } from 'react-router';

interface AssetPerformanceTableProps {
  assets: Asset[];
}

type SortField = 'name' | 'type' | 'totalValue' | 'totalCost' | 'unrealizedGain' | 'gainPercentage';
type SortDirection = 'asc' | 'desc';

interface SortConfig {
  field: SortField;
  direction: SortDirection;
}

export function AssetPerformanceTable({ assets }: AssetPerformanceTableProps) {
  const navigate = useNavigate();
  const { t } = useTranslation('assets');
  const [sortConfig, setSortConfig] = useState<SortConfig>({
    field: 'totalValue',
    direction: 'desc',
  });

  // Sort assets based on current sort config
  const sortedAssets = useMemo(() => {
    const sorted = [...assets];
    
    sorted.sort((a, b) => {
      const { field, direction } = sortConfig;
      let aValue: string | number;
      let bValue: string | number;

      switch (field) {
        case 'name':
          aValue = a.name.toLowerCase();
          bValue = b.name.toLowerCase();
          break;
        case 'type':
          aValue = a.type;
          bValue = b.type;
          break;
        case 'totalValue':
          aValue = a.totalValue;
          bValue = b.totalValue;
          break;
        case 'totalCost':
          aValue = a.totalCost;
          bValue = b.totalCost;
          break;
        case 'unrealizedGain':
          aValue = a.unrealizedGain;
          bValue = b.unrealizedGain;
          break;
        case 'gainPercentage':
          aValue = a.gainPercentage;
          bValue = b.gainPercentage;
          break;
        default:
          return 0;
      }

      if (aValue < bValue) return direction === 'asc' ? -1 : 1;
      if (aValue > bValue) return direction === 'asc' ? 1 : -1;
      return 0;
    });

    return sorted;
  }, [assets, sortConfig]);

  const handleSort = (field: SortField) => {
    setSortConfig(prev => ({
      field,
      direction: prev.field === field && prev.direction === 'asc' ? 'desc' : 'asc',
    }));
  };

  const getSortIcon = (field: SortField) => {
    if (sortConfig.field !== field) {
      return <ArrowUpDown className="h-4 w-4 text-muted-foreground" />;
    }
    return sortConfig.direction === 'asc' ? (
      <ArrowUp className="h-4 w-4 text-primary" />
    ) : (
      <ArrowDown className="h-4 w-4 text-primary" />
    );
  };

  const handleRowClick = (assetId: number) => {
    navigate(`/assets/${assetId}`);
  };

  // Empty state
  if (!assets || assets.length === 0) {
    return (
      <div className="flex items-center justify-center h-32 text-muted-foreground">
        <p className="text-sm">No assets to display</p>
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      {/* Desktop Table */}
      <table className="hidden md:table w-full">
        <thead className="border-b border-border">
          <tr>
            <th className="text-left py-3 px-4">
              <button
                onClick={() => handleSort('name')}
                className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
              >
                {t('table.name')}
                {getSortIcon('name')}
              </button>
            </th>
            <th className="text-left py-3 px-4">
              <button
                onClick={() => handleSort('type')}
                className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
              >
                {t('table.type')}
                {getSortIcon('type')}
              </button>
            </th>
            <th className="text-right py-3 px-4">
              <button
                onClick={() => handleSort('totalCost')}
                className="flex items-center justify-end gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors ml-auto"
              >
                {t('table.costBasis')}
                {getSortIcon('totalCost')}
              </button>
            </th>
            <th className="text-right py-3 px-4">
              <button
                onClick={() => handleSort('totalValue')}
                className="flex items-center justify-end gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors ml-auto"
              >
                {t('table.currentValue')}
                {getSortIcon('totalValue')}
              </button>
            </th>
            <th className="text-right py-3 px-4">
              <button
                onClick={() => handleSort('unrealizedGain')}
                className="flex items-center justify-end gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors ml-auto"
              >
                {t('table.gainLoss')}
                {getSortIcon('unrealizedGain')}
              </button>
            </th>
            <th className="text-right py-3 px-4">
              <button
                onClick={() => handleSort('gainPercentage')}
                className="flex items-center justify-end gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors ml-auto"
              >
                {t('table.return')}
                {getSortIcon('gainPercentage')}
              </button>
            </th>
          </tr>
        </thead>
        <tbody>
          {sortedAssets.map((asset) => (
            <tr
              key={asset.id}
              onClick={() => handleRowClick(asset.id)}
              className="border-b border-border hover:bg-surface/50 cursor-pointer transition-colors"
            >
              <td className="py-3 px-4">
                <div>
                  <p className="text-sm font-medium text-foreground">{asset.name}</p>
                  {asset.symbol ? (
                    <p className="text-xs text-muted-foreground">{asset.symbol}</p>
                  ) : (
                    <LastUpdatedIndicator lastUpdated={asset.lastUpdated} size="sm" />
                  )}
                </div>
              </td>
              <td className="py-3 px-4">
                <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-surface border border-border text-foreground">
                  {getAssetTypeLabel(asset.type)}
                </span>
              </td>
              <td className="py-3 px-4 text-right">
                <p className="text-sm text-foreground">
                  <ConvertedAmount
                    amount={asset.totalCost}
                    currency={asset.currency}
                    convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.totalCost, asset.exchangeRate) : undefined}
                    baseCurrency={asset.baseCurrency}
                    exchangeRate={asset.exchangeRate}
                    isConverted={asset.isConverted}
                    inline
                  />
                </p>
                <p className="text-xs text-muted-foreground">
                  {asset.quantity} × <ConvertedAmount
                    amount={asset.purchasePrice}
                    currency={asset.currency}
                    convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.purchasePrice, asset.exchangeRate) : undefined}
                    baseCurrency={asset.baseCurrency}
                    exchangeRate={asset.exchangeRate}
                    isConverted={asset.isConverted}
                    inline
                  />
                </p>
              </td>
              <td className="py-3 px-4 text-right">
                 <p className="text-sm font-medium text-foreground">
                   {/* REQ-2.2: Display current value with base-currency conversion when available */}
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
                  </p>
                <p className="text-xs text-muted-foreground">
                  {asset.quantity} × <ConvertedAmount
                    amount={asset.currentPrice}
                    currency={asset.currency}
                    convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.currentPrice, asset.exchangeRate) : undefined}
                    baseCurrency={asset.baseCurrency}
                    exchangeRate={asset.exchangeRate}
                    isConverted={asset.isConverted}
                    inline
                  />
                </p>
              </td>
              <td className="py-3 px-4 text-right">
                <div className="flex items-center justify-end gap-1">
                  {asset.unrealizedGain > 0 ? (
                    <TrendingUp className="h-4 w-4 text-green-600" />
                  ) : asset.unrealizedGain < 0 ? (
                    <TrendingDown className="h-4 w-4 text-red-600" />
                  ) : null}
                  <p className={`text-sm font-medium ${getGainLossColor(asset.unrealizedGain)}`}>
                    <ConvertedAmount
                      amount={asset.unrealizedGain}
                      currency={asset.currency}
                      convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.unrealizedGain, asset.exchangeRate) : undefined}
                      baseCurrency={asset.baseCurrency}
                      exchangeRate={asset.exchangeRate}
                      isConverted={asset.isConverted}
                      inline
                    />
                  </p>
                </div>
              </td>
              <td className="py-3 px-4 text-right">
                <p className={`text-sm font-medium ${getGainLossColor(asset.unrealizedGain)}`}>
                  {formatPercentage(asset.gainPercentage * 100)}
                </p>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Mobile Card View */}
      <div className="md:hidden space-y-3">
        {sortedAssets.map((asset) => (
          <div
            key={asset.id}
            onClick={() => handleRowClick(asset.id)}
            className="bg-surface border border-border rounded-lg p-4 cursor-pointer hover:bg-surface/50 transition-colors"
          >
            <div className="flex items-start justify-between mb-3">
              <div>
                <p className="text-sm font-medium text-foreground">{asset.name}</p>
                <p className="text-xs text-muted-foreground">
                  {asset.symbol || getAssetTypeLabel(asset.type)}
                </p>
              </div>
              <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-background border border-border text-foreground">
                {getAssetTypeLabel(asset.type)}
              </span>
            </div>

            <div className="grid grid-cols-2 gap-3 text-sm">
               <div>
                <p className="text-xs text-muted-foreground mb-1">{t('table.currentValue')}</p>
                 <p className="font-medium text-foreground">
                   {/* REQ-2.2: Display current value with base-currency conversion when available */}
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
                </p>
              </div>
              <div className="text-right">
                <p className="text-xs text-muted-foreground mb-1">{t('table.gainLoss')}</p>
                <div className="flex items-center justify-end gap-1">
                  {asset.unrealizedGain > 0 ? (
                    <TrendingUp className="h-3 w-3 text-green-600" />
                  ) : asset.unrealizedGain < 0 ? (
                    <TrendingDown className="h-3 w-3 text-red-600" />
                  ) : null}
                  <p className={`font-medium ${getGainLossColor(asset.unrealizedGain)}`}>
                    {formatPercentage(asset.gainPercentage * 100)}
                  </p>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
