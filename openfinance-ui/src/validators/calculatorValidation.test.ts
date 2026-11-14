import { describe, it, expect, vi } from 'vitest';

// Return the i18n key verbatim so tests can assert WHICH rule fired,
// not just that some rule did.
vi.mock('@/i18n', () => ({
  default: { t: (key: string) => key },
}));

import {
  validateCompoundInterest,
  validateEarlyPayoff,
  validateFreedom,
  validateLoan,
} from './calculatorValidation';
import {
  DEFAULT_COMPOUND_INTEREST_INPUT,
  DEFAULT_EARLY_PAYOFF_INPUT,
  DEFAULT_FREEDOM_CALCULATOR_INPUT,
  DEFAULT_LOAN_CALCULATOR_INPUT,
  type CompoundingFrequency,
} from '@/types/calculator';

const compound = (overrides = {}) => ({ ...DEFAULT_COMPOUND_INTEREST_INPUT, ...overrides });
const loan = (overrides = {}) => ({ ...DEFAULT_LOAN_CALCULATOR_INPUT, ...overrides });
const freedom = (overrides = {}) => ({ ...DEFAULT_FREEDOM_CALCULATOR_INPUT, ...overrides });
const payoff = (overrides = {}) => ({ ...DEFAULT_EARLY_PAYOFF_INPUT, ...overrides });

describe('validateCompoundInterest', () => {
  it('accepts the default input', () => {
    expect(validateCompoundInterest(compound())).toBeNull();
  });

  it('rejects negative, non-finite and over-precise principals', () => {
    expect(validateCompoundInterest(compound({ principal: -1 }))).toBe('validation.principal');
    expect(validateCompoundInterest(compound({ principal: Infinity }))).toBe(
      'validation.principal'
    );
    expect(validateCompoundInterest(compound({ principal: NaN }))).toBe('validation.principal');
    expect(validateCompoundInterest(compound({ principal: '0.1234567890123456789' }))).toBe(
      'validation.principal'
    );
  });

  it('accepts an 18-decimal-place principal (API fractional-digit limit)', () => {
    expect(validateCompoundInterest(compound({ principal: '0.123456789012345678' }))).toBeNull();
  });

  it('treats the 1e15 principal cap as exclusive', () => {
    expect(validateCompoundInterest(compound({ principal: '999999999999999' }))).toBeNull();
    expect(validateCompoundInterest(compound({ principal: '1000000000000000' }))).toBe(
      'validation.principal'
    );
  });

  it('rejects unparseable principal and rate without throwing', () => {
    expect(() => validateCompoundInterest(compound({ principal: 'abc' }))).not.toThrow();
    expect(validateCompoundInterest(compound({ principal: '' }))).toBe('validation.principal');
    expect(validateCompoundInterest(compound({ annualRate: 'abc' }))).toBe('validation.rate');
  });

  it('enforces rate bounds [0, 100]', () => {
    expect(validateCompoundInterest(compound({ annualRate: 100 }))).toBeNull();
    expect(validateCompoundInterest(compound({ annualRate: 100.01 }))).toBe('validation.rate');
    expect(validateCompoundInterest(compound({ annualRate: -0.01 }))).toBe('validation.rate');
  });

  it('enforces whole years within [1, 100]', () => {
    expect(validateCompoundInterest(compound({ years: 0 }))).toBe('validation.wholeYears');
    expect(validateCompoundInterest(compound({ years: 100 }))).toBeNull();
    expect(validateCompoundInterest(compound({ years: 101 }))).toBe('validation.wholeYears');
    expect(validateCompoundInterest(compound({ years: 2.5 }))).toBe('validation.wholeYears');
  });

  it('only accepts supported compounding frequencies', () => {
    for (const frequency of [1, 2, 4, 12, 52, 365] as CompoundingFrequency[]) {
      expect(validateCompoundInterest(compound({ compoundingFrequency: frequency }))).toBeNull();
    }
    expect(
      validateCompoundInterest(compound({ compoundingFrequency: 3 as CompoundingFrequency }))
    ).toBe('validation.frequency');
    expect(
      validateCompoundInterest(compound({ compoundingFrequency: 0 as CompoundingFrequency }))
    ).toBe('validation.frequency');
  });

  it('rejects negative or capped-out regular contributions', () => {
    expect(validateCompoundInterest(compound({ regularContribution: -5 }))).toBe(
      'validation.contribution'
    );
    expect(validateCompoundInterest(compound({ regularContribution: '1000000000000' }))).toBe(
      'validation.contribution'
    );
  });

  it('reports the first failing rule (principal before rate before years)', () => {
    expect(validateCompoundInterest(compound({ principal: -1, annualRate: 200, years: 0 }))).toBe(
      'validation.principal'
    );
    expect(validateCompoundInterest(compound({ annualRate: 200, years: 0 }))).toBe(
      'validation.rate'
    );
  });
});

