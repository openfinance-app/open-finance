import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { BuyRentResults, YearNAnalysis, YearlyResult } from '@/types/realEstateTools';
import type { ExactDecimalFields } from '@/utils/exact-decimal';
import { YearNAnalysisCard } from '@/components/real-estate-tools/ResultsPanel/YearNAnalysisCard';
import { ComparisonAnalysis } from '@/components/real-estate-tools/ResultsPanel/ComparisonAnalysis';
import { SummaryCards } from '@/components/real-estate-tools/ResultsPanel/SummaryCards';
import { YearlyTable } from '@/components/real-estate-tools/ResultsPanel/YearlyTable';

// Observe the lossless amount passed to the formatter, without rounding away the bug.
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number | string }) => <span>{String(amount)}</span>,
}));

function analysis(overrides: Partial<YearNAnalysis> & ExactDecimalFields = {}): YearNAnalysis {
  return {
    year: 5,
    propertyValue: 150000,
    remainingCapital: 50000,
    netWorth: 100000,
    totalCostsBuy: 20000,
    totalCostsRent: 30000,
    netExpenseBuy: 20000,
    netExpenseRent: 30000,
    annualProfitability: 2.5,
    minimumResalePrice: 140000,
    rentSavings: 90000,
    ...overrides,
  };
}

function year(yearNumber: number, buyerWins: boolean): YearlyResult {
  return {
    year: yearNumber,
    buy: {
      annualCost: 12000,
      cumulativeCost: yearNumber * 12000,
      propertyValue: 150000,
      remainingCapital: buyerWins ? 50000 : 140000,
      minimumResalePrice: buyerWins ? 140000 : 160000,
      details: {
        mortgage: 10000,
        insurance: 200,
        applicationFees: 0,
        guaranteeFees: 0,
        accountFees: 0,
        propertyTax: 700,
        coOwnershipCharges: 500,
        maintenance: 600,
        homeInsurance: 0,
        bankFees: 0,
        garbageTax: 0,
      },
    },
    rent: { annualCost: 10000, cumulativeCost: yearNumber * 10000, savings: 30000 },
  };
}

function results(buyWorth: number, rentWorth: number, monthlyGap: number): BuyRentResults {
  return {
    years: [year(1, true), year(2, false)],
    summary: {
      buy: {
        averageMonthlyCost: 1000 - monthlyGap,
        totalCost: 24000,
        finalPropertyValue: 150000,
        remainingCapital: 50000,
        totalCreditCost: 3000,
        netWorth: buyWorth,
        netExpense: 20000,
      },
      rent: {
        averageMonthlyCost: 1000,
        totalCost: 20000,
        accumulatedSavings: rentWorth,
        netWorth: rentWorth,
        netExpense: 15000,
      },
      comparison: {
        netWorthDifference: buyWorth - rentWorth,
        netExpenseDifference: -5000,
        monthlyGap,
        winner: buyWorth === rentWorth ? 'tie' : buyWorth > rentWorth ? 'buy' : 'rent',
      },
    },
  };
}

