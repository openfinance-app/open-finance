/**
 * AssetsPage Component
 * Task 5.2.7: Create AssetsPage component
 * 
 * Main page for managing user investment assets with filters and pagination
 */
import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router';
import { Plus, Filter, DollarSign, PieChart, TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Pagination } from '@/components/ui/Pagination';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { LoadingSkeleton } from '@/components/LoadingComponents';

import { AssetForm } from '@/components/assets/AssetForm';
import { AssetList } from '@/components/assets/AssetList';
import { AssetDetailModal } from '@/components/assets/AssetDetailModal';
import { AssetFilters } from '@/components/assets/AssetFilters';
import { BatchUpdateIndicator } from '@/components/assets/LastUpdatedIndicator';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { PrivateAmount } from '@/components/ui/PrivateAmount';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useAuthContext } from '@/context/AuthContext';
import {
  useAssetsSearch,
  useAssets,
  useCreateAsset,
  useUpdateAsset,
  useDeleteAsset,
} from '@/hooks/useAssets';
import { useUpdateAllAssetPrices } from '@/hooks/useMarketData';
import {
  calculatePortfolioMetrics,
  getTopPerformers,
  formatPercentage,
  getGainLossColor,
} from '@/utils/portfolio';
import type { Asset, AssetRequest, AssetFilters as Filters } from '@/types/asset';

const DEFAULT_PAGE_SIZE = 20;

