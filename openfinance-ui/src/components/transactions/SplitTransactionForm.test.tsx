/**
 * Unit tests for SplitTransactionForm component
 *
 * Tests split entry add/remove, running total display, validation error when sums don't match,
 * "Add split" button, category/amount/description inputs.
 */
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { act } from 'react';
import { SplitTransactionForm } from './SplitTransactionForm';
import * as useTransactionsModule from '@/hooks/useTransactions';
import { renderWithProviders } from '@/test/test-utils';
import type { TransactionSplitRequest } from '@/types/transaction';

// ── Polyfills ─────────────────────────────────────────────────────────────────

beforeAll(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
});

// ── Mock heavy sub-components ─────────────────────────────────────────────────

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    value,
    onValueChange,
    placeholder,
    type,
    allowNone,
  }: {
    value?: number;
    onValueChange: (v: number | undefined) => void;
    placeholder?: string;
    type?: string;
    allowNone?: boolean;
  }) => (
    <select
      data-testid="category-select"
      value={value ?? ''}
      onChange={(e) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
      placeholder={placeholder}
    >
      <option value="">-- no category --</option>
      <option value="10">Shopping (EXPENSE)</option>
      <option value="20">Salary (INCOME)</option>
      <option value="30">Entertainment (EXPENSE)</option>
    </select>
  ),
}));

vi.mock('@/utils/format', () => ({
  formatCurrency: vi.fn((amount: number, currency: string) => {
    const symbol = currency === 'USD' ? '$' : '€';
    return `${symbol}${amount.toFixed(2)}`;
  }),
}));

// ── Mock hooks ────────────────────────────────────────────────────────────────

vi.mock('@/hooks/useTransactions', async (importOriginal) => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return { ...actual, useCategoryTree: vi.fn() };
});

// ── Typed mock references ─────────────────────────────────────────────────────

const mockUseCategoryTree = vi.mocked(useTransactionsModule.useCategoryTree);

// ── Fixtures ──────────────────────────────────────────────────────────────────

const mockSplits: TransactionSplitRequest[] = [
  {
    categoryId: 10,
    amount: 50,
    description: 'Groceries',
  },
  {
    categoryId: 30,
    amount: 25,
    description: 'Entertainment',
  },
];

// ── Helpers ───────────────────────────────────────────────────────────────────

interface RenderFormOptions {
  totalAmount?: number;
  currency?: string;
  transactionType?: 'INCOME' | 'EXPENSE';
  splits?: TransactionSplitRequest[];
  onChange?: ReturnType<typeof vi.fn>;
}

