/**
 * Unit tests for TransactionDetailModal component
 *
 * Covers: rendering, tab switching, conditional Splits tab,
 * close button, backdrop click, Escape key, Edit button,
 * tags, notes, status badges, account link navigation.
 */
import { screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { TransactionDetailModal } from './TransactionDetailModal';
import type { Transaction } from '@/types/transaction';

// ─── Context mocks ────────────────────────────────────────────────────────────

const mockNavigate = vi.fn();
vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

vi.mock('@/context/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => (
    <div>{children}</div>
  ),
  useAuthContext: vi.fn(() => ({ baseCurrency: 'USD' })),
}));

// ─── Child component mocks ────────────────────────────────────────────────────

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: number; currency: string }) => (
    <span data-testid="converted-amount">{`${currency} ${amount}`}</span>
  ),
}));

vi.mock('./SplitDetail', () => ({
  SplitDetail: ({
    splits,
    currency,
  }: {
    splits: any[];
    currency: string;
  }) => (
    <div data-testid="split-detail" data-currency={currency}>
      {splits.map((s, i) => (
        <div key={i} data-testid="split-line">
          {s.description} – {currency} {s.amount}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/attachments', () => ({
  AttachmentList: () => <div data-testid="attachment-list">AttachmentList</div>,
  AttachmentUpload: () => <div data-testid="attachment-upload">AttachmentUpload</div>,
}));

// ─── Mock data ────────────────────────────────────────────────────────────────

const baseTransaction: Transaction = {
  id: 1,
  userId: 1,
  accountId: 10,
  type: 'EXPENSE',
  amount: 85.5,
  currency: 'USD',
  date: '2024-03-15',
  description: 'Coffee Shop',
  payee: 'Starbucks',
  isReconciled: false,
  createdAt: '2024-03-15T09:00:00Z',
  accountName: 'My Checking',
};

const incomeTransaction: Transaction = {
  ...baseTransaction,
  id: 2,
  type: 'INCOME',
  amount: 3000,
  description: 'Monthly Salary',
  payee: 'Employer Ltd',
  accountName: 'My Checking',
};

const transferTransaction: Transaction = {
  ...baseTransaction,
  id: 3,
  type: 'TRANSFER',
  amount: 500,
  description: 'Savings Transfer',
  toAccountName: 'Savings Account',
};

const splitTransaction: Transaction = {
  ...baseTransaction,
  id: 4,
  description: 'Grocery + Household',
  hasSplits: true,
  splits: [
    { id: 1, transactionId: 4, amount: 50, description: 'Groceries' },
    { id: 2, transactionId: 4, amount: 35.5, description: 'Household' },
  ],
};

const reconciledTransaction: Transaction = {
  ...baseTransaction,
  id: 5,
  isReconciled: true,
};

const transactionWithNotes: Transaction = {
  ...baseTransaction,
  id: 6,
  notes: 'Business expense — reimbursable',
};

const transactionWithTags: Transaction = {
  ...baseTransaction,
  id: 7,
  tags: ['food', 'business'],
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('TransactionDetailModal', () => {
  const onClose = vi.fn();
  const onEdit = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe('Rendering', () => {
    it('renders the transaction description in the header', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      // Description appears in both the modal title and the body description field
      expect(screen.getAllByText('Coffee Shop').length).toBeGreaterThan(0);
    });

    it('renders type label and payee in the subtitle', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      // "Expense" and "Starbucks" appear in both the subtitle and the Details section
      expect(screen.getAllByText(/Expense/).length).toBeGreaterThan(0);
      expect(screen.getAllByText(/Starbucks/).length).toBeGreaterThan(0);
    });

    it('renders formatted date in the subtitle', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      // Date appears in both the subtitle and the Details section Date field
      expect(screen.getAllByText(/March 15, 2024/).length).toBeGreaterThan(0);
    });

    it('renders the amount hero with ConvertedAmount', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.getByText('Amount')).toBeInTheDocument();
      expect(screen.getByTestId('converted-amount')).toBeInTheDocument();
    });

    it('renders account name in details section', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.getByText('My Checking')).toBeInTheDocument();
    });

    it('renders type "Income" for income transactions', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={incomeTransaction} onClose={onClose} />,
      );
      expect(screen.getAllByText('Income').length).toBeGreaterThan(0);
    });

    it('renders To Account field for transfer transactions', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={transferTransaction} onClose={onClose} />,
      );
      expect(screen.getByText('Savings Account')).toBeInTheDocument();
    });

    it('renders Reconciled badge when isReconciled is true', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={reconciledTransaction} onClose={onClose} />,
      );
      expect(screen.getByText('Reconciled')).toBeInTheDocument();
    });

    it('does not render Reconciled badge when isReconciled is false', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.queryByText('Reconciled')).not.toBeInTheDocument();
    });
  });

  // ── Tabs ───────────────────────────────────────────────────────────────────

  describe('Tabs', () => {
    it('renders Overview and Attachments tabs by default (no splits)', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toBeInTheDocument();
    });

    it('does NOT render Splits tab when hasSplits is false', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.queryByRole('button', { name: 'Splits' })).not.toBeInTheDocument();
    });

    it('renders Splits tab when hasSplits is true', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={splitTransaction} onClose={onClose} />,
      );
      expect(screen.getByRole('button', { name: 'Splits' })).toBeInTheDocument();
    });

    it('renders Split badge in amount hero when hasSplits is true', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={splitTransaction} onClose={onClose} />,
      );
      expect(screen.getByText('Split')).toBeInTheDocument();
    });

    it('switches to Attachments tab and renders attachment components', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Attachments/i }));
      });
      expect(screen.getByTestId('attachment-list')).toBeInTheDocument();
      expect(screen.getByTestId('attachment-upload')).toBeInTheDocument();
    });

    it('switches to Splits tab and renders SplitDetail', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={splitTransaction} onClose={onClose} />,
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: 'Splits' }));
      });
      expect(screen.getByTestId('split-detail')).toBeInTheDocument();
      expect(screen.getByText('Groceries – USD 50')).toBeInTheDocument();
      expect(screen.getByText('Household – USD 35.5')).toBeInTheDocument();
    });
  });

  // ── Notes and tags ─────────────────────────────────────────────────────────

  describe('Notes and tags', () => {
    it('renders notes section when notes are present', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={transactionWithNotes} onClose={onClose} />,
      );
      expect(screen.getByText('Notes')).toBeInTheDocument();
      expect(screen.getByText('Business expense — reimbursable')).toBeInTheDocument();
    });

    it('does not render notes section when notes are absent', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.queryByText('Notes')).not.toBeInTheDocument();
    });

    it('renders tags when present', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={transactionWithTags} onClose={onClose} />,
      );
      expect(screen.getByText('Tags')).toBeInTheDocument();
      expect(screen.getByText('food')).toBeInTheDocument();
      expect(screen.getByText('business')).toBeInTheDocument();
    });

    it('does not render tags section when tags are empty', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.queryByText('Tags')).not.toBeInTheDocument();
    });
  });

  // ── Close behaviour ────────────────────────────────────────────────────────

  describe('Close behaviour', () => {
    it('calls onClose when the X button is clicked', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      fireEvent.click(screen.getByLabelText('Close modal'));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when backdrop is clicked', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      const backdrop = document.querySelector('.absolute.inset-0');
      act(() => {
        fireEvent.click(backdrop!);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      act(() => {
        fireEvent.keyDown(window, { key: 'Escape' });
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });
  });

  // ── Edit button ────────────────────────────────────────────────────────────

  describe('Edit button', () => {
    it('renders Edit button when onEdit prop is provided', () => {
      renderWithProviders(
        <TransactionDetailModal
          transaction={baseTransaction}
          onClose={onClose}
          onEdit={onEdit}
        />,
      );
      expect(screen.getByRole('button', { name: /Edit/i })).toBeInTheDocument();
    });

    it('does NOT render Edit button when onEdit prop is absent', () => {
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      expect(screen.queryByRole('button', { name: /Edit/i })).not.toBeInTheDocument();
    });

    it('calls onClose and onEdit with the transaction when Edit is clicked', () => {
      renderWithProviders(
        <TransactionDetailModal
          transaction={baseTransaction}
          onClose={onClose}
          onEdit={onEdit}
        />,
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Edit/i }));
      });
      expect(onClose).toHaveBeenCalledTimes(1);
      expect(onEdit).toHaveBeenCalledTimes(1);
      expect(onEdit).toHaveBeenCalledWith(baseTransaction);
    });
  });

  // ── Account link navigation ────────────────────────────────────────────────

  describe('Account link navigation', () => {
    it('renders the account name as a link that navigates to the account details', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <TransactionDetailModal transaction={baseTransaction} onClose={onClose} />,
      );
      const link = screen.getByRole('button', { name: 'Open account details' });
      await user.click(link);
      expect(onClose).toHaveBeenCalledTimes(1);
      expect(mockNavigate).toHaveBeenCalledWith('/accounts?highlight=10');
    });

    it('does not navigate when accountId is missing', async () => {
      const user = userEvent.setup();
      const noAccount = { ...baseTransaction, accountId: undefined };
      renderWithProviders(
        <TransactionDetailModal transaction={noAccount} onClose={onClose} />,
      );
      const link = screen.getByRole('button', { name: 'Open account details' });
      await user.click(link);
      expect(onClose).not.toHaveBeenCalled();
      expect(mockNavigate).not.toHaveBeenCalled();
    });
  });
});
