import { describe, it, expect } from 'vitest';
import { computeAssetSummary } from '@/utils/asset-summary';
import type { Asset } from '@/types/asset';
const holding = (data: Partial<Asset>): Asset =>
  ({ currency: 'USD', totalValue: 100, totalCost: 80, ...data }) as Asset;
describe('Reporting currency asset aggregates', () => {
  it('excludes unavailable conversions rather than adding their native fallback at 1:1', () => {
    const summary = computeAssetSummary(
      [
        holding({}),
        holding({
          currency: 'GBP',
          baseCurrency: 'USD',
          totalValue: 100,
          totalCost: 100,
          valueInBaseCurrency: 150,
          exchangeRate: 1.5,
          isConverted: true,
        }),
        holding({
          currency: 'XTS',
          baseCurrency: 'USD',
          valueInBaseCurrency: 100,
          isConverted: false,
        }),
      ],
      'USD'
    );
    expect(summary).toMatchObject({
      totalValue: 250,
      totalCost: 230,
      totalGain: 20,
      missingCurrencies: ['XTS'],
    });
  });
  it('converts the cost of a holding even when its current value is zero', () => {
    expect(
      computeAssetSummary(
        [
          holding({
            currency: 'GBP',
            baseCurrency: 'USD',
            totalValue: 0,
            totalCost: 100,
            exchangeRate: 1.5,
            isConverted: true,
          }),
        ],
        'USD'
      )
    ).toMatchObject({ totalValue: 0, totalCost: 150, totalGain: -150, gainPct: -100 });
  });
  it('rejects conversions in a stale reporting currency and excludes planned holdings', () => {
    expect(
      computeAssetSummary(
        [
          holding({ currency: 'GBP', baseCurrency: 'EUR', exchangeRate: 1.2, isConverted: true }),
          holding({ acquisitionType: 'PLANNED' }),
        ],
        'USD'
      )
    ).toMatchObject({ totalValue: 0, totalCost: 0, missingCurrencies: ['GBP'] });
  });
});
