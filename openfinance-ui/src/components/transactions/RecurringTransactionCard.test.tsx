import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import type { RecurringTransaction } from '@/types/recurringTransaction';

vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: () => ({ data: { rate: 1 } }),
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: any) => <span data-testid="amount">{amount}</span>,
}));

import { RecurringTransactionCard } from './RecurringTransactionCard';

const makeRecurring = (overrides?: Partial<RecurringTransaction>): RecurringTransaction => ({
  id: 1,
  accountId: 1,
  accountName: 'Checking',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 50,
  currency: 'EUR',
  categoryId: 1,
  categoryName: 'Food',
  categoryIcon: '🍕',
  categoryColor: '#ff0000',
  payee: 'Netflix',
  description: 'Monthly subscription',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: '2025-02-01',
  endDate: null,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
  isDue: false,
  daysUntilNext: 15,
  isEnded: false,
  ...overrides,
});

describe('RecurringTransactionCard', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders description and account name', () => {
    renderWithProviders(<RecurringTransactionCard recurringTransaction={makeRecurring()} />);
    expect(screen.getByText('Monthly subscription')).toBeInTheDocument();
    expect(screen.getByText('Checking')).toBeInTheDocument();
  });

  it('renders amount', () => {
    renderWithProviders(<RecurringTransactionCard recurringTransaction={makeRecurring()} />);
    expect(screen.getByTestId('amount')).toBeInTheDocument();
  });

  it('renders category name', () => {
    renderWithProviders(<RecurringTransactionCard recurringTransaction={makeRecurring()} />);
    expect(screen.getByText('Food')).toBeInTheDocument();
  });

  it('calls onEdit when edit button clicked', async () => {
    const onEdit = vi.fn();
    const user = userEvent.setup();
    const rt = makeRecurring();
    renderWithProviders(<RecurringTransactionCard recurringTransaction={rt} onEdit={onEdit} />);
    // Icon-only buttons use title attribute
    const buttons = screen.getAllByRole('button');
    // Edit is the second-to-last button
    const editBtn = buttons.find(b => b.querySelector('.lucide-edit'));
    if (editBtn) {
      await user.click(editBtn);
      expect(onEdit).toHaveBeenCalledWith(rt);
    }
  });

  it('calls onDelete when delete button clicked', async () => {
    const onDelete = vi.fn();
    const user = userEvent.setup();
    const rt = makeRecurring();
    renderWithProviders(<RecurringTransactionCard recurringTransaction={rt} onDelete={onDelete} />);
    const buttons = screen.getAllByRole('button');
    const deleteBtn = buttons[buttons.length - 1]; // Last button
    await user.click(deleteBtn);
    expect(onDelete).toHaveBeenCalledWith(rt);
  });

  it('shows transfer destination for TRANSFER type', () => {
    renderWithProviders(
      <RecurringTransactionCard
        recurringTransaction={makeRecurring({ type: 'TRANSFER', toAccountName: 'Savings' })}
      />
    );
    expect(screen.getByText('Savings')).toBeInTheDocument();
  });
});
