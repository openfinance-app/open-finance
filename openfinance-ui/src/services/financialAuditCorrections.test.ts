import { describe, expect, it } from 'vitest';
import { RealEstateCalculationService as Calculator } from '@/services/realEstateCalculationService';
import { DEFAULT_BUY_RENT_INPUTS, DEFAULT_INVESTMENT_INPUTS } from '@/types/realEstateTools';
import { calculateAllRegimes } from '@/utils/taxRegimeCalculations';
import { calculateSavingsLongevity } from '@/utils/financialCalculations';
import { convertBuyRentInputs } from '@/utils/simulation-currency';
import { isBuyRentInputs } from '@/validators/simulationShape';
import { exportBuyRentToCSV, exportToJSON } from '@/components/real-estate-tools/exportImport';

function baseline() {
  const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
  for (const key of Object.keys(inputs.purchase) as Array<keyof typeof inputs.purchase>) {
    if (key !== 'isNewProperty') inputs.purchase[key] = 0;
  }
  Object.assign(inputs.purchase, { propertyPrice: 120000, downPayment: 20000, loanDuration: 10 });
  inputs.rental = {
    monthlyRent: 750,
    monthlyCharges: 0,
    securityDeposit: 0,
    rentalInsurance: 0,
    garbageTax: 0,
    initialSavings: 20000,
    monthlySavings: 1000 / 12,
  };
  inputs.market = { priceEvolution: 0, rentEvolution: 0, investmentReturn: 0, inflation: 0 };
  inputs.resale = { targetYear: 1, desiredProfit: 0, resaleFeesPercent: 0 };
  inputs.currency = 'EUR';
  return inputs;
}

