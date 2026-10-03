/**
 * Real Estate Input Validation Functions
 *
 * Input validation logic for real estate tools
 * Requirements: REQ-5.1, REQ-5.2
 */

import type {
  BuyRentInputs,
  InvestmentInputs,
  ValidationError,
  PurchaseInputs,
  RentalInputs,
  MarketInputs,
  ResaleInputs,
} from '@/types/realEstateTools';
import i18n from '@/i18n';
import { REGIME_LIMITS } from '@/types/realEstateTools';

/**
 * Validation rules for Buy/Rent inputs
 */
export const buyRentValidationRules = {
  propertyPrice: {
    min: 0,
    max: 100000000, // 100M EUR max
    required: true,
    get message() {
      return i18n.t('validation.propertyPrice', { ns: 'realEstate' });
    },
  },
  renovationAmount: {
    min: 0,
    max: 10000000, // 10M EUR max
    required: false,
    get message() {
      return i18n.t('validation.renovationAmount', { ns: 'realEstate' });
    },
  },
  downPayment: {
    min: 0,
    required: true,
    get message() {
      return i18n.t('validation.downPayment', { ns: 'realEstate' });
    },
    getCrossFieldError: (inputs: BuyRentInputs) => {
      const totalPrice =
        inputs.purchase.propertyPrice +
        inputs.purchase.renovationAmount +
        (inputs.purchase.propertyPrice * inputs.purchase.notaryFeesPercent) / 100 +
        inputs.purchase.agencyFees;
      if (inputs.purchase.downPayment > totalPrice) {
        return i18n.t('validation.downPaymentTotal', { ns: 'realEstate' });
      }
      return null;
    },
  },
  loanDuration: {
    min: 1,
    max: 40,
    required: true,
    get message() {
      return i18n.t('validation.loanDuration', { ns: 'realEstate' });
    },
  },
  interestRate: {
    min: 0,
    max: 100,
    required: true,
    get message() {
      return i18n.t('validation.interestRate', { ns: 'realEstate' });
    },
  },
  notaryFeesPercent: {
    min: 0,
    max: 100,
    required: true,
    get message() {
      return i18n.t('validation.notaryFeesPercent', { ns: 'realEstate' });
    },
  },
  agencyFees: {
    min: 0,
    max: 10000000,
    required: false,
    get message() {
      return i18n.t('validation.agencyFees', { ns: 'realEstate' });
    },
  },
  totalInsurance: {
    min: 0,
    max: 1000000,
    required: false,
    get message() {
      return i18n.t('validation.totalInsurance', { ns: 'realEstate' });
    },
  },
  applicationFees: {
    min: 0,
    max: 100000,
    required: false,
    get message() {
      return i18n.t('validation.applicationFees', { ns: 'realEstate' });
    },
  },
  guaranteeFees: {
    min: 0,
    max: 100000,
    required: false,
    get message() {
      return i18n.t('validation.guaranteeFees', { ns: 'realEstate' });
    },
  },
  accountFees: {
    min: 0,
    max: 100000,
    required: false,
    get message() {
      return i18n.t('validation.accountFees', { ns: 'realEstate' });
    },
  },
  propertyTax: {
    min: 0,
    max: 1000000,
    required: false,
    get message() {
      return i18n.t('validation.propertyTax', { ns: 'realEstate' });
    },
  },
  coOwnershipCharges: {
    min: 0,
    max: 1000000,
    required: false,
    get message() {
      return i18n.t('validation.coOwnershipCharges', { ns: 'realEstate' });
    },
  },
  maintenancePercent: {
    min: 0,
    max: 100,
    required: false,
    get message() {
      return i18n.t('validation.maintenancePercent', { ns: 'realEstate' });
    },
  },
  homeInsurance: {
    min: 0,
    max: 100000,
    required: false,
    get message() {
      return i18n.t('validation.homeInsurance', { ns: 'realEstate' });
    },
  },
  bankFees: {
    min: 0,
    max: 100000,
    required: false,
    get message() {
      return i18n.t('validation.bankFees', { ns: 'realEstate' });
    },
  },
  garbageTax: {
    min: 0,
    max: 10000,
    required: false,
    get message() {
      return i18n.t('validation.garbageTax', { ns: 'realEstate' });
    },
  },
};

/**
 * Validation rules for Rental inputs
 */