function renderForm({
  totalAmount = 100,
  currency = 'EUR',
  transactionType = 'EXPENSE',
  splits = mockSplits,
  onChange = vi.fn(),
}: RenderFormOptions = {}) {
  renderWithProviders(
    <SplitTransactionForm
      totalAmount={totalAmount}
      currency={currency}
      transactionType={transactionType}
      splits={splits}
      onChange={onChange}
    />
  );
  return { onChange };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('SplitTransactionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockUseCategoryTree.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useTransactionsModule.useCategoryTree>);
  });

  describe('Basic Rendering', () => {
    it('renders split entries with category, amount, and description inputs', () => {
      renderForm();

      expect(screen.getAllByTestId('category-select')).toHaveLength(2);
      expect(screen.getByLabelText(/Split 1 amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Split 1 description/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Split 2 amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Split 2 description/i)).toBeInTheDocument();
    });

    it('renders the "Add split" button', () => {
      renderForm();

      expect(screen.getByRole('button', { name: /add split/i })).toBeInTheDocument();
    });

    it('renders running total summary', () => {
      renderForm();

      expect(screen.getByText('Transaction total')).toBeInTheDocument();
      expect(screen.getByText('Split total')).toBeInTheDocument();
      expect(screen.getByText('Remaining')).toBeInTheDocument();
    });

    it('displays transaction total in formatted currency', () => {
      renderForm({ totalAmount: 123.45, currency: 'USD' });

      expect(screen.getAllByText('$123.45')).toHaveLength(2); // Summary and error banner
    });

    it('displays split total in formatted currency', () => {
      renderForm();

      expect(screen.getByText('€75.00')).toBeInTheDocument();
    });
  });

  describe('Split Entry Management', () => {
    it('renders the correct number of split entries', () => {
      const customSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 30, description: 'Test 1' },
        { categoryId: 20, amount: 40, description: 'Test 2' },
        { categoryId: 30, amount: 30, description: 'Test 3' },
      ];

      renderForm({ splits: customSplits });

      expect(screen.getByLabelText(/Split 1 amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Split 2 amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Split 3 amount/i)).toBeInTheDocument();
    });

    it('disables remove button when there are only 2 splits', () => {
      renderForm({ splits: mockSplits });

      const removeButtons = screen.getAllByRole('button', { name: /remove split/i });
      expect(removeButtons).toHaveLength(2);
      removeButtons.forEach(button => {
        expect(button).toBeDisabled();
      });
    });

    it('enables remove button when there are more than 2 splits', () => {
      const threeSplits = [
        ...mockSplits,
        { categoryId: undefined, amount: 25, description: 'Third split' },
      ];

      renderForm({ splits: threeSplits });

      const removeButtons = screen.getAllByRole('button', { name: /remove split/i });
      expect(removeButtons).toHaveLength(3);
      removeButtons.forEach(button => {
        expect(button).not.toBeDisabled();
      });
    });

    it('calls onChange when adding a new split', () => {
      const { onChange } = renderForm({ splits: mockSplits });

      const addButton = screen.getByRole('button', { name: /add split/i });
      addButton.click();

      expect(onChange).toHaveBeenCalledWith([
        ...mockSplits,
        {
          categoryId: undefined,
          amount: 25, // remaining amount
          description: undefined,
        },
      ]);
    });

    it('calls onChange when removing a split', () => {
      const threeSplits = [
        ...mockSplits,
        { categoryId: undefined, amount: 25, description: 'Third split' },
      ];
      const { onChange } = renderForm({ splits: threeSplits });

      const removeButtons = screen.getAllByRole('button', { name: /remove split/i });
      removeButtons[0].click(); // Remove first split

      expect(onChange).toHaveBeenCalledWith([
        mockSplits[1],
        threeSplits[2],
      ]);
    });

    it('pre-populates new split with remaining amount when adding', () => {
      const { onChange } = renderForm({ totalAmount: 100, splits: [{ categoryId: 10, amount: 30 }] });

      const addButton = screen.getByRole('button', { name: /add split/i });
      addButton.click();

      expect(onChange).toHaveBeenCalledWith([
        { categoryId: 10, amount: 30 },
        {
          categoryId: undefined,
          amount: 70, // remaining amount rounded to 2 decimals
          description: undefined,
        },
      ]);
    });

    it('pre-populates new split with the precisely-rounded remaining amount (no float rounding-boundary bug)', () => {
      // Regression test: Math.round(1.005 * 100) / 100 === 1 (not 1.01) in plain JS floats,
      // because 1.005 * 100 === 100.49999999999999. The remaining amount must be rounded
      // half-away-from-zero (matching the backend's BigDecimal HALF_UP), not truncated.
      const { onChange } = renderForm({
        totalAmount: 1.005,
        splits: [{ categoryId: 10, amount: 0 }],
      });

      const addButton = screen.getByRole('button', { name: /add split/i });
      addButton.click();

      expect(onChange).toHaveBeenCalledWith([
        { categoryId: 10, amount: 0 },
        {
          categoryId: undefined,
          amount: 1.01,
          description: undefined,
        },
      ]);
    });

    it('pre-populates new split with 0 when total is exceeded', () => {
      const { onChange } = renderForm({ totalAmount: 50, splits: [{ categoryId: 10, amount: 60 }] });

      const addButton = screen.getByRole('button', { name: /add split/i });
      addButton.click();

      expect(onChange).toHaveBeenCalledWith([
        { categoryId: 10, amount: 60 },
        {
          categoryId: undefined,
          amount: 0,
          description: undefined,
        },
      ]);
    });
  });

  describe('Field Updates', () => {
    it('calls onChange when category is updated', async () => {
      const { onChange } = renderForm();

      const categorySelects = screen.getAllByTestId('category-select');
      const firstCategorySelect = categorySelects[0] as HTMLSelectElement;

      await act(async () => {
        firstCategorySelect.value = '20';
        firstCategorySelect.dispatchEvent(new Event('change', { bubbles: true }));
      });

      expect(onChange).toHaveBeenCalledWith([
        { ...mockSplits[0], categoryId: 20 },
        mockSplits[1],
      ]);
    });

    it('calls onChange when amount is updated', () => {
      const { onChange } = renderForm();

      const amountInput = screen.getByLabelText(/Split 1 amount/i);

      fireEvent.change(amountInput, { target: { value: '75' } });

      expect(onChange).toHaveBeenCalledWith([
        { ...mockSplits[0], amount: 75 },
        mockSplits[1],
      ]);
    });

    it('calls onChange when description is updated', () => {
      const { onChange } = renderForm();

      const descriptionInput = screen.getByLabelText(/Split 1 description/i);

      fireEvent.change(descriptionInput, { target: { value: 'Updated description' } });

      expect(onChange).toHaveBeenCalledWith([
        { ...mockSplits[0], description: 'Updated description' },
        mockSplits[1],
      ]);
    });

    it('converts empty string amount to 0', () => {
      const { onChange } = renderForm();

      const amountInput = screen.getByLabelText(/Split 1 amount/i);

      fireEvent.change(amountInput, { target: { value: '' } });

      expect(onChange).toHaveBeenCalledWith([
        { ...mockSplits[0], amount: 0 },
        mockSplits[1],
      ]);
    });

    it('converts undefined description to undefined', () => {
      const { onChange } = renderForm();

      const descriptionInput = screen.getByLabelText(/Split 1 description/i);

      fireEvent.change(descriptionInput, { target: { value: '' } });

      expect(onChange).toHaveBeenCalledWith([
        { ...mockSplits[0], description: undefined },
        mockSplits[1],
      ]);
    });
  });

  describe('Running Total and Validation', () => {
    it('shows "Balanced" when split total equals transaction total', () => {
      renderForm({ totalAmount: 75, splits: mockSplits });

      expect(screen.getByText('Balanced')).toBeInTheDocument();
      expect(screen.getByText('✓')).toBeInTheDocument();
    });

    it('shows "Remaining" with positive amount when split total is less than transaction total', () => {
      renderForm({ totalAmount: 100, splits: mockSplits });

      expect(screen.getByText('Remaining')).toBeInTheDocument();
      expect(screen.getAllByText('€25.00')).toHaveLength(2); // Summary and error banner
    });

    it('shows "Over by" with negative amount when split total exceeds transaction total', () => {
      renderForm({ totalAmount: 50, splits: mockSplits });

      expect(screen.getByText('Over by')).toBeInTheDocument();
      expect(screen.getAllByText('€25.00')).toHaveLength(2); // Summary and error banner
    });

    it('applies success styling when balanced', () => {
      renderForm({ totalAmount: 75, splits: mockSplits });

      const statusElement = screen.getByText('Balanced').closest('div');
      expect(statusElement).toHaveClass('text-success');
    });

    it('applies warning styling when remaining', () => {
      renderForm({ totalAmount: 100, splits: mockSplits });

      const statusElement = screen.getByText('Remaining').closest('div');
      expect(statusElement).toHaveClass('text-warning');
    });

    it('applies error styling when over', () => {
      renderForm({ totalAmount: 50, splits: mockSplits });

      const statusElement = screen.getByText('Over by').closest('div');
      expect(statusElement).toHaveClass('text-error');
    });

    it('shows validation error banner when splits do not balance', () => {
      renderForm({ totalAmount: 100, splits: mockSplits });

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText((content) => content.includes('short.'))).toBeInTheDocument();
    });

    it('shows validation error banner when over by amount', () => {
      renderForm({ totalAmount: 50, splits: mockSplits });

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText((content) => content.includes('over.'))).toBeInTheDocument();
    });

    it('does not show validation error banner when balanced', () => {
      renderForm({ totalAmount: 75, splits: mockSplits });

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('does not show validation error banner when splits are empty', () => {
      renderForm({ splits: [] });

      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('handles empty splits array', () => {
      renderForm({ splits: [] });

      expect(screen.queryByLabelText(/Split 1 amount/i)).not.toBeInTheDocument();
      expect(screen.getByText('€0.00')).toBeInTheDocument(); // Split total
    });

    it('handles splits with zero amounts', () => {
      const zeroSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 0, description: 'Zero amount' },
        { categoryId: 20, amount: 50, description: 'Normal amount' },
      ];

      renderForm({ splits: zeroSplits });

      expect(screen.getByDisplayValue('')).toBeInTheDocument(); // Zero amount shows as empty string
      expect(screen.getByDisplayValue('50')).toBeInTheDocument();
    });

    it('handles splits without category', () => {
      const noCategorySplits: TransactionSplitRequest[] = [
        { amount: 50, description: 'No category' },
      ];

      renderForm({ splits: noCategorySplits });

      const categorySelect = screen.getByTestId('category-select') as HTMLSelectElement;
      expect(categorySelect.value).toBe('');
    });

    it('handles splits without description', () => {
      const noDescriptionSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 50 },
      ];

      renderForm({ splits: noDescriptionSplits });

      const descriptionInput = screen.getByLabelText(/Split 1 description/i) as HTMLInputElement;
      expect(descriptionInput.value).toBe('');
    });

    it('handles very small decimal amounts', () => {
      const smallDecimalSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 0.01, description: 'Penny' },
        { categoryId: 20, amount: 0.02, description: 'Two cents' },
      ];

      renderForm({ totalAmount: 0.03, splits: smallDecimalSplits });

      expect(screen.getByText('Balanced')).toBeInTheDocument();
    });

    it('handles tolerance for floating point precision (±0.01)', () => {
      // 0.1 + 0.2 = 0.30000000000000004 in JS, but should be considered equal within tolerance
      const floatingPointSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 0.1 },
        { categoryId: 20, amount: 0.2 },
      ];

      renderForm({ totalAmount: 0.3, splits: floatingPointSplits });

      expect(screen.getByText('Balanced')).toBeInTheDocument();
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('shows error when slightly over tolerance', () => {
      const overToleranceSplits: TransactionSplitRequest[] = [
        { categoryId: 10, amount: 50.02 }, // 50.02 - 50.00 = 0.02 > 0.01 tolerance
      ];

      renderForm({ totalAmount: 50, splits: overToleranceSplits });

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText((content) => content.includes('over.'))).toBeInTheDocument();
    });
  });
});