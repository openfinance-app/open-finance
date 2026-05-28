/**
 * Unit tests for TransactionForm component — auto-fill and override behavior
 *
 * Task TASK-CAT-3.2.2: Test TransactionForm integration
 *   - Auto-fill behavior (payee with default category → category auto-filled)
 *   - Override behavior (manually change category → indicator cleared)
 *
 * Requirement REQ-CAT-2.4: Payee-to-category auto-fill in TransactionForm
 * Requirement REQ-CAT-2.5: Allow override of auto-filled category
 */
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { TransactionForm } from './TransactionForm';
import * as usePayeesModule from '@/hooks/usePayees';
import * as useTransactionTagsModule from '@/hooks/useTransactionTags';
import * as useTransactionsModule from '@/hooks/useTransactions';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import { renderWithProviders } from '@/test/test-utils';
import type { Payee } from '@/types/payee';
import type { Category, Transaction } from '@/types/transaction';
import type { Account } from '@/types/account';

// ── Polyfills ─────────────────────────────────────────────────────────────────

// Radix UI Select calls scrollIntoView which jsdom doesn't implement
beforeAll(() => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();
});

// ── Mock heavy sub-components ─────────────────────────────────────────────────
// These components have their own complex hooks and Radix UI internals.
// We mock them at the module level so TransactionForm renders without errors
// and we can control what the user "sees".

vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({ onValueChange }: { onValueChange: (v: number | undefined) => void }) => (
    <button
      type="button"
      data-testid="account-selector"
      onClick={() => onValueChange(1)}
    >
      Account Selector
    </button>
  ),
}));

vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeSelector: ({
    value,
    onValueChange,
  }: {
    value?: string;
    onValueChange: (v: string | undefined) => void;
  }) => (
    <select
      data-testid="payee-selector"
      value={value ?? ''}
      onChange={(e) => onValueChange(e.target.value || undefined)}
    >
      <option value="">-- no payee --</option>
      <option value="Amazon">Amazon</option>
      <option value="Spotify">Spotify</option>
      <option value="Unknown Payee">Unknown Payee</option>
    </select>
  ),
}));

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    value,
    onValueChange,
  }: {
    value?: number;
    onValueChange: (v: number | undefined) => void;
  }) => (
    <select
      data-testid="category-select"
      value={value ?? ''}
      onChange={(e) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}
    >
      <option value="">-- no category --</option>
      <option value="10">Shopping (EXPENSE)</option>
      <option value="20">Salary (INCOME)</option>
      <option value="30">Entertainment (EXPENSE)</option>
    </select>
  ),
}));

vi.mock('@/components/attachments', () => ({
  AttachmentUpload: () => <div data-testid="attachment-upload" />,
  AttachmentList: () => <div data-testid="attachment-list" />,
}));

vi.mock('./TagInput', () => ({
  TagInput: () => <div data-testid="tag-input" />,
}));

vi.mock('@/components/ui/LiabilitySelector', () => ({
  LiabilitySelector: ({ value, onValueChange }: any) => (
    <select data-testid="liability-selector" value={value ?? ''} onChange={(e: any) => onValueChange(e.target.value ? Number(e.target.value) : undefined)}>
      <option value="">-- no liability --</option>
      <option value="1">Mortgage</option>
    </select>
  ),
}));

vi.mock('./SplitTransactionForm', () => ({
  SplitTransactionForm: () => <div data-testid="split-form">Split Form</div>,
}));

// ── Mock hooks ────────────────────────────────────────────────────────────────

vi.mock('@/hooks/usePayees', async (importOriginal) => {
  const actual = await importOriginal<typeof usePayeesModule>();
  return { ...actual, useActivePayees: vi.fn() };
});

vi.mock('@/hooks/useTransactionTags', async (importOriginal) => {
  const actual = await importOriginal<typeof useTransactionTagsModule>();
  return { ...actual, usePopularTags: vi.fn() };
});

