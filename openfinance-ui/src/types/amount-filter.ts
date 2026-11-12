export type AmountCurrencyMode = 'BASE' | 'NATIVE';

export interface AmountFilterValue {
  min?: number;
  max?: number;
  exact?: number;
  amountCurrencyMode?: AmountCurrencyMode;
}
