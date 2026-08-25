/**
 * AccountForm Validation Tests
 * TASK-13.2.3: Form validation tests for AccountForm component
 *
 * Tests the AccountForm's Zod validation, rendering, and user interactions.
 * Complex child components (CurrencySelector, InstitutionSelector, ExchangeRateDisplay)
 * are mocked to isolate form logic testing.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import { AccountForm } from '@/components/accounts/AccountForm';
import type { Account } from '@/types/account';

// Mock complex child components that use their own hooks internally
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="currency-selector"
      value={value || ''}
      onChange={(e) => onValueChange(e.target.value)}
    >
      <option value="">{placeholder || 'Select currency'}</option>
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
    </select>
  ),
}));

vi.mock('@/components/ui/InstitutionSelector', () => ({
  InstitutionSelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="institution-selector"
      value={value || ''}
      onChange={(e) => onValueChange(e.target.value || undefined)}
    >
      <option value="">{placeholder || 'Select institution'}</option>
      <option value="1">Bank A</option>
    </select>
  ),
}));

vi.mock('@/components/ui/ExchangeRateDisplay', () => ({
  ExchangeRateInline: () => <span data-testid="exchange-rate-inline" />,
}));

vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: () => ({ data: null }),
}));

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number, currency: string) => `${currency} ${amount.toFixed(2)}`,
  }),
}));

const mockOnSubmit = vi.fn();
const mockOnCancel = vi.fn();

const mockAccount: Account = {
  id: 1,
  name: 'Test Checking',
  accountNumber: '123456',
  type: 'CHECKING',
  currency: 'USD',
  balance: 5000,
  description: 'Test description',
  isActive: true,
  userId: 1,
  createdAt: '2026-01-01',
  updatedAt: '2026-01-01',
};

describe('AccountForm', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    mockOnSubmit.mockClear();
    mockOnCancel.mockClear();
  });

  describe('Rendering', () => {
    it('should render all required form fields', () => {
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByLabelText(/Account Name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Account Type/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Initial Balance/i)).toBeInTheDocument();
    });

    it('should show "Create Account" button for new account', () => {
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
    });

    it('should show "Update Account" button when editing', () => {
      renderWithProviders(
        <AccountForm account={mockAccount} onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /update account/i })).toBeInTheDocument();
    });

    it('should show Cancel button', () => {
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should render account type options', () => {
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const typeSelect = screen.getByLabelText(/Account Type/i) as HTMLSelectElement;
      expect(typeSelect.options.length).toBe(6); // CHECKING, SAVINGS, CREDIT_CARD, INVESTMENT, CASH, OTHER
    });
  });

  describe('Validation', () => {
    it('should show error when name is empty', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      // Clear name field and submit
      const nameInput = screen.getByLabelText(/Account Name/i);
      await user.clear(nameInput);

      const submitButton = screen.getByRole('button', { name: /create account/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Name is required/i)).toBeInTheDocument();
      });
      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should show error when name exceeds 100 characters', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const nameInput = screen.getByLabelText(/Account Name/i);
      const longName = 'A'.repeat(101);
      await user.type(nameInput, longName);

      const submitButton = screen.getByRole('button', { name: /create account/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Name is too long/i)).toBeInTheDocument();
      });
    });

    it('should show error when account number exceeds 50 characters', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const accountNumberInput = screen.getByLabelText(/Account Number/i);
      const longNumber = '1'.repeat(51);
      await user.type(accountNumberInput, longNumber);

      // Fill required name field
      const nameInput = screen.getByLabelText(/Account Name/i);
      await user.type(nameInput, 'Test');

      const submitButton = screen.getByRole('button', { name: /create account/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Account number is too long/i)).toBeInTheDocument();
      });
    });
  });

  describe('Submission', () => {
    it('should call onSubmit with correct data for valid form', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const nameInput = screen.getByLabelText(/Account Name/i);
      await user.type(nameInput, 'My Savings');

      const submitButton = screen.getByRole('button', { name: /create account/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(mockOnSubmit).toHaveBeenCalledTimes(1);
      });

      const submittedData = mockOnSubmit.mock.calls[0][0];
      expect(submittedData.name).toBe('My Savings');
      expect(submittedData.type).toBe('CHECKING'); // default
    });

    it('should call onCancel when Cancel button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalledTimes(1);
    });
  });

  describe('Loading state', () => {
    it('should disable Cancel button when isLoading is true', () => {
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} isLoading={true} />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
    });
  });

  describe('Edit mode', () => {
    it('should pre-populate form fields when editing an existing account', () => {
      renderWithProviders(
        <AccountForm account={mockAccount} onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const nameInput = screen.getByLabelText(/Account Name/i) as HTMLInputElement;
      expect(nameInput.value).toBe('Test Checking');

      const accountNumberInput = screen.getByLabelText(/Account Number/i) as HTMLInputElement;
      expect(accountNumberInput.value).toBe('123456');

      const typeSelect = screen.getByLabelText(/Account Type/i) as HTMLSelectElement;
      expect(typeSelect.value).toBe('CHECKING');

      // In edit mode, the label changes to "Current Balance"
      const balanceInput = screen.getByLabelText(/Current Balance/i) as HTMLInputElement;
      expect(balanceInput.value).toBe('5,000');
    });
  });

  describe('Interest Calculation', () => {
    it('should show interest rate fields when interest toggle is enabled', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <AccountForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      // Find the interest toggle switch (Switch component)
      const interestToggle = screen.getByRole('switch');
      await user.click(interestToggle);

      await waitFor(() => {
        expect(screen.getByRole('textbox', { name: /Interest Rate/i })).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: /Tax Rate/i })).toBeInTheDocument();
      });
    });
  });
});
