import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BudgetWizard } from '@/components/budgets/BudgetWizard';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import type {
  BudgetBulkCreateRequest,
  BudgetBulkCreateResponse,
  BudgetSuggestion,
  BudgetSuggestionRequest,
} from '@/types/budget';

const mocks = vi.hoisted(() => ({
  analyze: vi.fn<(request: BudgetSuggestionRequest) => Promise<BudgetSuggestion[]>>(),
  create: vi.fn<(request: BudgetBulkCreateRequest) => Promise<BudgetBulkCreateResponse>>(),
}));

vi.mock('@/hooks/useBudgets', () => ({
  useAnalyzeBudgets: () =>
    useMutation({ mutationFn: (request: BudgetSuggestionRequest) => mocks.analyze(request) }),
  useBulkCreateBudgets: () =>
    useMutation({ mutationFn: (request: BudgetBulkCreateRequest) => mocks.create(request) }),
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: string | number; currency: string }) => (
    <span>{`${amount} ${currency}`}</span>
  ),
}));

const dining: BudgetSuggestion = {
  categoryId: 1,
  categoryName: 'Dining Out',
  suggestedAmount: 45,
  averageSpent: 44.5,
  transactionCount: 5,
  currency: 'EUR',
  period: 'MONTHLY',
  startDate: '2026-03-30',
  endDate: '2026-09-29',
  hasExistingBudget: false,
};
const groceries: BudgetSuggestion = {
  ...dining,
  categoryId: 2,
  categoryName: 'Groceries',
  suggestedAmount: 90,
  hasExistingBudget: true,
};
const created: BudgetBulkCreateResponse = {
  created: [],
  successCount: 1,
  skippedCount: 0,
  errors: [],
};

function ReopenableWizard() {
  const [open, setOpen] = useState(true);
  return (
    <>
      <button onClick={() => setOpen(true)}>Open wizard</button>
      <BudgetWizard open={open} onClose={() => setOpen(false)} />
    </>
  );
}

