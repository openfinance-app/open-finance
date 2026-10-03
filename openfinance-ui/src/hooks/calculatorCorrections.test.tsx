import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { AllProviders, mockAuthentication } from '@/test/test-utils';
import { useLoanCalculator } from '@/hooks/useLoanCalculator';
import { useFinancialFreedom } from '@/hooks/useFinancialFreedom';
import { useBuyRentCalculations } from '@/hooks/useBuyRentCalculations';
import { useRentalSimulator } from '@/hooks/useRentalSimulator';
import {
  validateCompoundInterest,
  validateEarlyPayoff,
  validateFreedom,
} from '@/validators/calculatorValidation';
import { validateInvestmentInputs } from '@/validators/realEstateValidators';
import { isInvestmentInputs } from '@/validators/simulationShape';
import {
  DEFAULT_COMPOUND_INTEREST_INPUT,
  DEFAULT_EARLY_PAYOFF_INPUT,
  DEFAULT_FREEDOM_CALCULATOR_INPUT,
} from '@/types/calculator';
import {
  DEFAULT_BUY_RENT_INPUTS,
  DEFAULT_INVESTMENT_INPUTS,
  type BuyRentInputs,
} from '@/types/realEstateTools';
import { RealEstateCalculationService } from '@/services/realEstateCalculationService';
import { calculateCompoundInterest } from '@/utils/realEstateCalculations';
import { exportBuyRentToCSV } from '@/components/real-estate-tools/exportImport';
import i18n from '@/i18n';

vi.mock('@/services/calculatorApi', () => ({
  getCalculationDefaults: vi.fn().mockResolvedValue({}),
  calculateTimeline: vi.fn(),
  calculateLongevity: vi.fn(),
}));

function zeroCostPurchase(): BuyRentInputs {
  const input = structuredClone(DEFAULT_BUY_RENT_INPUTS);
  Object.keys(input.purchase).forEach(key => {
    if (key !== 'isNewProperty') (input.purchase as unknown as Record<string, number>)[key] = 0;
  });
  Object.keys(input.rental).forEach(key => {
    (input.rental as unknown as Record<string, number>)[key] = 0;
  });
  input.purchase.propertyPrice = 120000;
  input.purchase.downPayment = 20000;
  input.purchase.loanDuration = 10;
  input.rental.monthlyRent = 500;
  input.rental.initialSavings = 20000;
  input.rental.monthlySavings = 1000 / 3;
  input.market = { priceEvolution: 0, rentEvolution: 0, investmentReturn: 0, inflation: 0 };
  input.resale = { targetYear: 10, desiredProfit: 0, resaleFeesPercent: 0 };
  return input;
}

beforeEach(() => {
  mockAuthentication();
});

describe('reported calculator boundaries', () => {
  it('accepts a zero savings start and zero interest but never truncates years', () => {
    expect(
      validateCompoundInterest({ ...DEFAULT_COMPOUND_INTEREST_INPUT, principal: 0, annualRate: 0 })
    ).toBeNull();
    expect(
      validateCompoundInterest({ ...DEFAULT_COMPOUND_INTEREST_INPUT, years: 1.5 })
    ).not.toBeNull();
  });
  it('rejects invalid withdrawal and fractional lump-sum months', () => {
    expect(
      validateFreedom({ ...DEFAULT_FREEDOM_CALCULATOR_INPUT, withdrawalRate: -4 })
    ).not.toBeNull();
    expect(validateFreedom({ ...DEFAULT_FREEDOM_CALCULATOR_INPUT, inflationRate: 0 })).toBeNull();
    expect(
      validateEarlyPayoff({
        ...DEFAULT_EARLY_PAYOFF_INPUT,
        lumpSumPayments: [{ id: 'test', month: 1.5, amount: 6000 }],
      })
    ).not.toBeNull();
  });
  it('rejects zero and fractional-payment loan terms and amortizes eighteen months completely', () => {
    const { result } = renderHook(() => useLoanCalculator(), { wrapper: AllProviders });
    for (const years of [0, 1.1]) {
      act(() => result.current.updateInput('years', years));
      act(() => result.current.calculate());
      expect(result.current.result).toBeNull();
      expect(result.current.error).toBeTruthy();
    }
    act(() => result.current.updateInput('years', 1.5));
    act(() => result.current.calculate());
    expect(result.current.result?.amortizationSchedule).toHaveLength(18);
    expect(result.current.result?.amortizationSchedule.at(-1)?.remainingBalance).toBeCloseTo(0, 2);
  });
  it('reports six months of savings and uses calendar months for depletion', async () => {
    const { result } = renderHook(() => useFinancialFreedom(), { wrapper: AllProviders });
    act(() => {
      result.current.updateInput('currentSavings', 6000);
      result.current.updateInput('monthlyExpenses', 1000);
      result.current.updateInput('expectedAnnualReturn', 0);
    });
    act(() => {
      result.current.calculateLocal();
    });
    expect(result.current.longevityResult?.totalMonthsUntilDepletion).toBe(6);
    expect(result.current.longevityResult?.depletionYear).toBe(
      new Date(new Date().getFullYear(), new Date().getMonth() + 6, 1).getFullYear()
    );
    await waitFor(() => expect(result.current.defaults).toBeTruthy());
  });
});

