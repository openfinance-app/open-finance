import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { BudgetWizard } from '@/components/budgets/BudgetWizard';

const mocks = vi.hoisted(() => ({ analyze: vi.fn(), create: vi.fn(), reset: vi.fn() }));
vi.mock('@/hooks/useBudgets', () => ({
  useAnalyzeBudgets: () => ({ mutateAsync: mocks.analyze, reset: mocks.reset, isPending: false }),
  useBulkCreateBudgets: () => ({ mutateAsync: mocks.create, reset: mocks.reset, isPending: false }),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number }) => <span>{amount}</span>,
}));

describe('budget wizard edited amounts', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-30T12:00:00Z'));
    Element.prototype.scrollIntoView = vi.fn();
    mocks.analyze.mockResolvedValue([
      {
        categoryId: 1,
        categoryName: 'Dining Out',
        suggestedAmount: 45,
        averageSpent: 45,
        transactionCount: 1,
        currency: 'EUR',
        period: 'MONTHLY',
        hasExistingBudget: false,
      },
    ]);
    mocks.create.mockResolvedValue({ created: [], successCount: 1, skippedCount: 0, errors: [] });
  });
  afterEach(() => vi.useRealTimers());

  it.each(['0', ''])('rejects %j instead of creating the suggested amount', async amount => {
    const user = userEvent.setup();
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    const input = await screen.findByRole('textbox', { name: 'Budget amount for Dining Out' });
    await user.clear(input);
    if (amount) await user.type(input, amount);
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/greater than 0|positive/i);
    expect(mocks.create).not.toHaveBeenCalled();
    await user.clear(input);
    await user.type(input, '17.25');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    expect(mocks.create).toHaveBeenCalledWith({
      budgets: [
        {
          categoryId: 1,
          amount: '17.25',
          currency: 'EUR',
          period: 'MONTHLY',
          startDate: '2026-09-30',
          endDate: '2026-10-29',
          rollover: false,
        },
      ],
    });
  });
});
