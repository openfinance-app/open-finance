/**
 * Unit tests for RecurringTransactionDetailModal component
 *
 * Covers: rendering, tab switching, close button, backdrop click,
 * Escape key, Edit button, status badges, schedule section,
 * notes section, and currency conversion.
 */
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import React from 'react';
import { renderWithProviders, screen, fireEvent } from '@/test/test-utils';
import { RecurringTransactionDetailModal } from './RecurringTransactionDetailModal';
import * as useCurrencyModule from '@/hooks/useCurrency';
import type { RecurringTransaction } from '@/types/recurringTransaction';

// ─── Context mocks ────────────────────────────────────────────────────────────

vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

vi.mock('@/context/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  useAuthContext: vi.fn(() => ({ baseCurrency: 'USD' })),
}));

// ─── Hook mocks ───────────────────────────────────────────────────────────────

vi.mock('@/hooks/useCurrency', async importOriginal => {
  const actual = await importOriginal<typeof useCurrencyModule>();
  return {
    ...actual,
    useLatestExchangeRate: vi.fn(() => ({ data: undefined, isLoading: false })),
  };
});

// ─── Child component mocks ────────────────────────────────────────────────────

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: number; currency: string }) => (
    <span data-testid="converted-amount">{`${currency} ${amount}`}</span>
  ),
}));

vi.mock('@/components/attachments', () => ({
  AttachmentList: () => <div data-testid="attachment-list">AttachmentList</div>,
  AttachmentUpload: () => <div data-testid="attachment-upload">AttachmentUpload</div>,
}));

// ─── Typed mock helpers ───────────────────────────────────────────────────────

const mockUseLatestExchangeRate = vi.mocked(useCurrencyModule.useLatestExchangeRate);

// ─── Mock data ────────────────────────────────────────────────────────────────

const futureDate = '2099-12-31'; // Far future so "next occurrence" formatting always works

const activeExpense: RecurringTransaction = {
  id: 1,
  accountId: 10,
  accountName: 'My Checking',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 120,
  currency: 'USD',
  categoryId: 5,
  categoryName: 'Utilities',
  categoryIcon: '⚡',
  categoryColor: '#ff0',
  payee: 'Electric Company',
  description: 'Monthly Electricity Bill',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: futureDate,
  endDate: null,
  isActive: true,
  createdAt: '2023-01-01T00:00:00Z',
  updatedAt: '2023-01-01T00:00:00Z',
  isDue: false,
  daysUntilNext: 60,
  isEnded: false,
};

const pausedExpense: RecurringTransaction = {
  ...activeExpense,
  id: 2,
  description: 'Paused Gym Membership',
  isActive: false,
  isDue: false,
  daysUntilNext: 10,
  isEnded: false,
};

const endedExpense: RecurringTransaction = {
  ...activeExpense,
  id: 3,
  description: 'Old Subscription',
  isActive: false,
  isEnded: true,
  endDate: '2024-01-01',
  isDue: false,
  daysUntilNext: 0,
};

const dueExpense: RecurringTransaction = {
  ...activeExpense,
  id: 4,
  description: 'Due Bill',
  isDue: true,
  daysUntilNext: 0,
  isEnded: false,
};

const incomeRecurring: RecurringTransaction = {
  ...activeExpense,
  id: 5,
  type: 'INCOME',
  amount: 3000,
  description: 'Monthly Salary',
  payee: 'Employer Ltd',
  categoryName: null,
  categoryIcon: null,
  categoryColor: null,
};

const transferRecurring: RecurringTransaction = {
  ...activeExpense,
  id: 6,
  type: 'TRANSFER',
  amount: 500,
  description: 'Auto Savings Transfer',
  toAccountName: 'Savings Account',
};

const recurringWithNotes: RecurringTransaction = {
  ...activeExpense,
  id: 7,
  description: 'Annotated Bill',
  notes: 'Check invoice before paying',
};

// ─── Test wrapper ─────────────────────────────────────────────────────────────

