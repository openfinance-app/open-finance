import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransactionFilters } from './TransactionFilters';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

// Mock child selector components
vi.mock('@/components/ui/PayeeSelector', () => ({
  PayeeSelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="payee-selector"
      value={value || ''}
      onChange={(e: any) => onValueChange(e.target.value)}
    >
      <option value="">{placeholder}</option>
    </select>
  ),
}));

vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="category-selector"
      value={value || ''}
      onChange={(e: any) => onValueChange(e.target.value)}
    >
      <option value="">{placeholder}</option>
    </select>
  ),
}));

vi.mock('@/components/ui/AccountSelector', () => ({
  AccountSelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="account-selector"
      value={value || ''}
      onChange={(e: any) => onValueChange(e.target.value)}
    >
      <option value="">{placeholder}</option>
    </select>
  ),
}));

// Mock useTransactionTags hook
vi.mock('@/hooks/useTransactionTags', () => ({
  useTransactionTags: () => ({
    data: [
      { tag: 'groceries', count: 10 },
      { tag: 'utilities', count: 5 },
    ],
  }),
}));

// Mock date utils
vi.mock('@/utils/date', () => ({
  getToday: () => '2026-05-26',
  getDaysAgo: (days: number) => '2026-05-19',
  getStartOfMonth: () => '2026-05-01',
  getStartOfYear: () => '2026-01-01',
}));

