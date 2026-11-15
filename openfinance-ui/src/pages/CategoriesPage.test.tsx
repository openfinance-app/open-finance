import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within, fireEvent } from '@testing-library/react';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
} from '@/test/test-utils';
import CategoriesPage, { CategoryFormDialog } from '@/pages/CategoriesPage';

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
const mockImpact = {
  system: false,
  hasChildren: false,
  transactionCount: 0,
  splitCount: 0,
  recurringCount: 0,
  payeeCount: 0,
  budgetCount: 0,
  deletable: true,
};

vi.mock('@/hooks/useTransactions', () => ({
  useCategoryTree: () => ({
    data: mockCategoryData,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useCreateCategory: () => ({ mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateCategory: () => ({ mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useDeleteCategory: () => ({ mutateAsync: mockDeleteMutateAsync, isPending: false }),
  useCategoryDeletionImpact: () => ({
    data: mockImpact,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (a: number) => a,
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({
    id,
    value,
    onValueChange,
  }: {
    id?: string;
    value?: number;
    onValueChange: (value?: number) => void;
  }) => (
    <select
      id={id}
      data-testid="category-select"
      value={value ?? ''}
      onChange={event => onValueChange(event.target.value ? Number(event.target.value) : undefined)}
    >
      <option value="">None</option>
      <option value="1">Food & Dining</option>
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

  it('sorts exact category totals without losing sub-cent differences', async () => {
    mockCategoryData = [
      {
        id: 1,
        name: 'Lower amount',
        transactionCount: 1,
        totalAmount: 9007199254740992,
        exactValues: { totalAmount: '9007199254740992.01' },
        currency: 'USD',
        isSystem: false,
        subcategories: [],
      },
      {
        id: 2,
        name: 'Higher amount',
        transactionCount: 1,
        totalAmount: 9007199254740992,
        exactValues: { totalAmount: '9007199254740992.02' },
        currency: 'USD',
        isSystem: false,
        subcategories: [],
      },
    ];
    renderWithProviders(<CategoriesPage />);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Sort by' }), 'amount');
    const actions = screen.getAllByRole('button', { name: /Actions for/ });
    expect(actions[0]).toHaveAccessibleName('Actions for Higher amount');
    expect(actions[1]).toHaveAccessibleName('Actions for Lower amount');
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

  it('hides empty branches while retaining active descendants and zero-net transactions', async () => {
    const user = userEvent.setup();
    const unused = {
      id: 4,
      name: 'Unused',
      transactionCount: 0,
      totalAmount: 0,
      subcategories: [],
    };
    mockCategoryData = [
      {
        ...mockCategoryData[0],
        directTransactionCount: 0,
        totalAmount: 0,
        subcategories: [
          {
            ...mockCategoryData[0].subcategories[0],
            directTransactionCount: 0,
            totalAmount: 0,
            subcategories: [
              { ...unused, id: 5, name: 'Offsetting transactions', transactionCount: 2 },
            ],
          },
          { ...unused, id: 6, name: 'Unused child' },
        ],
      },
      { ...unused, subcategories: [{ ...unused, id: 7, name: 'Unused descendant' }] },
    ];
    renderWithProviders(<CategoriesPage />);
    const toggle = screen.getByRole('button', { name: 'Hide empty categories' });
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await user.click(screen.getByRole('button', { name: 'Expand all' }));
    const summary = screen.getByLabelText('Category summary').textContent;
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    for (const name of ['Unused', 'Unused child', 'Unused descendant']) {
      expect(screen.queryByRole('button', { name: `Actions for ${name}` })).not.toBeInTheDocument();
    }
    for (const name of ['Food & Dining', 'Groceries', 'Offsetting transactions']) {
      expect(screen.getByRole('button', { name: `Actions for ${name}` })).toBeInTheDocument();
    }
    expect(screen.getByLabelText('Category summary').textContent).toBe(summary);
    await user.click(screen.getByRole('button', { name: 'Collapse all' }));
    expect(screen.queryByText('Offsetting transactions')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand all' }));
    expect(screen.getByText('Offsetting transactions')).toBeInTheDocument();
    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Actions for Unused child' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Expand Unused', exact: true }));
    expect(
      screen.getByRole('button', { name: 'Actions for Unused descendant' })
    ).toBeInTheDocument();
  });

  it('retains subtree transaction links when only empty children are hidden', async () => {
    const user = userEvent.setup();
    mockCategoryData[0].subcategories[0].transactionCount = 0;
    renderWithProviders(<CategoriesPage />);
    await user.click(screen.getByRole('button', { name: 'Hide empty categories' }));
    expect(screen.queryByRole('button', { name: 'Expand Food & Dining' })).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: 'View transactions including subcategories for Food & Dining',
      })
    ).toHaveAttribute(
      'href',
      '/transactions?categoryId=1&includeSubcategories=true&excludeTransfers=true'
    );
    expect(
      screen.getByRole('link', { name: 'View direct transactions for Food & Dining' })
    ).toBeInTheDocument();
  });

  it('combines the filter with group and search filters and lets the user recover empty results', async () => {
    const user = userEvent.setup();
    mockCategoryData[0].transactionCount = 0;
    mockCategoryData[0].directTransactionCount = 0;
    mockCategoryData[0].subcategories[0].transactionCount = 0;
    renderWithProviders(<CategoriesPage />);
    const group = screen.getByRole('combobox', { name: 'Category group' });
    await user.selectOptions(group, '1');
    await user.type(screen.getByRole('textbox', { name: /search categories/i }), 'Grocer');
    await user.click(screen.getByRole('button', { name: 'Hide empty categories' }));
    expect(screen.getByText('No categories with transactions')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Actions for Groceries' })).not.toBeInTheDocument();
    expect(group).toHaveValue('1');
    await user.click(screen.getByRole('button', { name: 'Show empty categories' }));
    expect(screen.getByRole('button', { name: 'Hide empty categories' })).toHaveAttribute(
      'aria-pressed',
      'false'
    );
    expect(screen.getByRole('button', { name: 'Actions for Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /search categories/i })).toHaveValue('Grocer');
    expect(group).toHaveValue('1');
  });

  it('remembers the hide-empty preference when returning to the categories view', async () => {
    const user = userEvent.setup();
    const firstVisit = renderWithProviders(<CategoriesPage />);
    await user.click(screen.getByRole('button', { name: 'Hide empty categories' }));
    firstVisit.unmount();
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByRole('button', { name: 'Hide empty categories' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });

  it('shows transaction counts', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText(/10 transactions/)).toBeInTheDocument();
  });

  it('shows system badge on system categories', () => {
    renderWithProviders(<CategoriesPage />);
    expect(screen.getByText('System')).toBeInTheDocument();
  });

  it('retains the category group while categories reload on a return visit', async () => {
    const user = userEvent.setup();
    const firstVisit = renderWithProviders(<CategoriesPage />);
    await user.selectOptions(screen.getByRole('combobox', { name: 'Category group' }), '1');
    firstVisit.unmount();
    const loadedCategories = mockCategoryData;
    mockCategoryData = [];
    mockIsLoading = true;
    const returnVisit = renderWithProviders(<CategoriesPage />);
    mockCategoryData = loadedCategories;
    mockIsLoading = false;
    returnVisit.rerender(<CategoriesPage />);
    expect(screen.getByRole('combobox', { name: 'Category group' })).toHaveValue('1');
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

  it('prevents editing and deletion of system categories', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CategoriesPage />);
    await user.click(screen.getByRole('button', { name: 'Actions for Salary' }));
    const editAction = screen.getByRole('menuitem', { name: /edit category/i });
    expect(editAction).toHaveAttribute('aria-disabled', 'true');
    expect(screen.queryByRole('menuitem', { name: /delete category/i })).not.toBeInTheDocument();
    await user.click(editAction);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mockUpdateMutateAsync).not.toHaveBeenCalled();
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
  it.each([undefined, 1])(
    'creates a category with parent %s and the selected icon',
    async parent => {
      renderWithProviders(<CategoriesPage />);
      fireEvent.click(screen.getByRole('button', { name: 'Add Category' }));
      fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
        target: { value: 'New category' },
      });
      if (parent)
        fireEvent.change(screen.getByLabelText('Parent Category (Optional)'), {
          target: { value: String(parent) },
        });
      fireEvent.click(screen.getByRole('button', { name: 'Choose icon 🎁' }));
      expect(screen.getByRole('button', { name: 'Choose icon 🎁' })).toHaveAttribute(
        'aria-pressed',
        'true'
      );
      fireEvent.click(screen.getByRole('button', { name: 'Create' }));
      await waitFor(() =>
        expect(mockCreateMutateAsync).toHaveBeenCalledWith({
          name: 'New category',
          parentId: parent ?? null,
          icon: '🎁',
          color: expect.any(String),
        })
      );
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(mockUpdateMutateAsync).not.toHaveBeenCalled();
    }
  );

  it.each([undefined, 1])(
    'updates a category with parent %s without creating a duplicate',
    async parent => {
      renderWithProviders(<CategoriesPage />);
      await userEvent.click(screen.getByRole('button', { name: 'Actions for Food & Dining' }));
      await userEvent.click(screen.getByRole('menuitem', { name: 'Edit category' }));
      fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
        target: { value: 'Renamed' },
      });
      fireEvent.change(screen.getByLabelText('Parent Category (Optional)'), {
        target: { value: parent ? String(parent) : '' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Update' }));
      await waitFor(() =>
        expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
          id: 1,
          data: {
            name: 'Renamed',
            parentId: parent ?? null,
            icon: expect.any(String),
            color: expect.any(String),
          },
        })
      );
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(mockCreateMutateAsync).not.toHaveBeenCalled();
    }
  );

  it.each([
    [{ response: { data: { message: 'Name already exists' } } }, 'Name already exists'],
    [
      { response: { data: null } },
      'Failed to save category. Please check your input and try again.',
    ],
    [new Error('Offline'), 'Failed to save category. Please check your input and try again.'],
  ])(
    'keeps rejected category input and clears the error when dismissed (%j)',
    async (error, message) => {
      mockCreateMutateAsync.mockRejectedValueOnce(error);
      renderWithProviders(<CategoriesPage />);
      fireEvent.click(screen.getByRole('button', { name: 'Add Category' }));
      fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
        target: { value: 'Retry me' },
      });
      fireEvent.click(screen.getByRole('button', { name: 'Create' }));
      expect(await screen.findByText(message as string)).toBeInTheDocument();
      expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('Retry me');
      fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
      fireEvent.click(screen.getByRole('button', { name: 'Add Category' }));
      expect(screen.queryByText(message as string)).not.toBeInTheDocument();
      expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('');
    }
  );

  it('shows the name limit and prevents duplicate submission during saving', () => {
    const submit = vi.fn();
    renderWithProviders(
      <CategoryFormDialog
        open
        onOpenChange={vi.fn()}
        onSubmit={submit}
        isLoading
        error="Retry later"
      />
    );
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), {
      target: { value: 'x'.repeat(91) },
    });
    expect(screen.getByText('91/100')).toHaveClass('text-red-500');
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('maxlength', '100');
    expect(screen.getByRole('button', { name: 'Saving...' })).toBeDisabled();
    expect(screen.getByText('Retry later')).toBeInTheDocument();
  });

  it('sorts by transaction count with missing counts treated as zero', async () => {
    mockCategoryData = [
      { id: 1, name: 'Zero', subcategories: [], isSystem: false },
      { id: 2, name: 'Most', transactionCount: 5, subcategories: [], isSystem: false },
      { id: 3, name: 'Middle', transactionCount: 1, subcategories: [], isSystem: false },
    ];
    renderWithProviders(<CategoriesPage />);
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Sort by' }),
      'transactions'
    );
    const names = screen
      .getAllByRole('button', { name: /^Actions for/ })
      .map(button => button.getAttribute('aria-label'));
    expect(names).toEqual(['Actions for Most', 'Actions for Middle', 'Actions for Zero']);
  });

  it('returns to all groups when the selected root disappears after refresh', async () => {
    const { rerender } = renderWithProviders(<CategoriesPage />);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Category group' }), '1');
    mockCategoryData = [mockCategoryData[1]];
    rerender(<CategoriesPage />);
    await waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Category group' })).toHaveValue('all')
    );
    expect(screen.getByRole('button', { name: 'Actions for Salary' })).toBeInTheDocument();
  });
});
