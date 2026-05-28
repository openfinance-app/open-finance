/**
 * Unit tests for AccountDetailModal component
 *
 * Covers: rendering, loading state, error state, tab switching,
 * conditional Interest tab, close button, backdrop click, Escape key,
 * Edit button behaviour, and "View All Transactions" navigation.
 */
import { screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { AccountDetailModal } from './AccountDetailModal';
import * as useAccountsModule from '@/hooks/useAccounts';
import * as useTransactionsModule from '@/hooks/useTransactions';
import type { Account } from '@/types/account';

// ─── Context mocks ────────────────────────────────────────────────────────────

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

// ─── Hook mocks ───────────────────────────────────────────────────────────────

vi.mock('@/hooks/useAccounts', async (importOriginal) => {
  const actual = await importOriginal<typeof useAccountsModule>();
  return {
    ...actual,
    useAccount: vi.fn(() => ({ data: undefined, isLoading: false })),
    useAccountBalanceHistory: vi.fn(() => ({ data: undefined, isLoading: false })),
    useUpdateAccount: vi.fn(() => ({ mutateAsync: vi.fn(), isPending: false })),
  };
});

vi.mock('@/hooks/useTransactions', async (importOriginal) => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return {
    ...actual,
    useTransactions: vi.fn(() => ({ data: undefined, isLoading: false })),
  };
});

// ─── Child component mocks ────────────────────────────────────────────────────

vi.mock('./InterestRateVariationsSection', () => ({
  InterestRateVariationsSection: () => (
    <div data-testid="interest-rate-variations">InterestRateVariationsSection</div>
  ),
}));

vi.mock('./AccountForm', () => ({
  AccountForm: ({ onCancel }: { onCancel: () => void }) => (
    <div data-testid="account-form">
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount, currency }: { amount: number; currency: string }) => (
    <span data-testid="converted-amount">{`${currency} ${amount}`}</span>
  ),
}));

vi.mock('@/components/attachments', () => ({
  AttachmentList: () => <div data-testid="attachment-list">AttachmentList</div>,
  AttachmentUpload: () => <div data-testid="attachment-upload">AttachmentUpload</div>,
}));

vi.mock('@/components/ui/Dialog', () => ({
  Dialog: ({ children, open }: { children: React.ReactNode; open: boolean }) =>
    open ? <div data-testid="edit-dialog">{children}</div> : null,
  DialogContent: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dialog-content">{children}</div>
  ),
  DialogHeader: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dialog-header">{children}</div>
  ),
  DialogTitle: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="dialog-title">{children}</div>
  ),
}));



// ─── Typed mock helpers ───────────────────────────────────────────────────────

const mockUseAccount = vi.mocked(useAccountsModule.useAccount);
const mockUseAccountBalanceHistory = vi.mocked(useAccountsModule.useAccountBalanceHistory);
const mockUseTransactions = vi.mocked(useTransactionsModule.useTransactions);

// ─── Mock data ────────────────────────────────────────────────────────────────

const mockAccount: Account = {
  id: 1,
  userId: 1,
  name: 'My Checking Account',
  type: 'CHECKING',
  currency: 'USD',
  balance: 5000,
  isActive: true,
  createdAt: '2023-01-01T00:00:00Z',
  description: 'Main checking account',
  accountNumber: '****1234',
};

const mockAccountWithInterest: Account = {
  ...mockAccount,
  id: 2,
  name: 'High-Yield Savings',
  type: 'SAVINGS',
  isInterestEnabled: true,
  interestPeriod: 'ANNUAL',
};

