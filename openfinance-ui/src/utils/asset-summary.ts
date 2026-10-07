import type { Asset } from '@/types/asset';
import { multiply, sum, subtract, percentage } from '@/utils/money';

/** Never add an unconverted native amount to a reporting-currency total. */
export function computeAssetSummary(assets: Asset[], currency: string) {
  const values: number[] = [];
  const costs: number[] = [];
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
  }
  const totalValue = sum(values);
  const totalCost = sum(costs);
  const totalGain = subtract(totalValue, totalCost);
  return {
    totalValue,
    totalCost,
    totalGain,
    gainPct: totalCost > 0 ? percentage(totalGain, totalCost) : 0,
    currency,
    missingCurrencies: [...missingCurrencies].sort(),
  };
}