describe('real estate reconciliation and state', () => {
  it('has no gain when the property sells for exactly the total cash paid', () => {
    const results = RealEstateCalculationService.calculateBuyRentComparison(zeroCostPurchase());
    expect(results.years[9].rent.savings).toBeCloseTo(60000, 2);
    expect(
      RealEstateCalculationService.calculateYearNAnalysis(results, 10)?.annualProfitability
    ).toBe(0);
    expect(
      RealEstateCalculationService.calculateYearNAnalysis(results, 10, 10)?.annualProfitability
    ).toBeLessThan(0);
  });
  it('applies the same effective annual loss to principal and monthly contributions', () => {
    let balance = 20000;
    const factor = 0.88 ** (1 / 12);
    for (let month = 0; month < 120; month++) balance = balance * factor + 1000 / 3;
    expect(calculateCompoundInterest(20000, -12, 10, 1000 / 3)).toBeCloseTo(balance, 2);
    expect(calculateCompoundInterest(20000, -12, 1, 1000 / 3)).toBe(21374.92);
  });
  it('calculates a selected property atomically and clears results on edit or load', async () => {
    const initial = zeroCostPurchase();
    const { result } = renderHook(() => useBuyRentCalculations(initial), { wrapper: AllProviders });
    const selected = zeroCostPurchase();
    selected.purchase.propertyPrice = 240000;
    act(() => result.current.calculate(selected));
    await waitFor(() => expect(result.current.results?.years[0].buy.propertyValue).toBe(240000));
    expect(result.current.inputs.rental.monthlySavings).toBeCloseTo(1333.3333, 2);
    act(() => result.current.updatePurchaseInput('propertyPrice', 300000));
    expect(result.current.results).toBeNull();
    act(() => {
      result.current.calculate();
      result.current.setInputs(initial);
    });
    await new Promise(resolve => setTimeout(resolve, 10));
    expect(result.current.results).toBeNull();
  });
  it('resets to supplied country defaults', () => {
    const initial = zeroCostPurchase();
    initial.purchase.notaryFeesPercent = 3;
    const { result } = renderHook(() => useBuyRentCalculations(initial), { wrapper: AllProviders });
    act(() => result.current.updatePurchaseInput('notaryFeesPercent', 15));
    act(() => result.current.reset());
    expect(result.current.inputs.purchase.notaryFeesPercent).toBe(3);
  });
  it('validates every rental expense and rejects buy-rent data as rental data', () => {
    for (const field of Object.keys(DEFAULT_INVESTMENT_INPUTS.expenses)) {
      const inputs = {
        ...structuredClone(DEFAULT_INVESTMENT_INPUTS),
        credit: { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 },
      };
      inputs.property.totalPrice = 120000;
      (inputs.expenses as unknown as Record<string, number>)[field] = -1;
      expect(
        validateInvestmentInputs(inputs).some(error => error.field === `expenses.${field}`)
      ).toBe(true);
    }
    expect(isInvestmentInputs(DEFAULT_BUY_RENT_INPUTS)).toBe(false);
    expect(
      isInvestmentInputs({
        ...DEFAULT_INVESTMENT_INPUTS,
        credit: { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 },
      })
    ).toBe(true);
  });
  it('preserves transferred zero expenses and clears rental results on edit', async () => {
    const shared = {
      totalPrice: 120000,
      credit: { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 },
      propertyTax: 0,
      coOwnershipCharges: 0,
    };
    const { result } = renderHook(() => useRentalSimulator(shared), { wrapper: AllProviders });
    expect(result.current.inputs.expenses.propertyTax).toBe(0);
    expect(isInvestmentInputs(JSON.parse(JSON.stringify(result.current.inputs)))).toBe(true);
    act(() => result.current.calculate());
    await waitFor(() => expect(result.current.results).not.toBeNull());
    act(() => result.current.updateExpenseInput('propertyTax', -100));
    expect(result.current.results).toBeNull();
  });
  it('exports translated CSV headers', async () => {
    await i18n.changeLanguage('en');
    const input = zeroCostPurchase();
    const csv = exportBuyRentToCSV(
      input,
      RealEstateCalculationService.calculateBuyRentComparison(input)
    );
    expect(csv).not.toContain('realEstate');
    expect(csv).not.toContain('exportImport');
    expect(csv).toContain('Year');
  });
});