describe('independent financial audit accounting controls', () => {
  it('settles debt and counts savings funding before computing year-one net costs', () => {
    const results = Calculator.calculateBuyRentComparison(baseline());
    const year = Calculator.calculateYearNAnalysis(results, 1)!;
    expect(year.remainingCapital).toBeCloseTo(90000, 2);
    expect(year.netWorth).toBeCloseTo(30000, 2);
    expect(year.rentSavings).toBeCloseTo(21000, 2);
    expect(year.netExpenseBuy).toBeCloseTo(0, 2);
    expect(year.netExpenseRent).toBeCloseTo(9000, 2);
    expect(results.summary.rent.netExpense).toBeCloseTo(90000, 2);
  });

  it('invests the buyer savings when a cash purchase has no ongoing costs', () => {
    const inputs = baseline();
    Object.assign(inputs.purchase, { propertyPrice: 180000, downPayment: 180000 });
    const result = Calculator.calculateBuyRentComparison(inputs).summary;
    expect(result.buy.accumulatedSavings).toBeCloseTo(90000, 2);
    expect(result.buy.netWorth).toBeCloseTo(270000, 2);
    expect(result.rent.netWorth).toBeCloseTo(180000, 2);
    expect(result.comparison.winner).toBe('buy');
    expect(result.buy.netExpense).toBeCloseTo(0, 2);
    expect(result.rent.netExpense).toBeCloseTo(90000, 2);
  });

  it('derives the full first-year ownership gap including maintenance and finance costs', () => {
    const inputs = baseline();
    Object.assign(inputs.purchase, {
      totalInsurance: 12000,
      applicationFees: 2400,
      maintenancePercent: 1,
    });
    expect(Calculator.calculateDerivedValues(inputs).suggestedMonthlySavings).toBeCloseTo(
      303.333333,
      5
    );
    const result = Calculator.calculateBuyRentComparison(inputs);
    expect(result.years[0].rent.savings).toBeCloseTo(23640, 2);
  });

  it('keeps total household funding equal as rents change and investment returns accrue', () => {
    const inputs = baseline();
    Object.assign(inputs.market, { rentEvolution: 8, inflation: 2, investmentReturn: 3 });
    inputs.purchase.totalInsurance = 12000;
    inputs.rental.securityDeposit = 1500;
    for (const year of Calculator.calculateBuyRentComparison(inputs).years) {
      const buyFunding = year.buy.cumulativeCost + year.buy.savingsContributions!;
      const rentFunding = year.rent.cumulativeCost + year.rent.savingsContributions!;
      expect(buyFunding).toBeCloseTo(rentFunding, 5);
      expect(year.rent.netExpense! - year.buy.netExpense!).toBeCloseTo(
        year.buy.netWorth! - year.rent.netWorth!,
        5
      );
    }
  });

  it('settles resale fees and represents a genuine tie explicitly', () => {
    const inputs = baseline();
    inputs.resale.resaleFeesPercent = 5;
    expect(Calculator.calculateBuyRentComparison(inputs).years[0].buy.netExpense).toBeCloseTo(
      6000,
      2
    );
    inputs.resale.resaleFeesPercent = 0;
    inputs.rental.monthlyRent = 0;
    expect(Calculator.calculateBuyRentComparison(inputs).summary.comparison.winner).toBe('tie');
  });

  it('preserves monetary identity while converting all amount fields and no rates', () => {
    const inputs = baseline();
    inputs.purchase.interestRate = 3.5;
    inputs.purchase.totalInsurance = 12000;
    inputs.resale.desiredProfit = 10000;
    const converted = convertBuyRentInputs(inputs, 'USD', 1.25);
    expect(converted.currency).toBe('USD');
    expect(converted.purchase.propertyPrice).toBe(150000);
    expect(converted.purchase.totalInsurance).toBe(15000);
    expect(converted.purchase.interestRate).toBe(3.5);
    expect(converted.purchase.loanDuration).toBe(10);
    expect(converted.rental.monthlyRent).toBe(937.5);
    expect(converted.resale.desiredProfit).toBe(12500);
    expect(inputs.currency).toBe('EUR');
    expect(isBuyRentInputs({ ...inputs, currency: 'invalid' })).toBe(false);
    expect(() => convertBuyRentInputs(inputs, 'USD', 0)).toThrow();
  });

  it('exports currency and reconciled wealth in both formats', () => {
    const inputs = baseline();
    const result = Calculator.calculateBuyRentComparison(inputs);
    const csv = exportBuyRentToCSV(inputs, result);
    expect(
      csv
        .split('\n')
        .slice(1)
        .every(row => row.endsWith(';EUR'))
    ).toBe(true);
    const json = JSON.parse(
      exportToJSON(
        {
          metadata: {
            id: 'test',
            name: 'control',
            type: 'buy_rent',
            createdAt: new Date(),
            updatedAt: new Date(),
          },
          data: inputs,
        },
        result
      )
    );
    expect(json.inputs.currency).toBe('EUR');
  });

  it('counts insurance and bank fees as cash payments independently of micro tax allowances', () => {
    const inputs = structuredClone(DEFAULT_INVESTMENT_INPUTS);
    inputs.property.totalPrice = 120000;
    inputs.credit = { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 };
    const before = calculateAllRegimes(inputs);
    inputs.credit.assurance = 1200;
    inputs.credit.bankFees = 240;
    const after = calculateAllRegimes(inputs);
    for (const regime of ['microFoncier', 'microBic'] as const) {
      expect(after[regime].investment.detail.credit).toBe(0);
      expect(after[regime].investment.annualCreditCost).toBe(1440);
      expect(after[regime].taxation.totalTaxes).toEqual(before[regime].taxation.totalTaxes);
      expect(after[regime].performance.monthlyCashFlow).toBeCloseTo(
        before[regime].performance.monthlyCashFlow! - 120,
        2
      );
    }
  });

  it('does not mistake the projection limit for actual depletion', () => {
    const exact = calculateSavingsLongevity(150000, 100, 0);
    expect(exact.monthsUntilDepletion).toBe(1500);
    expect(exact.finalBalance).toBe(0);
    const bounded = calculateSavingsLongevity(150000, 100, 0.1);
    expect(bounded.exceedsProjection).toBe(true);
    expect(bounded.finalBalance).toBeGreaterThan(0);
  });
});
