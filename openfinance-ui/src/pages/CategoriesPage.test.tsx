import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
} from '@/test/test-utils';
import CategoriesPage from '@/pages/CategoriesPage';

let mockCategoryData: any[] = [
  {
    id: 1,
    name: 'Food & Dining',

    transactionCount: 10,
    directTransactionCount: 5,
    totalAmount: 500,
    directAmount: 200,
    currency: 'USD',
    isSystem: false,
    subcategories: [
      {
        id: 2,
        name: 'Groceries',

        transactionCount: 5,
        totalAmount: 300,
        currency: 'USD',
        isSystem: false,
        subcategories: [],
      },
    ],
  },
  {
    id: 3,
    name: 'Salary',

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
  useSecondaryConversion: () => ({
    convert: (a: number) => a,
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: () => (
    <select data-testid="category-select">
      <option>None</option>
    </select>
  ),
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

        transactionCount: 10,
        directTransactionCount: 5,
        totalAmount: 500,
        directAmount: 200,
        currency: 'USD',
        isSystem: false,
        subcategories: [
          {
            id: 2,
            name: 'Groceries',

            transactionCount: 5,
            totalAmount: 300,
            currency: 'USD',
            isSystem: false,
            subcategories: [],
          },
        ],
      },
      {
        id: 3,
        name: 'Salary',

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
    // Root names are also available in the category-group filter.
    expect(screen.getAllByText('Food & Dining').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Salary').length).toBeGreaterThan(0);
  });

  it('starts with all categories collapsed', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Expand Food & Dining' })).toHaveAttribute(
      'aria-expanded',
      'false'
    );
    expect(screen.getByRole('button', { name: 'Expand all' })).toHaveAttribute(
      'aria-expanded',
      'false'
    );
  });

  it('does not assign a direction badge to categories', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.queryByText('Expense')).not.toBeInTheDocument();
    expect(screen.queryByText('Income')).not.toBeInTheDocument();
  });

  it('shows transaction counts', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText(/10 transactions/)).toBeInTheDocument();
  });

  it('shows system badge on system categories', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('System')).toBeInTheDocument();
  });

  it('shows loading skeletons', () => {
    mockIsLoading = true;
    renderWithProviders(<CategoriesPage />);
    expect(
      screen.queryByRole('button', { name: 'Actions for Food & Dining' })
    ).not.toBeInTheDocument();
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
    expect(screen.getAllByText('Salary').length).toBeGreaterThan(0);
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

    await user.click(screen.getByRole('button', { name: 'Actions for Food & Dining' }));
    await user.click(screen.getByRole('menuitem', { name: /edit category/i }));
    await waitFor(() => {
      expect(screen.getByText('Edit Category')).toBeInTheDocument();
    });
  });

  it('does not offer deletion for system categories', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);
    await user.click(screen.getByRole('button', { name: 'Actions for Salary' }));
    expect(screen.getByRole('menuitem', { name: /edit category/i })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: /delete category/i })).not.toBeInTheDocument();
  });

  it('shows delete confirmation when delete clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    await user.click(screen.getByRole('button', { name: 'Actions for Food & Dining' }));
    await user.click(screen.getByRole('menuitem', { name: /delete category/i }));
    expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
  });

  it('calls deleteCategory on confirm', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<CategoriesPage />);

    await user.click(screen.getByRole('button', { name: 'Actions for Food & Dining' }));
    await user.click(screen.getByRole('menuitem', { name: /delete category/i }));
    await user.click(screen.getByRole('button', { name: /confirm/i }));
    expect(mockDeleteMutateAsync).toHaveBeenCalled();
  });

  it('shows a shared hierarchy without direction tabs', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getAllByText('Food & Dining').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Salary').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /^income$|^expenses$/i })).not.toBeInTheDocument();
  });

  it('shows compact category summary counts', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('Total Categories')).toBeInTheDocument();
  });

  it('shows wall totals without double-counting subtrees', () => {
    renderWithProviders(<CategoriesPage />);
    // Roots aggregate their slots: 10 + 2 root transactions
    const summary = within(screen.getByLabelText('Category summary'));
    expect(summary.getByText('Transactions')).toBeInTheDocument();
    expect(summary.getByText('12')).toBeInTheDocument();
  });

  it('collapses and expands tree nodes', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);

    await user.click(screen.getByRole('button', { name: 'Expand Food & Dining' }));
    expect(screen.getByText('Groceries')).toBeInTheDocument();

    // Click collapse on Food & Dining
    await user.click(screen.getByRole('button', { name: 'Collapse Food & Dining' }));
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();
    expect(screen.getByText('In this category')).toBeInTheDocument();
    const directActivity = within(
      screen.getByText('In this category').parentElement!.parentElement!
    );
    expect(directActivity.getByTitle('Direct amount')).toHaveTextContent('200');

    // Click expand
    await user.click(screen.getByRole('button', { name: 'Expand Food & Dining' }));
    expect(screen.getByText('Groceries')).toBeInTheDocument();
  });

  it('expands and collapses every level, including manually toggled branches', async () => {
    const user = userEvent.setup();
    const groceries = mockCategoryData[0].subcategories[0];
    groceries.subcategories = [{ ...groceries, id: 4, name: 'Weekly shop', subcategories: [] }];
    renderWithProviders(<CategoriesPage />);

    expect(screen.queryByText('Weekly shop')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getByText('Weekly shop')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Collapse all' })).toHaveAttribute(
      'aria-expanded',
      'true'
    );
    await user.click(screen.getByRole('button', { name: 'Collapse Groceries' }));
    expect(screen.getByRole('button', { name: 'Expand all' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getByText('Weekly shop')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();
    expect(screen.queryByText('Weekly shop')).not.toBeInTheDocument();
    expect(screen.getByText('In this category')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand Food & Dining' }));
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(screen.queryByText('Weekly shop')).not.toBeInTheDocument();
  });

  it('shows a matching parent and all descendants once, even after folding the tree', async () => {
    const user = userEvent.setup();
    const groceries = mockCategoryData[0].subcategories[0];
    groceries.subcategories = [{ ...groceries, id: 4, name: 'Food discounts', subcategories: [] }];
    renderWithProviders(<CategoriesPage />);
    await user.click(screen.getByRole('button', { name: 'Expand Food & Dining' }));
    await user.click(screen.getByRole('button', { name: 'Collapse Food & Dining' }));
    await user.type(screen.getByRole('textbox', { name: /search categories/i }), 'Food');

    expect(screen.getAllByRole('button', { name: 'Actions for Food & Dining' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Actions for Groceries' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Actions for Food discounts' })).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Actions for Salary' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getByText('Food discounts')).toBeInTheDocument();
  });

  it('includes children of a matching nested parent with its parent breadcrumb', async () => {
    const user = userEvent.setup();
    const groceries = mockCategoryData[0].subcategories[0];
    groceries.subcategories = [{ ...groceries, id: 4, name: 'Weekly shop', subcategories: [] }];
    renderWithProviders(<CategoriesPage />);
    await user.type(screen.getByRole('textbox', { name: /search categories/i }), 'Grocer');

    expect(screen.getByRole('button', { name: 'Collapse Groceries' })).toBeInTheDocument();
    expect(screen.getByText('Weekly shop')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Actions for Food & Dining' })
    ).not.toBeInTheDocument();
    expect(screen.getByText('Food & Dining', { selector: 'p' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Actions for Salary' })).not.toBeInTheDocument();
  });

  it('keeps regex search and leaf-only matches scoped correctly', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);
    const search = screen.getByRole('textbox', { name: /search categories/i });
    await user.click(screen.getByRole('button', { name: 'Regex', exact: true }));
    await user.type(search, '^Food');
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    await user.clear(search);
    await user.type(search, '^Gro');
    expect(screen.getByRole('button', { name: 'Actions for Groceries' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Actions for Food & Dining' })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /^(Expand|Collapse) all$/ })
    ).not.toBeInTheDocument();
    await user.clear(search);
    await user.type(search, '[[');
    expect(screen.getByText('No matching categories')).toBeInTheDocument();
  });

  it('keeps parent and direct transaction scopes when filtering or searching a group', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);
    await user.selectOptions(screen.getByRole('combobox', { name: 'Category group' }), '1');
    expect(screen.queryByRole('button', { name: 'Actions for Salary' })).not.toBeInTheDocument();
    expect(screen.getByTitle('Including subcategories')).toHaveTextContent('500');
    await user.type(screen.getByRole('textbox', { name: /search categories/i }), 'Food');
    expect(screen.getByText('Groceries')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'View direct transactions for Food & Dining' })
    ).toHaveAttribute('href', '/transactions?categoryId=1&excludeTransfers=true');
    expect(
      screen.getByRole('link', {
        name: 'View transactions including subcategories for Food & Dining',
      })
    ).toHaveAttribute(
      'href',
      '/transactions?categoryId=1&includeSubcategories=true&excludeTransfers=true'
    );
    await user.clear(screen.getByRole('textbox', { name: /search categories/i }));
    await user.type(screen.getByRole('textbox', { name: /search categories/i }), 'Salary');
    expect(screen.getByText('No matching categories')).toBeInTheDocument();
  });

  it('shows direct activity even when its transactions net to zero', () => {
    mockCategoryData[0].directAmount = 0;
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('In this category')).toBeInTheDocument();
    const directActivity = within(
      screen.getByText('In this category').parentElement!.parentElement!
    );
    expect(directActivity.getByTitle('Direct amount')).toHaveTextContent('$0.00');
    expect(
      screen.getByRole('link', { name: 'View direct transactions for Food & Dining' })
    ).toHaveTextContent('5 transactions');
  });

  it('does not add a direct-activity line when a parent has no direct transactions', () => {
    mockCategoryData[0].directTransactionCount = 0;
    mockCategoryData[0].directAmount = 0;
    renderWithProviders(<CategoriesPage />);
    expect(screen.queryByText('In this category')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View transactions including subcategories for Food & Dining',
      })
    ).toHaveTextContent('10 transactions');
  });

  it('sorts the hierarchy by the selected total', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sort by' }), 'amount');
    expect(screen.getAllByRole('button', { name: /Actions for/ })[0]).toHaveAccessibleName(
      'Actions for Salary'
    );
  });
});
