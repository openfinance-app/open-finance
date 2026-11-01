import type { InvestmentMovementRequest, Transaction, TransactionType } from '@/types/transaction';
import { decimalField, ExactDecimal } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';

export function investmentInstruction(transaction?: Transaction): InvestmentMovementRequest | null {
  if (!transaction?.assetId || transaction.investmentQuantity == null) return null;
  const units = new ExactDecimal(decimalField(transaction, 'investmentQuantity'));
  return {
    assetId: transaction.assetId,
    operation: transaction.investmentCash
      ? units.gt(0)
        ? 'BUY'
        : 'SELL'
      : units.gt(0)
        ? 'ADD'
        : 'REMOVE',
    pricing: transaction.investmentPriceSource === 'HISTORICAL_CLOSE' ? 'HISTORICAL' : 'QUANTITY',
    quantity: units.abs().toFixed(),
  };
}

export function investmentOperations(
  type: TransactionType
): InvestmentMovementRequest['operation'][] {
  return type === 'TRANSFER' ? ['REMOVE'] : type === 'INCOME' ? ['ADD', 'SELL'] : ['BUY', 'REMOVE'];
}

export function validInvestmentQuantity(value?: string): boolean {
  if (!value || !isValidDecimalString(value)) return false;
  const quantity = new ExactDecimal(value);
  return quantity.gt(0) && quantity.decimalPlaces() <= 18 && quantity.lt('1e26');
}
