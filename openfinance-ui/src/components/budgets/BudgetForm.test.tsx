/**
 * BudgetForm Validation Tests
 * TASK-13.2.3: Form validation tests for BudgetForm component
 *
 * Tests the BudgetForm's Zod validation, rendering, and user interactions.
 * Complex child components (CurrencySelector, CategorySelect) are mocked
 * to isolate form logic testing.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import { BudgetForm } from '@/components/budgets/BudgetForm';
import type { BudgetResponse } from '@/types/budget';

vi.mock('@/hooks/useUserSettings', async (importOriginal) => {
  const actual = await importOriginal() as any;
  return {
    ...actual,
    useUserSettings: () => ({ data: { dateFormat: 'YYYY-MM-DD' }, isLoading: false }),
  };
});

// Mock complex child components
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
      <option value="2">Transport</option>
    </select>
  ),
}));

const mockOnSubmit = vi.fn();
const mockOnCancel = vi.fn();

const mockBudget: BudgetResponse = {
  id: 1,
  categoryId: 1,
  categoryName: 'Food',
  amount: 500,
  spent: 200,
  remaining: 300,
  percentage: 40,
  currency: 'USD',
  period: 'MONTHLY',
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  rollover: false,
  notes: 'Monthly food budget',
  userId: 1,
  createdAt: '2026-01-01',
  updatedAt: '2026-01-01',
};

describe('BudgetForm', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    mockOnSubmit.mockClear();
    mockOnCancel.mockClear();
  });

  describe('Rendering', () => {
    it('should render all required form fields', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      // Category uses mocked CategorySelect with aria-label
      expect(screen.getByLabelText(/Category/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Budget Amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Budget Period/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Start Date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/End Date/i)).toBeInTheDocument();
    });

    it('should show "Create Budget" button for new budget', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /create budget/i })).toBeInTheDocument();
    });

    it('should show "Update Budget" button when editing', () => {
      renderWithProviders(
        <BudgetForm budget={mockBudget} onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /update budget/i })).toBeInTheDocument();
    });

    it('should show Cancel button', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    });

    it('should render budget period options', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const periodSelect = screen.getByLabelText(/Budget Period/i) as HTMLSelectElement;
      expect(periodSelect.options.length).toBe(4); // WEEKLY, MONTHLY, QUARTERLY, YEARLY
    });

    it('should default to Monthly period', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const periodSelect = screen.getByLabelText(/Budget Period/i) as HTMLSelectElement;
      expect(periodSelect.value).toBe('MONTHLY');
    });

    it('should render rollover checkbox', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByLabelText(/Rollover unused budget/i)).toBeInTheDocument();
    });

    it('should render notes field', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      expect(screen.getByLabelText(/Notes/i)).toBeInTheDocument();
    });
  });

  describe('Validation', () => {

    it('should show error when end date is before start date', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const startDateInput = screen.getByLabelText(/Start Date/i);
      const endDateInput = screen.getByLabelText(/End Date/i);

      // Set start date after end date
      fireEvent.change(startDateInput, { target: { value: '2026-12-31' } });
      fireEvent.change(endDateInput, { target: { value: '2026-01-01' } });

      // Fill amount to pass other validations
      const amountInput = screen.getByLabelText(/Budget Amount/i);
      await user.clear(amountInput);
      await user.type(amountInput, '100');

      const submitButton = screen.getByRole('button', { name: /create budget/i });
      await user.click(submitButton);

      await waitFor(() => {
        expect(screen.getByText(/End date must be at least 1 day after start date/i)).toBeInTheDocument();
      });
    });
  });

  describe('Submission', () => {
    it('should call onCancel when Cancel button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalledTimes(1);
    });
  });

  describe('Loading state', () => {
    it('should disable Cancel button when isLoading is true', () => {
      renderWithProviders(
        <BudgetForm onSubmit={mockOnSubmit} onCancel={mockOnCancel} isLoading={true} />
      );

      expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled();
    });
  });

  describe('Server error', () => {
    it('should display server error message when serverError is provided', () => {
      renderWithProviders(
        <BudgetForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          serverError="Budget already exists for this category"
        />
      );

      expect(screen.getByText(/Budget already exists for this category/i)).toBeInTheDocument();
    });
  });

  describe('Edit mode', () => {
    it('should pre-populate form fields when editing', () => {
      renderWithProviders(
        <BudgetForm budget={mockBudget} onSubmit={mockOnSubmit} onCancel={mockOnCancel} />
      );

      const amountInput = screen.getByLabelText(/Budget Amount/i) as HTMLInputElement;
      expect(amountInput.value).toBe('500');

      const periodSelect = screen.getByLabelText(/Budget Period/i) as HTMLSelectElement;
      expect(periodSelect.value).toBe('MONTHLY');

      const startDateInput = screen.getByLabelText(/Start Date/i) as HTMLInputElement;
      expect(startDateInput.value).toBe('2026-01-01');

      const endDateInput = screen.getByLabelText(/End Date/i) as HTMLInputElement;
      expect(endDateInput.value).toBe('2026-12-31');

      const notesInput = screen.getByLabelText(/Notes/i) as HTMLTextAreaElement;
      expect(notesInput.value).toBe('Monthly food budget');
    });
  });
});
