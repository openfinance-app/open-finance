/**
 * RecurringTransactionForm Validation Tests
 * TASK-13.2.3: Form validation tests for RecurringTransactionForm component
 *
 * Tests the RecurringTransactionForm's Zod validation, rendering, and user interactions.
 * Complex child components (CategorySelect, PayeeSelector, AccountSelector) are mocked
 * to isolate form logic testing.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import { RecurringTransactionForm } from '@/components/transactions/RecurringTransactionForm';
import type { Account } from '@/types/account';
import type { Category } from '@/types/transaction';

vi.mock('@/hooks/useUserSettings', async (importOriginal) => {
  const actual = await importOriginal() as any;
  return {
    ...actual,
    useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' }, isLoading: false }),
  };
});

// Mock complex child components
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="category-select"
      aria-label="Category"
      value={value || ''}
      onChange={(e) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">{placeholder || 'Select category'}</option>
      <option value="1">Food</option>
      <option value="2">Salary</option>
    </select>
  ),
}));

vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeSelector: ({ value, onValueChange, placeholder }: any) => (
    <input
      data-testid="payee-selector"
      aria-label="Payee"
      value={value || ''}
      onChange={(e) => onValueChange(e.target.value || undefined)}
      placeholder={placeholder || 'Select payee'}
    />
  ),
}));

vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="account-selector"
      aria-label="Account"
      value={value || ''}
      onChange={(e) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">{placeholder || 'Select account'}</option>
      <option value="1">Checking</option>
      <option value="2">Savings</option>
    </select>
  ),
}));

vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: () => ({ data: null }),
  useCurrencyFormat: () => (amount: number) => `$${amount.toFixed(2)}`,
}));

vi.mock('@/hooks/usePayees', () => ({
  useActivePayees: () => ({ data: [] }),
}));

const mockOnSubmit = vi.fn();
const mockOnCancel = vi.fn();

const mockAccounts: Account[] = [
  {
    id: 1,
    name: 'Checking',
    type: 'CHECKING',
    currency: 'USD',
    balance: 5000,
    isActive: true,
    userId: 1,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
  {
    id: 2,
    name: 'Savings',
    type: 'SAVINGS',
    currency: 'USD',
    balance: 10000,
    isActive: true,
    userId: 1,
    createdAt: '2026-01-01',
    updatedAt: '2026-01-01',
  },
];

const mockCategories: Category[] = [
  { id: 1, name: 'Food', type: 'EXPENSE', userId: 1, isActive: true },
  { id: 2, name: 'Salary', type: 'INCOME', userId: 1, isActive: true },
];

describe('RecurringTransactionForm', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    mockOnSubmit.mockClear();
    mockOnCancel.mockClear();
  });

  describe('Rendering', () => {
    it('should render all required form fields', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByLabelText(/Type/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Currency/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Description/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Frequency/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Next Occurrence/i)).toBeInTheDocument();
    });

    it('should show "Create" button for new recurring transaction', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByRole('button', { name: /create/i })).toBeInTheDocument();
    });

    it('should show Cancel button', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should render transaction type options', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const typeSelect = screen.getByLabelText(/^Type/i) as HTMLSelectElement;
      expect(typeSelect.options.length).toBe(3); // INCOME, EXPENSE, TRANSFER
    });

    it('should render frequency options', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const freqSelect = screen.getByLabelText(/Frequency/i) as HTMLSelectElement;
      expect(freqSelect.options.length).toBe(6); // DAILY, WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY
    });

    it('should default to EXPENSE type and MONTHLY frequency', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const typeSelect = screen.getByLabelText(/^Type/i) as HTMLSelectElement;
      expect(typeSelect.value).toBe('EXPENSE');

      const freqSelect = screen.getByLabelText(/Frequency/i) as HTMLSelectElement;
      expect(freqSelect.value).toBe('MONTHLY');
    });

    it('should render optional end date field', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByLabelText(/End Date/i)).toBeInTheDocument();
    });

    it('should render notes field', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByLabelText(/Notes/i)).toBeInTheDocument();
    });
  });

  describe('Validation', () => {
    it('should show error when description is empty', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const descInput = screen.getByLabelText(/Description/i);
      await user.clear(descInput);

      const submitButton = screen.getByRole('button', { name: /create/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Description is required/i)).toBeInTheDocument();
      });
      expect(mockOnSubmit).not.toHaveBeenCalled();
    });

    it('should show error when amount is not positive', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      // Fill description to pass that validation
      const descInput = screen.getByLabelText(/Description/i);
      await user.type(descInput, 'Monthly rent');

      const submitButton = screen.getByRole('button', { name: /create/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/Amount must be positive/i)).toBeInTheDocument();
      });
    });
  });

  describe('Submission', () => {
    it('should call onCancel when Cancel button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalledTimes(1);
    });
  });

  describe('Loading state', () => {
    it('should disable Cancel button when isSubmitting is true', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isSubmitting={true}
        />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
    });

    it('should show "Saving..." text when isSubmitting is true', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isSubmitting={true}
        />
      );

      expect(screen.getByRole('button', { name: /saving/i })).toBeInTheDocument();
    });
  });

  describe('Error display', () => {
    it('should display error message when error prop is provided', () => {
      renderWithProviders(
        <RecurringTransactionForm
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          error="Failed to create recurring transaction"
        />
      );

      expect(screen.getByText(/Failed to create recurring transaction/i)).toBeInTheDocument();
    });
  });

  describe('Edit mode', () => {
    it('should show "Update" button when editing', () => {
      renderWithProviders(
        <RecurringTransactionForm
          recurringTransaction={{
            id: 1,
            accountId: 1,
            type: 'EXPENSE',
            amount: 1200,
            currency: 'USD',
            description: 'Monthly rent',
            frequency: 'MONTHLY',
            nextOccurrence: '2026-04-01',
            isActive: true,
            userId: 1,
            createdAt: '2026-01-01',
            updatedAt: '2026-01-01',
          }}
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      expect(screen.getByRole('button', { name: /update/i })).toBeInTheDocument();
    });

    it('should pre-populate form fields when editing', () => {
      renderWithProviders(
        <RecurringTransactionForm
          recurringTransaction={{
            id: 1,
            accountId: 1,
            type: 'EXPENSE',
            amount: 1200,
            currency: 'USD',
            description: 'Monthly rent',
            notes: 'Apartment rent',
            frequency: 'MONTHLY',
            nextOccurrence: '2026-04-01',
            endDate: '2026-12-31',
            isActive: true,
            userId: 1,
            createdAt: '2026-01-01',
            updatedAt: '2026-01-01',
          }}
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );

      const descInput = screen.getByLabelText(/Description/i) as HTMLInputElement;
      expect(descInput.value).toBe('Monthly rent');

      const amountInput = screen.getByLabelText(/Amount/i) as HTMLInputElement;
      expect(amountInput.value).toBe('1200');

      const freqSelect = screen.getByLabelText(/Frequency/i) as HTMLSelectElement;
      expect(freqSelect.value).toBe('MONTHLY');

      const nextOccInput = screen.getByLabelText(/Next Occurrence/i) as HTMLInputElement;
      expect(nextOccInput.value).toBe('2026-04-01');

      const endDateInput = screen.getByLabelText(/End Date/i) as HTMLInputElement;
      expect(endDateInput.value).toBe('2026-12-31');
    });
  });
});