describe('TransactionFilters', () => {
  const mockOnFiltersChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders without crashing with empty filters', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByText(/search|filterKeys\.search/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByRole('textbox', { name: /search|filterKeys\.search/i })).toBeInTheDocument();
  });

  it('renders type filter with options', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const typeSelect = screen.getByRole('combobox', { name: /type|form\.type/i });
    expect(typeSelect).toBeInTheDocument();
  });

  it('renders sort selector', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const sortSelect = screen.getByTestId('filter-sort');
    expect(sortSelect).toBeInTheDocument();
  });

  it('renders min/max amount inputs', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByTestId('filter-min-amount')).toBeInTheDocument();
    expect(screen.getByTestId('filter-max-amount')).toBeInTheDocument();
  });

  it('renders clear button disabled when no active filters', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const clearButton = screen.getByRole('button', { name: /clear|filterKeys\.clear/i });
    expect(clearButton).toBeDisabled();
  });

  it('renders clear button enabled when filters are active', () => {
    renderWithProviders(
      <TransactionFilters
        filters={{ keyword: 'test' }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    const clearButton = screen.getByRole('button', { name: /clear|filterKeys\.clear/i });
    expect(clearButton).not.toBeDisabled();
  });

  it('calls onFiltersChange when clear is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters
        filters={{ keyword: 'test' }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    const clearButton = screen.getByRole('button', { name: /clear|filterKeys\.clear/i });
    await user.click(clearButton);
    expect(mockOnFiltersChange).toHaveBeenCalledWith({});
  });

  it('renders tag badges', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByText(/groceries/)).toBeInTheDocument();
    expect(screen.getByText(/utilities/)).toBeInTheDocument();
  });

  it('renders date preset buttons', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByText(/date range|filterKeys\.dateRange/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /today|filterKeys\.today/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /last\s*7\s*days|filterKeys\.last7Days/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /last\s*30\s*days|filterKeys\.last30Days/i })).toBeInTheDocument();
  });

  it('calls onFiltersChange with date preset values', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    await user.click(screen.getByRole('button', { name: /today|filterKeys\.today/i }));
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({
        dateFrom: '2026-05-26',
        dateTo: '2026-05-26',
      })
    );
  });

  it('renders date from/to inputs', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByLabelText(/from|filterKeys\.from/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/to|filterKeys\.to/i)).toBeInTheDocument();
  });

  it('renders account, category, payee selectors', () => {
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    expect(screen.getByTestId('account-selector')).toBeInTheDocument();
    expect(screen.getByTestId('category-selector')).toBeInTheDocument();
    expect(screen.getByTestId('payee-selector')).toBeInTheDocument();
  });

  it('renders noCategory badge when filter is active', () => {
    renderWithProviders(
      <TransactionFilters
        filters={{ noCategory: true }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    expect(screen.getByText(/no category|filterKeys\.noCategory/i)).toBeInTheDocument();
  });

  it('renders noPayee badge when filter is active', () => {
    renderWithProviders(
      <TransactionFilters
        filters={{ noPayee: true }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    expect(screen.getByText(/no payee|filterKeys\.noPayee/i)).toBeInTheDocument();
  });

  it('calls onFiltersChange when keyword is typed', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const searchInput = screen.getByRole('textbox', { name: /search|filterKeys\.search/i });
    await user.type(searchInput, 'rent');
    expect(mockOnFiltersChange).toHaveBeenCalled();
    // Last call should have keyword set
    const lastCall = mockOnFiltersChange.mock.calls[mockOnFiltersChange.mock.calls.length - 1][0];
    expect(lastCall.keyword).toBeDefined();
  });

  it('calls onFiltersChange when type is changed', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const typeSelect = screen.getByRole('combobox', { name: /type|form\.type/i });
    await user.selectOptions(typeSelect, 'INCOME');
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'INCOME' })
    );
  });

  it('calls onFiltersChange when min amount is entered', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const minInput = screen.getByTestId('filter-min-amount');
    await user.type(minInput, '100');
    expect(mockOnFiltersChange).toHaveBeenCalled();
  });

  it('calls onFiltersChange when max amount is entered', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const maxInput = screen.getByTestId('filter-max-amount');
    await user.type(maxInput, '500');
    expect(mockOnFiltersChange).toHaveBeenCalled();
  });

  it('toggles tag filter when tag badge is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    await user.click(screen.getByText(/groceries/));
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ tag: 'groceries' })
    );
  });

  it('deselects tag when clicking active tag', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters
        filters={{ tag: 'groceries' }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    const tagBadges = screen.getAllByText(/groceries/);
    // Click the badge (first match), not the active label
    await user.click(tagBadges[0]);
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ tag: undefined })
    );
  });

  it('removes noCategory filter when badge is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters
        filters={{ noCategory: true }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    await user.click(screen.getByText(/no category|filterKeys\.noCategory/i));
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ noCategory: undefined })
    );
  });

  it('removes noPayee filter when badge is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters
        filters={{ noPayee: true }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    await user.click(screen.getByText(/no payee|filterKeys\.noPayee/i));
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ noPayee: undefined })
    );
  });

  it('calls onFiltersChange when sort is changed', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const sortSelect = screen.getByTestId('filter-sort');
    await user.selectOptions(sortSelect, 'amount,desc');
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ sort: 'amount,desc' })
    );
  });

  it('calls onFiltersChange when account selector changes', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    const accountSelect = screen.getByTestId('account-selector');
    await user.selectOptions(accountSelect, '');
    // Changing to empty string should set undefined via handleChange
    expect(mockOnFiltersChange).toHaveBeenCalled();
  });

  it('calls onFiltersChange with last7Days preset', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters filters={{}} onFiltersChange={mockOnFiltersChange} />
    );
    await user.click(screen.getByRole('button', { name: /last\s*7\s*days|filterKeys\.last7Days/i }));
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({
        dateFrom: '2026-05-19',
        dateTo: '2026-05-26',
      })
    );
  });

  it('converts empty string to undefined in handleChange', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <TransactionFilters
        filters={{ type: 'INCOME' }}
        onFiltersChange={mockOnFiltersChange}
      />
    );
    const typeSelect = screen.getByRole('combobox', { name: /type|form\.type/i });
    await user.selectOptions(typeSelect, '');
    expect(mockOnFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ type: undefined })
    );
  });
});