function deferred<T>(): { promise: Promise<T>; resolve: (result: T) => void } {
  let resolve!: (result: T) => void;
  const promise = new Promise<T>(resolvePromise => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

describe('budget wizard workflow', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-30T12:00:00Z'));
    Element.prototype.scrollIntoView = vi.fn();
    mocks.analyze.mockResolvedValue([dining, groceries]);
    mocks.create.mockResolvedValue(created);
  });

  afterEach(() => vi.useRealTimers());

  it('uses the selected period and lookback, and lets an empty analysis be retried', async () => {
    const user = userEvent.setup();
    mocks.analyze.mockResolvedValueOnce([]);
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);

    const [period, lookback] = screen.getAllByRole('combobox');
    await user.selectOptions(period, 'WEEKLY');
    await user.selectOptions(lookback, '12');
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByText('No spending data found in the last 12 months.')).toBeVisible();
    expect(mocks.analyze).toHaveBeenLastCalledWith({ period: 'WEEKLY', lookbackMonths: 12 });
    expect(screen.queryByRole('button', { name: /Create \d/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getAllByRole('combobox')[0]).toHaveValue('WEEKLY');
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByRole('button', { name: 'Create 1 Budget' })).toBeEnabled();
    expect(mocks.analyze).toHaveBeenCalledTimes(2);
  });

  it('excludes existing budgets by default and supports selecting individual suggestions', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByText('Budget exists')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Create 1 Budget' })).toBeEnabled();

    await user.click(screen.getByRole('button', { name: 'Deselect all' }));
    expect(screen.getByRole('button', { name: 'Create 0 Budgets' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Select all' }));
    expect(screen.getByRole('button', { name: 'Create 2 Budgets' })).toBeEnabled();
    await user.click(screen.getByText('Groceries', { exact: true }));
    await user.click(screen.getByText('Dining Out', { exact: true }));
    expect(screen.getByRole('button', { name: 'Create 0 Budgets' })).toBeDisabled();
    await user.click(screen.getByText('Dining Out', { exact: true }));

    const amount = screen.getByRole('textbox', { name: 'Budget amount for Dining Out' });
    await user.clear(amount);
    await user.type(amount, '17.25');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    await screen.findByRole('button', { name: 'Done' });
    expect(mocks.create).toHaveBeenCalledExactlyOnceWith({
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

  it('preserves the exact suggested amount when the numeric API value is rounded', async () => {
    const user = userEvent.setup();
    mocks.analyze.mockResolvedValue([
      {
        ...dining,
        suggestedAmount: 9007199254740992,
        exactValues: { suggestedAmount: '9007199254740993' },
      },
    ]);
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(
      await screen.findByRole('textbox', { name: 'Budget amount for Dining Out' })
    ).toHaveValue('9,007,199,254,740,993');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    await screen.findByRole('button', { name: 'Done' });
    expect(mocks.create.mock.calls[0][0].budgets[0].amount).toBe('9007199254740993');
  });

  it('blocks an invalid visible draft instead of submitting its last valid amount', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    const amount = await screen.findByRole('textbox', { name: 'Budget amount for Dining Out' });
    await user.type(amount, '-');
    expect(amount).toHaveValue('45-');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    expect(mocks.create).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeVisible();

    await user.clear(amount);
    await user.type(amount, '12.75');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    await screen.findByRole('button', { name: 'Done' });
    expect(mocks.create.mock.calls[0][0].budgets[0].amount).toBe('12.75');
  });

  it('does not let a deselected invalid amount block valid selected budgets', async () => {
    const user = userEvent.setup();
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    await screen.findByRole('button', { name: 'Create 1 Budget' });
    await user.click(screen.getByText('Groceries', { exact: true }));
    await user.type(screen.getByRole('textbox', { name: 'Budget amount for Groceries' }), '-');
    await user.click(screen.getByText('Groceries', { exact: true }));
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    await screen.findByRole('button', { name: 'Done' });
    expect(mocks.create.mock.calls[0][0].budgets.map(budget => budget.categoryId)).toEqual([1]);
  });

  it('shows analysis failures and allows retrying without leaving configuration', async () => {
    const user = userEvent.setup();
    mocks.analyze.mockRejectedValueOnce(new Error('Analysis unavailable'));
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByText('Failed to analyse spending. Please try again.')).toBeVisible();
    expect(mocks.create).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByRole('button', { name: 'Create 1 Budget' })).toBeEnabled();
  });

  it('keeps suggestions after a failed save and displays the retry result accurately', async () => {
    const user = userEvent.setup();
    const result: BudgetBulkCreateResponse = {
      successCount: 1,
      skippedCount: 1,
      errors: ['Travel could not be created'],
      created: [
        {
          id: 10,
          categoryId: 1,
          categoryName: 'Dining Out',
          amount: 9007199254740992,
          exactValues: { amount: '9007199254740993' },
          currency: 'EUR',
          period: 'MONTHLY',
          startDate: '2026-09-30',
          endDate: '2026-10-29',
          rollover: false,
          createdAt: '2026-09-30T12:00:00',
          updatedAt: '2026-09-30T12:00:00',
        },
      ],
    };
    mocks.create.mockRejectedValueOnce(new Error('Save unavailable')).mockResolvedValueOnce(result);
    renderWithProviders(<BudgetWizard open onClose={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    await user.click(await screen.findByRole('button', { name: 'Create 1 Budget' }));
    expect(await screen.findByText('Failed to create budgets. Please try again.')).toBeVisible();
    expect(screen.getByRole('textbox', { name: 'Budget amount for Dining Out' })).toHaveValue('45');
    await user.click(screen.getByRole('button', { name: 'Create 1 Budget' }));
    await screen.findByRole('button', { name: 'Done' });
    expect(screen.getByText(/1 budget was skipped/)).toBeVisible();
    expect(screen.getByText(/Travel could not be created/)).toBeVisible();
    expect(screen.getByText('9007199254740993 EUR')).toBeVisible();
    expect(mocks.create).toHaveBeenCalledTimes(2);
  });

  it('resets configuration and results after completing and reopening', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ReopenableWizard />);
    await user.selectOptions(screen.getAllByRole('combobox')[0], 'YEARLY');
    await user.selectOptions(screen.getAllByRole('combobox')[1], '24');
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    await user.click(await screen.findByRole('button', { name: 'Create 1 Budget' }));
    await user.click(await screen.findByRole('button', { name: 'Done' }));
    await user.click(screen.getByRole('button', { name: 'Open wizard' }));
    expect(screen.getByText('Step 1 of 3')).toBeVisible();
    expect(screen.getAllByRole('combobox')[0]).toHaveValue('MONTHLY');
    expect(screen.getAllByRole('combobox')[1]).toHaveValue('6');
    expect(screen.queryByText('Dining Out', { exact: true })).not.toBeInTheDocument();
  });

  it('ignores an analysis that finishes after the wizard was cancelled and reopened', async () => {
    const user = userEvent.setup();
    const pending = deferred<BudgetSuggestion[]>();
    mocks.analyze.mockReturnValueOnce(pending.promise);
    renderWithProviders(<ReopenableWizard />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    expect(await screen.findByRole('button', { name: 'Analysing…' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await user.click(screen.getByRole('button', { name: 'Open wizard' }));

    await act(async () => pending.resolve([dining]));
    expect(screen.getByText('Step 1 of 3')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Analyse Spending' })).toBeEnabled();
    expect(mocks.create).not.toHaveBeenCalled();
  });

  it('ignores an earlier save result after cancellation without issuing a duplicate save', async () => {
    const user = userEvent.setup();
    const pending = deferred<BudgetBulkCreateResponse>();
    mocks.create.mockReturnValueOnce(pending.promise);
    renderWithProviders(<ReopenableWizard />);
    await user.click(screen.getByRole('button', { name: 'Analyse Spending' }));
    await user.click(await screen.findByRole('button', { name: 'Create 1 Budget' }));
    expect(await screen.findByRole('button', { name: 'Creating…' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await user.click(screen.getByRole('button', { name: 'Open wizard' }));

    await act(async () => pending.resolve(created));
    await waitFor(() => expect(screen.getByText('Step 1 of 3')).toBeVisible());
    expect(mocks.create).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: 'Done' })).not.toBeInTheDocument();
  });
});
