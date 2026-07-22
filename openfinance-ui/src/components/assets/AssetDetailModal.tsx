/**
 * AssetDetailModal Component
 * Task 5.4.4: Create AssetDetailModal component
 * 
 * Modal displaying detailed asset information with historical price chart
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { X, TrendingUp, TrendingDown, Edit, Trash2, Calendar, DollarSign, Hash } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import type { Asset } from '@/types/asset';
import { useHistoricalPrices } from '@/hooks/useMarketData';
import { formatPercentage, getGainLossColor } from '@/utils/portfolio';
import { multiply } from '@/utils/money';
import { getCurrencySymbol } from '@/utils/currency';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { LastUpdatedIndicator } from './LastUpdatedIndicator';
import { Button } from '@/components/ui/Button';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import { cn } from '@/lib/utils';
import { AttachmentList, AttachmentUpload } from '@/components/attachments';
import { AttachmentEntityType } from '@/types/attachment';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { getAssetTypeName } from '@/hooks/useAssets';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';

interface AssetDetailModalProps {
  asset: Asset;
  onClose: () => void;
  onEdit?: (asset: Asset) => void;
  onDelete?: (assetId: number) => void;
}

export function AssetDetailModal({ asset, onClose, onEdit, onDelete }: AssetDetailModalProps) {
  const { format: formatCurrency } = useFormatCurrency();
  const { convert: convertChart, secondaryCurrency: secCurrencyChart, secondaryExchangeRate: secExRateChart } = useSecondaryConversion(
    asset.isConverted && asset.baseCurrency ? asset.baseCurrency : asset.currency
  );
  const { t: tc } = useTranslation('common');
  const { t, i18n } = useTranslation('assets');
  const [timeRange, setTimeRange] = useState<'1M' | '3M' | '6M' | '1Y'>('3M');
  const [activeTab, setActiveTab] = useState<'overview' | 'attachments'>('overview');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Calculate date range based on selection
  const getDateRange = () => {
    const endDate = new Date();
    const startDate = new Date();

    switch (timeRange) {
      case '1M':
        startDate.setMonth(startDate.getMonth() - 1);
        break;
      case '3M':
        startDate.setMonth(startDate.getMonth() - 3);
        break;
      case '6M':
        startDate.setMonth(startDate.getMonth() - 6);
        break;
      case '1Y':
        startDate.setFullYear(startDate.getFullYear() - 1);
        break;
    }

    return {
      startDate: startDate.toISOString().split('T')[0],
      endDate: endDate.toISOString().split('T')[0],
    };
  };

  const { startDate, endDate } = getDateRange();
  const { data: historicalData, isLoading: isLoadingHistory } = useHistoricalPrices(
    asset.symbol || null,
    startDate,
    endDate
  );

  // Close modal on Escape key
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [onClose]);

  const handleEdit = () => {
    onEdit?.(asset);
    onClose();
  };

  const handleDelete = () => {
    setShowDeleteConfirm(true);
  };

  const handleConfirmDelete = () => {
    onDelete?.(asset.id);
    setShowDeleteConfirm(false);
    onClose();
  };

  // Determine if we should display chart in base currency
  const isConverted = asset.isConverted && !!asset.baseCurrency && !!asset.exchangeRate;
  const chartCurrency = isConverted && asset.baseCurrency ? asset.baseCurrency : asset.currency;

  // Transform historical data for chart
  const chartData = historicalData?.map(item => ({
    date: new Date(item.date).toLocaleDateString(i18n.language, { month: 'short', day: 'numeric' }),
    price: isConverted && asset.exchangeRate ? multiply(item.close, asset.exchangeRate) : item.close,
  })) || [];

  const hasHistoricalData = asset.symbol && historicalData && historicalData.length > 0;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-background/80 backdrop-blur-sm"
        onClick={onClose}
      />

      {/* Modal */}
      <div className="relative bg-surface border border-border rounded-lg shadow-lg max-w-4xl w-full max-h-[90vh] overflow-y-auto m-4">
        {/* Header */}
        <div className="sticky top-0 z-10 bg-surface px-6 py-4 flex items-center justify-between">
          <div>
            <h2 className="text-xl font-bold text-foreground">{asset.name}</h2>
            {asset.symbol && (
              <p className="text-sm text-muted-foreground">{asset.symbol}</p>
            )}
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-background transition-colors"
            aria-label={tc('aria.closeModal')}
          >
            <X className="h-5 w-5 text-muted-foreground" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6">
          <div className="w-full">
            {/* Tabs */}
            <div className="border-b border-border mb-6">
              <div className="flex gap-4">
                <button
                  onClick={() => setActiveTab('overview')}
                  className={cn(
                    'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                    activeTab === 'overview'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-muted-foreground hover:text-foreground'
                  )}
                >
                  {t('detail.tabs.overview')}
                </button>
                <button
                  onClick={() => setActiveTab('attachments')}
                  className={cn(
                    'px-4 py-2 text-sm font-medium border-b-2 transition-colors',
                    activeTab === 'attachments'
                      ? 'border-primary text-primary'
                      : 'border-transparent text-muted-foreground hover:text-foreground'
                  )}
                >
                  {t('detail.tabs.attachments')}
                </button>
              </div>
            </div>

            {/* Tab Content */}
            <div className="py-4 space-y-6">
              {activeTab === 'overview' && (
                <>
                  {/* Key Metrics */}
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="bg-background border border-border rounded-lg p-4">
                      <div className="flex items-center gap-2 mb-2">
                        <DollarSign className="h-4 w-4 text-muted-foreground" />
                        <span className="text-sm text-muted-foreground">{t('detail.metrics.currentValue')}</span>
                      </div>
                      <p className="text-2xl font-bold text-foreground">
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
                        />
                      </p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {asset.quantity} × <ConvertedAmount
                          amount={isConverted && asset.exchangeRate ? multiply(asset.currentPrice, asset.exchangeRate) : asset.currentPrice}
                          currency={chartCurrency}
                          isConverted={false}
                          secondaryAmount={convertChart(isConverted && asset.exchangeRate ? multiply(asset.currentPrice, asset.exchangeRate) : asset.currentPrice)}
                          secondaryCurrency={secCurrencyChart}
                          secondaryExchangeRate={secExRateChart}
                          inline
                        />
                      </p>
                    </div>

                    <div className="bg-background border border-border rounded-lg p-4">
                      <div className="flex items-center gap-2 mb-2">
                        <DollarSign className="h-4 w-4 text-muted-foreground" />
                        <span className="text-sm text-muted-foreground">{t('detail.metrics.costBasis')}</span>
                      </div>
                      <p className="text-2xl font-bold text-foreground">
                        {/* Cost basis converted to base currency using same rate as totalValue */}
                        <ConvertedAmount
                          amount={asset.totalCost}
                          currency={asset.currency}
                          convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.totalCost, asset.exchangeRate) : undefined}
                          baseCurrency={asset.baseCurrency}
                          exchangeRate={asset.exchangeRate}
                          isConverted={asset.isConverted}
                        />
                      </p>
                      <p className="text-xs text-muted-foreground mt-1">
                        {asset.quantity} × <ConvertedAmount
                          amount={isConverted && asset.exchangeRate ? multiply(asset.purchasePrice, asset.exchangeRate) : asset.purchasePrice}
                          currency={chartCurrency}
                          isConverted={false}
                          secondaryAmount={convertChart(isConverted && asset.exchangeRate ? multiply(asset.purchasePrice, asset.exchangeRate) : asset.purchasePrice)}
                          secondaryCurrency={secCurrencyChart}
                          secondaryExchangeRate={secExRateChart}
                          inline
                        />
                      </p>
                    </div>

                    <div className="bg-background border border-border rounded-lg p-4">
                      <div className="flex items-center gap-2 mb-2">
                        {asset.unrealizedGain >= 0 ? (
                          <TrendingUp className="h-4 w-4 text-green-600" />
                        ) : (
                          <TrendingDown className="h-4 w-4 text-red-600" />
                        )}
                        <span className="text-sm text-muted-foreground">{t('detail.metrics.gainLoss')}</span>
                      </div>
                      <p className={`text-2xl font-bold ${getGainLossColor(asset.unrealizedGain)}`}>
                        {/* Gain/Loss converted to base currency using same rate */}
                        <ConvertedAmount
                          amount={asset.unrealizedGain}
                          currency={asset.currency}
                          convertedAmount={asset.isConverted && asset.exchangeRate ? multiply(asset.unrealizedGain, asset.exchangeRate) : undefined}
                          baseCurrency={asset.baseCurrency}
                          exchangeRate={asset.exchangeRate}
                          isConverted={asset.isConverted}
                        />
                      </p>
                      <p className={`text-xs mt-1 ${getGainLossColor(asset.unrealizedGain)}`}>
                        {formatPercentage(asset.gainPercentage * 100)}
                      </p>
                    </div>
                  </div>

                  {/* Historical Price Chart */}
                  {asset.symbol && (
                    <div className="bg-background border border-border rounded-lg p-6">
                      <div className="flex items-center justify-between mb-4">
                        <h3 className="text-lg font-semibold text-foreground">{t('detail.sections.priceHistory')}</h3>
                        <div className="flex gap-2">
                          {(['1M', '3M', '6M', '1Y'] as const).map((range) => (
                            <button
                              key={range}
                              onClick={() => setTimeRange(range)}
                              className={`px-3 py-1 rounded text-sm transition-colors ${timeRange === range
                                ? 'bg-primary text-background font-medium'
                                : 'bg-surface text-muted-foreground hover:text-foreground hover:bg-surface-elevated'
                                }`}
                            >
                              {range}
                            </button>
                          ))}
                        </div>
                      </div>

                      {isLoadingHistory && (
                        <LoadingSkeleton className="h-64" />
                      )}

                      {!isLoadingHistory && hasHistoricalData && (
                        <ResponsiveContainer width="100%" height={300} minWidth={0}>
                          <LineChart data={chartData}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#333" />
                            <XAxis
                              dataKey="date"
                              stroke="#888"
                              tick={{ fill: '#888', fontSize: 12 }}
                            />
                            <YAxis
                              stroke="#888"
                              tick={{ fill: '#888', fontSize: 12 }}
                              tickFormatter={(value) => `${getCurrencySymbol(chartCurrency)}${value.toFixed(0)}`}
                            />
                            <Tooltip
                              contentStyle={{
                                backgroundColor: '#1a1a1a',
                                border: '1px solid #333',
                                borderRadius: '8px',
                                color: '#fff',
                              }}
                              formatter={(value: number | undefined) => [value != null ? formatCurrency(value, chartCurrency) : 'N/A', 'Price']}
                            />
                            <Line
                              type="monotone"
                              dataKey="price"
                              stroke="#f5a623"
                              strokeWidth={2}
                              dot={false}
                              activeDot={{ r: 6 }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      )}

                      {!isLoadingHistory && !hasHistoricalData && (
                        <div className="flex items-center justify-center h-64 text-muted-foreground">
                          <p className="text-sm">
                            {asset.symbol
                              ? t('detail.noHistoricalData')
                              : t('detail.addSymbolForHistory')}
                          </p>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Asset Details */}
                  <div className="bg-background border border-border rounded-lg p-6">
                    <h3 className="text-lg font-semibold text-foreground mb-4">{t('detail.sections.assetDetails')}</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="flex items-start gap-3">
                        <Hash className="h-5 w-5 text-muted-foreground mt-0.5" />
                        <div>
                          <p className="text-sm text-muted-foreground">{t('detail.fields.type')}</p>
                          <p className="text-sm font-medium text-foreground">{getAssetTypeName(asset.type)}</p>
                        </div>
                      </div>

                      <div className="flex items-start gap-3">
                        <Calendar className="h-5 w-5 text-muted-foreground mt-0.5" />
                        <div>
                          <p className="text-sm text-muted-foreground">{t('detail.fields.purchaseDate')}</p>
                          <p className="text-sm font-medium text-foreground">
                            {new Date(asset.purchaseDate).toLocaleDateString(i18n.language, {
                              month: 'long',
                              day: 'numeric',
                              year: 'numeric',
                            })}
                          </p>
                          <p className="text-xs text-muted-foreground">{t('detail.fields.daysHeld', { count: asset.holdingDays })}</p>
                        </div>
                      </div>

                      {asset.accountName && (
                        <div className="flex items-start gap-3">
                          <DollarSign className="h-5 w-5 text-muted-foreground mt-0.5" />
                          <div>
                            <p className="text-sm text-muted-foreground">{t('detail.fields.account')}</p>
                            <p className="text-sm font-medium text-foreground">{asset.accountName}</p>
                          </div>
                        </div>
                      )}

                      <div className="flex items-start gap-3">
                        <Calendar className="h-5 w-5 text-muted-foreground mt-0.5" />
                        <div>
                          <p className="text-sm text-muted-foreground">{t('detail.fields.lastUpdated')}</p>
                          <LastUpdatedIndicator lastUpdated={asset.lastUpdated} size="sm" />
                        </div>
                      </div>
                    </div>

                    {asset.notes && (
                      <div className="mt-4 pt-4 border-t border-border">
                        <p className="text-sm text-muted-foreground mb-2">{t('detail.fields.notes')}</p>
                        <p className="text-sm text-foreground">{asset.notes}</p>
                      </div>
                    )}
                  </div>
                </>
              )}

              {/* Attachments Tab */}
              {activeTab === 'attachments' && (
                <div className="space-y-4">
                  <AttachmentList
                    entityType={AttachmentEntityType.ASSET}
                    entityId={asset.id}
                  />
                  <AttachmentUpload
                    entityType={AttachmentEntityType.ASSET}
                    entityId={asset.id}
                  />
                </div>
              )}
            </div>

            {/* Actions */}
            <div className="flex justify-end gap-3 mt-6">
              <Button variant="secondary" onClick={onClose}>
                {t('detail.actions.close')}
              </Button>
              {onDelete && (
                <Button variant="danger" onClick={handleDelete}>
                  <Trash2 className="h-4 w-4 mr-2" />
                  {t('detail.actions.delete')}
                </Button>
              )}
              {onEdit && (
                <Button variant="primary" onClick={handleEdit}>
                  <Edit className="h-4 w-4 mr-2" />
                  {t('detail.actions.edit')}
                </Button>
              )}
            </div>

            <ConfirmationDialog
              open={showDeleteConfirm}
              onOpenChange={setShowDeleteConfirm}
              onConfirm={handleConfirmDelete}
              title={t('detail.deleteConfirm.title')}
              description={t('detail.deleteConfirm.message', { name: asset.name })}
              confirmText={t('detail.deleteConfirm.confirm')}
              variant="danger"
            />
          </div>
        </div>
      </div>
    </div>
  );
}
