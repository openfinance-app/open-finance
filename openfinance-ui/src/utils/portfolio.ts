/**
 * Portfolio calculation utilities
 * Task 5.4: Portfolio metrics and calculations
 */
import type { Asset } from '@/types/asset';
import { DEFAULT_CURRENCY } from './currency';
import { add, sum, subtract, percentage } from '@/utils/money';

export interface PortfolioMetrics {
  totalValue: number;
  totalCost: number;
  unrealizedGain: number;
  gainPercentage: number;
  assetCount: number;
}

export interface AssetAllocation {
  type: string;
  value: number;
  percentage: number;
  count: number;
}

export interface TopPerformer {
  asset: Asset;
  gainPercentage: number;
}

/**
 * Calculate overall portfolio metrics
 */
export const calculatePortfolioMetrics = (assets: Asset[]): PortfolioMetrics => {
  if (!assets || assets.length === 0) {
    return {
      totalValue: 0,
      totalCost: 0,
      unrealizedGain: 0,
      gainPercentage: 0,
      assetCount: 0,
    };
  }

  const totalValue = sum(assets.map((asset) => Number(asset.totalValue)));
  const totalCost = sum(assets.map((asset) => Number(asset.totalCost)));
  const unrealizedGain = subtract(totalValue, totalCost);
  const gainPercentage = totalCost > 0 ? percentage(unrealizedGain, totalCost) : 0;

  return {
    totalValue,
    totalCost,
    unrealizedGain,
    gainPercentage,
    assetCount: assets.length,
  };
};

/**
 * Calculate asset allocation by type
 */
export const calculateAssetAllocation = (assets: Asset[]): AssetAllocation[] => {
  if (!assets || assets.length === 0) {
    return [];
  }

  const totalValue = sum(assets.map((asset) => Number(asset.totalValue)));

  // Group by asset type
  const allocationMap = assets.reduce((acc, asset) => {
    const type = asset.type;
    if (!acc[type]) {
      acc[type] = { value: 0, count: 0 };
    }
    acc[type].value = add(acc[type].value, Number(asset.totalValue));
    acc[type].count += 1;
    return acc;
  }, {} as Record<string, { value: number; count: number }>);

  // Convert to array and calculate percentages
  return Object.entries(allocationMap)
    .map(([type, data]) => ({
      type,
      value: data.value,
      percentage: totalValue > 0 ? percentage(data.value, totalValue) : 0,
      count: data.count,
    }))
    .sort((a, b) => b.value - a.value); // Sort by value descending
};

/**
 * Get best and worst performing assets
 */
export const getTopPerformers = (
  assets: Asset[]
): { best: TopPerformer[]; worst: TopPerformer[] } => {
  if (!assets || assets.length === 0) {
    return { best: [], worst: [] };
  }

  // Sort by gain percentage
  const sorted = [...assets].sort((a, b) => b.gainPercentage - a.gainPercentage);

  // Get top 3 best and worst
  const best = sorted
    .slice(0, 3)
    .filter(asset => asset.gainPercentage > 0)
    .map(asset => ({
      asset,
      gainPercentage: asset.gainPercentage,
    }));

  const worst = sorted
    .slice(-3)
    .reverse()
    .filter(asset => asset.gainPercentage < 0)
    .map(asset => ({
      asset,
      gainPercentage: asset.gainPercentage,
    }));

  return { best, worst };
};

/**
 * Format currency value
 */
export const formatCurrency = (value: number, currency: string = DEFAULT_CURRENCY): string => {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
};

/**
 * Format percentage
 */
export const formatPercentage = (value: number, includeSign: boolean = true): string => {
  const sign = includeSign && value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
};

/**
 * Get color class for gain/loss
 */
export const getGainLossColor = (value: number): string => {
  if (value > 0) return 'text-green-500';
  if (value < 0) return 'text-red-500';
  return 'text-text-muted';
};

/**
 * Get asset type display name
 */
export const getAssetTypeLabel = (type: string): string => {
  const labels: Record<string, string> = {
    STOCK: 'Stock',
    ETF: 'ETF',
    CRYPTO: 'Cryptocurrency',
    BOND: 'Bond',
    MUTUAL_FUND: 'Mutual Fund',
    REAL_ESTATE: 'Real Estate',
    COMMODITY: 'Commodity',
    OTHER: 'Other',
  };
  return labels[type] || type;
};

/**
 * Get color for asset type (for charts)
 */
export const getAssetTypeColor = (type: string): string => {
  const colors: Record<string, string> = {
    STOCK: '#3b82f6', // blue
    ETF: '#8b5cf6', // purple
    CRYPTO: '#f59e0b', // amber
    BOND: '#10b981', // green
    MUTUAL_FUND: '#ec4899', // pink
    REAL_ESTATE: '#06b6d4', // cyan
    COMMODITY: '#f97316', // orange
    OTHER: '#6b7280', // gray
  };
  return colors[type] || '#6b7280';
};
