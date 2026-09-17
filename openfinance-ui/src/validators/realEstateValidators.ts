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
import { REGIME_LIMITS } from '@/types/realEstateTools';

/**
 * Validation rules for Buy/Rent inputs
 */
export const buyRentValidationRules = {
  propertyPrice: {
    min: 0,
    max: 100000000, // 100M EUR max
    required: true,
    message: 'Le prix du bien doit être positif',
  },
  renovationAmount: {
    min: 0,
    max: 10000000, // 10M EUR max
    required: false,
    message: 'Le montant des travaux doit être positif',
  },
  downPayment: {
    min: 0,
    required: true,
    message: "L'apport personnel doit être positif",
    getCrossFieldError: (inputs: BuyRentInputs) => {
      const totalPrice =
        inputs.purchase.propertyPrice +
        inputs.purchase.renovationAmount +
        (inputs.purchase.propertyPrice * inputs.purchase.notaryFeesPercent) / 100 +
        inputs.purchase.agencyFees;
      if (inputs.purchase.downPayment > totalPrice) {
        return "L'apport ne peut pas dépasser le prix total du bien";
      }
      return null;
    },
  },
  loanDuration: {
    min: 1,
    max: 40,
    required: true,
    message: 'La durée doit être entre 1 et 40 ans',
  },
  interestRate: {
    min: 0,
    max: 100,
    required: true,
    message: 'Le taux doit être entre 0% et 100%',
  },
  notaryFeesPercent: {
    min: 0,
    max: 100,
    required: true,
    message: 'Les frais de notaire doivent être entre 0% et 100%',
  },
  agencyFees: {
    min: 0,
    max: 10000000,
    required: false,
    message: 'Les frais agence doivent être positifs',
  },
  totalInsurance: {
    min: 0,
    max: 1000000,
    required: false,
    message: 'Le montant assurance doit être positif',
  },
  applicationFees: {
    min: 0,
    max: 100000,
    required: false,
    message: 'Les frais de dossier doivent être positifs',
  },
  guaranteeFees: {
    min: 0,
    max: 100000,
    required: false,
    message: 'Les frais de garantie doivent être positifs',
  },
  accountFees: {
    min: 0,
    max: 100000,
    required: false,
    message: 'Les frais de tenue de compte doivent être positifs',
  },
  propertyTax: {
    min: 0,
    max: 1000000,
    required: false,
    message: 'La taxe foncière doit être positive',
  },
  coOwnershipCharges: {
    min: 0,
    max: 1000000,
    required: false,
    message: 'Les charges de copropriété doivent être positives',
  },
  maintenancePercent: {
    min: 0,
    max: 100,
    required: false,
    message: 'Le pourcentage entretien doit être entre 0% et 100%',
  },
  homeInsurance: {
    min: 0,
    max: 100000,
    required: false,
    message: "L'assurance habitation doit être positive",
  },
  bankFees: {
    min: 0,
    max: 100000,
    required: false,
    message: 'Les frais bancaires doivent être positifs',
  },
  garbageTax: {
    min: 0,
    max: 10000,
    required: false,
    message: 'La taxe ordures doit être positive',
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
    message: 'Le loyer mensuel doit être positif',
  },
  monthlyCharges: {
    min: 0,
    max: 10000,
    required: false,
    message: 'Les charges locatives doivent être positives',
  },
  securityDeposit: {
    min: 0,
    max: 50000,
    required: false,
    message: 'Le dépôt de garantie doit être positif',
  },
  rentalInsurance: {
    min: 0,
    max: 10000,
    required: false,
    message: "L'assurance locative doit être positive",
  },
  initialSavings: {
    min: 0,
    max: 100000000,
    required: false,
    message: "L'épargne initiale doit être positive",
  },
  monthlySavings: {
    min: -10000,
    max: 50000,
    required: false,
    message: "L'épargne mensuelle doit être valide",
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
    message: "L'évolution des prix doit être entre -50% et +50%",
  },
  rentEvolution: {
    min: -50,
    max: 50,
    required: false,
    message: "L'évolution des loyers doit être entre -50% et +50%",
  },
  investmentReturn: {
    min: -20,
    max: 50,
    required: false,
    message: 'Le rendement placement doit être entre -20% et +50%',
  },
  inflation: {
    min: -10,
    max: 50,
    required: false,
    message: "L'inflation doit être entre -10% et +50%",
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
    message: "L'année de revente doit être entre 1 et 100",
    getCrossFieldError: (inputs: BuyRentInputs) => {
      if (inputs.resale.targetYear > inputs.purchase.loanDuration) {
        return `L'année de revente ne peut pas dépasser la durée du prêt (${inputs.purchase.loanDuration} ans)`;
      }
      return null;
    },
  },
  desiredProfit: {
    min: -1000000,
    max: 10000000,
    required: false,
    message: 'Le bénéfice souhaité doit être valide',
  },
  resaleFeesPercent: {
    min: 0,
    max: 100,
    required: false,
    message: 'Les frais de revente doivent être entre 0% et 100%',
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

  // Validate revenue inputs
  if (inputs.revenue.monthlyRent < 0) {
    errors.push({ field: 'revenue.monthlyRent', message: 'Le loyer mensuel doit être positif' });
  }

  if (inputs.revenue.monthlyRent > 50000) {
    errors.push({
      field: 'revenue.monthlyRent',
      message: 'Le loyer mensuel maximum est de 50 000€',
    });
  }

  if (inputs.revenue.occupancyRate < 0 || inputs.revenue.occupancyRate > 100) {
    errors.push({
      field: 'revenue.occupancyRate',
      message: 'Le taux occupation doit être entre 0% et 100%',
    });
  }

  if (inputs.revenue.badDebtRate < 0 || inputs.revenue.badDebtRate > 100) {
    errors.push({
      field: 'revenue.badDebtRate',
      message: "Le taux d'impayés doit être entre 0% et 100%",
    });
  }

  // Validate expense inputs
  if (inputs.expenses.marginalTaxRate < 0 || inputs.expenses.marginalTaxRate > 60) {
    errors.push({ field: 'expenses.marginalTaxRate', message: 'La TMI doit être entre 0% et 60%' });
  }

  // Validate property inputs
  if (inputs.property.totalPrice <= 0) {
    errors.push({ field: 'property.totalPrice', message: 'Le prix du bien doit être positif' });
  }

  if (inputs.property.furnitureValue < 0) {
    errors.push({
      field: 'property.furnitureValue',
      message: 'La valeur du mobilier doit être positive',
    });
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
export function checkRegimeEligibility(grossRevenue: number): {
  microFoncier: { eligible: boolean; limit: number };
  microBic: { eligible: boolean; limit: number };
  lmnp: { isLMP: boolean; threshold: number };
} {
  return {
    microFoncier: {
      eligible: grossRevenue <= REGIME_LIMITS.MICRO_FONCIER,
      limit: REGIME_LIMITS.MICRO_FONCIER,
    },
    microBic: {
      eligible: grossRevenue <= REGIME_LIMITS.MICRO_BIC,
      limit: REGIME_LIMITS.MICRO_BIC,
    },
    lmnp: {
      isLMP: grossRevenue > REGIME_LIMITS.LMNP_SOCIAL_THRESHOLD,
      threshold: REGIME_LIMITS.LMNP_SOCIAL_THRESHOLD,
    },
  };
}
