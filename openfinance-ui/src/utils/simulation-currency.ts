import type { BuyRentInputs } from '@/types/realEstateTools';
import { multiply, roundToDecimals } from '@/utils/money';

/** Convert monetary inputs only; rates, years and flags retain their meaning. */
export function convertBuyRentInputs(
  inputs: BuyRentInputs,
  currency: string,
  rate: number
): BuyRentInputs {
  if (!Number.isFinite(rate) || rate <= 0) throw new Error('Invalid exchange rate');
  const purchase = { ...inputs.purchase };
  const moneyFields = [
    'propertyPrice',
    'renovationAmount',
    'agencyFees',
    'downPayment',
    'totalInsurance',
    'applicationFees',
    'guaranteeFees',
    'accountFees',
    'propertyTax',
    'coOwnershipCharges',
    'homeInsurance',
    'bankFees',
    'garbageTax',
  ] as const;
  const convert = (amount: number): number => roundToDecimals(multiply(amount, rate), 2);
  for (const key of moneyFields) purchase[key] = convert(purchase[key]);
  const rental = { ...inputs.rental };
  for (const key of Object.keys(rental) as Array<keyof typeof rental>) {
    rental[key] = convert(rental[key]);
  }
  return {
    ...inputs,
    currency,
    purchase,
    rental,
    resale: { ...inputs.resale, desiredProfit: convert(inputs.resale.desiredProfit) },
  };
}
