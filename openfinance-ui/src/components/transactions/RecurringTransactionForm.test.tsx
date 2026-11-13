import type { RecurringTransaction } from '@/types/recurringTransaction';
/**
 * RecurringTransactionForm Validation Tests
 * TASK-13.2.3: Form validation tests for RecurringTransactionForm component
 *
 * Tests the RecurringTransactionForm's Zod validation, rendering, and user interactions.
 * Complex child components (CategorySelect, PayeeSelector, AccountSelector) are mocked
 * to isolate form logic testing.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
} from '@/test/test-utils';
import { RecurringTransactionForm } from '@/components/transactions/RecurringTransactionForm';
import type { Account } from '@/types/account';
import type { Category } from '@/types/transaction';

vi.mock('@/hooks/useUserSettings', async importOriginal => {
  const actual = (await importOriginal()) as any;
  return {
    ...actual,
    useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' }, isLoading: false }),
  };
});

vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({
    id,
    value,
    onValueChange,
  }: {
    id: string;
    value: string;
    onValueChange: (value: string) => void;
  }) => (
    <select id={id} value={value} onChange={e => onValueChange(e.target.value)}>
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
    </select>
  ),
}));

// Mock complex child components
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="category-select"
      aria-label="Category"
      value={value || ''}
      onChange={e => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
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
      onChange={e => onValueChange(e.target.value || undefined)}
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
      onChange={e => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
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
    it('retains the exact recurring amount during a metadata edit', async () => {
      renderWithProviders(
        <RecurringTransactionForm
          recurringTransaction={
            {
              id: 1,
              accountId: 1,
              type: 'EXPENSE',
              amount: 10006858.91125675,
              exactValues: { amount: '10006858.9112567483' },
              currency: 'USD',
              description: 'Recurring expense',
              frequency: 'MONTHLY',
              nextOccurrence: '2026-04-01',
              isActive: true,
            } as RecurringTransaction
          }
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );
      await userEvent.setup().click(screen.getByRole('button', { name: /update/i }));
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ amount: '10006858.9112567483' })
        )
      );
    });

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
      expect(amountInput.value).toBe('1,200');

      const freqSelect = screen.getByLabelText(/Frequency/i) as HTMLSelectElement;
      expect(freqSelect.value).toBe('MONTHLY');

      const nextOccInput = screen.getByLabelText(/Next Occurrence/i) as HTMLInputElement;
      expect(nextOccInput.value).toBe('2026-04-01');

      const endDateInput = screen.getByLabelText(/End Date/i) as HTMLInputElement;
      expect(endDateInput.value).toBe('2026-12-31');
    });
  });
  describe('editing round trips', () => {
    const template: RecurringTransaction = {
      id: 10,
      accountId: 1,
      accountName: 'Checking',
      toAccountId: null,
      toAccountName: null,
      type: 'EXPENSE',
      amount: 100,
      currency: 'EUR',
      categoryId: 1,
      categoryName: 'Food',
      categoryIcon: null,
      categoryColor: null,
      payee: 'Shop',
      description: 'Subscription',
      notes: 'Notes',
      frequency: 'MONTHLY',
      frequencyDisplayName: 'Monthly',
      nextOccurrence: '2099-01-01',
      endDate: null,
      isActive: true,
      createdAt: '',
      updatedAt: '',
      isDue: false,
      daysUntilNext: 1,
      isEnded: false,
    };
    const renderEdit = (overrides: Partial<RecurringTransaction> = {}) =>
      renderWithProviders(
        <RecurringTransactionForm
          recurringTransaction={{ ...template, ...overrides }}
          accounts={mockAccounts}
          categories={mockCategories}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
        />
      );
    const save = () => userEvent.setup().click(screen.getByRole('button', { name: /update/i }));

    it('preserves EUR100 when renaming a template whose account is now USD', async () => {
      renderEdit();
      fireEvent.change(screen.getByRole('textbox', { name: /Description/i }), {
        target: { value: 'Renamed' },
      });
      expect(screen.getByLabelText(/Currency/i)).toHaveValue('EUR');
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            amount: '100',
            currency: 'EUR',
            description: 'Renamed',
            payee: 'Shop',
          })
        )
      );
    });

    it('allows changing the explicit currency without changing the entered amount', async () => {
      renderEdit();
      await userEvent.setup().selectOptions(screen.getByLabelText(/Currency/i), 'USD');
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ amount: '100', currency: 'USD' })
        )
      );
    });

    it('clears hidden category/payee when changing an expense to a transfer', async () => {
      renderEdit();
      await userEvent.setup().selectOptions(screen.getByLabelText(/^Type/i), 'TRANSFER');
      const accounts = screen.getAllByRole('combobox', { name: 'Account' });
      await userEvent.setup().selectOptions(accounts[1], '2');
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({
            type: 'TRANSFER',
            toAccountId: 2,
            categoryId: null,
            payee: null,
          })
        )
      );
    });

    it('clears the hidden destination when changing a transfer to an expense', async () => {
      renderEdit({ type: 'TRANSFER', toAccountId: 2, categoryId: null });
      await userEvent.setup().selectOptions(screen.getByLabelText(/^Type/i), 'EXPENSE');
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'EXPENSE', toAccountId: null })
        )
      );
    });

    it('allows an inclusive end date and clears notes', async () => {
      renderEdit();
      fireEvent.change(screen.getByLabelText(/End Date/i), {
        target: { value: template.nextOccurrence },
      });
      fireEvent.change(screen.getByLabelText(/Notes/i), { target: { value: '' } });
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ endDate: template.nextOccurrence, notes: null })
        )
      );
    });

    it('renames a completed schedule without rescheduling it', async () => {
      renderEdit({
        nextOccurrence: '2020-02-01',
        endDate: '2020-01-31',
        isActive: false,
        isEnded: true,
      });
      fireEvent.change(screen.getByLabelText(/Description/i), {
        target: { value: 'Old subscription renamed' },
      });
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ nextOccurrence: '2020-02-01', endDate: '2020-01-31' })
        )
      );
    });

    it('round-trips descriptions and notes supported by the API', async () => {
      renderEdit({ description: 'd'.repeat(800), notes: 'n'.repeat(1800) });
      await save();
      await waitFor(() =>
        expect(mockOnSubmit).toHaveBeenCalledWith(
          expect.objectContaining({ description: 'd'.repeat(800), notes: 'n'.repeat(1800) })
        )
      );
    });
  });
});