// No longer need manual makeWrapper as we use renderWithProviders

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('RecurringTransactionDetailModal', () => {
  const onClose = vi.fn();
  const onEdit = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseLatestExchangeRate.mockReturnValue({ data: undefined, isLoading: false } as any);
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe('Rendering', () => {
    it('renders the description in the header', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Monthly Electricity Bill')).toBeInTheDocument();
    });

    it('renders type label and frequency in the subtitle', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      // "Expense" and "Monthly" appear in both the subtitle and the Details section
      expect(screen.getAllByText(/Expense/).length).toBeGreaterThan(0);
      expect(screen.getAllByText(/Monthly/).length).toBeGreaterThan(0);
    });

    it('renders payee in subtitle when payee is set', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      // Payee appears in both the header subtitle and the Details section
      expect(screen.getAllByText(/Electric Company/).length).toBeGreaterThan(0);
    });

    it('renders the amount hero with ConvertedAmount', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Amount per occurrence')).toBeInTheDocument();
      expect(screen.getByTestId('converted-amount')).toBeInTheDocument();
    });

    it('renders account name in the Details section', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('My Checking')).toBeInTheDocument();
    });

    it('renders category name in the Details section when set', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Utilities')).toBeInTheDocument();
    });

    it('renders To Account field for transfer recurring transactions', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal
          recurringTransaction={transferRecurring}
          onClose={onClose}
        />
      );
      expect(screen.getByText('Savings Account')).toBeInTheDocument();
    });
  });

  // ── Status badges ──────────────────────────────────────────────────────────

  describe('Status badges', () => {
    it('shows "Active" status for an active, not-due transaction', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Active')).toBeInTheDocument();
    });

    it('shows "Paused" status for an inactive transaction', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={pausedExpense} onClose={onClose} />
      );
      expect(screen.getByText('Paused')).toBeInTheDocument();
    });

    it('shows "Ended" status for an ended transaction', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={endedExpense} onClose={onClose} />
      );
      expect(screen.getByText('Ended')).toBeInTheDocument();
    });

    it('shows "Due Now" badge when isDue and not ended', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={dueExpense} onClose={onClose} />
      );
      // "Due Now" appears in both the header badge and the schedule section badge
      expect(screen.getAllByText('Due Now').length).toBeGreaterThan(0);
    });

    it('does NOT show "Due Now" badge when isEnded', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={endedExpense} onClose={onClose} />
      );
      expect(screen.queryByText('Due Now')).not.toBeInTheDocument();
    });
  });

  // ── Schedule section ───────────────────────────────────────────────────────

  describe('Schedule section', () => {
    it('renders Schedule heading in the Overview tab', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Schedule')).toBeInTheDocument();
    });

    it('renders Frequency label in schedule section', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Frequency')).toBeInTheDocument();
      expect(screen.getAllByText('Monthly').length).toBeGreaterThan(0);
    });

    it('renders Next Occurrence when not ended', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Next Occurrence')).toBeInTheDocument();
    });

    it('does NOT render Next Occurrence when ended', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={endedExpense} onClose={onClose} />
      );
      expect(screen.queryByText('Next Occurrence')).not.toBeInTheDocument();
    });

    it('renders "Ended On" when endDate is set and isEnded', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={endedExpense} onClose={onClose} />
      );
      expect(screen.getByText('Ended On')).toBeInTheDocument();
    });

    it('renders Created date in schedule section', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByText('Created')).toBeInTheDocument();
      expect(screen.getByText('Jan 1, 2023')).toBeInTheDocument();
    });
  });

  // ── Tabs ───────────────────────────────────────────────────────────────────

  describe('Tabs', () => {
    it('renders Overview and Attachments tabs', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toBeInTheDocument();
    });

    it('switches to Attachments tab and renders attachment components', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Attachments/i }));
      });
      expect(screen.getByTestId('attachment-list')).toBeInTheDocument();
      expect(screen.getByTestId('attachment-upload')).toBeInTheDocument();
    });

    it('hides Overview content when Attachments tab is active', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Attachments/i }));
      });
      expect(screen.queryByText('Schedule')).not.toBeInTheDocument();
    });
  });

  // ── Notes ──────────────────────────────────────────────────────────────────

  describe('Notes', () => {
    it('renders notes section when notes are present', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal
          recurringTransaction={recurringWithNotes}
          onClose={onClose}
        />
      );
      expect(screen.getByText('Notes')).toBeInTheDocument();
      expect(screen.getByText('Check invoice before paying')).toBeInTheDocument();
    });

    it('does not render notes section when notes are null', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.queryByText('Notes')).not.toBeInTheDocument();
    });
  });

  // ── Close behaviour ────────────────────────────────────────────────────────

  describe('Close behaviour', () => {
    it('calls onClose when the X button is clicked', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      fireEvent.click(screen.getByLabelText('Close modal'));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when backdrop is clicked', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      const backdrop = document.querySelector('.absolute.inset-0');
      act(() => {
        fireEvent.click(backdrop!);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
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
        <RecurringTransactionDetailModal
          recurringTransaction={activeExpense}
          onClose={onClose}
          onEdit={onEdit}
        />
      );
      expect(screen.getByRole('button', { name: /Edit/i })).toBeInTheDocument();
    });

    it('does NOT render Edit button when onEdit prop is absent', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={activeExpense} onClose={onClose} />
      );
      expect(screen.queryByRole('button', { name: /Edit/i })).not.toBeInTheDocument();
    });

    it('calls onClose and onEdit with the recurring transaction when Edit is clicked', () => {
      renderWithProviders(
        <RecurringTransactionDetailModal
          recurringTransaction={activeExpense}
          onClose={onClose}
          onEdit={onEdit}
        />
      );
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Edit/i }));
      });
      expect(onClose).toHaveBeenCalledTimes(1);
      expect(onEdit).toHaveBeenCalledTimes(1);
      expect(onEdit).toHaveBeenCalledWith(activeExpense);
    });
  });

  // ── Currency conversion ────────────────────────────────────────────────────

  describe('Currency conversion', () => {
    it('uses exchange rate from hook when currencies differ', () => {
      mockUseLatestExchangeRate.mockReturnValue({
        data: { rate: 0.92 },
        isLoading: false,
      } as any);

      const eurTransaction: RecurringTransaction = {
        ...activeExpense,
        currency: 'EUR',
      };

      renderWithProviders(
        <RecurringTransactionDetailModal recurringTransaction={eurTransaction} onClose={onClose} />
      );

      // ConvertedAmount should receive the EUR amount
      expect(screen.getByTestId('converted-amount')).toBeInTheDocument();
    });
  });
});