describe('buy/rent result decisions', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  it.each([
    { worth: 100000, savings: 90000, label: 'Buy recommended' },
    { worth: 80000, savings: 90000, label: 'Rent recommended' },
    { worth: 90000, savings: 90000, label: 'Equal net worth' },
    { worth: 90000.004, savings: 90000, label: 'Equal net worth' },
  ])(
    'chooses the outcome at the half-cent boundary: $label ($worth)',
    ({ worth, savings, label }) => {
      renderWithProviders(
        <YearNAnalysisCard
          analysis={analysis({ netWorth: worth, rentSavings: savings })}
          targetYear={5}
        />
      );
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
      expect(screen.getByText('Year 5 Analysis')).toBeInTheDocument();
    }
  );

  it.each([null, 0, -2.5])(
    'shows undefined, zero, and negative annual returns honestly (%s)',
    profitability => {
      renderWithProviders(
        <YearNAnalysisCard
          analysis={analysis({ annualProfitability: profitability })}
          targetYear={5}
        />
      );
      expect(
        screen.getByText(
          profitability === null
            ? 'Not defined for these cash flows'
            : `${profitability.toFixed(2)}%`
        )
      ).toBeInTheDocument();
    }
  );

  it('warns when the desired resale price cannot be achieved', () => {
    renderWithProviders(
      <YearNAnalysisCard analysis={analysis({ minimumResalePrice: 170000 })} targetYear={5} />
    );
    expect(screen.getByText(/You will not be able to achieve/)).toHaveTextContent('170000');
    expect(screen.queryByText(/Possible profit/)).not.toBeInTheDocument();
  });

  it('shows zero profit when the resale price exactly meets the required price', () => {
    renderWithProviders(
      <YearNAnalysisCard analysis={analysis({ minimumResalePrice: 150000 })} targetYear={5} />
    );
    expect(screen.getByText(/Possible profit/)).toHaveTextContent('Possible profit: 0');
  });

  it('shows a positive saving when renting costs less', () => {
    renderWithProviders(
      <YearNAnalysisCard
        analysis={analysis({ netWorth: 80000, netExpenseRent: 10000 })}
        targetYear={5}
      />
    );
    expect(screen.getByText(/Renting saves/)).toHaveTextContent('Renting saves 10000');
    expect(screen.getByText('saved with rental')).toBeInTheDocument();
  });

  it('preserves cent differences above the safe numeric precision in the recommendation', () => {
    renderWithProviders(
      <YearNAnalysisCard
        analysis={analysis({
          netWorth: 1e20,
          rentSavings: 1e20,
          exactValues: {
            netWorth: '100000000000000000000.01',
            rentSavings: '100000000000000000000',
          },
        })}
        targetYear={5}
      />
    );
    expect(screen.getByText('Buy recommended')).toBeInTheDocument();
    expect(screen.getByText('0.01')).toBeInTheDocument();
  });

  it('uses precise resale amounts when their numeric values round to the same number', () => {
    renderWithProviders(
      <YearNAnalysisCard
        analysis={analysis({
          propertyValue: 1e20,
          minimumResalePrice: 1e20,
          exactValues: {
            propertyValue: '100000000000000000000',
            minimumResalePrice: '100000000000000000000.01',
          },
        })}
        targetYear={5}
      />
    );
    expect(screen.getByText(/You will not be able to achieve/)).toBeInTheDocument();
  });

  it.each([
    [100000, 90000, 100, 'Buying is more advantageous', 'Buying requires lower'],
    [80000, 90000, -100, 'Renting is more advantageous', 'Renting requires lower'],
    [90000, 90000, 0, 'Equal net worth', 'Equal housing payments'],
  ] as const)(
    'compares worth and housing costs independently (%s, %s)',
    (buy, rent, gap, winner, payments) => {
      renderWithProviders(<ComparisonAnalysis results={results(buy, rent, gap)} />);
      expect(screen.getByText(winner)).toBeInTheDocument();
      expect(screen.getByText(new RegExp(payments))).toBeInTheDocument();
    }
  );

  it.each([
    [0, 0],
    [-1000, -500],
    [-500, 0],
    [0, -500],
  ])('keeps progress finite and nonnegative for net worth %s/%s', (buy, rent) => {
    renderWithProviders(<ComparisonAnalysis results={results(buy, rent, 0)} />);
    for (const bar of screen.getAllByRole('progressbar')) {
      const value = Number(bar.getAttribute('aria-valuenow'));
      expect(Number.isFinite(value)).toBe(true);
      expect(value).toBeGreaterThanOrEqual(0);
      expect(value).toBeLessThanOrEqual(100);
    }
    expect(screen.getByText('Equal housing payments.')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/NaN|Infinity/);
    expect(document.body.textContent).not.toMatch(/\(-[\d,.]+%\)/);
  });

  it.each(['buy', 'rent', 'tie'] as const)(
    'summarizes a %s outcome without inventing buyer savings',
    winner => {
      const data = results(
        winner === 'buy' ? 100000 : winner === 'rent' ? 80000 : 90000,
        90000,
        winner === 'buy' ? 100 : -100
      );
      renderWithProviders(<SummaryCards results={data} monthlySavings={0} />);
      expect(
        screen.getByText(
          winner === 'tie'
            ? 'Equal net worth'
            : `in favor of ${winner === 'buy' ? 'purchase' : 'rental'}`
        )
      ).toBeInTheDocument();
      expect(screen.getAllByText('0').length).toBeGreaterThan(0);
    }
  );

  it('expands and collapses individual years without losing the other year details', () => {
    renderWithProviders(<YearlyTable results={results(100000, 90000, 100)} />);
    const first = screen.getByRole('cell', { name: '1' });
    const second = screen.getByRole('cell', { name: '2' });
    expect(screen.queryByText('Buy Details')).not.toBeInTheDocument();
    fireEvent.click(first);
    expect(screen.getByText('Advantage:', { exact: false }).textContent).toBe(
      'Advantage: purchase'
    );
    fireEvent.click(second);
    expect(screen.getAllByText('Buy Details')).toHaveLength(2);
    expect(screen.getByText('Advantage: rental')).toBeInTheDocument();
    fireEvent.click(first);
    expect(screen.getAllByText('Buy Details')).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getAllByText('Buy Details')).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(screen.queryByText('Buy Details')).not.toBeInTheDocument();
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(3);
  });
});
