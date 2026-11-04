import { describe, expect, it } from 'vitest';
import {
  checkRegimeEligibility,
  formatValidationErrors,
  getFieldError,
  getFieldErrors,
  isInRange,
  isValid,
  validateBuyRentInputs,
  validateInvestmentInputs,
  validateLoanParameters,
  validatePercentage,
  validatePositiveAmount,
} from '@/validators/realEstateValidators';
import {
  DEFAULT_BUY_RENT_INPUTS,
  DEFAULT_INVESTMENT_INPUTS,
  DEFAULT_RENTAL_TAX_CONTEXT,
  type InvestmentInputs,
} from '@/types/realEstateTools';

const investment = (): InvestmentInputs => ({
  ...structuredClone(DEFAULT_INVESTMENT_INPUTS),
  property: { totalPrice: 250000, furnishingType: 'unfurnished', furnitureValue: 0 },
  credit: {
    monthlyPayment: 1000,
    annualCost: 12000,
    totalCost: 240000,
    assurance: 500,
    bankFees: 100,
  },
  tax: { ...DEFAULT_RENTAL_TAX_CONTEXT },
});

describe('simulation input boundaries', () => {
  it('accepts default purchase inputs and a fully specified rental investment', () => {
    expect(validateBuyRentInputs(DEFAULT_BUY_RENT_INPUTS)).toEqual([]);
    expect(validateInvestmentInputs(investment())).toEqual([]);
  });
  it.each([NaN, Infinity, -Infinity])('rejects nonfinite values in every section: %s', value => {
    const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
    inputs.purchase.propertyPrice = value;
    inputs.rental.monthlyRent = value;
    inputs.market.priceEvolution = value;
    inputs.resale.desiredProfit = value;
    expect(validateBuyRentInputs(inputs).map(error => error.field)).toEqual(
      expect.arrayContaining([
        'purchase.propertyPrice',
        'rental.monthlyRent',
        'market.priceEvolution',
        'resale.desiredProfit',
      ])
    );
  });
  it('reports invalid fields across sections rather than stopping at the first error', () => {
    const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
    for (const key of Object.keys(inputs.purchase) as Array<keyof typeof inputs.purchase>) {
      if (typeof inputs.purchase[key] === 'number') Object.assign(inputs.purchase, { [key]: -1 });
    }
    Object.assign(inputs.rental, {
      monthlyRent: 50001,
      monthlyCharges: 10001,
      securityDeposit: 50001,
      rentalInsurance: 10001,
      initialSavings: -1,
      monthlySavings: -10001,
    });
    Object.assign(inputs.market, {
      priceEvolution: -51,
      rentEvolution: 51,
      investmentReturn: -21,
      inflation: 51,
    });
    Object.assign(inputs.resale, { targetYear: 0, resaleFeesPercent: 101 });
    const errors = validateBuyRentInputs(inputs);
    expect(errors.map(error => error.field)).toEqual(
      expect.arrayContaining([
        'purchase.propertyPrice',
        'purchase.interestRate',
        'purchase.downPayment',
        'purchase.loanDuration',
        'rental.monthlyRent',
        'rental.monthlySavings',
        'market.priceEvolution',
        'market.rentEvolution',
        'market.investmentReturn',
        'resale.targetYear',
        'resale.resaleFeesPercent',
      ])
    );
    expect(errors.every(error => error.message.length > 0)).toBe(true);
    expect(isValid(errors)).toBe(false);
  });
  it('includes acquisition costs in the maximum down payment', () => {
    const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
    Object.assign(inputs.purchase, {
      propertyPrice: 200000,
      renovationAmount: 20000,
      notaryFeesPercent: 5,
      agencyFees: 5000,
      downPayment: 235000,
    });
    expect(getFieldError(validateBuyRentInputs(inputs), 'purchase.downPayment')).toBeNull();
    inputs.purchase.downPayment++;
    expect(getFieldError(validateBuyRentInputs(inputs), 'purchase.downPayment')).not.toBeNull();
  });
  it('rejects fractional years and a 100% resale fee', () => {
    const inputs = structuredClone(DEFAULT_BUY_RENT_INPUTS);
    inputs.purchase.loanDuration = 12.5;
    inputs.resale.targetYear = 3.5;
    inputs.resale.resaleFeesPercent = 100;
    expect(validateBuyRentInputs(inputs).map(error => error.field)).toEqual(
      expect.arrayContaining([
        'purchase.loanDuration',
        'resale.targetYear',
        'resale.resaleFeesPercent',
      ])
    );
  });
  it('validates investment costs, credit, and percentages independently', () => {
    const inputs = investment();
    inputs.revenue.occupancyRate = 101;
    inputs.revenue.badDebtRate = -1;
    inputs.property.totalPrice = 0;
    inputs.property.furnitureValue = Infinity;
    inputs.expenses.marginalTaxRate = 61;
    inputs.credit.annualCost = NaN;
    expect(validateInvestmentInputs(inputs).map(error => error.field)).toEqual(
      expect.arrayContaining([
        'revenue.occupancyRate',
        'revenue.badDebtRate',
        'property.totalPrice',
        'property.furnitureValue',
        'expenses.marginalTaxRate',
        'credit.annualCost',
      ])
    );
  });
  it.each([-1, NaN, Infinity])(
    'rejects invalid household income %s but allows unknown income',
    amount => {
      const inputs = investment();
      inputs.tax = { ...DEFAULT_RENTAL_TAX_CONTEXT, otherHouseholdIncome: amount };
      expect(getFieldError(validateInvestmentInputs(inputs), 'general')).not.toBeNull();
      inputs.tax.otherHouseholdIncome = null;
      expect(validateInvestmentInputs(inputs)).toEqual([]);
    }
  );
});

