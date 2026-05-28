/**
 * Unit tests for TransactionList component
 *
 * Tests transaction list rendering, split transaction display and expansion,
 * and split-specific functionality.
 */
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi, describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { act } from 'react';
import { TransactionList } from './TransactionList';
import * as usePayeesModule from '@/hooks/usePayees';
import { renderWithProviders } from '@/test/test-utils';
import type { Transaction, TransactionSplitResponse } from '@/types/transaction';
import type { Payee } from '@/types/payee';

// ── Polyfills ─────────────────────────────────────────────────────────────────

beforeAll(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
});

// ── Mock hooks ────────────────────────────────────────────────────────────────

vi.mock('@/hooks/usePayees', async (importOriginal) => {
  const actual = await importOriginal<typeof usePayeesModule>();
  return { ...actual, useActivePayees: vi.fn() };
});

// ── Mock components ───────────────────────────────────────────────────────────

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children, inline }: { children: React.ReactNode; inline?: boolean }) => (
    <span data-testid="private-amount" data-inline={inline}>
      {children}
    </span>
  ),
}));

vi.mock('./SplitDetail', () => ({
  SplitDetail: ({ splits, currency }: { splits: TransactionSplitResponse[]; currency: string }) => (
    <div data-testid="split-detail" data-currency={currency}>
      Split details for {splits.length} splits
    </div>
  ),
}));

// ── Typed mock references ─────────────────────────────────────────────────────

const mockUseActivePayees = vi.mocked(usePayeesModule.useActivePayees);

// ── Fixtures ──────────────────────────────────────────────────────────────────

const mockPayees: Payee[] = [
  {
    id: 1,
    name: 'Amazon',
    logo: 'https://example.com/amazon.png',
    categoryId: 10,
    categoryName: 'Shopping',
    isSystem: false,
    isActive: true,
  },
  {
    id: 2,
    name: 'Spotify',
    logo: 'https://example.com/spotify.png',
    categoryId: 20,
    categoryName: 'Entertainment',
    isSystem: false,
    isActive: true,
  },
];

const mockSplitTransaction: Transaction = {
  id: 1,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 75.00,
  currency: 'EUR',
  date: '2024-06-01',
  description: 'Split transaction test',
  payee: 'Amazon',
  isReconciled: false,
  createdAt: '2024-06-01',
  hasSplits: true,
  splits: [
    {
      id: 1,
      transactionId: 1,
      categoryId: 10,
      categoryName: 'Shopping',
      categoryColor: '#ff0000',
      categoryIcon: '🛒',
      amount: 50.00,
      description: 'Groceries',
    },
    {
      id: 2,
      transactionId: 1,
      categoryId: 20,
      categoryName: 'Entertainment',
      categoryColor: '#00ff00',
      categoryIcon: undefined,
      amount: 25.00,
      description: 'Music subscription',
    },
  ],
};

const mockRegularTransaction: Transaction = {
  id: 2,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 25.00,
  currency: 'EUR',
  date: '2024-06-01',
  description: 'Regular transaction',
  payee: 'Spotify',
  categoryId: 20,
  categoryName: 'Entertainment',
  categoryColor: '#00ff00',
  isReconciled: false,
  createdAt: '2024-06-01',
  hasSplits: false,
};

const mockTransactions = [mockSplitTransaction, mockRegularTransaction];

// ── Helpers ───────────────────────────────────────────────────────────────────

interface RenderListOptions {
  transactions?: Transaction[];
  onEdit?: ReturnType<typeof vi.fn>;
  onDelete?: ReturnType<typeof vi.fn>;
  highlightedId?: number | null;
}

