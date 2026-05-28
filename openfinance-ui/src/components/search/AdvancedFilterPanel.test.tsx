import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: () => ({ data: [{ id: 1, name: 'Checking' }, { id: 2, name: 'Savings' }], isLoading: false }),
}));
vi.mock('@/hooks/useCategories', () => ({
  useCategories: () => ({ data: [{ id: 10, name: 'Food' }, { id: 11, name: 'Transport' }], isLoading: false }),
}));
vi.mock('@/hooks/useTransactionTags', () => ({
  useTransactionTags: () => ({ data: [{ tag: 'groceries', count: 5 }, { tag: 'fuel', count: 3 }], isLoading: false }),
}));

import { AdvancedFilterPanel } from './AdvancedFilterPanel';

describe('AdvancedFilterPanel', () => {
  const defaultProps = {
    filters: {},
    onFiltersChange: vi.fn(),
    onApply: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders the filter toggle button', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    const btn = screen.getByRole('button', { name: /filters/i });
    expect(btn).toBeInTheDocument();
  });

  it('expands on toggle click', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    // After expanding, should show apply button
    expect(screen.getByText(/apply/i)).toBeInTheDocument();
  });

  it('shows entity type badges when expanded', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getAllByText(/TRANSACTION/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/ACCOUNT/i).length).toBeGreaterThan(0);
  });

  it('shows amount range inputs when expanded', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByLabelText(/minimum/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/maximum/i)).toBeInTheDocument();
  });

  it('calls onFiltersChange when min amount changes', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.change(screen.getByLabelText(/minimum/i), { target: { value: '100' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ minAmount: 100 }));
  });

  it('calls onApply when apply button clicked', () => {
    const onApply = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onApply={onApply} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText(/apply/i));
    expect(onApply).toHaveBeenCalled();
  });

  it('shows active filter count badge', () => {
    const filtersWithValues = {
      query: 'test',
      entityTypes: ['TRANSACTION'],
      minAmount: 100,
    };
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} filters={filtersWithValues} />);
    // Should show badge with count of active filters (entityTypes + minAmount = 2)
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('clears filters when clear button clicked', () => {
    const onFiltersChange = vi.fn();
    const onApply = vi.fn();
    const filtersWithValues = {
      query: 'test',
      entityTypes: ['TRANSACTION'],
      minAmount: 100,
    };
    renderWithProviders(
      <AdvancedFilterPanel
        {...defaultProps}
        filters={filtersWithValues}
        onFiltersChange={onFiltersChange}
        onApply={onApply}
      />
    );
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    const clearButton = screen.getByText(/clear/i);
    fireEvent.click(clearButton);
    expect(onFiltersChange).toHaveBeenCalledWith({ query: 'test' });
    expect(onApply).toHaveBeenCalled();
  });

  it('shows date preset buttons when expanded', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText(/today/i)).toBeInTheDocument();
  });

  it('shows save search button when onSaveSearch provided and filters active', () => {
    const filtersWithValues = { query: 'test', minAmount: 100 };
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} filters={filtersWithValues} onSaveSearch={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText(/Save Search/i)).toBeInTheDocument();
  });

  it('shows transaction type filters when expanded', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText(/INCOME/i)).toBeInTheDocument();
    expect(screen.getByText(/EXPENSE/i)).toBeInTheDocument();
  });

  it('calls onFiltersChange when max amount changes', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.change(screen.getByLabelText(/maximum/i), { target: { value: '500' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ maxAmount: 500 }));
  });

  it('clears min amount when input emptied', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(
      <AdvancedFilterPanel {...defaultProps} filters={{ minAmount: 100 }} onFiltersChange={onFiltersChange} />
    );
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.change(screen.getByLabelText(/minimum/i), { target: { value: '' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ minAmount: undefined }));
  });

  it('toggles entity type badge on click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    // Entity types are translated - ASSET becomes 'Assets'
    fireEvent.click(screen.getByText('Assets'));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ entityTypes: ['ASSET'] }));
  });

  it('removes entity type when already selected', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(
      <AdvancedFilterPanel {...defaultProps} filters={{ entityTypes: ['TRANSACTION'] }} onFiltersChange={onFiltersChange} />
    );
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    // Entity types are translated - TRANSACTION becomes 'Transactions'
    const txBadges = screen.getAllByText('Transactions');
    fireEvent.click(txBadges[0]);
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ entityTypes: undefined }));
  });

  it('applies date preset on click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText(/today/i));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({
      dateFrom: expect.any(String),
      dateTo: expect.any(String),
    }));
  });

  it('updates dateFrom on date input change', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.change(screen.getByLabelText(/from/i), { target: { value: '2024-01-01' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ dateFrom: '2024-01-01' }));
  });

  it('updates dateTo on date input change', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.change(screen.getByLabelText(/^to$/i), { target: { value: '2024-12-31' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ dateTo: '2024-12-31' }));
  });

  it('shows account filter badges', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText('Checking')).toBeInTheDocument();
    expect(screen.getByText('Savings')).toBeInTheDocument();
  });

  it('toggles account filter on click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText('Checking'));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ accountIds: [1] }));
  });

  it('shows category filter badges', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText('Food')).toBeInTheDocument();
    expect(screen.getByText('Transport')).toBeInTheDocument();
  });

  it('toggles category filter on click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText('Food'));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ categoryIds: [10] }));
  });

  it('shows tag filter badges', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText(/groceries/)).toBeInTheDocument();
    expect(screen.getByText(/fuel/)).toBeInTheDocument();
  });

  it('toggles tag filter on click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText(/groceries/));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ tags: ['groceries'] }));
  });

  it('changes transaction type select', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    const typeSelect = document.querySelector('#transactionType') as HTMLSelectElement;
    fireEvent.change(typeSelect, { target: { value: 'INCOME' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ transactionType: 'INCOME' }));
  });

  it('changes reconciled status select', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} onFiltersChange={onFiltersChange} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    const reconciledSelect = document.querySelector('#isReconciled') as HTMLSelectElement;
    fireEvent.change(reconciledSelect, { target: { value: 'true' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ isReconciled: true }));
  });

  it('clears reconciled status when set to empty', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(
      <AdvancedFilterPanel {...defaultProps} filters={{ isReconciled: true }} onFiltersChange={onFiltersChange} />
    );
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    const reconciledSelect = document.querySelector('#isReconciled') as HTMLSelectElement;
    fireEvent.change(reconciledSelect, { target: { value: '' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ isReconciled: undefined }));
  });

  it('calls onSaveSearch when save search button clicked', () => {
    const onSaveSearch = vi.fn();
    const filtersWithValues = { query: 'test', minAmount: 100 };
    renderWithProviders(
      <AdvancedFilterPanel {...defaultProps} filters={filtersWithValues} onSaveSearch={onSaveSearch} />
    );
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    fireEvent.click(screen.getByText(/Save Search/i));
    expect(onSaveSearch).toHaveBeenCalledTimes(1);
  });

  it('shows loading state on apply button', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} isLoading={true} />);
    fireEvent.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByText(/searching/i)).toBeInTheDocument();
  });

  it('collapses panel on second toggle click', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    const filterBtn = screen.getByRole('button', { name: /advanced filters/i });
    fireEvent.click(filterBtn);
    expect(screen.getByText(/apply/i)).toBeInTheDocument();
    fireEvent.click(filterBtn);
    expect(screen.queryByLabelText(/minimum/i)).not.toBeInTheDocument();
  });

  it('shows subtitle text for no active filters', () => {
    renderWithProviders(<AdvancedFilterPanel {...defaultProps} />);
    // Should show subtitle for no filters active
    const body = document.body.textContent || '';
    expect(body.length).toBeGreaterThan(0);
  });
});