describe('validation feedback', () => {
  const errors = [
    { field: 'purchase.price', message: 'Price is required' },
    { field: 'purchase.price', message: 'Price exceeds the limit' },
    { field: 'purchaseExtra.fee', message: 'Unrelated section' },
    { field: 'rental.rent', message: 'Rent is required' },
  ];
  it('returns the first message or every message for a field', () => {
    expect(getFieldError(errors, 'purchase.price')).toBe('Price is required');
    expect(getFieldError(errors, 'missing')).toBeNull();
    expect(getFieldErrors(errors, 'purchase.price')).toEqual([
      'Price is required',
      'Price exceeds the limit',
    ]);
    expect(getFieldErrors(errors, 'missing')).toEqual([]);
  });
  it('matches only the requested wildcard section', () => {
    expect(getFieldErrors(errors, 'purchase.*')).toEqual([
      'Price is required',
      'Price exceeds the limit',
    ]);
  });
  it('formats empty, single, and multiple errors without losing messages', () => {
    expect(isValid([])).toBe(true);
    expect(formatValidationErrors([])).toBe('');
    expect(formatValidationErrors(errors.slice(0, 1))).toBe('Price is required');
    expect(formatValidationErrors(errors)).toContain('- Rent is required');
    expect(formatValidationErrors(errors)).toContain('4');
  });
});

describe('numeric helper boundaries', () => {
  it.each([0, 100])('accepts percentage boundary %s', value =>
    expect(validatePercentage(value, 'Rate')).toBeNull()
  );
  it.each([-1, 101, NaN, Infinity])('rejects invalid percentage %s', value =>
    expect(validatePercentage(value, 'Rate')).not.toBeNull()
  );
  it.each([-1, NaN, Infinity, -Infinity])('rejects nonfinite or negative amounts %s', value =>
    expect(validatePositiveAmount(value, 'Amount')).not.toBeNull()
  );
  it('allows zero amounts and inclusive range bounds', () => {
    expect(validatePositiveAmount(0, 'Amount')).toBeNull();
    expect(isInRange(0, 0, 100)).toBe(true);
    expect(isInRange(100, 0, 100)).toBe(true);
    expect(isInRange(NaN, 0, 100)).toBe(false);
    expect(isInRange(101, 0, 100)).toBe(false);
  });
  it.each([
    [0, 3, 20],
    [100000, -1, 20],
    [100000, 3, 0],
    [100000, 3, 41],
    [NaN, 3, 20],
    [100000, Infinity, 20],
    [100000, 3, NaN],
  ])('rejects invalid loan %s / %s / %s', (principal, rate, years) =>
    expect(validateLoanParameters(principal, rate, years)).not.toBeNull()
  );
  it('allows zero interest and the maximum duration', () =>
    expect(validateLoanParameters(100000, 0, 40)).toBeNull());
});

describe('tax eligibility boundaries', () => {
  it('includes the micro-foncier limit and excludes the next euro', () => {
    expect(checkRegimeEligibility(15000).microFoncier.eligible).toBe(true);
    expect(checkRegimeEligibility(15001).microFoncier.eligible).toBe(false);
  });
  it.each([2025, 2026] as const)('applies the %s micro-BIC ceiling inclusively', year => {
    const limit = checkRegimeEligibility(0, null, year).microBic.limit;
    expect(checkRegimeEligibility(limit, null, year).microBic.eligible).toBe(true);
    expect(checkRegimeEligibility(limit + 1, null, year).microBic.eligible).toBe(false);
  });
  it('requires household income to determine professional status above the threshold', () => {
    expect(checkRegimeEligibility(23000).lmnp.isLMP).toBe(false);
    expect(checkRegimeEligibility(23001).lmnp.isLMP).toBeNull();
    expect(checkRegimeEligibility(30000, 30000).lmnp.isLMP).toBe(false);
    expect(checkRegimeEligibility(30001, 30000).lmnp.isLMP).toBe(true);
  });
});