export const rentalValidationRules = {
  monthlyRent: {
    min: 0,
    max: 50000,
    required: true,
    get message() {
      return i18n.t('validation.monthlyRent', { ns: 'realEstate' });
    },
  },
  monthlyCharges: {
    min: 0,
    max: 10000,
    required: false,
    get message() {
      return i18n.t('validation.monthlyCharges', { ns: 'realEstate' });
    },
  },
  securityDeposit: {
    min: 0,
    max: 50000,
    required: false,
    get message() {
      return i18n.t('validation.securityDeposit', { ns: 'realEstate' });
    },
  },
  rentalInsurance: {
    min: 0,
    max: 10000,
    required: false,
    get message() {
      return i18n.t('validation.rentalInsurance', { ns: 'realEstate' });
    },
  },
  initialSavings: {
    min: 0,
    max: 100000000,
    required: false,
    get message() {
      return i18n.t('validation.initialSavings', { ns: 'realEstate' });
    },
  },
  monthlySavings: {
    min: -10000,
    max: 50000,
    required: false,
    get message() {
      return i18n.t('validation.monthlySavings', { ns: 'realEstate' });
    },
  },
};

/**
 * Validation rules for Market inputs
 */
export const marketValidationRules = {
  priceEvolution: {
    min: -50,
    max: 50,
    required: false,
    get message() {
      return i18n.t('validation.priceEvolution', { ns: 'realEstate' });
    },
  },
  rentEvolution: {
    min: -50,
    max: 50,
    required: false,
    get message() {
      return i18n.t('validation.rentEvolution', { ns: 'realEstate' });
    },
  },
  investmentReturn: {
    min: -20,
    max: 50,
    required: false,
    get message() {
      return i18n.t('validation.investmentReturn', { ns: 'realEstate' });
    },
  },
  inflation: {
    min: -10,
    max: 50,
    required: false,
    get message() {
      return i18n.t('validation.inflation', { ns: 'realEstate' });
    },
  },
};

/**
 * Validation rules for Resale inputs
 */
export const resaleValidationRules = {
  targetYear: {
    min: 1,
    max: 100,
    required: false,
    get message() {
      return i18n.t('validation.targetYear', { ns: 'realEstate' });
    },
    getCrossFieldError: (inputs: BuyRentInputs) => {
      if (inputs.resale.targetYear > inputs.purchase.loanDuration) {
        return i18n.t('validation.resaleTerm', {
          ns: 'realEstate',
          years: inputs.purchase.loanDuration,
        });
      }
      return null;
    },
  },
  desiredProfit: {
    min: -1000000,
    max: 10000000,
    required: false,
    get message() {
      return i18n.t('validation.desiredProfit', { ns: 'realEstate' });
    },
  },
  resaleFeesPercent: {
    min: 0,
    max: 100,
    required: false,
    get message() {
      return i18n.t('validation.resaleFeesPercent', { ns: 'realEstate' });
    },
  },
};

/**
 * Validate a single numeric value against rules
 *
 * @param value - Value to validate
 * @param fieldName - Field name for error message
 * @param rules - Validation rules
 * @returns Error message or null if valid
 */
function validateNumericValue(
  value: number,
  rules: { min?: number; max?: number; required?: boolean; message: string }
): string | null {
  // Check if required
  if (rules.required && (value === undefined || value === null)) {
    return rules.message;
  }

  // Skip validation if not required and empty
  if (!rules.required && (value === undefined || value === null)) {
    return null;
  }

  // Check if valid number
  if (typeof value !== 'number' || isNaN(value) || !isFinite(value)) {
    return rules.message;
  }

  // Check min
  if (rules.min !== undefined && value < rules.min) {
    return `${rules.message} (minimum: ${rules.min})`;
  }

  // Check max
  if (rules.max !== undefined && value > rules.max) {
    return `${rules.message} (maximum: ${rules.max})`;
  }

  return null;
}

/**
 * Validate Buy/Rent inputs
 * REQ-5.1, REQ-5.2
 *
 * @param inputs - Buy/Rent input data
 * @returns Array of validation errors
 */
