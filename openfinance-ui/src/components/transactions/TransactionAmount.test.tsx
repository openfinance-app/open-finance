import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { TransactionAmount } from '@/components/transactions/TransactionAmount';
import type { Transaction } from '@/types/transaction';

const state = vi.hoisted(() => ({ secondaryCurrency: 'XOF', convert: vi.fn() }));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: 'native', secondaryCurrency: state.secondaryCurrency }),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: (...args: unknown[]) => {
    state.convert(...args);
    return {
      secondaryCurrency: state.secondaryCurrency,
      secondaryExchangeRate: 0.8,
      convert: (amount: number) => amount * 0.8,
    };
  },
}));
vi.mock('@/components/ui/Tooltip', () => ({
  TooltipProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  Tooltip: ({ children }: { children: ReactNode }) => <>{children}</>,
  TooltipTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
  TooltipContent: ({ children }: { children: ReactNode }) => <div role="tooltip">{children}</div>,
}));

describe('recorded transaction currency amounts', () => {
  const debit = {
    id: 1,
    type: 'EXPENSE',
    amount: 500,
    currency: 'EUR',
    date: '2026-10-05',
    transferId: 'imported',
    transferAmount: 327978.4947019899,
    transferCurrency: 'XOF',
    amountInBaseCurrency: 500,
    baseCurrency: 'EUR',
    isConverted: false,
  } as Transaction;

  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    state.secondaryCurrency = 'XOF';
  });

  it('uses the paired receiving amount without requesting a market estimate', () => {
    renderWithProviders(<TransactionAmount transaction={debit} />);
    const tooltip = screen.getByRole('tooltip');
    expect(tooltip).toHaveTextContent(/327[\s,.]?978/);
    expect(tooltip).toHaveTextContent(/655[,.]957/);
    expect(state.convert).not.toHaveBeenCalled();
  });

  it('retains the original amount when it is the selected secondary currency', () => {
    state.secondaryCurrency = 'EUR';
    renderWithProviders(
      <TransactionAmount
        transaction={{
          ...debit,
          amount: 327978.4947019899,
          currency: 'XOF',
          transferAmount: undefined,
          transferCurrency: undefined,
          originalAmount: 500,
          originalCurrency: 'EUR',
        }}
      />
    );
    expect(screen.getByRole('tooltip')).toHaveTextContent(/500/);
    expect(state.convert).not.toHaveBeenCalled();
  });

  it('uses a dated market estimate for a third currency', () => {
    state.secondaryCurrency = 'GBP';
    renderWithProviders(<TransactionAmount transaction={debit} />);
    expect(screen.getByRole('tooltip')).toHaveTextContent(/400/);
    expect(state.convert).toHaveBeenCalledWith('EUR', '2026-10-05');
  });
});