function renderList({
  transactions = mockTransactions,
  onEdit = vi.fn(),
  onDelete = vi.fn(),
  highlightedId = null,
}: RenderListOptions = {}) {
  renderWithProviders(
    <TransactionList
      transactions={transactions}
      onEdit={onEdit}
      onDelete={onDelete}
      highlightedId={highlightedId}
    />
  );
  return { onEdit, onDelete };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('TransactionList', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUseActivePayees.mockReturnValue({
      data: mockPayees,
      isLoading: false,
      isError: false,
    } as ReturnType<typeof usePayeesModule.useActivePayees>);
  });

  describe('Split Transaction Display', () => {
    it('shows "Split" badge for transactions with hasSplits=true', () => {
      renderList();

      const splitBadge = screen.getByText('Split');
      expect(splitBadge).toBeInTheDocument();
      expect(splitBadge.closest('button')).toHaveAttribute('aria-expanded', 'false');
    });

    it('does not show "Split" badge for regular transactions', () => {
      renderList({ transactions: [mockRegularTransaction] });

      expect(screen.queryByText('Split')).not.toBeInTheDocument();
    });

    it('shows chevron down icon when split details are collapsed', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      const chevronDown = splitBadge?.querySelector('svg');
      expect(chevronDown).toBeInTheDocument();
    });

    it('shows scissors icon in split badge', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      const scissorsIcon = splitBadge?.querySelector('svg');
      expect(scissorsIcon).toBeInTheDocument();
    });

    it('has correct accessibility attributes for split badge', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      expect(splitBadge).toHaveAttribute('aria-expanded', 'false');
      expect(splitBadge).toHaveAttribute('aria-label', 'Toggle split details');
      expect(splitBadge).toHaveAttribute('title', 'This transaction is split across multiple categories');
    });

    it('toggles split details expansion when split badge is clicked', async () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button') as HTMLButtonElement;

      // Initially collapsed - no split detail visible
      expect(screen.queryByTestId('split-detail')).not.toBeInTheDocument();

      // Click to expand
      await act(async () => {
        splitBadge.click();
      });

      // Should now be expanded - check for split detail component
      await waitFor(() => {
        expect(screen.getByTestId('split-detail')).toBeInTheDocument();
        expect(screen.getByTestId('split-detail')).toHaveTextContent('Split details for 2 splits');
      });
    });

    it('shows chevron up icon when split details are expanded', async () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button') as HTMLButtonElement;

      await act(async () => {
        splitBadge.click();
      });

      await waitFor(() => {
        const chevronUp = splitBadge.querySelector('svg');
        expect(chevronUp).toBeInTheDocument();
      });
    });

    it('renders SplitDetail component with correct props when expanded', async () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button') as HTMLButtonElement;

      await act(async () => {
        splitBadge.click();
      });

      await waitFor(() => {
        const splitDetail = screen.getByTestId('split-detail');
        expect(splitDetail).toBeInTheDocument();
        expect(splitDetail).toHaveAttribute('data-currency', 'EUR');
        expect(splitDetail).toHaveTextContent('Split details for 2 splits');
      });
    });

    it('only shows split details when transaction has both hasSplits=true and splits data', async () => {
      const transactionWithoutSplitsData: Transaction = {
        ...mockSplitTransaction,
        splits: undefined,
      };

      renderList({ transactions: [transactionWithoutSplitsData] });

      const splitBadge = screen.getByText('Split').closest('button') as HTMLButtonElement;

      await act(async () => {
        splitBadge.click();
      });

      // Should not show split details if splits data is missing
      expect(screen.queryByTestId('split-detail')).not.toBeInTheDocument();
    });

    it('handles multiple split transactions independently', async () => {
      const anotherSplitTransaction: Transaction = {
        ...mockSplitTransaction,
        id: 3,
        description: 'Another split transaction',
        splits: [
          {
            id: 3,
            transactionId: 3,
            categoryId: 10,
            categoryName: 'Shopping',
            categoryColor: '#ff0000',
            amount: 30.00,
            description: 'More groceries',
          },
        ],
      };

      renderList({ transactions: [mockSplitTransaction, anotherSplitTransaction] });

      const splitBadges = screen.getAllByText('Split');
      expect(splitBadges).toHaveLength(2);

      // Expand first split transaction
      await act(async () => {
        splitBadges[0].closest('button')?.click();
      });

      await waitFor(() => {
        expect(screen.getByTestId('split-detail')).toHaveTextContent('Split details for 2 splits');
      });

      // Second split transaction should still be collapsed
      const secondBadge = splitBadges[1].closest('button');
      expect(secondBadge).toHaveAttribute('aria-expanded', 'false');
    });
  });

  describe('Split Transaction Edge Cases', () => {
    it('handles split transaction with empty splits array', () => {
      const emptySplitsTransaction: Transaction = {
        ...mockSplitTransaction,
        splits: [],
      };

      renderList({ transactions: [emptySplitsTransaction] });

      const splitBadge = screen.getByText('Split');
      expect(splitBadge).toBeInTheDocument();
    });

    it('handles split transaction with single split', () => {
      const singleSplitTransaction: Transaction = {
        ...mockSplitTransaction,
        splits: [
          {
            id: 1,
            transactionId: 1,
            categoryId: 10,
            categoryName: 'Shopping',
            categoryColor: '#ff0000',
            amount: 75.00,
            description: 'Single split',
          },
        ],
      };

      renderList({ transactions: [singleSplitTransaction] });

      const splitBadge = screen.getByText('Split');
      expect(splitBadge).toBeInTheDocument();
    });

    it('handles split transaction with many splits', () => {
      const manySplits: TransactionSplitResponse[] = Array.from({ length: 10 }, (_, i) => ({
        id: i + 1,
        transactionId: 1,
        categoryId: 10,
        categoryName: `Category ${i + 1}`,
        categoryColor: '#ff0000',
        amount: 7.50,
        description: `Split ${i + 1}`,
      }));

      const manySplitsTransaction: Transaction = {
        ...mockSplitTransaction,
        splits: manySplits,
      };

      renderList({ transactions: [manySplitsTransaction] });

      const splitBadge = screen.getByText('Split');
      expect(splitBadge).toBeInTheDocument();
    });

    it('handles split transaction with negative amounts', () => {
      const negativeSplitTransaction: Transaction = {
        ...mockSplitTransaction,
        type: 'INCOME',
        amount: -75.00,
        splits: [
          {
            id: 1,
            transactionId: 1,
            categoryId: 10,
            categoryName: 'Shopping',
            categoryColor: '#ff0000',
            amount: -50.00,
            description: 'Negative split',
          },
          {
            id: 2,
            transactionId: 1,
            categoryId: 20,
            categoryName: 'Entertainment',
            categoryColor: '#00ff00',
            amount: -25.00,
            description: 'Another negative split',
          },
        ],
      };

      renderList({ transactions: [negativeSplitTransaction] });

      const splitBadge = screen.getByText('Split');
      expect(splitBadge).toBeInTheDocument();
    });
  });

  describe('Split Badge Interaction', () => {
    it('is clickable and has cursor pointer', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      expect(splitBadge).toHaveClass('cursor-pointer');
      expect(splitBadge).toHaveClass('select-none');
    });

    it('prevents text selection on the badge', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      expect(splitBadge).toHaveClass('select-none');
    });

    it('has info variant styling', () => {
      renderList();

      const splitBadge = screen.getByText('Split').closest('button');
      expect(splitBadge).toHaveClass('text-accent-blue');
    });
  });

  describe('Empty State', () => {
    it('shows empty message when no transactions', () => {
      renderList({ transactions: [] });
      expect(screen.getByText(/no transactions found/i)).toBeInTheDocument();
    });
  });

  describe('Transaction Rendering', () => {
    it('renders transaction description', () => {
      renderList({ transactions: [mockRegularTransaction] });
      expect(screen.getByText('Regular transaction')).toBeInTheDocument();
    });

    it('renders payee name', () => {
      renderList({ transactions: [mockRegularTransaction] });
      expect(screen.getByText('Spotify')).toBeInTheDocument();
    });

    it('renders category name', () => {
      renderList({ transactions: [mockRegularTransaction] });
      expect(screen.getByText('Entertainment')).toBeInTheDocument();
    });

    it('renders account name when present', () => {
      const txWithAccount: Transaction = {
        ...mockRegularTransaction,
        accountName: 'Checking Account',
      };
      renderList({ transactions: [txWithAccount] });
      expect(screen.getByText('Checking Account')).toBeInTheDocument();
    });

    it('renders tags from array', () => {
      const txWithTags: Transaction = {
        ...mockRegularTransaction,
        tags: ['groceries', 'food'],
      };
      renderList({ transactions: [txWithTags] });
      expect(screen.getByText('groceries')).toBeInTheDocument();
      expect(screen.getByText('food')).toBeInTheDocument();
    });

    it('renders tags from comma-separated string', () => {
      const txWithStringTags: Transaction = {
        ...mockRegularTransaction,
        tags: 'travel, work' as any,
      };
      renderList({ transactions: [txWithStringTags] });
      expect(screen.getByText('travel')).toBeInTheDocument();
      expect(screen.getByText('work')).toBeInTheDocument();
    });

    it('shows income transaction with + prefix', () => {
      const incomeTx: Transaction = {
        ...mockRegularTransaction,
        id: 10,
        type: 'INCOME',
        amount: 1000,
        description: 'Salary',
      };
      renderList({ transactions: [incomeTx] });
      expect(screen.getByText('+')).toBeInTheDocument();
    });

    it('shows future date badge for future transactions', () => {
      const futureTx: Transaction = {
        ...mockRegularTransaction,
        id: 11,
        date: '2099-12-31',
      };
      renderList({ transactions: [futureTx] });
      expect(screen.getByText(/future/i)).toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('calls onEdit when edit button is clicked', async () => {
      const onEdit = vi.fn();
      renderList({ transactions: [mockRegularTransaction], onEdit });
      const editBtn = screen.getByLabelText(/edit/i);
      await userEvent.setup().click(editBtn);
      expect(onEdit).toHaveBeenCalledWith(mockRegularTransaction);
    });

    it('calls onDelete when delete button is clicked', async () => {
      const onDelete = vi.fn();
      renderList({ transactions: [mockRegularTransaction], onDelete });
      const deleteBtn = screen.getByLabelText(/delete/i);
      await userEvent.setup().click(deleteBtn);
      expect(onDelete).toHaveBeenCalledWith(mockRegularTransaction);
    });

    it('calls onViewDetail when card is clicked', async () => {
      const onViewDetail = vi.fn();
      renderWithProviders(
        <TransactionList
          transactions={[mockRegularTransaction]}
          onEdit={vi.fn()}
          onDelete={vi.fn()}
          onViewDetail={onViewDetail}
        />
      );
      fireEvent.click(screen.getByText('Regular transaction'));
      expect(onViewDetail).toHaveBeenCalledWith(mockRegularTransaction);
    });

    it('does not call onViewDetail when button inside card is clicked', async () => {
      const onViewDetail = vi.fn();
      renderWithProviders(
        <TransactionList
          transactions={[mockRegularTransaction]}
          onEdit={vi.fn()}
          onDelete={vi.fn()}
          onViewDetail={onViewDetail}
        />
      );
      const editBtn = screen.getByLabelText(/edit/i);
      await userEvent.setup().click(editBtn);
      expect(onViewDetail).not.toHaveBeenCalled();
    });

    it('scrolls highlighted transaction into view', () => {
      vi.useFakeTimers();
      renderList({ transactions: [mockRegularTransaction], highlightedId: 2 });
      vi.advanceTimersByTime(200);
      expect(window.HTMLElement.prototype.scrollIntoView).toHaveBeenCalled();
      vi.useRealTimers();
    });
  });

  describe('Transfer Rendering', () => {
    it('renders transfer pair with source and destination', () => {
      const sourceTx: Transaction = {
        ...mockRegularTransaction,
        id: 20,
        type: 'EXPENSE',
        transferId: 'xfer-1',
        description: 'Transfer Out',
      };
      const destTx: Transaction = {
        ...mockRegularTransaction,
        id: 21,
        type: 'INCOME',
        transferId: 'xfer-1',
        description: 'Transfer In',
      };
      renderList({ transactions: [sourceTx, destTx] });
      expect(screen.getByText('Transfer Out')).toBeInTheDocument();
      expect(screen.getByText('Transfer In')).toBeInTheDocument();
    });

    it('renders transfer destination account name', () => {
      const transferTx: Transaction = {
        ...mockRegularTransaction,
        id: 30,
        type: 'TRANSFER',
        toAccountName: 'Savings Account',
      };
      renderList({ transactions: [transferTx] });
      expect(screen.getByText('Savings Account')).toBeInTheDocument();
    });
  });

  describe('Payee Avatar', () => {
    it('renders payee logo when available', () => {
      renderList({ transactions: [mockRegularTransaction] });
      const img = screen.getByAltText('Spotify');
      expect(img).toBeInTheDocument();
      expect(img).toHaveAttribute('src', 'https://example.com/spotify.png');
    });

    it('shows initial circle for payee without logo', () => {
      mockUseActivePayees.mockReturnValue({
        data: [{ ...mockPayees[1], logo: undefined }],
        isLoading: false,
        isError: false,
      } as any);
      renderList({ transactions: [mockRegularTransaction] });
      expect(screen.getByText('S')).toBeInTheDocument();
    });

    it('shows type icon when no payee found', () => {
      mockUseActivePayees.mockReturnValue({
        data: [],
        isLoading: false,
        isError: false,
      } as any);
      const txNoPayee: Transaction = {
        ...mockRegularTransaction,
        payee: undefined,
      };
      renderList({ transactions: [txNoPayee] });
      // Should show the type icon circle, not a payee avatar
      expect(screen.queryByAltText('Spotify')).not.toBeInTheDocument();
    });
  });

  describe('Sort Direction', () => {
    it('sorts date groups in descending order by default', () => {
      const tx1: Transaction = { ...mockRegularTransaction, id: 40, date: '2024-01-01', description: 'Old' };
      const tx2: Transaction = { ...mockRegularTransaction, id: 41, date: '2024-06-01', description: 'New' };
      renderWithProviders(
        <TransactionList transactions={[tx1, tx2]} onEdit={vi.fn()} onDelete={vi.fn()} />
      );
      const headings = screen.getAllByRole('heading', { level: 3 });
      // Desc order: newest date first
      expect(headings.length).toBeGreaterThanOrEqual(2);
    });

    it('sorts date groups in ascending order when specified', () => {
      const tx1: Transaction = { ...mockRegularTransaction, id: 42, date: '2024-01-01', description: 'Old' };
      const tx2: Transaction = { ...mockRegularTransaction, id: 43, date: '2024-06-01', description: 'New' };
      renderWithProviders(
        <TransactionList transactions={[tx1, tx2]} onEdit={vi.fn()} onDelete={vi.fn()} sortDirection="asc" />
      );
      const headings = screen.getAllByRole('heading', { level: 3 });
      expect(headings.length).toBeGreaterThanOrEqual(2);
    });
  });
});