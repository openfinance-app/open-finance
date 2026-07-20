import { describe, it, expect } from 'vitest';
import {
  calculatePortfolioMetrics,
  calculateAssetAllocation,
  getTopPerformers,
  formatCurrency,
  formatPercentage,
  getGainLossColor,
  getAssetTypeLabel,
} from './portfolio';
import type { Asset } from '@/types/asset';

const makeAsset = (overrides: Partial<Asset> = {}): Asset => ({
  id: 1,
  userId: 1,
  name: 'Test Stock',
  assetType: 'STOCK',
  type: 'STOCK',
  quantity: 10,
  purchasePrice: 100,
  currentPrice: 150,
  totalValue: 1500,
  totalCost: 1000,
  currentValue: 1500,
  unrealizedGain: 500,
  gainPercentage: 0.5,
  currency: 'USD',
  createdAt: '2024-01-01',
  ...overrides,
} as Asset);

describe('calculatePortfolioMetrics', () => {
  it('returns zeros for empty array', () => {
    const result = calculatePortfolioMetrics([]);
    expect(result).toEqual({
      totalValue: 0,
      totalCost: 0,
      unrealizedGain: 0,
      gainPercentage: 0,
      assetCount: 0,
    });
  });

  it('returns zeros for null/undefined', () => {
    const result = calculatePortfolioMetrics(null as unknown as Asset[]);
    expect(result.assetCount).toBe(0);
  });

  it('calculates metrics for single asset', () => {
    const result = calculatePortfolioMetrics([makeAsset()]);
    expect(result.totalValue).toBe(1500);
    expect(result.totalCost).toBe(1000);
    expect(result.unrealizedGain).toBe(500);
    expect(result.gainPercentage).toBe(50);
    expect(result.assetCount).toBe(1);
  });

  it('calculates metrics for multiple assets', () => {
    const assets = [
      makeAsset({ id: 1, totalValue: 1000, totalCost: 800 }),
      makeAsset({ id: 2, totalValue: 2000, totalCost: 1500 }),
    ];
    const result = calculatePortfolioMetrics(assets);
    expect(result.totalValue).toBe(3000);
    expect(result.totalCost).toBe(2300);
    expect(result.assetCount).toBe(2);
  });
});

describe('calculateAssetAllocation', () => {
  it('returns empty array for empty input', () => {
    expect(calculateAssetAllocation([])).toEqual([]);
  });

  it('returns empty array for null', () => {
    expect(calculateAssetAllocation(null as unknown as Asset[])).toEqual([]);
  });

  it('groups assets by type', () => {
    const assets = [
      makeAsset({ id: 1, type: 'STOCK', totalValue: 1000 }),
      makeAsset({ id: 2, type: 'STOCK', totalValue: 2000 }),
      makeAsset({ id: 3, type: 'ETF', totalValue: 1000 }),
    ];
    const result = calculateAssetAllocation(assets);
    expect(result.length).toBe(2);
    const stock = result.find(a => a.type === 'STOCK');
    expect(stock?.count).toBe(2);
    expect(stock?.value).toBe(3000);
  });

  it('calculates percentages', () => {
    const assets = [
      makeAsset({ id: 1, type: 'STOCK', totalValue: 7500 }),
      makeAsset({ id: 2, type: 'ETF', totalValue: 2500 }),
    ];
    const result = calculateAssetAllocation(assets);
    const stock = result.find(a => a.type === 'STOCK');
    expect(stock?.percentage).toBe(75);
  });
});

describe('getTopPerformers', () => {
  it('returns empty for empty input', () => {
    const result = getTopPerformers([]);
    expect(result.best).toEqual([]);
    expect(result.worst).toEqual([]);
  });

  it('returns empty for null', () => {
    const result = getTopPerformers(null as unknown as Asset[]);
    expect(result.best).toEqual([]);
  });

  it('identifies best performers', () => {
    const assets = [
      makeAsset({ id: 1, gainPercentage: 0.5 }),
      makeAsset({ id: 2, gainPercentage: 0.3 }),
      makeAsset({ id: 3, gainPercentage: -0.1 }),
    ];
    const result = getTopPerformers(assets);
    expect(result.best.length).toBe(2);
    expect(result.best[0].asset.id).toBe(1);
  });

  it('identifies worst performers', () => {
    const assets = [
      makeAsset({ id: 1, gainPercentage: 0.5 }),
      makeAsset({ id: 2, gainPercentage: -0.2 }),
      makeAsset({ id: 3, gainPercentage: -0.4 }),
    ];
    const result = getTopPerformers(assets);
    expect(result.worst.length).toBe(2);
  });
});

describe('formatCurrency', () => {
  it('formats USD amount', () => {
    const result = formatCurrency(1234.56, 'USD');
    expect(result).toBe('$1,234.56');
  });

  it('defaults to the app default currency (EUR)', () => {
    const result = formatCurrency(100);
    expect(result).toContain('€');
  });
});

describe('formatPercentage', () => {
  it('includes positive sign by default', () => {
    expect(formatPercentage(5.5)).toBe('+5.50%');
  });

  it('omits sign when disabled', () => {
    expect(formatPercentage(5.5, false)).toBe('5.50%');
  });

  it('shows negative sign', () => {
    expect(formatPercentage(-3.2)).toBe('-3.20%');
  });
});

describe('getGainLossColor', () => {
  it('returns green for positive', () => {
    expect(getGainLossColor(1)).toBe('text-green-500');
  });

  it('returns red for negative', () => {
    expect(getGainLossColor(-1)).toBe('text-red-500');
  });

  it('returns muted for zero', () => {
    expect(getGainLossColor(0)).toBe('text-text-muted');
  });
});

describe('getAssetTypeLabel', () => {
  it('returns display name for known types', () => {
    expect(getAssetTypeLabel('STOCK')).toBe('Stock');
    expect(getAssetTypeLabel('ETF')).toBe('ETF');
    expect(getAssetTypeLabel('CRYPTO')).toBe('Cryptocurrency');
  });

  it('returns type string for unknown types', () => {
    expect(getAssetTypeLabel('UNKNOWN')).toBe('UNKNOWN');
  });
});
