import type { InvestmentMovementRequest, Transaction, TransactionType } from '@/types/transaction';
import { investmentInstruction, investmentOperations } from '@/utils/investment-movement';

export type LinkedOperation =
  | 'NONE'
  | InvestmentMovementRequest['operation']
  | 'LOAN_PAYMENT'
  | 'MAINTENANCE'
  | 'CAPITAL_IMPROVEMENT'
  | 'INTEREST'
  | 'EXISTING';

export function isInvestmentOperation(
  operation: LinkedOperation
): operation is InvestmentMovementRequest['operation'] {
  return ['ADD', 'REMOVE', 'BUY', 'SELL'].includes(operation);
}

export function initialLinkedOperation(transaction?: Transaction): LinkedOperation {
  if (!transaction) return 'NONE';
  const investment = investmentInstruction(transaction);
  if (investment) return investment.operation;
  if (transaction.liabilityId) {
    return transaction.type === 'EXPENSE' ? 'LOAN_PAYMENT' : 'EXISTING';
  }
  if (
    transaction.movementType === 'MAINTENANCE' ||
    transaction.movementType === 'CAPITAL_IMPROVEMENT'
  )
    return transaction.movementType;
  if (transaction.type === 'INCOME' && transaction.movementType === 'INTEREST') return 'INTEREST';
  return transaction.assetId || transaction.realEstateId || transaction.movementType
    ? 'EXISTING'
    : 'NONE';
}

export function linkedOperations(
  type: TransactionType,
  allowLoan: boolean,
  allowInterest: boolean
): LinkedOperation[] {
  const choices: LinkedOperation[] = ['NONE', ...investmentOperations(type)];
  if (type === 'EXPENSE') {
    if (allowLoan) choices.push('LOAN_PAYMENT');
    choices.push('MAINTENANCE', 'CAPITAL_IMPROVEMENT');
  }
  if (type === 'INCOME' && allowInterest) choices.push('INTEREST');
  return choices;
}

export function linkedOperationLabel(operation: LinkedOperation, type: TransactionType): string {
  if (type === 'TRANSFER' && operation === 'NONE') return 'linkedOperation.cashFunding';
  if (type === 'TRANSFER' && operation === 'REMOVE') return 'linkedOperation.investmentFunding';
  return `linkedOperation.actions.${operation}`;
}

/** Describe a booked operation without treating loan costs as principal repayments. */
export function linkedOperationSummary(
  transaction: Transaction
): { labelKey: string; effectKey: string } | null {
  const investment = investmentInstruction(transaction);
  if (transaction.transferId || transaction.type === 'TRANSFER') {
    return {
      labelKey: `linkedOperation.summary.${investment ? 'HOLDING_TRANSFER' : 'TRANSFER'}`,
      effectKey: `linkedOperation.effects.${investment ? 'holdingTransfer' : 'cashTransfer'}`,
    };
  }
  if (investment) {
    return {
      labelKey: `linkedOperation.summary.${investment.operation}`,
      effectKey: `linkedOperation.effects.${investment.operation}`,
    };
  }
  const movement = transaction.movementType;
  if (
    transaction.liabilityId &&
    (movement === 'REPAYMENT' || (!movement && transaction.type === 'EXPENSE'))
  ) {
    return {
      labelKey: 'linkedOperation.actions.LOAN_PAYMENT',
      effectKey: 'linkedOperation.effects.LOAN_PAYMENT',
    };
  }
  if (movement === 'MAINTENANCE' || movement === 'CAPITAL_IMPROVEMENT') {
    return {
      labelKey: `linkedOperation.actions.${movement}`,
      effectKey: `linkedOperation.effects.${movement}`,
    };
  }
  if (
    movement === 'INTEREST' &&
    transaction.type === 'INCOME' &&
    !transaction.liabilityId &&
    !transaction.assetId &&
    !transaction.realEstateId
  ) {
    return {
      labelKey: 'linkedOperation.actions.INTEREST',
      effectKey: 'linkedOperation.effects.INTEREST',
    };
  }
  if (movement) {
    return {
      labelKey: `linkedOperation.summary.${movement}`,
      effectKey: 'linkedOperation.details.recordedLink',
    };
  }
  return transaction.assetId || transaction.realEstateId || transaction.liabilityId
    ? {
        labelKey: 'linkedOperation.summary.EXISTING',
        effectKey: 'linkedOperation.details.recordedLink',
      }
    : null;
}
