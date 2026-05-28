import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import CategoriesPage from '@/pages/CategoriesPage';

let mockCategoryData: any[] = [
  {
    id: 1,
    name: 'Food & Dining',
    type: 'EXPENSE',
    transactionCount: 10,
    totalAmount: 500,
    currency: 'USD',
    isSystem: false,
    subcategories: [
      { id: 2, name: 'Groceries', type: 'EXPENSE', transactionCount: 5, totalAmount: 300, currency: 'USD', isSystem: false, subcategories: [] },
    ],
  },
  {
    id: 3,
    name: 'Salary',
    type: 'INCOME',
    transactionCount: 2,
    totalAmount: 5000,
    currency: 'USD',
    isSystem: true,
    subcategories: [],
  },
];
let mockIsLoading = false;
let mockError: Error | null = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useTransactions', () => ({
  useCategoryTree: () => ({
    data: mockCategoryData,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useCreateCategory: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateCategory: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useDeleteCategory: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: (a: number) => a, secondaryCurrency: null, secondaryExchangeRate: null }),
}));

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: () => <select data-testid="category-select"><option>None</option></select>,
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, onCancel, title }: any) =>
    open ? (
      <div data-testid="confirmation-dialog">
        <span>{title}</span>
        <button onClick={onConfirm}>Confirm</button>
        <button onClick={onCancel}>Cancel Delete</button>
      </div>
    ) : null,
}));

describe('CategoriesPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockCategoryData = [
      {
        id: 1,
        name: 'Food & Dining',
        type: 'EXPENSE',
        transactionCount: 10,
        totalAmount: 500,
        currency: 'USD',
        isSystem: false,
        subcategories: [
          { id: 2, name: 'Groceries', type: 'EXPENSE', transactionCount: 5, totalAmount: 300, currency: 'USD', isSystem: false, subcategories: [] },
        ],
      },
      {
        id: 3,
        name: 'Salary',
        type: 'INCOME',
        transactionCount: 2,
        totalAmount: 5000,
        currency: 'USD',
        isSystem: true,
        subcategories: [],
      },
    ];
    mockIsLoading = false;
    mockError = null;
  });

  it('renders the page heading', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('Categories')).toBeInTheDocument();
  });

  it('displays category names', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('Food & Dining')).toBeInTheDocument();
    expect(screen.getByText('Salary')).toBeInTheDocument();
  });

  it('displays subcategories (first level expanded by default)', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('Groceries')).toBeInTheDocument();
  });

  it('shows type badges', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getAllByText('Expense').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Income').length).toBeGreaterThan(0);
  });

  it('shows transaction counts', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText(/10 Txns/)).toBeInTheDocument();
  });

  it('shows system badge on system categories', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('System')).toBeInTheDocument();
  });

  it('shows loading skeletons', () => {
    mockIsLoading = true;
    renderWithProviders(<CategoriesPage />);
    expect(screen.queryByText('Food & Dining')).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText(/failed to load categories/i)).toBeInTheDocument();
  });

  it('shows empty state when no categories', () => {
    mockCategoryData = [];
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText(/no categories yet/i)).toBeInTheDocument();
  });

  it('has add category button', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByRole('button', { name: /add category/i })).toBeInTheDocument();
  });

  it('has search input', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByPlaceholderText(/search categories/i)).toBeInTheDocument();
  });

  it('filters categories by search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const searchInput = screen.getByPlaceholderText(/search categories/i);
    await user.type(searchInput, 'Salary');
    expect(screen.getByText('Salary')).toBeInTheDocument();
  });

  it('opens add category dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    await user.click(screen.getByRole('button', { name: /add category/i }));
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
  });

  it('opens edit dialog when edit button clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const editButtons = screen.getAllByRole('button', { name: /edit category/i });
    await user.click(editButtons[0]);
    await waitFor(() => {
      expect(screen.getByText('Edit Category')).toBeInTheDocument();
    });
  });

  it('does not show delete button for system categories', () => {
    renderWithProviders(<CategoriesPage />);
    // Salary is system - should have fewer delete buttons than edit buttons
    const editButtons = screen.getAllByRole('button', { name: /edit category/i });
    const deleteButtons = screen.getAllByRole('button', { name: /delete category/i });
    expect(deleteButtons.length).toBeLessThan(editButtons.length);
  });

  it('shows delete confirmation when delete clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    const deleteButtons = screen.getAllByRole('button', { name: /delete category/i });
    await user.click(deleteButtons[0]);
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('calls deleteCategory on confirm', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<CategoriesPage />);

    const deleteButtons = screen.getAllByRole('button', { name: /delete category/i });
    await user.click(deleteButtons[0]);
    await user.click(screen.getByRole('button', { name: /confirm/i }));
    expect(mockDeleteMutateAsync).toHaveBeenCalled();
  });

  it('switches tabs between All, Expenses, and Income', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    // Click Income tab
    await user.click(screen.getByRole('button', { name: /^income$/i }));
    // Salary (INCOME) should remain visible
    expect(screen.getByText('Salary')).toBeInTheDocument();

    // Click Expenses tab
    await user.click(screen.getByRole('button', { name: /expenses/i }));
    expect(screen.getByText('Food & Dining')).toBeInTheDocument();

    // Click All tab
    await user.click(screen.getByRole('button', { name: /all categories/i }));
    expect(screen.getByText('Food & Dining')).toBeInTheDocument();
    expect(screen.getByText('Salary')).toBeInTheDocument();
  });

  it('shows stats cards with counts', () => {
    renderWithProviders(<CategoriesPage />);
    // Should show total categories count, income count, expense count
    expect(screen.getByText('Total Categories')).toBeInTheDocument();
  });

  it('collapses and expands tree nodes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    // Groceries visible by default (first level expanded)
    expect(screen.getByText('Groceries')).toBeInTheDocument();

    // Click collapse on Food & Dining
    const collapseButtons = screen.getAllByRole('button', { name: /collapse/i });
    await user.click(collapseButtons[0]);
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();

    // Click expand
    const expandButtons = screen.getAllByRole('button', { name: /expand/i });
    await user.click(expandButtons[0]);
    expect(screen.getByText('Groceries')).toBeInTheDocument();
  });
});