describe('validateLoan', () => {
  it('accepts the default input', () => {
    expect(validateLoan(loan())).toBeNull();
  });

  it('rejects a zero principal in every representation', () => {
    expect(validateLoan(loan({ principal: 0 }))).toBe('validation.loanPrincipal');
    expect(validateLoan(loan({ principal: '0.00' }))).toBe('validation.loanPrincipal');
    expect(validateLoan(loan({ principal: '0.01' }))).toBeNull();
  });

  it('rejects unparseable principal without throwing (short-circuit before re-parse)', () => {
    expect(() => validateLoan(loan({ principal: 'not-a-number' }))).not.toThrow();
    expect(validateLoan(loan({ principal: 'not-a-number' }))).toBe('validation.loanPrincipal');
    expect(validateLoan(loan({ principal: -100 }))).toBe('validation.loanPrincipal');
  });

  it('enforces rate bounds [0, 100]', () => {
    expect(validateLoan(loan({ annualRate: -1 }))).toBe('validation.rate');
    expect(validateLoan(loan({ annualRate: 101 }))).toBe('validation.rate');
  });

  it('accepts fractional years that resolve to whole months only', () => {
    expect(validateLoan(loan({ years: 1.5 }))).toBeNull(); // 18 months
    expect(validateLoan(loan({ years: 0.25 }))).toBeNull(); // 3 months
    expect(validateLoan(loan({ years: 1.1 }))).toBe('validation.loanTerm'); // 13.2 months
    expect(validateLoan(loan({ years: 1 / 12 }))).toBeNull(); // 1 month within float tolerance
  });

  it('enforces term bounds [1, 1200] months', () => {
    expect(validateLoan(loan({ years: 0 }))).toBe('validation.loanTerm');
    expect(validateLoan(loan({ years: 100 }))).toBeNull();
    expect(validateLoan(loan({ years: 100 + 1 / 12 }))).toBe('validation.loanTerm');
    expect(validateLoan(loan({ years: NaN }))).toBe('validation.loanTerm');
  });
});

describe('validateFreedom', () => {
  it('accepts the default input and omitted optional fields', () => {
    expect(validateFreedom(freedom())).toBeNull();
    expect(
      validateFreedom(
        freedom({
          monthlyContribution: undefined,
          withdrawalRate: undefined,
          inflationRate: undefined,
        })
      )
    ).toBeNull();
  });

  it('rejects invalid savings, expenses and contribution', () => {
    expect(validateFreedom(freedom({ currentSavings: -1 }))).toBe('validation.savings');
    expect(validateFreedom(freedom({ monthlyExpenses: '1000000000000' }))).toBe(
      'validation.expenses'
    );
    expect(validateFreedom(freedom({ monthlyExpenses: -5 }))).toBe('validation.expenses');
    expect(validateFreedom(freedom({ monthlyContribution: -5 }))).toBe('validation.contribution');
  });

  it('enforces withdrawal rate bounds [0.5, 10]', () => {
    expect(validateFreedom(freedom({ withdrawalRate: 0.5 }))).toBeNull();
    expect(validateFreedom(freedom({ withdrawalRate: 10 }))).toBeNull();
    expect(validateFreedom(freedom({ withdrawalRate: 0.49 }))).toBe('validation.withdrawal');
    expect(validateFreedom(freedom({ withdrawalRate: 10.01 }))).toBe('validation.withdrawal');
    expect(validateFreedom(freedom({ withdrawalRate: 0 }))).toBe('validation.withdrawal');
  });

  it('enforces expected return bounds [-10, 30]', () => {
    expect(validateFreedom(freedom({ expectedAnnualReturn: -10 }))).toBeNull();
    expect(validateFreedom(freedom({ expectedAnnualReturn: 30 }))).toBeNull();
    expect(validateFreedom(freedom({ expectedAnnualReturn: -10.01 }))).toBe('validation.return');
    expect(validateFreedom(freedom({ expectedAnnualReturn: 30.01 }))).toBe('validation.return');
  });

  it('enforces inflation bounds [-99, 100] so prices can never be zeroed out', () => {
    expect(validateFreedom(freedom({ inflationRate: -99 }))).toBeNull();
    expect(validateFreedom(freedom({ inflationRate: 100 }))).toBeNull();
    expect(validateFreedom(freedom({ inflationRate: -100 }))).toBe('validation.inflation');
    expect(validateFreedom(freedom({ inflationRate: 100.01 }))).toBe('validation.inflation');
  });
});