const mockTransactionsPage = {
  content: [
    {
      id: 101,
      userId: 1,
      accountId: 1,
      type: 'EXPENSE' as const,
      amount: -150,
      currency: 'USD',
      date: '2024-01-15',
      description: 'Grocery Store',
      isReconciled: false,
      createdAt: '2024-01-15T10:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 10,
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('AccountDetailModal', () => {
  const defaultProps = {
    accountId: 1,
    onClose: vi.fn(),
    onEdit: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAccount.mockReturnValue({ data: mockAccount, isLoading: false } as any);
    mockUseAccountBalanceHistory.mockReturnValue({ data: [], isLoading: false } as any);
    mockUseTransactions.mockReturnValue({ data: undefined, isLoading: false } as any);
  });

  // ── Rendering ──────────────────────────────────────────────────────────────

  describe('Rendering', () => {
    it('renders account name in the header', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('My Checking Account')).toBeInTheDocument();
    });

    it('renders account type and currency in subtitle', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      // "Checking" appears in both the subtitle and the Details section Type field
      expect(screen.getAllByText(/Checking/).length).toBeGreaterThan(0);
      // "USD" appears in both the subtitle and the Details section Currency field
      expect(screen.getAllByText(/USD/).length).toBeGreaterThan(0);
    });

    it('renders balance hero card with ConvertedAmount', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Current Balance')).toBeInTheDocument();
      expect(screen.getByTestId('converted-amount')).toBeInTheDocument();
    });

    it('renders description in details section', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Main checking account')).toBeInTheDocument();
    });

    it('shows Closed badge when account is inactive', () => {
      mockUseAccount.mockReturnValue({
        data: { ...mockAccount, isActive: false },
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Closed')).toBeInTheDocument();
    });

    it('does not show Closed badge when account is active', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.queryByText('Closed')).not.toBeInTheDocument();
    });
  });

  // ── Loading state ──────────────────────────────────────────────────────────

  describe('Loading state', () => {
    it('shows skeleton when account is loading', () => {
      mockUseAccount.mockReturnValue({ data: undefined, isLoading: true } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      // LoadingSkeleton renders divs with the 'shimmer' CSS class (not 'animate-pulse')
      const skeletons = document.querySelectorAll('.shimmer');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  // ── Error / empty state ────────────────────────────────────────────────────

  describe('Error state', () => {
    it('shows error message when account fails to load', () => {
      mockUseAccount.mockReturnValue({ data: undefined, isLoading: false } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Failed to load account details.')).toBeInTheDocument();
    });
  });

  // ── Tabs ───────────────────────────────────────────────────────────────────

  describe('Tabs', () => {
    it('renders Overview and Attachments tabs by default', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByRole('button', { name: 'Overview' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Attachments/i })).toBeInTheDocument();
    });

    it('does NOT render Interest tab when isInterestEnabled is false', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.queryByRole('button', { name: 'Interest' })).not.toBeInTheDocument();
    });

    it('renders Interest tab when isInterestEnabled is true', () => {
      mockUseAccount.mockReturnValue({
        data: mockAccountWithInterest,
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} accountId={2} />);
      expect(screen.getByRole('button', { name: 'Interest' })).toBeInTheDocument();
    });

    it('switches to Attachments tab and renders attachment components', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Attachments/i }));
      });
      expect(screen.getByTestId('attachment-list')).toBeInTheDocument();
      expect(screen.getByTestId('attachment-upload')).toBeInTheDocument();
    });

    it('switches to Interest tab and renders InterestRateVariationsSection', () => {
      mockUseAccount.mockReturnValue({
        data: mockAccountWithInterest,
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} accountId={2} />);
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: 'Interest' }));
      });
      expect(screen.getByTestId('interest-rate-variations')).toBeInTheDocument();
    });

    it('shows balance history section on Overview tab', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Balance History')).toBeInTheDocument();
    });

    it('shows Recent Transactions section on Overview tab', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Recent Transactions')).toBeInTheDocument();
    });
  });

  // ── Transactions in overview ───────────────────────────────────────────────

  describe('Recent Transactions', () => {
    it('shows empty state when no transactions', () => {
      mockUseTransactions.mockReturnValue({ data: undefined, isLoading: false } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('No transactions for this account')).toBeInTheDocument();
    });

    it('renders transaction entries when transactions exist', () => {
      mockUseTransactions.mockReturnValue({
        data: mockTransactionsPage,
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Grocery Store')).toBeInTheDocument();
    });

    it('shows View All Transactions button when totalElements > 0', () => {
      mockUseTransactions.mockReturnValue({
        data: mockTransactionsPage,
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('View All Transactions')).toBeInTheDocument();
    });
  });

  // ── Close button ───────────────────────────────────────────────────────────

  describe('Close behaviour', () => {
    it('calls onClose when the X button is clicked', () => {
      const onClose = vi.fn();
      renderWithProviders(<AccountDetailModal {...defaultProps} onClose={onClose} />);
      fireEvent.click(screen.getByLabelText('Close modal'));
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when backdrop is clicked', () => {
      const onClose = vi.fn();
      renderWithProviders(<AccountDetailModal {...defaultProps} onClose={onClose} />);
      // Backdrop is the first child div of the fixed container
      const backdrop = document.querySelector('.absolute.inset-0');
      act(() => {
        fireEvent.click(backdrop!);
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('calls onClose when Escape key is pressed', () => {
      const onClose = vi.fn();
      renderWithProviders(<AccountDetailModal {...defaultProps} onClose={onClose} />);
      act(() => {
        fireEvent.keyDown(window, { key: 'Escape' });
      });
      expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('does NOT call onClose on Escape when edit dialog is open', () => {
      const onClose = vi.fn();
      renderWithProviders(<AccountDetailModal {...defaultProps} onClose={onClose} />);
      // Open the edit dialog
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Edit/i }));
      });
      expect(screen.getByTestId('edit-dialog')).toBeInTheDocument();
      // Escape should NOT close the main modal while edit dialog is open
      act(() => {
        fireEvent.keyDown(window, { key: 'Escape' });
      });
      expect(onClose).not.toHaveBeenCalled();
    });
  });

  // ── Edit button ────────────────────────────────────────────────────────────

  describe('Edit button', () => {
    it('renders Edit button when account is loaded', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByRole('button', { name: /Edit/i })).toBeInTheDocument();
    });

    it('opens internal edit dialog on Edit click', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Edit/i }));
      });
      expect(screen.getByTestId('edit-dialog')).toBeInTheDocument();
      expect(screen.getByTestId('account-form')).toBeInTheDocument();
    });

    it('closes the edit dialog on Cancel inside AccountForm', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: /Edit/i }));
      });
      act(() => {
        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      });
      expect(screen.queryByTestId('edit-dialog')).not.toBeInTheDocument();
    });
  });

  // ── Balance history chart ──────────────────────────────────────────────────

  describe('Balance history chart', () => {
    it('shows "No balance history" when history array is empty', () => {
      mockUseAccountBalanceHistory.mockReturnValue({ data: [], isLoading: false } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('No balance history for this period')).toBeInTheDocument();
    });

    it('renders chart when balance history data is available', () => {
      mockUseAccountBalanceHistory.mockReturnValue({
        data: [{ date: '2024-01-01', balance: 5000 }],
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByTestId('responsive-container')).toBeInTheDocument();
    });

    it('renders period selector buttons', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByRole('button', { name: '1M' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '3M' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'All' })).toBeInTheDocument();
    });

    it('changes period when a period button is clicked', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      fireEvent.click(screen.getByRole('button', { name: '1M' }));
      // The 1M button should now be active (has different styling)
      const btn = screen.getByRole('button', { name: '1M' });
      expect(btn.className).toContain('bg-primary');
    });

    it('shows loading skeleton for balance history', () => {
      mockUseAccountBalanceHistory.mockReturnValue({ data: undefined, isLoading: true } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      const skeletons = document.querySelectorAll('.shimmer');
      expect(skeletons.length).toBeGreaterThan(0);
    });

    it('shows loading skeleton for transactions', () => {
      mockUseTransactions.mockReturnValue({ data: undefined, isLoading: true } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      const skeletons = document.querySelectorAll('.shimmer');
      expect(skeletons.length).toBeGreaterThan(0);
    });
  });

  describe('Account details section', () => {
    it('displays account number', () => {
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('****1234')).toBeInTheDocument();
    });

    it('displays dash when no account number', () => {
      mockUseAccount.mockReturnValue({
        data: { ...mockAccount, accountNumber: undefined },
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      // Should show dash for missing account number
      const body = document.body.textContent || '';
      expect(body).toContain('—');
    });

    it('displays institution name when present', () => {
      mockUseAccount.mockReturnValue({
        data: { ...mockAccount, institution: { id: 1, name: 'Chase Bank' } },
        isLoading: false,
      } as any);
      renderWithProviders(<AccountDetailModal {...defaultProps} />);
      expect(screen.getByText('Chase Bank')).toBeInTheDocument();
    });
  });
});
