import { describe, expect, it } from 'vitest';
import { accountBalanceTotal } from '@/utils/account-summary';
import type { Account } from '@/types/account';

const account = (values: Partial<Account>): Account =>
  ({
    id: 1,
    name: 'Account',
    type: 'CHECKING',
    balance: 0,
    currency: 'EUR',
    isActive: true,
    ...values,
  }) as Account;

describe('combined account balance', () => {
  it('preserves authoritative cents beyond the JavaScript safe integer range', () => {
    expect(
      accountBalanceTotal(
        [
          account({ balance: 9007199254740992, exactValues: { balance: '9007199254740992.01' } }),
          account({
            currency: 'USD',
            balance: 10,
            baseCurrency: 'EUR',
            isConverted: true,
            balanceInBaseCurrency: 0.02,
            exactValues: { balanceInBaseCurrency: '0.020000000000000001' },
          }),
        ],
        'EUR'
      )
    ).toBe('9007199254740992.030000000000000001');
  });

  it.each<Partial<Account>>([
    { balance: Number.NaN },
    { balance: Number.POSITIVE_INFINITY },
    { currency: 'USD', baseCurrency: 'GBP', isConverted: true, balanceInBaseCurrency: 10 },
    { currency: 'USD', baseCurrency: 'EUR', isConverted: false, balanceInBaseCurrency: 10 },
    { currency: 'USD', baseCurrency: 'EUR', isConverted: true },
  ])(
    'withholds a total when one balance cannot be represented in the requested currency: %j',
    values => {
      expect(accountBalanceTotal([account({ balance: 100 }), account(values)], 'EUR')).toBeNull();
    }
  );

  it('uses a valid exact amount even when its chart number overflows', () => {
    expect(
      accountBalanceTotal(
        [account({ balance: Infinity, exactValues: { balance: '99999999999999999999999999.99' } })],
        'EUR'
      )
    ).toBe('99999999999999999999999999.99');
  });
  it('adds native and converted balances once, with decimal precision', () => {
    expect(
      accountBalanceTotal(
        [
          account({ balance: 88027.49 }),
          account({
            currency: 'XOF',
            balance: 21148332.79,
            baseCurrency: 'EUR',
            balanceInBaseCurrency: 32240.27,
            isConverted: true,
          }),
        ],
        'EUR'
      )
    ).toBe('120267.76');
  });

  it('does not add an unconverted foreign balance or report a partial total', () => {
    expect(
      accountBalanceTotal(
        [account({ balance: 100 }), account({ currency: 'USD', balance: 50 })],
        'EUR'
      )
    ).toBeNull();
    expect(accountBalanceTotal([], 'EUR')).toBe('0');
  });
});