export default function AssetsPage() {
  const { t } = useTranslation('assets');
  useDocumentTitle(t('title'));
  const [searchParams] = useSearchParams();
  const highlightId = searchParams.get('highlight') ? parseInt(searchParams.get('highlight')!) : null;

  const { baseCurrency } = useAuthContext();

  const [filters, setFilters] = useState<Filters>({ page: 0, size: DEFAULT_PAGE_SIZE, sort: 'name,asc' });
  const [showFilters, setShowFilters] = useState(false);
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingAsset, setEditingAsset] = useState<Asset | null>(null);
  const [viewingAsset, setViewingAsset] = useState<Asset | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  // BUG-ASSETS-20: Escape key closes the filter panel
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && showFilters) {
        setShowFilters(false);
      }
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [showFilters]);

  const { data: assetsPage, isLoading, error } = useAssetsSearch(filters);
  // Fetch all assets (unfiltered) for global totals in summary cards
  const { data: allAssetsPage } = useAssetsSearch({ page: 0, size: 10000, sort: 'name,asc' });
  // Also use the flat assets list for portfolio metrics
  const { data: allAssetsList } = useAssets();
  const createAsset = useCreateAsset();
  const updateAsset = useUpdateAsset();
  const deleteAsset = useDeleteAsset();
  const updateAllPrices = useUpdateAllAssetPrices();

  const assets = assetsPage?.content || [];
  const allAssets = allAssetsPage?.content || [];

  // Assets with symbols (for price refresh)
  const assetsWithSymbols = allAssetsList?.filter(a => a.symbol) || [];

  /** Check if any meaningful filter (beyond pagination/sort) is active */
  const isFiltered = !!(
    filters.keyword ||
    filters.type ||
    filters.currency ||
    filters.symbol ||
    filters.purchaseDateFrom ||
    filters.purchaseDateTo ||
    filters.valueMin !== undefined ||
    filters.valueMax !== undefined
  );

  /** Compute summary stats for a set of assets, summing in base currency when available */
  const computeAssetSummary = (assetList: Asset[]) => {
    // For physical assets, use conditionAdjustedValue (current depreciated value) rather than
    // totalValue (purchase cost). Scale to base currency via the implicit exchange rate.
    const totalValue = assetList.reduce((sum, a) => {
      const effectiveNative = a.isPhysical
        ? (a.conditionAdjustedValue ?? a.depreciatedValue ?? a.totalValue)
        : a.totalValue;
      if (a.valueInBaseCurrency !== undefined && a.valueInBaseCurrency !== null && a.totalValue > 0) {
        const rate = a.valueInBaseCurrency / a.totalValue;
        return sum + effectiveNative * rate;
      }
      return sum + effectiveNative;
    }, 0);
    const totalCost = assetList.reduce((sum, a) => {
      if (a.valueInBaseCurrency !== undefined && a.valueInBaseCurrency !== null && a.totalValue > 0) {
        const rate = a.valueInBaseCurrency / a.totalValue;
        return sum + a.totalCost * rate;
      }
      return sum + a.totalCost;
    }, 0);
    const totalGain = totalValue - totalCost;
    const gainPct = totalCost > 0 ? (totalGain / totalCost) * 100 : 0;
    // Prefer the base currency from the server response; fall back to auth context baseCurrency
    const currency = assetList[0]?.baseCurrency ?? baseCurrency ?? assetList[0]?.currency ?? 'USD';
    return { totalValue, totalCost, totalGain, gainPct, currency };
  };

  const globalSummary = useMemo(() => computeAssetSummary(allAssets), [allAssets]);
  const filteredSummary = useMemo(() => computeAssetSummary(assets), [assets]);

  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(globalSummary.currency);

  // Portfolio metrics from the flat all-assets list
  const metrics = useMemo(
    () => (allAssetsList ? calculatePortfolioMetrics(allAssetsList) : null),
    [allAssetsList]
  );

  // Best performer — use filtered assets when filters are active, otherwise global
  const { best } = useMemo(
    () => {
      const sourceList = isFiltered ? assets : (allAssetsList ?? []);
      return sourceList.length > 0 ? getTopPerformers(sourceList) : { best: [] };
    },
    [isFiltered, assets, allAssetsList]
  );

  const handleRefreshPrices = async () => {
    if (assetsWithSymbols.length === 0) return;
    setIsRefreshing(true);
    try {
      const assetIds = assetsWithSymbols.map(a => a.id);
      await updateAllPrices.mutateAsync(assetIds);
    } catch (err) {
      console.error('Failed to refresh prices:', err);
    } finally {
      setIsRefreshing(false);
    }
  };

  const handleFiltersChange = (newFilters: Filters) => {
    setFilters({ ...newFilters, page: 0, size: filters.size || DEFAULT_PAGE_SIZE });
  };

  const handlePageChange = (page: number) => {
    setFilters({ ...filters, page });
  };

  const handlePageSizeChange = (size: number) => {
    setFilters({ ...filters, page: 0, size });
  };

  const handleCreate = () => {
    setEditingAsset(null);
    setIsFormOpen(true);
  };

  const handleEdit = (asset: Asset) => {
    setEditingAsset(asset);
    setIsFormOpen(true);
  };

  const handleView = (asset: Asset) => {
    setViewingAsset(asset);
  };

  const handleDelete = async (assetId: number) => {
    try {
      await deleteAsset.mutateAsync(assetId);
    } catch (error) {
      console.error('Failed to delete asset:', error);
    }
  };

  const handleFormSubmit = async (data: AssetRequest) => {
    try {
      if (editingAsset) {
        await updateAsset.mutateAsync({ id: editingAsset.id, data });
      } else {
        await createAsset.mutateAsync(data);
      }
      setIsFormOpen(false);
      setEditingAsset(null);
    } catch (error) {
      console.error('Failed to save asset:', error);
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingAsset(null);
  };

  if (error) {
    return (
      <div className="p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      {/* Header */}
      <div className="flex flex-col gap-4 mb-6">
        <div className="flex items-center justify-between">
          <PageHeader
            title={t('title')}
            description={t('description')}
          />
          <div className="flex items-center gap-3">
            {/* Refresh Prices button */}
            <Button
              variant="secondary"
              onClick={handleRefreshPrices}
              disabled={isRefreshing || assetsWithSymbols.length === 0}
            >
              <RefreshCw className={`h-4 w-4 mr-2 ${isRefreshing ? 'animate-spin' : ''}`} />
              {isRefreshing ? t('refreshing') : t('refreshPrices')}
            </Button>
            {/* Filter Toggle Button */}
            <Button
              variant={showFilters ? 'primary' : 'outline'}
              onClick={() => setShowFilters(!showFilters)}
            >
              <Filter className="h-4 w-4 mr-2" />
              {t('filters')}
            </Button>
            <Button variant="primary" onClick={handleCreate}>
              <Plus className="h-4 w-4 mr-2" />
              {t('addAsset')}
            </Button>
          </div>
        </div>

        {/* Stale price indicator */}
        {allAssetsList && allAssetsList.length > 0 && (
          <BatchUpdateIndicator assets={allAssetsList} size="md" />
        )}
      </div>

      {/* Filters Panel */}
      {showFilters && (
        <div className="mb-6">
          <AssetFilters filters={filters} onFiltersChange={handleFiltersChange} />
        </div>
      )}

      {/* Summary Cards — Portfolio-style */}
      {!isLoading && allAssets.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {/* Total Portfolio Value */}
          <div className="bg-surface border border-border rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">{t('summary.totalPortfolioValue')}</span>
              <DollarSign className="h-5 w-5 text-primary" />
            </div>
            <ConvertedAmount
              amount={globalSummary.totalValue}
              currency={globalSummary.currency}
              isConverted={false}
              secondaryAmount={convert(globalSummary.totalValue)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              className="text-2xl font-bold text-foreground"
            />
            <p className="text-xs text-muted-foreground mt-1">
              {t('assetCount', { count: metrics?.assetCount ?? allAssets.length })}
            </p>
            {isFiltered && (
              <p className="text-xs font-mono text-text-tertiary mt-1">
                {t('filtered')}{' '}
                <ConvertedAmount
                  amount={filteredSummary.totalValue}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalValue)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </div>

          {/* Total Cost Basis */}
          <div className="bg-surface border border-border rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">{t('summary.totalCostBasis')}</span>
              <PieChart className="h-5 w-5 text-muted-foreground" />
            </div>
            <ConvertedAmount
              amount={globalSummary.totalCost}
              currency={globalSummary.currency}
              isConverted={false}
              secondaryAmount={convert(globalSummary.totalCost)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              className="text-2xl font-bold text-foreground"
            />
            <p className="text-xs text-muted-foreground mt-1">{t('summary.initialInvestment')}</p>
            {isFiltered && (
              <p className="text-xs font-mono text-text-tertiary mt-1">
                {t('filtered')}{' '}
                <ConvertedAmount
                  amount={filteredSummary.totalCost}
                  currency={filteredSummary.currency}
                  isConverted={false}
                  secondaryAmount={convert(filteredSummary.totalCost)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />
              </p>
            )}
          </div>

          {/* Merged Unrealized Gain/Loss + Overall Return */}
          <div className="bg-surface border border-border rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">{t('summary.overallReturn')}</span>
              {globalSummary.totalGain >= 0 ? (
                <TrendingUp className="h-5 w-5 text-green-600" />
              ) : (
                <TrendingDown className="h-5 w-5 text-red-600" />
              )}
            </div>
            <ConvertedAmount
              amount={globalSummary.totalGain}
              currency={globalSummary.currency}
              isConverted={false}
              secondaryAmount={convert(globalSummary.totalGain)}
              secondaryCurrency={secCurrency}
              secondaryExchangeRate={secondaryExchangeRate}
              className={`text-2xl font-bold ${getGainLossColor(globalSummary.totalGain)}`}
            />
            <p className={`text-xs mt-1 ${getGainLossColor(globalSummary.gainPct)}`}>
              {formatPercentage(globalSummary.gainPct)}
            </p>
            {isFiltered && (
              <p className="text-xs font-mono text-text-tertiary mt-1">
                {t('filtered')} {filteredSummary.gainPct >= 0 ? '+' : ''}
                {filteredSummary.gainPct.toFixed(2)}%
              </p>
            )}
          </div>

          {/* Best Performer */}
          <div className="bg-surface border border-border rounded-lg p-6">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">{t('summary.bestPerformer')}</span>
              <TrendingUp className="h-5 w-5 text-green-600" />
            </div>
            {best.length > 0 ? (
              <>
                <p className="text-lg font-bold text-foreground truncate">{best[0].asset.name}</p>
                <PrivateAmount inline className="text-sm text-green-500">
                  {formatPercentage(best[0].gainPercentage * 100)}
                </PrivateAmount>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">{t('summary.noGainsYet')}</p>
            )}
          </div>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="space-y-4">
          {[...Array(5)].map((_, i) => (
            <LoadingSkeleton key={i} className="h-16" />
          ))}
        </div>
      )}

      {/* Empty State */}
      {!isLoading && assets && assets.length === 0 && (
        <EmptyState
          title={t('empty.noResults')}
          description={isFiltered
            ? t('empty.noMatch')
            : t('empty.noAssets')}
          action={{
            label: t('addAsset'),
            onClick: handleCreate,
          }}
        />
      )}

      {/* Assets List */}
      {!isLoading && assets && assets.length > 0 && (
        <>
          <AssetList
            assets={assets}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onView={handleView}
            highlightedId={highlightId}
          />

          {/* Pagination */}
          {assetsPage && assetsPage.totalPages > 1 && (
            <Pagination
              currentPage={assetsPage.number}
              totalPages={assetsPage.totalPages}
              pageSize={assetsPage.size}
              totalElements={assetsPage.totalElements}
              onPageChange={handlePageChange}
              onPageSizeChange={handlePageSizeChange}
            />
          )}
        </>
      )}

      {/* Asset Detail Modal */}
      {viewingAsset && (
        <AssetDetailModal
          asset={viewingAsset}
          onClose={() => setViewingAsset(null)}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />
      )}

      {/* Create/Edit Dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingAsset ? t('dialogs.editTitle') : t('dialogs.createTitle')}
            </DialogTitle>
          </DialogHeader>
          <AssetForm
            asset={editingAsset || undefined}
            onSubmit={handleFormSubmit}
            onCancel={handleFormCancel}
            isLoading={createAsset.isPending || updateAsset.isPending}
          />
        </DialogContent>
      </Dialog>
    </div>
  );
}