export function validateBuyRentInputs(inputs: BuyRentInputs): ValidationError[] {
  const errors: ValidationError[] = [];

  // Validate purchase inputs
  const purchaseFields: (keyof PurchaseInputs)[] = [
    'propertyPrice',
    'renovationAmount',
    'notaryFeesPercent',
    'agencyFees',
    'downPayment',
    'loanDuration',
    'interestRate',
    'totalInsurance',
    'applicationFees',
    'guaranteeFees',
    'accountFees',
    'propertyTax',
    'coOwnershipCharges',
    'maintenancePercent',
    'homeInsurance',
    'bankFees',
    'garbageTax',
  ];

  for (const field of purchaseFields) {
    const rules = buyRentValidationRules[field as keyof typeof buyRentValidationRules];
    if (rules) {
      const error = validateNumericValue(inputs.purchase[field] as number, rules);
      if (error) {
        errors.push({ field: `purchase.${field}`, message: error });
      }

      // Check cross-field validations
      if ('getCrossFieldError' in rules && typeof rules.getCrossFieldError === 'function') {
        const crossFieldError = rules.getCrossFieldError(inputs);
        if (crossFieldError) {
          errors.push({ field: `purchase.${field}`, message: crossFieldError });
        }
      }
    }
  }

  // Validate rental inputs
  const rentalFields: (keyof RentalInputs)[] = [
    'monthlyRent',
    'monthlyCharges',
    'securityDeposit',
    'rentalInsurance',
    'initialSavings',
    'monthlySavings',
  ];

  for (const field of rentalFields) {
    const rules = rentalValidationRules[field as keyof typeof rentalValidationRules];
    if (rules) {
      const error = validateNumericValue(inputs.rental[field] as number, rules);
      if (error) {
        errors.push({ field: `rental.${field}`, message: error });
      }
    }
  }

  // Validate market inputs
  const marketFields: (keyof MarketInputs)[] = [
    'priceEvolution',
    'rentEvolution',
    'investmentReturn',
    'inflation',
  ];

  for (const field of marketFields) {
    const rules = marketValidationRules[field as keyof typeof marketValidationRules];
    if (rules) {
      const error = validateNumericValue(inputs.market[field] as number, rules);
      if (error) {
        errors.push({ field: `market.${field}`, message: error });
      }
    }
  }

  // Validate resale inputs
  const resaleFields: (keyof ResaleInputs)[] = ['targetYear', 'desiredProfit', 'resaleFeesPercent'];

  for (const field of resaleFields) {
    const rules = resaleValidationRules[field as keyof typeof resaleValidationRules];
    if (rules) {
      const error = validateNumericValue(inputs.resale[field] as number, rules);
      if (error) {
        errors.push({ field: `resale.${field}`, message: error });
      }

      // Check cross-field validations
      if ('getCrossFieldError' in rules && typeof rules.getCrossFieldError === 'function') {
        const crossFieldError = rules.getCrossFieldError(inputs);
        if (crossFieldError) {
          errors.push({ field: `resale.${field}`, message: crossFieldError });
        }
      }
    }
  }

  if (!Number.isInteger(inputs.purchase.loanDuration))
    errors.push({
      field: 'purchase.loanDuration',
      message: buyRentValidationRules.loanDuration.message,
    });
  if (!Number.isInteger(inputs.resale.targetYear))
    errors.push({ field: 'resale.targetYear', message: resaleValidationRules.targetYear.message });
  if (inputs.resale.resaleFeesPercent >= 100)
    errors.push({
      field: 'resale.resaleFeesPercent',
      message: resaleValidationRules.resaleFeesPercent.message,
    });
  return errors;
}

/**
 * Validate Investment inputs
 *
 * @param inputs - Investment input data
 * @returns Array of validation errors
 */
export function validateInvestmentInputs(inputs: InvestmentInputs): ValidationError[] {
  const errors: ValidationError[] = [];

  const check = (field: string, value: number, min: number, max: number): void => {
    if (!Number.isFinite(value) || value < min || value > max)
      errors.push({
        field,
        message: i18n.t('validation.amountRange', { ns: 'realEstate', min, max }),
      });
  };
  check('revenue.monthlyRent', inputs.revenue.monthlyRent, 0, 50000);
  check('revenue.recoverableCharges', inputs.revenue.recoverableCharges, 0, 1000000);
  check('revenue.occupancyRate', inputs.revenue.occupancyRate, 0, 100);
  check('revenue.badDebtRate', inputs.revenue.badDebtRate, 0, 100);
  Object.entries(inputs.expenses).forEach(([field, value]) =>
    check(`expenses.${field}`, value, 0, field === 'marginalTaxRate' ? 60 : 100000000)
  );
  Object.entries(inputs.credit).forEach(([field, value]) =>
    check(`credit.${field}`, value, 0, 100000000)
  );
  check('property.totalPrice', inputs.property.totalPrice, 0.01, 100000000);
  check('property.furnitureValue', inputs.property.furnitureValue, 0, 10000000);

  if (inputs.tax) {
    const { incomeYear, otherHouseholdIncome, otherFurnishedReceipts, otherUnfurnishedRent } =
      inputs.tax;
    const amounts = [otherHouseholdIncome, otherFurnishedReceipts, otherUnfurnishedRent];
    if (
      ![2025, 2026].includes(incomeYear) ||
      amounts.some(value => value !== null && (!Number.isFinite(value) || value < 0))
    ) {
      errors.push({
        field: 'general',
        message: i18n.t('taxContext.invalid', { ns: 'realEstate' }),
      });
    }
  }

  return errors;
}

