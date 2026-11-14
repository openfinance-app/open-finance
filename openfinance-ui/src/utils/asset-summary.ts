import type { Asset } from '@/types/asset';
import { percentage } from '@/utils/money';
import { decimalValue, ExactDecimal, exactSum } from '@/utils/exact-decimal';

/** Never add an unconverted native amount to a reporting-currency total. */
export function computeAssetSummary(assets: Asset[], currency: string) {
  const values: string[] = [];
  const costs: string[] = [];
  const gains: string[] = [];
  const bases: string[] = [];
  let excludedFromPerformance = 0;
  const missingCurrencies = new Set<string>();
  for (const asset of assets) {
    if (asset.acquisitionType === 'PLANNED' || asset.isActive === false) continue;
    const rate =
      asset.currency === currency
        ? 1
        : asset.isConverted && asset.baseCurrency === currency
          ? decimalValue(asset, 'exchangeRate')
          : undefined;
    if (rate == null || !new ExactDecimal(rate).isFinite() || new ExactDecimal(rate).lte(0)) {
      missingCurrencies.add(asset.currency);
      continue;
    }
    const nativeValue = asset.isPhysical
      ? (decimalValue(asset, 'totalValue') ??
        decimalValue(asset, 'conditionAdjustedValue') ??
        decimalValue(asset, 'depreciatedValue') ??
        0)
      : (decimalValue(asset, 'totalValue') ?? 0);
    values.push(new ExactDecimal(nativeValue).times(rate).toFixed());
    costs.push(new ExactDecimal(decimalValue(asset, 'totalCost') ?? 0).times(rate).toFixed());
    const basis =
      asset.performanceBasis !== undefined
        ? decimalValue(asset, 'performanceBasis')
        : asset.acquisitionType === 'GIFT'
          ? null
          : decimalValue(asset, 'totalCost');
    if (basis != null) {
      gains.push(new ExactDecimal(nativeValue).minus(basis).times(rate).toFixed());
      bases.push(new ExactDecimal(basis).times(rate).toFixed());
    } else {
      excludedFromPerformance++;
    }
  }
  const totalValue = exactSum(values);
  const totalCost = exactSum(costs);
  const totalGain = exactSum(gains);
  const performanceBasis = exactSum(bases);
  return {
    totalValue: Number(totalValue),
    totalCost: Number(totalCost),
    totalGain: Number(totalGain),
    exactValues: { totalValue, totalCost, totalGain },
    gainPct: new ExactDecimal(performanceBasis).gt(0)
      ? percentage(totalGain, performanceBasis)
      : null,
    hasPerformance: gains.length > 0,
    excludedFromPerformance,
    currency,
    missingCurrencies: [...missingCurrencies].sort(),
  };
}
