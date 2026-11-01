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
  it('separates gifted wealth from returns and excludes unknown receipt values from performance', () => {
    const result = computeAssetSummary(
      [
        holding({
          acquisitionType: 'GIFT',
          receivedValue: 150000,
          performanceBasis: 150000,
          totalCost: 0,
          totalValue: 165000,
        }),
        holding({
          acquisitionType: 'GIFT',
          performanceBasis: null,
          totalCost: 0,
          totalValue: 5000,
        }),
        holding({ acquisitionType: 'PLANNED', totalCost: 250000, totalValue: 0 }),
      ],
      'USD'
    );
    expect(result).toMatchObject({
      totalValue: 170000,
      totalCost: 0,
      totalGain: 15000,
      gainPct: 10,
      excludedFromPerformance: 1,
    });
  });
  it('does not report a zero percent return when the only receipt value is unknown', () => {
    expect(
      computeAssetSummary([holding({ acquisitionType: 'GIFT', performanceBasis: null })], 'USD')
    ).toMatchObject({ hasPerformance: false, gainPct: null });
  });
});
