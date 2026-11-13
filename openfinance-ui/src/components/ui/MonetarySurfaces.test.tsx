import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { PhysicalAssetCard } from '@/components/assets/PhysicalAssetCard';
import { OwnershipActions } from '@/components/assets/OwnershipActions';
import { BudgetCard } from '@/components/budgets/BudgetCard';
import { SearchResultAmount } from '@/components/search/SearchResultAmount';
import type { Asset } from '@/types/asset';
import type { BudgetProgressResponse } from '@/types/budget';
import type { SearchResult } from '@/types/search';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';

const state = vi.hoisted(() => ({ mode: 'base' as AmountDisplayMode }));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: state.mode, secondaryCurrency: null }),
}));
vi.mock('@/components/assets/AssetCoverImage', () => ({ AssetCoverImage: () => null }));
vi.mock('@/hooks/useCurrency', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useCurrency')>()),
  useLatestExchangeRate: () => ({ data: { rate: 0.8 } }),
  useExchangeRate: () => ({ data: { rate: 0.7 } }),
}));

describe('currency display across financial surfaces', () => {
  beforeEach(() => {
    mockAuthentication();
    state.mode = 'base';
  });

  it('shows physical asset costs and depreciation in the same currency as its value', () => {
    renderWithProviders(
      <PhysicalAssetCard
        asset={
          {
            id: 1,
            name: 'Car',
            type: 'VEHICLE',
            quantity: 1,
            currency: 'USD',
            totalValue: 75,
            totalCost: 100,
            currentPrice: 75,
            purchasePrice: 100,
            baseCurrency: 'EUR',
            valueInBaseCurrency: 60,
            exchangeRate: 0.8,
            isConverted: true,
          } as Asset
        }
      />
    );
    expect(screen.getAllByText('€80.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('€60.00').length).toBeGreaterThan(0);
    expect(screen.getByText('€20.00')).toBeVisible();
    expect(screen.queryByText('$100.00')).not.toBeInTheDocument();
  });

  it('uses the sale date for closed ownership proceeds and gains', () => {
    renderWithProviders(
      <OwnershipActions
        target={{
          id: 1,
          name: 'Sold item',
          currency: 'USD',
          quantity: '1',
          currentValue: '100',
          purchaseDate: '2024-01-01',
        }}
        closed
        soldDate="2024-06-01"
        saleProceeds={100}
        realizedGain={20}
      />
    );
    // Dated rate 0.7 differs from latest 0.8.
    expect(screen.getByText('€70.00')).toBeVisible();
    expect(screen.getByText('€14.00')).toBeVisible();
  });

  it.each(['base', 'native', 'both'] as const)(
    'shows carryover in %s mode like the budget total',
    mode => {
      state.mode = mode;
      renderWithProviders(
        <BudgetCard
          budget={
            {
              budgetId: 1,
              categoryName: 'Food',
              budgeted: 120,
              spent: 40,
              remaining: 80,
              nativeBudgeted: 150,
              nativeSpent: 50,
              nativeRemaining: 100,
              nativeCarryover: 50,
              carryover: 40,
              nativeCurrency: 'USD',
              currency: 'EUR',
              period: 'MONTHLY',
              status: 'ON_TRACK',
              startDate: '2024-01-01',
              endDate: '2024-01-31',
            } as BudgetProgressResponse
          }
          onEdit={vi.fn()}
          onDelete={vi.fn()}
        />
      );
      const carryover = screen.getByText(/Included carryover/i).closest('p');
      if (mode !== 'native') expect(carryover).toHaveTextContent('€40.00');
      else expect(carryover).not.toHaveTextContent('€40.00');
      if (mode !== 'base') expect(carryover).toHaveTextContent('$50.00');
      else expect(carryover).not.toHaveTextContent('$50.00');
    }
  );

  it.each(['base', 'native', 'both'] as const)(
    'renders recorded search amounts in %s mode',
    mode => {
      state.mode = mode;
      renderWithProviders(
        <SearchResultAmount
          result={
            {
              resultType: 'TRANSACTION',
              id: 1,
              title: 'Transfer',
              amount: 655957,
              currency: 'XOF',
              amountInBaseCurrency: 1000,
              baseCurrency: 'EUR',
              isConverted: true,
              date: '2024-06-01',
              createdAt: '2024-06-01T00:00:00Z',
            } as SearchResult
          }
        />
      );
      if (mode !== 'native') expect(screen.getByText('€1,000.00')).toBeVisible();
      else expect(screen.queryByText('€1,000.00')).not.toBeInTheDocument();
      if (mode !== 'base') expect(screen.getByText(/655,957/)).toBeVisible();
      else expect(screen.queryByText(/655,957/)).not.toBeInTheDocument();
    }
  );
});
