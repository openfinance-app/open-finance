import type { Asset } from '@/types/asset';
import { multiply, sum, subtract, percentage } from '@/utils/money';

/** Never add an unconverted native amount to a reporting-currency total. */
export function computeAssetSummary(assets: Asset[], currency: string) {
  const values: number[] = [];
  const costs: number[] = [];
  const gains: number[] = [];
  const bases: number[] = [];
  let excludedFromPerformance = 0;
  const missingCurrencies = new Set<string>();
  for (const asset of assets) {
    if (asset.acquisitionType === 'PLANNED' || asset.isActive === false) continue;
    const rate =
      asset.currency === currency
        ? 1
        : asset.isConverted && asset.baseCurrency === currency
          ? asset.exchangeRate
          : undefined;
    if (rate == null || !Number.isFinite(rate) || rate <= 0) {
      missingCurrencies.add(asset.currency);
      continue;
    }
    const nativeValue = asset.isPhysical
      ? (asset.totalValue ?? asset.conditionAdjustedValue ?? asset.depreciatedValue ?? 0)
      : asset.totalValue;
    values.push(multiply(nativeValue, rate));
    costs.push(multiply(asset.totalCost, rate));
    const basis =
      asset.performanceBasis !== undefined
        ? asset.performanceBasis
        : asset.acquisitionType === 'GIFT'
          ? null
          : asset.totalCost;
    if (basis != null) {
      gains.push(multiply(subtract(nativeValue, basis), rate));
      bases.push(multiply(basis, rate));
    } else {
      excludedFromPerformance++;
    }
  }
  const totalValue = sum(values);
  const totalCost = sum(costs);
  const totalGain = sum(gains);
  const performanceBasis = sum(bases);
  return {
    totalValue,
    totalCost,
    totalGain,
    gainPct: performanceBasis > 0 ? percentage(totalGain, performanceBasis) : null,
    hasPerformance: gains.length > 0,
    excludedFromPerformance,
    currency,
    missingCurrencies: [...missingCurrencies].sort(),
  };
}
