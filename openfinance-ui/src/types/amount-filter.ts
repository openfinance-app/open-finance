export type AmountCurrencyMode = 'BASE' | 'NATIVE';

export interface AmountFilterValue {
  min?: number | string;
  max?: number | string;
  exact?: number | string;
  amountCurrencyMode?: AmountCurrencyMode;
}