/**
 * Check if inputs are valid (no errors)
 *
 * @param errors - Array of validation errors
 * @returns True if valid (no errors)
 */
export function isValid(errors: ValidationError[]): boolean {
  return errors.length === 0;
}

/**
 * Get first error message for a field
 *
 * @param errors - Array of validation errors
 * @param field - Field name
 * @returns Error message or null
 */
export function getFieldError(errors: ValidationError[], field: string): string | null {
  const error = errors.find(e => e.field === field);
  return error ? error.message : null;
}

/**
 * Get all error messages for a field
 *
 * @param errors - Array of validation errors
 * @param field - Field name (can include wildcard like 'purchase.*')
 * @returns Array of error messages
 */
export function getFieldErrors(errors: ValidationError[], field: string): string[] {
  if (field.includes('*')) {
    const prefix = field.replace('.*', '');
    return errors.filter(e => e.field.startsWith(prefix)).map(e => e.message);
  }
  return errors.filter(e => e.field === field).map(e => e.message);
}

/**
 * Format validation errors for display
 *
 * @param errors - Array of validation errors
 * @returns Formatted error message
 */
export function formatValidationErrors(errors: ValidationError[]): string {
  if (errors.length === 0) return '';

  if (errors.length === 1) {
    return errors[0].message;
  }

  return `${errors.length} erreurs de validation:\n${errors.map(e => `- ${e.message}`).join('\n')}`;
}

/**
 * Validate a percentage value
 *
 * @param value - Percentage value
 * @param fieldName - Field name for error
 * @returns Error message or null
 */
export function validatePercentage(value: number, fieldName: string): string | null {
  if (typeof value !== 'number' || isNaN(value)) {
    return `${fieldName} doit être un nombre`;
  }
  if (value < 0) {
    return `${fieldName} doit être positif`;
  }
  if (value > 100) {
    return `${fieldName} ne peut pas dépasser 100%`;
  }
  return null;
}

/**
 * Validate a positive amount
 *
 * @param value - Amount value
 * @param fieldName - Field name for error
 * @returns Error message or null
 */
export function validatePositiveAmount(value: number, fieldName: string): string | null {
  if (typeof value !== 'number' || isNaN(value)) {
    return `${fieldName} doit être un nombre`;
  }
  if (value < 0) {
    return `${fieldName} doit être positif`;
  }
  return null;
}

/**
 * Check if value is within range
 *
 * @param value - Value to check
 * @param min - Minimum value
 * @param max - Maximum value
 * @returns True if within range
 */
export function isInRange(value: number, min: number, max: number): boolean {
  return typeof value === 'number' && !isNaN(value) && value >= min && value <= max;
}

/**
 * Validate loan parameters compatibility
 *
 * @param principal - Borrowed amount
 * @param annualRate - Annual interest rate
 * @param years - Loan duration
 * @returns Error message or null
 */
export function validateLoanParameters(
  principal: number,
  annualRate: number,
  years: number
): string | null {
  if (principal <= 0) {
    return 'Le montant emprunté doit être positif';
  }
  if (annualRate < 0) {
    return 'Le taux ne peut pas être négatif';
  }
  if (years <= 0) {
    return 'La durée doit être positive';
  }
  if (years > 40) {
    return 'La durée maximum est de 40 ans';
  }
  return null;
}

/**
 * Validate investment revenue thresholds for regime eligibility
 *
 * @param grossRevenue - Gross annual revenue
 * @returns Object with eligibility info for each regime
 */
export function checkRegimeEligibility(
  grossRevenue: number,
  otherHouseholdIncome: number | null = null,
  incomeYear: 2025 | 2026 = 2026
): {
  microFoncier: { eligible: boolean; limit: number };
  microBic: { eligible: boolean; limit: number };
  lmnp: { isLMP: boolean | null; threshold: number };
} {
  return {
    microFoncier: {
      eligible: grossRevenue <= REGIME_LIMITS.MICRO_FONCIER,
      limit: REGIME_LIMITS.MICRO_FONCIER,
    },
    microBic: {
      eligible: grossRevenue <= REGIME_LIMITS.MICRO_BIC[incomeYear],
      limit: REGIME_LIMITS.MICRO_BIC[incomeYear],
    },
    lmnp: {
      isLMP:
        grossRevenue <= REGIME_LIMITS.LMNP_SOCIAL_THRESHOLD
          ? false
          : otherHouseholdIncome === null
            ? null
            : grossRevenue > otherHouseholdIncome,
      threshold: REGIME_LIMITS.LMNP_SOCIAL_THRESHOLD,
    },
  };
}
