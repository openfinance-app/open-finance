import {
  DEFAULT_BUY_RENT_INPUTS,
  DEFAULT_INVESTMENT_INPUTS,
  DEFAULT_RENTAL_TAX_CONTEXT,
} from '@/types/realEstateTools';
import type { BuyRentInputs, InvestmentInputs } from '@/types/realEstateTools';

function matchesShape(value: unknown, template: unknown): boolean {
  if (typeof template === 'number') return typeof value === 'number' && Number.isFinite(value);
  if (template === null)
    return value === null || (typeof value === 'number' && Number.isFinite(value));
  if (typeof template !== 'object') return typeof value === typeof template;
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  return Object.entries(template as Record<string, unknown>).every(([key, child]) =>
    matchesShape((value as Record<string, unknown>)[key], child)
  );
}

export function isBuyRentInputs(value: unknown): value is BuyRentInputs {
  return (
    matchesShape(value, DEFAULT_BUY_RENT_INPUTS) &&
    ((value as BuyRentInputs).currency === undefined ||
      /^[A-Z]{3}$/.test((value as BuyRentInputs).currency ?? ''))
  );
}

export function isInvestmentInputs(value: unknown): value is InvestmentInputs {
  const { tax: _tax, ...required } = DEFAULT_INVESTMENT_INPUTS;
  if (
    !matchesShape(value, {
      ...required,
      credit: { monthlyPayment: 0, annualCost: 0, totalCost: 0, assurance: 0, bankFees: 0 },
    })
  )
    return false;
  const input = value as InvestmentInputs;
  return (
    (input.currency === undefined || input.currency === 'EUR') &&
    ['unfurnished', 'basic', 'standard', 'luxury'].includes(input.property.furnishingType) &&
    (input.tax === undefined || matchesShape(input.tax, DEFAULT_RENTAL_TAX_CONTEXT))
  );
}
