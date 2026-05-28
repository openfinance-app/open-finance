import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AccountCard } from '@/components/accounts/AccountCard';
import type { Account } from '@/types/account';

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (amount: number) => amount * 1.1,
    secondaryCurrency: 'EUR',
    secondaryExchangeRate: 1.1,
  }),
}));

vi.mock('@/hooks/useAccounts', () => ({
  useInterestEstimate: () => ({ data: null }),
}));

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
const baseAccount: Account = {
  id: 1,
  userId: 1,
  name: 'Checking Account',
  type: 'CHECKING',
  currency: 'USD',
  balance: 5000,
  isActive: true,
  createdAt: '2026-01-01T00:00:00Z',
};

const mockOnEdit = vi.fn();
const mockOnDelete = vi.fn();
const mockOnClose = vi.fn();
const mockOnReopen = vi.fn();
const mockOnViewDetail = vi.fn();

describe('AccountCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  describe('Rendering', () => {
    it('should render account name', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.getByText('Checking Account')).toBeInTheDocument();
    });

    it('should render account type label', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      // The type label is translated ("Checking"), account name is "Checking Account"
      expect(screen.getByText('Checking')).toBeInTheDocument();
    });

    it('should render balance', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.getByText(/5,000|5000/)).toBeInTheDocument();
    });

    it('should render institution name when provided', () => {
      const accountWithInstitution: Account = {
        ...baseAccount,
        institution: { id: 1, name: 'Chase Bank' },
      };
      renderWithProviders(
        <AccountCard account={accountWithInstitution} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.getByText('Chase Bank')).toBeInTheDocument();
    });

    it('should render institution logo when provided', () => {
      const accountWithLogo: Account = {
        ...baseAccount,
        institution: { id: 1, name: 'Chase Bank', logo: 'https://example.com/logo.png' },
      };
      renderWithProviders(
        <AccountCard account={accountWithLogo} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      // The logo img has alt="" so it's presentation role; use querySelector
      const logo = document.querySelector('img[src="https://example.com/logo.png"]');
      expect(logo).toBeInTheDocument();
    });

    it('should use type icon when no institution logo is provided', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      // No img element for type icons (they are SVG/lucide)
      expect(screen.queryByRole('img')).not.toBeInTheDocument();
    });
  });

  describe('Different account types', () => {
    const types: Account['type'][] = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER'];

    types.forEach((type) => {
      it(`should render ${type} account without errors`, () => {
        const account = { ...baseAccount, type };
        renderWithProviders(
          <AccountCard account={account} onEdit={mockOnEdit} onDelete={mockOnDelete} />
        );
        expect(screen.getByText('Checking Account')).toBeInTheDocument();
      });
    });
  });

  describe('Negative balance', () => {
    it('should render negative balance', () => {
      const account = { ...baseAccount, balance: -500 };
      renderWithProviders(
        <AccountCard account={account} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.getByText(/500/)).toBeInTheDocument();
    });
  });

  describe('Actions', () => {
    it('should call onEdit when edit button is clicked', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      const editBtn = screen.getByLabelText(/edit account/i);
      fireEvent.click(editBtn);
      expect(mockOnEdit).toHaveBeenCalledWith(baseAccount);
    });

    it('should call onDelete when delete button is clicked', () => {
      renderWithProviders(
        <AccountCard account={baseAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      const deleteBtn = screen.getByTitle(/delete permanently/i);
      fireEvent.click(deleteBtn);
      expect(mockOnDelete).toHaveBeenCalledWith(baseAccount);
    });

    it('should call onClose when close button is clicked for active account', () => {
      renderWithProviders(
        <AccountCard
          account={baseAccount}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onClose={mockOnClose}
        />
      );
      const closeBtn = screen.getByTitle(/close account/i);
      fireEvent.click(closeBtn);
      expect(mockOnClose).toHaveBeenCalledWith(baseAccount);
    });

    it('should call onReopen when reopen button is clicked for closed account', () => {
      const closedAccount: Account = { ...baseAccount, isActive: false };
      renderWithProviders(
        <AccountCard
          account={closedAccount}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onReopen={mockOnReopen}
        />
      );
      const reopenBtn = screen.getByTitle(/reopen account/i);
      fireEvent.click(reopenBtn);
      expect(mockOnReopen).toHaveBeenCalledWith(closedAccount);
    });

    it('should call onViewDetail when card body is clicked', () => {
      renderWithProviders(
        <AccountCard
          account={baseAccount}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetail={mockOnViewDetail}
        />
      );
      const nameEl = screen.getByText('Checking Account');
      fireEvent.click(nameEl);
      expect(mockOnViewDetail).toHaveBeenCalledWith(baseAccount);
    });

    it('should not call onViewDetail when action buttons are clicked', () => {
      renderWithProviders(
        <AccountCard
          account={baseAccount}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetail={mockOnViewDetail}
        />
      );
      const deleteBtn = screen.getByTitle(/delete permanently/i);
      fireEvent.click(deleteBtn);
      expect(mockOnViewDetail).not.toHaveBeenCalled();
    });
  });

  describe('Closed account', () => {
    it('should not render close button for already closed account', () => {
      const closedAccount: Account = { ...baseAccount, isActive: false };
      renderWithProviders(
        <AccountCard
          account={closedAccount}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onClose={mockOnClose}
        />
      );
      expect(screen.queryByTitle(/close account/i)).not.toBeInTheDocument();
    });

    it('should not render reopen button when onReopen is not provided', () => {
      const closedAccount: Account = { ...baseAccount, isActive: false };
      renderWithProviders(
        <AccountCard account={closedAccount} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.queryByTitle(/reopen account/i)).not.toBeInTheDocument();
    });
  });

  describe('Interest estimate', () => {
    it('should render without error when isInterestEnabled is true', () => {
      const accountWithInterest: Account = { ...baseAccount, isInterestEnabled: true };
      renderWithProviders(
        <AccountCard account={accountWithInterest} onEdit={mockOnEdit} onDelete={mockOnDelete} />
      );
      expect(screen.getByText('Checking Account')).toBeInTheDocument();
    });
  });
});