describe('validateEarlyPayoff', () => {
  it('accepts the default input and an empty lump-sum list', () => {
    expect(validateEarlyPayoff(payoff())).toBeNull();
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: [] }))).toBeNull();
  });

  it('rejects zero balance and out-of-range rate', () => {
    expect(validateEarlyPayoff(payoff({ loanBalance: 0 }))).toBe('validation.loanPrincipal');
    expect(validateEarlyPayoff(payoff({ loanBalance: 'x' }))).toBe('validation.loanPrincipal');
    expect(validateEarlyPayoff(payoff({ annualRate: 101 }))).toBe('validation.rate');
  });

  it('rejects fractional or out-of-range remaining term parts', () => {
    expect(validateEarlyPayoff(payoff({ remainingYears: 1.5 }))).toBe('validation.remainingTerm');
    expect(validateEarlyPayoff(payoff({ remainingYears: 101 }))).toBe('validation.remainingTerm');
    expect(validateEarlyPayoff(payoff({ remainingMonthsExtra: 12 }))).toBe(
      'validation.remainingTerm'
    );
    expect(validateEarlyPayoff(payoff({ remainingMonthsExtra: -1 }))).toBe(
      'validation.remainingTerm'
    );
    expect(validateEarlyPayoff(payoff({ remainingMonthsExtra: 2.5 }))).toBe(
      'validation.remainingTerm'
    );
  });

  it('rejects a zero total remaining term and terms over 1200 months', () => {
    expect(validateEarlyPayoff(payoff({ remainingYears: 0, remainingMonthsExtra: 0 }))).toBe(
      'validation.remainingTerm'
    );
    expect(validateEarlyPayoff(payoff({ remainingYears: 100, remainingMonthsExtra: 1 }))).toBe(
      'validation.remainingTerm'
    );
    expect(validateEarlyPayoff(payoff({ remainingYears: 0, remainingMonthsExtra: 1 }))).toBe(
      'validation.lumpSum' // default lump sum at month 12 exceeds the 1-month term
    );
  });

  it('rejects negative monthly extra payments', () => {
    expect(validateEarlyPayoff(payoff({ monthlyExtraPayment: -1 }))).toBe(
      'validation.contribution'
    );
  });

  it('validates every lump-sum payment against the term', () => {
    const lump = (month: number, amount: number | string) => [{ id: 'x', month, amount }];
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: lump(0, 100) }))).toBe(
      'validation.lumpSum'
    );
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: lump(241, 100) }))).toBe(
      'validation.lumpSum'
    );
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: lump(240, 100) }))).toBeNull();
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: lump(6, -100) }))).toBe(
      'validation.lumpSum'
    );
    expect(validateEarlyPayoff(payoff({ lumpSumPayments: lump(6, 0) }))).toBeNull();
    expect(
      validateEarlyPayoff(
        payoff({
          lumpSumPayments: [
            { id: 'a', month: 6, amount: 100 },
            { id: 'b', month: 12.5, amount: 100 },
          ],
        })
      )
    ).toBe('validation.lumpSum');
  });
});
