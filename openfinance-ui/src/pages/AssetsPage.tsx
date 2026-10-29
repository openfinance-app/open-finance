import { formatDecimal } from '@/utils/format';
/**
 * AssetsPage Component
 * Task 5.2.7: Create AssetsPage component
 *
 * Main page for managing user investment assets with filters and pagination
 */
import React, { useState, useMemo, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { isAxiosError } from 'axios';
import {
  Boxes,
  Building2,
  ChevronLeft,
  Plus,
  Filter,
  DollarSign,
  PieChart,
  TrendingUp,
  TrendingDown,
  RefreshCw,
} from 'lucide-react';
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
import { computeAssetSummary } from '@/utils/asset-summary';
import type { Asset, AssetRequest, AssetType, AssetFilters as Filters } from '@/types/asset';
import { DEFAULT_PAGE_SIZE, FETCH_ALL_PAGE_SIZE } from '@/constants/pagination';

const ASSET_TYPE_VALUES = [
  'STOCK',
  'ETF',
  'CRYPTO',
  'BOND',
  'MUTUAL_FUND',
  'REAL_ESTATE',
  'COMMODITY',
  'VEHICLE',
  'JEWELRY',
  'COLLECTIBLE',
  'ELECTRONICS',
  'FURNITURE',
  'OTHER',
] as const;

export default function AssetsPage() {
  const { t } = useTranslation('assets');
  const navigate = useNavigate();
  useDocumentTitle(t('title'));
  const [searchParams, setSearchParams] = useSearchParams();
  const highlightId = searchParams.get('highlight')
    ? parseInt(searchParams.get('highlight')!)
    : null;

  // Deep-link support: seed currency/type/symbol filters from URL params (validated)
  const currencyParam = searchParams.get('currency');
  const typeParam = searchParams.get('type');
  const groupParam = searchParams.get('group');
  const symbolParam = searchParams.get('symbol');
  const validType =
    typeParam && (ASSET_TYPE_VALUES as readonly string[]).includes(typeParam)
      ? (typeParam as Filters['type'])
      : undefined;
  const validCurrency =
    currencyParam && /^[A-Za-z]{3,4}$/.test(currencyParam)
      ? currencyParam.toUpperCase()
      : undefined;
  const validSymbol =
    symbolParam && /^[A-Za-z0-9.-]{1,20}$/.test(symbolParam)
      ? symbolParam.toUpperCase()
      : undefined;

  const { baseCurrency } = useAuthContext();

  const [filters, setFilters] = useState<Filters>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name,asc',
    currency: validCurrency,
    type: validType,
    group: groupParam === 'PHYSICAL' || groupParam === 'FINANCIAL' ? groupParam : undefined,
    symbol: validSymbol,
  });
  useEffect(() => {
    const group = groupParam === 'PHYSICAL' || groupParam === 'FINANCIAL' ? groupParam : undefined;
    setFilters(prev =>
      prev.group === group ? prev : { ...prev, group, type: undefined, page: 0 }
    );
  }, [groupParam]);
  const [showFilters, setShowFilters] = useState(
    Boolean(validCurrency || validType || validSymbol)
  );
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [creationType, setCreationType] = useState<AssetType | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
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

  // Consume deep-link params after mount so a refresh doesn't re-impose filters
  useEffect(() => {
    if (
      searchParams.get('currency') !== null ||
      searchParams.get('type') !== null ||
      searchParams.get('symbol') !== null
    ) {
      const next = new URLSearchParams(searchParams);
      next.delete('currency');
      next.delete('type');
      next.delete('symbol');
      setSearchParams(next, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- consume once on mount
  }, []);

  const { data: assetsPage, isLoading, error } = useAssetsSearch(filters);
  // Fetch all assets (unfiltered) for global totals in summary cards
  const { data: allAssetsPage } = useAssetsSearch({
    page: 0,
    size: FETCH_ALL_PAGE_SIZE,
    group: filters.group,
    sort: 'name,asc',
  });
  // Also use the flat assets list for portfolio metrics
  const { data: allAssetsList } = useAssets();
  const createAsset = useCreateAsset();
  const updateAsset = useUpdateAsset();
  const deleteAsset = useDeleteAsset();
  const updateAllPrices = useUpdateAllAssetPrices();

  const assets = assetsPage?.content || [];
  const allAssets = allAssetsPage?.content || [];

  // Assets with symbols (for price refresh)
  const assetsWithSymbols =
    allAssetsList?.filter(a => a.symbol && a.acquisitionType !== 'PLANNED') || [];

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

  const globalSummary = useMemo(
    () => computeAssetSummary(allAssets, baseCurrency),
    [allAssets, baseCurrency]
  );
  const filteredSummary = useMemo(
    () => computeAssetSummary(assets, baseCurrency),
    [assets, baseCurrency]
  );

  const {
    convert,
    secondaryCurrency: secCurrency,
    secondaryExchangeRate,
  } = useSecondaryConversion(globalSummary.currency);

  // Portfolio metrics from the flat all-assets list
  const metrics = useMemo(
    () => (allAssets.length ? calculatePortfolioMetrics(allAssets) : null),
    [allAssets]
  );

  // Best performer — use filtered assets when filters are active, otherwise global
  const { best } = useMemo(() => {
    const sourceList = isFiltered ? assets : allAssets;
    return sourceList.length > 0 ? getTopPerformers(sourceList) : { best: [] };
  }, [isFiltered, assets, allAssets]);

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
    setCreationType(
      filters.group === 'PHYSICAL' ? 'VEHICLE' : filters.group === 'FINANCIAL' ? 'STOCK' : null
    );
    setSaveError(null);
    setIsFormOpen(true);
  };

  const handleEdit = (asset: Asset) => {
    if (asset.type === 'REAL_ESTATE') {
      navigate(`/real-estate?asset=${asset.id}`);
      return;
    }
    setSaveError(null);
    setEditingAsset(asset);
    setIsFormOpen(true);
  };

  const handleView = (asset: Asset) => {
    if (asset.type === 'REAL_ESTATE') navigate(`/real-estate?asset=${asset.id}`);
    else setViewingAsset(asset);
  };

  useEffect(() => {
    if (!highlightId || !allAssetsList) return;
    const asset = allAssetsList.find(item => item.id === highlightId);
    if (!asset) return;
    if (asset.type === 'REAL_ESTATE') navigate(`/real-estate?asset=${asset.id}`, { replace: true });
    else {
      setViewingAsset(asset);
      const next = new URLSearchParams(searchParams);
      next.delete('highlight');
      setSearchParams(next, { replace: true });
    }
  }, [highlightId, allAssetsList, navigate, searchParams, setSearchParams]);

  const handleDelete = async (assetId: number) => {
    try {
      await deleteAsset.mutateAsync(assetId);
    } catch (error) {
      console.error('Failed to delete asset:', error);
    }
  };

  const handleFormSubmit = async (data: AssetRequest) => {
    setSaveError(null);
    try {
      if (editingAsset) {
        await updateAsset.mutateAsync({ id: editingAsset.id, data });
      } else {
        const created = await createAsset.mutateAsync(data);
        setViewingAsset(created);
      }
      setIsFormOpen(false);
      setEditingAsset(null);
    } catch (error) {
      setSaveError(
        isAxiosError<{ message?: string }>(error) && error.response?.data.message
          ? error.response.data.message
          : t('saveError')
      );
    }
  };

  const handleFormCancel = () => {
    setIsFormOpen(false);
    setEditingAsset(null);
  };

  if (error) {
    return (
      <div className="p-4 sm:p-8">
        <PageHeader title={t('title')} />
        <div className="mt-6 p-4 bg-error/10 border border-error/20 rounded-lg text-error">
          {t('loadError')}
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 sm:p-8">
      {/* Header */}
      <div className="flex flex-col gap-4 mb-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <PageHeader
            title={t(filters.group === 'PHYSICAL' ? 'groups.PHYSICAL' : 'title')}
            description={t(filters.group === 'PHYSICAL' ? 'physicalDescription' : 'description')}
          />
          <div className="flex flex-wrap items-center gap-3">
            {filters.group !== 'PHYSICAL' && (
              <>
                {/* Refresh Prices button */}
                <Button
                  variant="secondary"
                  onClick={handleRefreshPrices}
                  disabled={isRefreshing || assetsWithSymbols.length === 0}
                >
                  <RefreshCw className={`h-4 w-4 mr-2 ${isRefreshing ? 'animate-spin' : ''}`} />
                  {isRefreshing ? t('refreshing') : t('refreshPrices')}
                </Button>
              </>
            )}
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
        {filters.group !== 'PHYSICAL' && allAssetsList && allAssetsList.length > 0 && (
          <BatchUpdateIndicator assets={allAssetsList} size="md" />
        )}
      </div>

      <div className="mb-6 flex flex-wrap gap-2" role="group" aria-label={t('groups.label')}>
        {(['ALL', 'PHYSICAL', 'FINANCIAL'] as const).map(group => (
          <Button
            key={group}
            size="sm"
            variant={(filters.group ?? 'ALL') === group ? 'primary' : 'outline'}
            aria-pressed={(filters.group ?? 'ALL') === group}
            onClick={() => {
              setFilters(prev => ({
                ...prev,
                group: group === 'ALL' ? undefined : group,
                type: undefined,
                page: 0,
              }));
              const next = new URLSearchParams(searchParams);
              if (group === 'ALL') next.delete('group');
              else next.set('group', group);
              setSearchParams(next, { replace: true });
            }}
          >
            {t(`groups.${group}`)}
          </Button>
        ))}
      </div>
      {/* Filters Panel */}
      {showFilters && (
        <div className="mb-6">
          <AssetFilters filters={filters} onFiltersChange={handleFiltersChange} />
        </div>
      )}

      {globalSummary.missingCurrencies.length > 0 && (
        <p
          role="status"
          className="mb-4 rounded-lg border border-warning/30 bg-warning/10 p-3 text-sm"
        >
          {t('summary.missingRates', {
            currencies: globalSummary.missingCurrencies.join(', '),
            currency: globalSummary.currency,
          })}
        </p>
      )}
      {/* Summary Cards — Portfolio-style */}
      {!isLoading && allAssets.length > 0 && (
        <div
          className={`grid grid-cols-1 md:grid-cols-2 ${filters.group === 'PHYSICAL' ? 'lg:grid-cols-3' : 'lg:grid-cols-4'} gap-4 mb-6`}
        >
          {/* Total Portfolio Value */}
          <div
            className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-6 stagger-item"
            style={{ '--stagger-index': 0 } as React.CSSProperties}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">
                {t(
                  filters.group === 'PHYSICAL'
                    ? 'summary.physicalValue'
                    : 'summary.totalPortfolioValue'
                )}
              </span>
              <span
                className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10"
                aria-hidden="true"
              >
                <DollarSign className="h-4 w-4 text-primary" />
              </span>
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
            <p className="plate-label mt-1">
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
          <div
            className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-6 stagger-item"
            style={{ '--stagger-index': 1 } as React.CSSProperties}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">{t('summary.totalCostBasis')}</span>
              <span
                className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-elevated"
                aria-hidden="true"
              >
                <PieChart className="h-4 w-4 text-muted-foreground" />
              </span>
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
            <p className="plate-label mt-1">{t('summary.initialInvestment')}</p>
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
          <div
            className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-6 stagger-item"
            style={{ '--stagger-index': 2 } as React.CSSProperties}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm text-muted-foreground">
                {t(filters.group === 'PHYSICAL' ? 'summary.valueChange' : 'summary.overallReturn')}
              </span>
              <span
                className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface-elevated"
                aria-hidden="true"
              >
                {globalSummary.totalGain >= 0 ? (
                  <TrendingUp className="h-4 w-4 text-success" />
                ) : (
                  <TrendingDown className="h-4 w-4 text-error" />
                )}
              </span>
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
                {formatDecimal(filteredSummary.gainPct, 2)}%
              </p>
            )}
          </div>

          {filters.group !== 'PHYSICAL' && (
            <>
              {/* Best Performer */}
              <div
                className="bg-surface border border-border rounded-[var(--radius-card)] shadow-plate p-6 stagger-item"
                style={{ '--stagger-index': 3 } as React.CSSProperties}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm text-muted-foreground">
                    {t('summary.bestPerformer')}
                  </span>
                  <span
                    className="flex h-8 w-8 items-center justify-center rounded-lg bg-success/10"
                    aria-hidden="true"
                  >
                    <TrendingUp className="h-4 w-4 text-success" />
                  </span>
                </div>
                {best.length > 0 ? (
                  <>
                    <p className="text-lg font-bold text-foreground truncate">
                      {best[0].asset.name}
                    </p>
                    <PrivateAmount inline className="text-sm text-green-500">
                      {formatPercentage(best[0].gainPercentage * 100)}
                    </PrivateAmount>
                  </>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('summary.noGainsYet')}</p>
                )}
              </div>
            </>
          )}
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
          description={isFiltered ? t('empty.noMatch') : t('empty.noAssets')}
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
          key={viewingAsset.id}
          asset={allAssetsList?.find(asset => asset.id === viewingAsset.id) ?? viewingAsset}
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
          {!editingAsset && !creationType ? (
            <div className="space-y-3">
              <p className="text-sm text-text-secondary">{t('createChoice.intro')}</p>
              {(
                [
                  { key: 'physical', icon: Boxes, select: () => setCreationType('VEHICLE') },
                  { key: 'investment', icon: TrendingUp, select: () => setCreationType('STOCK') },
                  {
                    key: 'property',
                    icon: Building2,
                    select: () => navigate('/real-estate?create=1'),
                  },
                ] as const
              ).map(({ key, icon: Icon, select }) => (
                <button
                  key={key}
                  type="button"
                  onClick={select}
                  className="flex w-full items-center gap-4 rounded-xl border border-border bg-surface p-4 text-left hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <Icon className="h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
                  <span>
                    <span className="block font-semibold">{t(`createChoice.${key}`)}</span>
                    <span className="mt-1 block text-sm text-text-secondary">
                      {t(`createChoice.${key}Hint`)}
                    </span>
                  </span>
                </button>
              ))}
            </div>
          ) : (
            <>
              {!editingAsset && (
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  className="justify-self-start"
                  onClick={() => setCreationType(null)}
                >
                  <ChevronLeft className="mr-2 h-4 w-4" aria-hidden="true" />
                  {t('createChoice.back')}
                </Button>
              )}
              <AssetForm
                kind={
                  editingAsset ? undefined : creationType === 'VEHICLE' ? 'physical' : 'financial'
                }
                initialType={creationType ?? undefined}
                asset={editingAsset || undefined}
                onSubmit={handleFormSubmit}
                onCancel={handleFormCancel}
                isLoading={createAsset.isPending || updateAsset.isPending}
              />
            </>
          )}
          {saveError && (
            <p role="alert" className="text-sm text-error">
              {saveError}
            </p>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
