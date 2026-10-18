import { describe, expect, it } from 'vitest';
import { buildTransactionRequest, reconstructInitialSplits } from '@/utils/transaction-request';
import type { Transaction } from '@/types/transaction';

function context(transaction?: Transaction): Parameters<typeof buildTransactionRequest>[0] {
  return {
    transaction,
    data: {
      accountId: 1,
      type: 'EXPENSE',
      amount: '10006858.9112567483',
      currency: 'XOF',
      date: '2026-01-01',
      description: 'Updated notes',
    },
    splits: [],
    splitMode: false,
    tags: [],
    categories: [],
    inputCurrency: 'XOF',
    accountCurrency: 'XOF',
    needsConversion: false,
    needsLiabilityFx: false,
    applyRepaymentSplit: false,
    linkedLiability: false,
  };
}

describe('transaction decimal precision', () => {
  it.each([undefined, 'INTEREST'] as const)(
    'retains the exact FX booking when toggling interest credit from %s',
    movementType => {
      const stored = {
        id: 1,
        accountId: 1,
        type: 'INCOME',
        currency: 'XOF',
        date: '2026-01-01',
        amount: 10006858.91125675,
        exactValues: { amount: '10006858.9112567483' },
        originalAmount: 500,
        originalCurrency: 'EUR',
        conversionRate: 20013.7178,
        movementType,
      } as Transaction;
      const edit = context(stored);
      edit.data.type = 'INCOME';
      edit.data.amount = '500';
      edit.data.currency = 'EUR';
      edit.data.movementType = movementType == null ? 'INTEREST' : undefined;
      edit.inputCurrency = 'EUR';
      edit.needsConversion = true;
      edit.rate = '20013.7178';
      expect(buildTransactionRequest(edit)).toMatchObject({
        amount: '10006858.9112567483',
        originalAmount: 500,
        originalCurrency: 'EUR',
        conversionRate: 20013.7178,
        movementType: edit.data.movementType,
      });
    }
  );

  it('preserves the exact amount on create and metadata edits after JSON parsing', () => {
    const stored = JSON.parse(
      '{"id":1,"accountId":1,"type":"EXPENSE","amount":10006858.9112567483,"exactValues":{"amount":"10006858.9112567483"},"currency":"XOF","date":"2026-01-01"}'
    );
    expect(buildTransactionRequest(context()).amount).toBe('10006858.9112567483');
    expect(buildTransactionRequest(context(stored)).amount).toBe('10006858.9112567483');
  });

  it('detects a deliberate change below JavaScript number precision', () => {
    const stored = {
      ...context().data,
      id: 1,
      amount: 10006858.91125675,
      exactValues: { amount: '10006858.9112567483' },
    } as Transaction;
    const edit = context(stored);
    edit.data.amount = '10006858.9112567484';
    expect(buildTransactionRequest(edit).amount).toBe('10006858.9112567484');
  });

  it('preserves nested split values and their exact sum', () => {
    const stored = {
      ...context().data,
      id: 1,
      hasSplits: true,
      amount: 10006858.91125675,
      exactValues: { amount: '10006858.9112567483' },
      splits: [
        {
          id: 1,
          transactionId: 1,
          amount: 10006858.91125675,
          exactValues: { amount: '10006858.9112567482' },
        },
        { id: 2, transactionId: 1, amount: 1e-10, exactValues: { amount: '0.0000000001' } },
      ],
    } as Transaction;
    const edit = context(stored);
    edit.splits = reconstructInitialSplits(stored);
    edit.splitMode = true;
    const result = buildTransactionRequest(edit);
    expect(result.splits?.map(split => split.amount)).toEqual([
      '10006858.9112567482',
      '0.0000000001',
    ]);
  });

  it('converts using the exact rate and retains the original amount', () => {
    const edit = context();
    edit.needsConversion = true;
    edit.rate = '0.123456789012345678';
    edit.accountCurrency = 'EUR';
    const result = buildTransactionRequest(edit);
    expect(result.originalAmount).toBe('10006858.9112567483');
    expect(result.conversionRate).toBe('0.123456789012345678');
    expect(result.amount).toBe('1235414.67');
  });

  it('preserves exact principal from the repayment preview', () => {
    const edit = context();
    edit.data.liabilityId = 1;
    edit.linkedLiability = true;
    edit.applyRepaymentSplit = true;
    edit.repaymentPreview = {
      principal: 10006858.91125675,
      interest: 0,
      insurance: 0,
      total: 10006858.91125675,
      interestOnly: false,
      exactValues: { principal: '10006858.9112567483' },
    };
    expect(buildTransactionRequest(edit).principalAmount).toBe('10006858.9112567483');
  });
});