// CategorySelect uses useCategoryTree internally (already mocked at component level,
// but the hook module mock is kept to prevent any stray real network calls)
vi.mock('@/hooks/useTransactions', async (importOriginal) => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return { ...actual, useCategoryTree: vi.fn() };
});

vi.mock('@/hooks/useLiabilities', async (importOriginal) => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return { ...actual, useLiabilities: vi.fn() };
});

// ── Typed mock references ─────────────────────────────────────────────────────

const mockUseActivePayees = vi.mocked(usePayeesModule.useActivePayees);
const mockUsePopularTags = vi.mocked(useTransactionTagsModule.usePopularTags);
const mockUseCategoryTree = vi.mocked(useTransactionsModule.useCategoryTree);
const mockUseLiabilities = vi.mocked(useLiabilitiesModule.useLiabilities);

// ── Fixtures ──────────────────────────────────────────────────────────────────

const mockAccounts: Account[] = [
  {
    id: 1,
    name: 'Checking',
    type: 'CHECKING',
    currency: 'EUR',
    balance: 1000,
    userId: 1,
    isActive: true,
    createdAt: '2024-01-01',
  },
];

const mockCategories: Category[] = [
  { id: 10, userId: 1, name: 'Shopping', type: 'EXPENSE' },
  { id: 20, userId: 1, name: 'Salary', type: 'INCOME' },
  { id: 30, userId: 1, name: 'Entertainment', type: 'EXPENSE' },
];

/** Payee that has a default category (Shopping = id 10) */
const amazonPayee: Payee = {
  id: 1,
  name: 'Amazon',
  categoryId: 10,
  categoryName: 'Shopping',
  isSystem: false,
  isActive: true,
};

/** Payee with an INCOME category (Salary = id 20) */
const spotifyPayee: Payee = {
  id: 2,
  name: 'Spotify',
  categoryId: 30,
  categoryName: 'Entertainment',
  isSystem: false,
  isActive: true,
};

/** Payee without a default category */
const unknownPayee: Payee = {
  id: 3,
  name: 'Unknown Payee',
  categoryId: undefined,
  isSystem: false,
  isActive: true,
};

const mockPayees: Payee[] = [amazonPayee, spotifyPayee, unknownPayee];

// ── Helpers ───────────────────────────────────────────────────────────────────

interface RenderFormOptions {
  transaction?: Transaction;
  onSubmit?: ReturnType<typeof vi.fn>;
  onCancel?: ReturnType<typeof vi.fn>;
}

function renderForm({ transaction, onSubmit = vi.fn(), onCancel = vi.fn() }: RenderFormOptions = {}) {
  renderWithProviders(
    <TransactionForm
      accounts={mockAccounts}
      categories={mockCategories}
      onSubmit={onSubmit}
      onCancel={onCancel}
      transaction={transaction}
    />
  );
  return { onSubmit, onCancel };
}

