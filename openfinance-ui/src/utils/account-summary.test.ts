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
    ).toBe(120267.76);
  });

  it('does not add an unconverted foreign balance or report a partial total', () => {
    expect(
      accountBalanceTotal(
        [account({ balance: 100 }), account({ currency: 'USD', balance: 50 })],
        'EUR'
      )
    ).toBeNull();
    expect(accountBalanceTotal([], 'EUR')).toBe(0);
  });
});
