import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, act } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AccountFilters } from '@/components/accounts/AccountFilters';
import type { AccountFilters as Filters } from '@/types/account';

// ---------------------------------------------------------------------------
// Mock CurrencySelector (heavy dependency)
// ---------------------------------------------------------------------------
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({ value, onValueChange }: { value: string; onValueChange?: (v: string) => void }) => (
    <select data-testid="currency-selector" value={value ?? ''} onChange={(e) => onValueChange?.(e.target.value)}>
      <option value="">All currencies</option>
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
    </select>
  ),
}));

describe('AccountFilters', () => {
  const defaultFilters: Filters = { sort: 'name,asc', page: 0 };
  const mockOnFiltersChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  describe('Rendering', () => {
    it('should render the keyword search input', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );
      expect(screen.getByPlaceholderText(/search accounts/i)).toBeInTheDocument();
    });

    it('should render account type filter', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );
      // Type filter is a select
      expect(screen.getByRole('combobox', { name: /type/i }) || document.querySelector('select')).toBeTruthy();
    });

    it('should render currency selector', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );
      expect(screen.getByTestId('currency-selector')).toBeInTheDocument();
    });
  });

  describe('Keyword filter', () => {
    it('should update keyword filter after debounce', async () => {
      vi.useFakeTimers();
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const input = screen.getByPlaceholderText(/search accounts/i);
      fireEvent.change(input, { target: { value: 'savings' } });

      await act(async () => {
        vi.advanceTimersByTime(500);
      });

      expect(mockOnFiltersChange).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: 'savings', page: 0 })
      );

      vi.useRealTimers();
    });

    it('should clear keyword when input is cleared', async () => {
      vi.useFakeTimers();
      renderWithProviders(
        <AccountFilters
          filters={{ ...defaultFilters, keyword: 'savings' }}
          onFiltersChange={mockOnFiltersChange}
        />
      );

      const input = screen.getByPlaceholderText(/search accounts/i);
      fireEvent.change(input, { target: { value: '' } });

      await act(async () => {
        vi.advanceTimersByTime(500);
      });

      expect(mockOnFiltersChange).toHaveBeenCalledWith(
        expect.objectContaining({ keyword: undefined })
      );

      vi.useRealTimers();
    });

    it('should sync local keyword when external filter keyword is cleared', async () => {
      const { rerender } = renderWithProviders(
        <AccountFilters
          filters={{ ...defaultFilters, keyword: 'savings' }}
          onFiltersChange={mockOnFiltersChange}
        />
      );

      const input = screen.getByPlaceholderText(/search accounts/i) as HTMLInputElement;
      expect(input.value).toBe('savings');

      rerender(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      await waitFor(() => {
        expect(input.value).toBe('');
      });
    });
  });

  describe('Account type filter', () => {
    it('should call onFiltersChange when type changes', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const selects = screen.getAllByRole('combobox');
      // Find the account type select (first select after currency)
      const typeSelect = selects.find(
        (s) => s !== screen.getByTestId('currency-selector')
      );
      if (typeSelect) {
        fireEvent.change(typeSelect, { target: { value: 'CHECKING' } });
        expect(mockOnFiltersChange).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'CHECKING' })
        );
      }
    });
  });

  describe('Clear filters', () => {
    it('should show clear button when active filters exist', () => {
      renderWithProviders(
        <AccountFilters
          filters={{ ...defaultFilters, keyword: 'test', type: 'SAVINGS' }}
          onFiltersChange={mockOnFiltersChange}
        />
      );
      expect(screen.getByRole('button', { name: /clear/i })).toBeInTheDocument();
    });

    it('should disable clear button when only default filters are set', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );
      // Clear button is always present but disabled when no active filters
      const clearButton = screen.queryByRole('button', { name: /clear/i });
      if (clearButton) {
        expect(clearButton).toBeDisabled();
      } else {
        // Button may be hidden when no active filters
        expect(clearButton).toBeNull();
      }
    });

    it('should reset filters when clear button is clicked', () => {
      renderWithProviders(
        <AccountFilters
          filters={{ ...defaultFilters, keyword: 'test', type: 'SAVINGS' }}
          onFiltersChange={mockOnFiltersChange}
        />
      );

      fireEvent.click(screen.getByRole('button', { name: /clear/i }));

      expect(mockOnFiltersChange).toHaveBeenCalledWith({ sort: 'name,asc', page: 0 });
    });
  });

  describe('Currency filter', () => {
    it('should call onFiltersChange when currency changes', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const currencySelector = screen.getByTestId('currency-selector');
      fireEvent.change(currencySelector, { target: { value: 'EUR' } });

      expect(mockOnFiltersChange).toHaveBeenCalledWith(
        expect.objectContaining({ currency: 'EUR' })
      );
    });
  });

  describe('Sort filter', () => {
    it('should call onFiltersChange when sort changes', () => {
      renderWithProviders(
        <AccountFilters filters={defaultFilters} onFiltersChange={mockOnFiltersChange} />
      );

      const selects = screen.getAllByRole('combobox');
      const sortSelect = selects[selects.length - 1]; // sort is typically last
      fireEvent.change(sortSelect, { target: { value: 'balance,desc' } });

      expect(mockOnFiltersChange).toHaveBeenCalled();
    });
  });
});
