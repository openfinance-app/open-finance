/**
 * Unit tests for BudgetFilters component
 */
import { renderWithProviders, screen, fireEvent } from '@/test/test-utils';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { BudgetFilters, type BudgetFiltersState } from './BudgetFilters';

describe('BudgetFilters', () => {
  const mockOnFiltersChange = vi.fn();

  const defaultFilters: BudgetFiltersState = {};

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders search input with correct label and placeholder', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      expect(screen.getByLabelText('Search')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('Search budgets...')).toBeInTheDocument();
    });

    it('renders period dropdown with correct label and options', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      expect(screen.getByLabelText('Period')).toBeInTheDocument();
      expect(screen.getByRole('combobox')).toBeInTheDocument();

      // Check all period options are present
      expect(screen.getByRole('option', { name: 'All Periods' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Weekly' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Monthly' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Quarterly' })).toBeInTheDocument();
      expect(screen.getByRole('option', { name: 'Yearly' })).toBeInTheDocument();
    });

    it('renders clear button', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      expect(screen.getByRole('button', { name: /clear/i })).toBeInTheDocument();
    });
  });

  describe('Search Input', () => {
    it('displays current keyword value', () => {
      const filtersWithKeyword: BudgetFiltersState = { keyword: 'test search' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithKeyword} onFiltersChange={mockOnFiltersChange} />
      );

      const input = screen.getByPlaceholderText('Search budgets...');
      expect(input).toHaveValue('test search');
    });

    it('calls onFiltersChange with updated keyword when user types', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const input = screen.getByPlaceholderText('Search budgets...');
      fireEvent.change(input, { target: { value: 'new search' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ keyword: 'new search' });
    });

    it('calls onFiltersChange with undefined when search is cleared', () => {
      const filtersWithKeyword: BudgetFiltersState = { keyword: 'test' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithKeyword} onFiltersChange={mockOnFiltersChange} />
      );

      const input = screen.getByPlaceholderText('Search budgets...');
      fireEvent.change(input, { target: { value: '' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ keyword: undefined });
    });
  });

  describe('Period Dropdown', () => {
    it('displays current period value', () => {
      const filtersWithPeriod: BudgetFiltersState = { period: 'MONTHLY' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithPeriod} onFiltersChange={mockOnFiltersChange} />
      );

      const select = screen.getByRole('combobox');
      expect(select).toHaveValue('MONTHLY');
    });

    it('calls onFiltersChange with updated period when user selects', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const select = screen.getByRole('combobox');
      fireEvent.change(select, { target: { value: 'WEEKLY' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ period: 'WEEKLY' });
    });

    it('calls onFiltersChange with undefined when "All Periods" is selected', () => {
      const filtersWithPeriod: BudgetFiltersState = { period: 'MONTHLY' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithPeriod} onFiltersChange={mockOnFiltersChange} />
      );

      const select = screen.getByRole('combobox');
      fireEvent.change(select, { target: { value: '' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ period: undefined });
    });
  });

  describe('Clear Button', () => {
    it('is disabled when no active filters', () => {
      renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      expect(clearButton).toBeDisabled();
    });

    it('is enabled when keyword filter is active', () => {
      const filtersWithKeyword: BudgetFiltersState = { keyword: 'test' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithKeyword} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      expect(clearButton).toBeEnabled();
    });

    it('is enabled when period filter is active', () => {
      const filtersWithPeriod: BudgetFiltersState = { period: 'MONTHLY' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithPeriod} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      expect(clearButton).toBeEnabled();
    });

    it('is enabled when both filters are active', () => {
      const filtersWithBoth: BudgetFiltersState = { keyword: 'test', period: 'MONTHLY' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithBoth} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      expect(clearButton).toBeEnabled();
    });

    it('calls onFiltersChange with empty object when clicked', () => {
      const filtersWithKeyword: BudgetFiltersState = { keyword: 'test' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithKeyword} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      fireEvent.click(clearButton);

      expect(mockOnFiltersChange).toHaveBeenCalledWith({});
    });
  });

  describe('Filter State Combinations', () => {
    it('enables clear button when period is selected from empty state', () => {
      const { rerender } = renderWithProviders(
        <BudgetFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const select = screen.getByRole('combobox');
      fireEvent.change(select, { target: { value: 'MONTHLY' } });

      // Re-render with updated filters
      rerender(
        <BudgetFilters filters={{ period: 'MONTHLY' }} onFiltersChange={mockOnFiltersChange} />
      );

      const clearButton = screen.getByRole('button', { name: /clear/i });
      expect(clearButton).toBeEnabled();
    });

    it('maintains other filters when one is changed', () => {
      const filtersWithBoth: BudgetFiltersState = { keyword: 'test', period: 'MONTHLY' };
      renderWithProviders(
        <BudgetFilters filters={filtersWithBoth} onFiltersChange={mockOnFiltersChange} />
      );

      const input = screen.getByPlaceholderText('Search budgets...');
      fireEvent.change(input, { target: { value: 'new keyword' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith({
        keyword: 'new keyword',
        period: 'MONTHLY',
      });
    });
  });
});