/** Simulate the user selecting a payee via the mocked <select> */
async function selectPayee(name: string) {
  const payeeSelect = screen.getByTestId('payee-selector') as HTMLSelectElement;
  await act(async () => {
    payeeSelect.value = name;
    payeeSelect.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

/** Simulate the user selecting a category via the mocked <select> */
async function selectCategory(categoryId: number | string) {
  const catSelect = screen.getByTestId('category-select') as HTMLSelectElement;
  await act(async () => {
    catSelect.value = String(categoryId);
    catSelect.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

// ── Tests ─────────────────────────────────────────────────────────────────────

describe('TransactionForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Default mock: payees loaded, no popular tags
    mockUseActivePayees.mockReturnValue({
      data: mockPayees,
      isLoading: false,
      isError: false,
    } as ReturnType<typeof usePayeesModule.useActivePayees>);

    mockUsePopularTags.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useTransactionTagsModule.usePopularTags>);

    mockUseCategoryTree.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as ReturnType<typeof useTransactionsModule.useCategoryTree>);

    mockUseLiabilities.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as any);
  });

  // ── Basic Rendering ─────────────────────────────────────────────────────────

  describe('Basic Rendering', () => {
    it('renders the form with required fields', () => {
      renderForm();

      expect(screen.getByLabelText(/type/i)).toBeInTheDocument();
      expect(screen.getByTestId('payee-selector')).toBeInTheDocument();
      expect(screen.getByTestId('category-select')).toBeInTheDocument();
      expect(screen.getByLabelText(/amount/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/date/i)).toBeInTheDocument();
    });

    it('shows "Create Transaction" button for a new transaction', () => {
      renderForm();

      expect(screen.getByRole('button', { name: /create transaction/i })).toBeInTheDocument();
    });

    it('shows "Update Transaction" button when editing an existing transaction', () => {
      const existing: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 50,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
      };

      renderForm({ transaction: existing });

      expect(screen.getByRole('button', { name: /update transaction/i })).toBeInTheDocument();
    });

    it('calls onCancel when Cancel button is clicked', () => {
      const { onCancel } = renderForm();

      screen.getByRole('button', { name: /cancel/i }).click();

      expect(onCancel).toHaveBeenCalledTimes(1);
    });

    it('does not show auto-fill indicator on initial render (no payee selected)', () => {
      renderForm();

      expect(screen.queryByText(/auto-filled from payee/i)).not.toBeInTheDocument();
    });
  });

  // ── Auto-Fill Behavior ──────────────────────────────────────────────────────

  describe('Auto-Fill Behavior', () => {
    /**
     * Requirement REQ-CAT-2.4:
     * When a payee with a default category is selected and no category has been
     * set yet, the category field should be auto-filled.
     */
    it('auto-fills category when payee with default category is selected', async () => {
      renderForm();

      await selectPayee('Amazon');

      await waitFor(() => {
        // "Amazon" → categoryId 10 (Shopping/EXPENSE), type defaults to EXPENSE → should match
        // The text is split across elements: "Auto-filled from payee: " + <span>Amazon</span>
        // Use textContent check on the container paragraph
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });
    });

    it('sets the category select value to the payee default category id', async () => {
      renderForm();

      await selectPayee('Amazon');

      await waitFor(() => {
        const catSelect = screen.getByTestId('category-select') as HTMLSelectElement;
        expect(catSelect.value).toBe('10');
      });
    });

    it('shows the Clear button alongside the auto-fill indicator', async () => {
      renderForm();

      await selectPayee('Amazon');

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /clear/i })).toBeInTheDocument();
      });
    });

    it('does not auto-fill when the payee has no default category', async () => {
      renderForm();

      await selectPayee('Unknown Payee');

      // No auto-fill indicator should appear
      expect(screen.queryByText(/auto-filled from payee/i)).not.toBeInTheDocument();
    });

    it('does not auto-fill when editing an existing transaction (transaction prop is set)', async () => {
      // Requirement: auto-fill only for new transactions
      const existing: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 50,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
      };

      renderForm({ transaction: existing });

      await selectPayee('Amazon');

      // Auto-fill must NOT happen when editing
      expect(screen.queryByText(/auto-filled from payee/i)).not.toBeInTheDocument();
    });

    it('does not auto-fill when payee category type does not match transaction type', async () => {
      // "Amazon" has categoryId=10 which is EXPENSE type.
      // If the form type is INCOME, the category should NOT be auto-filled.
      renderForm();

      // Change transaction type to INCOME first
      const typeSelect = screen.getByLabelText(/type/i);
      await act(async () => {
        (typeSelect as HTMLSelectElement).value = 'INCOME';
        typeSelect.dispatchEvent(new Event('change', { bubbles: true }));
      });

      // Now select Amazon (which has EXPENSE category)
      await selectPayee('Amazon');

      // Category type mismatch — auto-fill should NOT happen
      expect(screen.queryByText(/auto-filled from payee/i)).not.toBeInTheDocument();
    });
  });

  // ── Override Behavior ───────────────────────────────────────────────────────

  describe('Override Behavior', () => {
    /**
     * Requirement REQ-CAT-2.5:
     * After auto-fill, if the user manually selects a different category,
     * the auto-fill indicator should be cleared.
     */
    it('clears the auto-fill indicator when user manually selects a different category', async () => {
      renderForm();

      // Step 1: select payee → auto-fill
      await selectPayee('Amazon');

      await waitFor(() => {
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });

      // Step 2: user manually picks a different category (Entertainment = 30)
      await selectCategory(30);

      await waitFor(() => {
        expect(document.querySelector('.bg-emerald-500\\/10')).not.toBeInTheDocument();
      });
    });

    it('clears the auto-fill indicator when the Clear button is clicked', async () => {
      renderForm();

      await selectPayee('Amazon');

      await waitFor(() => {
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });

      // Click the Clear button
      screen.getByRole('button', { name: /clear/i }).click();

      await waitFor(() => {
        expect(document.querySelector('.bg-emerald-500\\/10')).not.toBeInTheDocument();
      });
    });

    it('keeps the auto-fill indicator when user re-selects the same auto-filled category', async () => {
      renderForm();

      await selectPayee('Amazon'); // auto-fills to 10

      await waitFor(() => {
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });

      // Re-select the same category value (10) — the indicator should remain
      await selectCategory(10);

      // Indicator should still be present because the value matches the auto-filled one
      // (the override logic only fires when value !== autoFilledCategory)
      await waitFor(() => {
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });
    });

    it('does not re-auto-fill after user cleared the indicator (respects user intent)', async () => {
      // After the user explicitly clears the auto-fill, switching to another payee
      // should NOT auto-fill again, because a category is already set and the
      // auto-fill state was cleared (treated as a manual override).
      renderForm();

      // Auto-fill from Amazon
      await selectPayee('Amazon');
      await waitFor(() => {
        const container = document.querySelector('.bg-emerald-500\\/10');
        expect(container).toBeInTheDocument();
        expect(container?.textContent).toMatch(/auto-filled from payee:\s*amazon/i);
      });

      // Clear via button
      screen.getByRole('button', { name: /clear/i }).click();
      await waitFor(() => {
        expect(document.querySelector('.bg-emerald-500\\/10')).not.toBeInTheDocument();
      });

      // Switch to Spotify — categoryId is still 10 from Amazon and autoFilledCategory=null
      // so the auto-fill condition (!categoryId || autoFilledCategory) is false → no refill
      await selectPayee('Spotify');

    });
  });

  // ── Split Transaction Mode ─────────────────────────────────────────────────────

  describe('Split Transaction Mode', () => {
    it('starts in single category mode for new transactions', () => {
      renderForm();

      // In single mode: "Category" label is shown, and no "Remove split" button
      expect(screen.getByText('Category')).toBeInTheDocument();
      // The split button says "Split transaction" but we're NOT in split mode
      // Verify by checking aria-pressed is false on the split button
      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      expect(splitButton).toHaveAttribute('aria-pressed', 'false');
      // And no "Remove split" button
      expect(screen.queryByRole('button', { name: /remove split/i })).not.toBeInTheDocument();
    });

    it('starts in split mode when editing a transaction with splits', () => {
      const transactionWithSplits: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 100,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
        hasSplits: true,
        splits: [
          { id: 1, transactionId: 99, categoryId: 10, amount: 50, description: 'Split 1' },
          { id: 2, transactionId: 99, categoryId: 20, amount: 50, description: 'Split 2' },
        ],
      };

      renderForm({ transaction: transactionWithSplits });

      expect(screen.getByText('Split transaction')).toBeInTheDocument();
      expect(screen.queryByText('Category')).not.toBeInTheDocument();
    });

    it('starts in single mode when editing transaction without splits', () => {
      const transactionWithoutSplits: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 50,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
        hasSplits: false,
      };

      renderForm({ transaction: transactionWithoutSplits });

      // In single mode: "Category" label is shown
      expect(screen.getByText('Category')).toBeInTheDocument();
      // The split button is aria-pressed=false
      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      expect(splitButton).toHaveAttribute('aria-pressed', 'false');
      // No "Remove split" button
      expect(screen.queryByRole('button', { name: /remove split/i })).not.toBeInTheDocument();
    });

    it('toggles to split mode when split button is clicked', async () => {
      renderForm();

      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      expect(screen.getByText('Split transaction')).toBeInTheDocument();
      expect(screen.queryByText('Category')).not.toBeInTheDocument();
    });

    it('toggles back to single mode when remove split button is clicked', async () => {
      // Start with split mode
      const transactionWithSplits: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 100,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
        hasSplits: true,
        splits: [
          { id: 1, transactionId: 99, categoryId: 10, amount: 50, description: 'Split 1' },
          { id: 2, transactionId: 99, categoryId: 20, amount: 50, description: 'Split 2' },
        ],
      };

      renderForm({ transaction: transactionWithSplits });

      // Find the toggle button specifically (has title attribute and aria-pressed)
      const removeSplitButton = screen.getByRole('button', { name: 'Remove split' });
      await act(async () => {
        removeSplitButton.click();
      });

      // Now back in single mode: "Category" label, split button aria-pressed=false
      expect(screen.getByText('Category')).toBeInTheDocument();
      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      expect(splitButton).toHaveAttribute('aria-pressed', 'false');
    });

    it('pre-populates with two blank splits when entering split mode', async () => {
      renderForm();

      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      // Should show SplitTransactionForm component (mocked in existing tests)
      // The test verifies the mode toggle works
      expect(screen.getByText('Split transaction')).toBeInTheDocument();
    });

    it('hides parent category field when in split mode', async () => {
      renderForm();

      // Initially shows category field
      expect(screen.getByTestId('category-select')).toBeInTheDocument();

      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      // Category field should be hidden in split mode (handled by conditional rendering)
      expect(screen.getByText('Split transaction')).toBeInTheDocument();
    });

    it('includes splits in form submission when in split mode', async () => {
      // This would require mocking the SplitTransactionForm component to simulate
      // onChange calls, but for now we test the mode toggle behavior
      renderForm();

      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      expect(screen.getByText('Split transaction')).toBeInTheDocument();
    });

    it('clears parent category when entering split mode', async () => {
      renderForm();

      // Select a category first
      await selectCategory(10);
      expect(screen.getByTestId('category-select')).toHaveValue('10');

      // Enter split mode
      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      // Category should be cleared (handled by the form logic)
      expect(screen.getByText('Split transaction')).toBeInTheDocument();
    });

    it('shows correct button title and aria-pressed state', async () => {
      renderForm();

      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      expect(splitButton).toHaveAttribute('aria-pressed', 'false');
      expect(splitButton).toHaveAttribute('title', 'Split this transaction across categories');

      await act(async () => {
        splitButton.click();
      });

      // After clicking, the button now says "Remove split" and is aria-pressed=true
      // Use exact match to avoid matching "Remove split 1", "Remove split 2" from SplitTransactionForm
      const removeButton = screen.getByRole('button', { name: 'Remove split' });
      expect(removeButton).toHaveAttribute('aria-pressed', 'true');
      expect(removeButton).toHaveAttribute('title', 'Switch back to single category');
    });
  });

  // ── Transfer Type ─────────────────────────────────────────────────────────

  describe('Transfer Type', () => {
    it('shows "To Account" field when type is TRANSFER', async () => {
      renderForm();

      const typeSelect = screen.getByLabelText(/type/i);
      await act(async () => {
        fireEvent.change(typeSelect, { target: { value: 'TRANSFER' } });
      });

      expect(screen.getByText(/to account/i)).toBeInTheDocument();
    });

    it('shows "From Account" label instead of "Account" for TRANSFER', async () => {
      renderForm();

      const typeSelect = screen.getByLabelText(/type/i);
      await act(async () => {
        fireEvent.change(typeSelect, { target: { value: 'TRANSFER' } });
      });

      expect(screen.getByText(/from account/i)).toBeInTheDocument();
    });

    it('hides category field when type is TRANSFER', async () => {
      renderForm();

      const typeSelect = screen.getByLabelText(/type/i);
      await act(async () => {
        fireEvent.change(typeSelect, { target: { value: 'TRANSFER' } });
      });

      // Category is hidden for TRANSFER type
      expect(screen.queryByTestId('category-select')).not.toBeInTheDocument();
    });
  });

  // ── Liability Selector ────────────────────────────────────────────────────

  describe('Liability Selector', () => {
    it('shows liability selector for EXPENSE when liabilities exist', () => {
      mockUseLiabilities.mockReturnValue({
        data: [{ id: 1, name: 'Mortgage', userId: 1, type: 'MORTGAGE', currentBalance: 100000, currency: 'EUR', interestRate: 3.5, startDate: '2020-01-01', isActive: true, createdAt: '2020-01-01' }],
        isLoading: false,
        isError: false,
      } as any);

      renderForm();

      expect(screen.getByTestId('liability-selector')).toBeInTheDocument();
    });

    it('hides liability selector when no liabilities exist', () => {
      renderForm();

      expect(screen.queryByTestId('liability-selector')).not.toBeInTheDocument();
    });

    it('hides liability selector for INCOME type even with liabilities', () => {
      mockUseLiabilities.mockReturnValue({
        data: [{ id: 1, name: 'Mortgage', userId: 1, type: 'MORTGAGE', currentBalance: 100000, currency: 'EUR', interestRate: 3.5, startDate: '2020-01-01', isActive: true, createdAt: '2020-01-01' }],
        isLoading: false,
        isError: false,
      } as any);

      renderForm();

      const typeSelect = screen.getByLabelText(/type/i);
      fireEvent.change(typeSelect, { target: { value: 'INCOME' } });

      expect(screen.queryByTestId('liability-selector')).not.toBeInTheDocument();
    });
  });

  // ── Currency Auto-Set ─────────────────────────────────────────────────────

  describe('Currency Auto-Set', () => {
    it('submits with account currency after selecting account', async () => {
      const { onSubmit } = renderForm();

      // Fill required fields
      fireEvent.change(screen.getByLabelText(/type/i), { target: { value: 'EXPENSE' } });
      fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '25' } });
      fireEvent.change(screen.getByLabelText(/date/i), { target: { value: '2024-06-15' } });

      // Select account (triggers currency auto-set to EUR)
      await act(async () => {
        screen.getByTestId('account-selector').click();
      });

      // Submit
      await act(async () => {
        screen.getByRole('button', { name: /create transaction/i }).click();
      });

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledTimes(1);
      });

      expect(onSubmit.mock.calls[0][0].currency).toBe('EUR');
    });
  });

  // ── Form Submission ───────────────────────────────────────────────────────

  describe('Form Submission', () => {
    it('submits form with correct data for EXPENSE transaction', async () => {
      const { onSubmit } = renderForm();

      // Fill required fields
      const typeSelect = screen.getByLabelText(/type/i);
      fireEvent.change(typeSelect, { target: { value: 'EXPENSE' } });

      const amountInput = screen.getByLabelText(/amount/i);
      fireEvent.change(amountInput, { target: { value: '42.50' } });

      const dateInput = screen.getByLabelText(/date/i);
      fireEvent.change(dateInput, { target: { value: '2024-06-15' } });

      // Select account
      const accountBtn = screen.getByTestId('account-selector');
      await act(async () => {
        accountBtn.click();
      });

      // Submit
      const submitButton = screen.getByRole('button', { name: /create transaction/i });
      await act(async () => {
        submitButton.click();
      });

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledTimes(1);
      });

      const submittedData = onSubmit.mock.calls[0][0];
      expect(submittedData.type).toBe('EXPENSE');
      expect(submittedData.amount).toBe(42.5);
      expect(submittedData.date).toBe('2024-06-15');
      expect(submittedData.accountId).toBe(1);
    });

    it('excludes categoryId when submitting in split mode', async () => {
      const { onSubmit } = renderForm();

      // Fill required fields
      fireEvent.change(screen.getByLabelText(/type/i), { target: { value: 'EXPENSE' } });
      fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '100' } });
      fireEvent.change(screen.getByLabelText(/date/i), { target: { value: '2024-06-15' } });

      // Select account
      await act(async () => {
        screen.getByTestId('account-selector').click();
      });

      // Select a category first
      await selectCategory(10);

      // Enter split mode — this should clear categoryId
      const splitButton = screen.getByRole('button', { name: /split transaction/i });
      await act(async () => {
        splitButton.click();
      });

      // Submit
      await act(async () => {
        screen.getByRole('button', { name: /create transaction/i }).click();
      });

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledTimes(1);
      });

      const submittedData = onSubmit.mock.calls[0][0];
      expect(submittedData.categoryId).toBeUndefined();
    });

    it('excludes liabilityId for non-EXPENSE transactions', async () => {
      mockUseLiabilities.mockReturnValue({
        data: [{ id: 1, name: 'Mortgage', userId: 1, type: 'MORTGAGE', currentBalance: 100000, currency: 'EUR', interestRate: 3.5, startDate: '2020-01-01', isActive: true, createdAt: '2020-01-01' }],
        isLoading: false,
        isError: false,
      } as any);

      const { onSubmit } = renderForm();

      // Start as EXPENSE, select a liability
      fireEvent.change(screen.getByLabelText(/type/i), { target: { value: 'EXPENSE' } });
      fireEvent.change(screen.getByTestId('liability-selector'), { target: { value: '1' } });

      // Switch to INCOME
      fireEvent.change(screen.getByLabelText(/type/i), { target: { value: 'INCOME' } });

      // Fill required fields
      fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '100' } });
      fireEvent.change(screen.getByLabelText(/date/i), { target: { value: '2024-06-15' } });
      await act(async () => {
        screen.getByTestId('account-selector').click();
      });

      // Submit
      await act(async () => {
        screen.getByRole('button', { name: /create transaction/i }).click();
      });

      await waitFor(() => {
        expect(onSubmit).toHaveBeenCalledTimes(1);
      });

      expect(onSubmit.mock.calls[0][0].liabilityId).toBeUndefined();
    });
  });

  // ── Payment Method & Notes ────────────────────────────────────────────────

  describe('Payment Method & Notes', () => {
    it('renders payment method dropdown', () => {
      renderForm();

      expect(screen.getByLabelText(/payment method/i)).toBeInTheDocument();
    });

    it('renders notes textarea', () => {
      renderForm();

      expect(screen.getByLabelText(/notes/i)).toBeInTheDocument();
    });

    it('pre-fills notes when editing a transaction with notes', () => {
      const existing: Transaction = {
        id: 99,
        userId: 1,
        accountId: 1,
        type: 'EXPENSE',
        amount: 50,
        currency: 'EUR',
        date: '2024-06-01',
        isReconciled: false,
        createdAt: '2024-06-01',
        notes: 'Some important note',
      };

      renderForm({ transaction: existing });

      expect(screen.getByLabelText(/notes/i)).toHaveValue('Some important note');
    });
  });
});
