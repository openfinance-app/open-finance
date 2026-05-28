import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AssetFilters } from './AssetFilters';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({ value, onValueChange }: { value?: string; onValueChange: (v?: string) => void }) => (
    <select
      data-testid="currency-selector"
      value={value || ''}
      onChange={e => onValueChange(e.target.value || undefined)}
    >
      <option value="">All</option>
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
    </select>
  ),
}));

describe('AssetFilters', () => {
  const onFiltersChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders core inputs and clear button', () => {
    renderWithProviders(
      <AssetFilters
        filters={{ sort: 'name,asc', page: 0 }}
        onFiltersChange={onFiltersChange}
      />
    );

    expect(screen.getByLabelText(/search|filtersPanel\.search/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/asset type|filtersPanel\.assetType/i)).toBeInTheDocument();
    expect(screen.getByTestId('currency-selector')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /clear|filtersPanel\.clear/i }).closest('button')).toBeDisabled();
  });

  it('updates keyword, numeric ranges, and sort', async () => {
    const user = userEvent.setup();

    renderWithProviders(
      <AssetFilters
        filters={{ sort: 'name,asc', page: 0 }}
        onFiltersChange={onFiltersChange}
      />
    );

    await user.type(screen.getByLabelText(/search|filtersPanel\.search/i), 'apple');
    await user.type(screen.getByTestId('filter-min-value'), '10');
    await user.type(screen.getByTestId('filter-max-value'), '200');
    await user.selectOptions(screen.getByTestId('filter-sort'), 'totalValue,desc');

    expect(onFiltersChange).toHaveBeenCalled();
  });

  it('clears active filters to default payload', async () => {
    const user = userEvent.setup();

    renderWithProviders(
      <AssetFilters
        filters={{
          sort: 'totalValue,desc',
          page: 4,
          keyword: 'btc',
          symbol: 'BTC',
        }}
        onFiltersChange={onFiltersChange}
      />
    );

    const clearButton = screen.getByRole('button', { name: /clear|filtersPanel\.clear/i });
    expect(clearButton).not.toBeDisabled();
    await user.click(clearButton);

    expect(onFiltersChange).toHaveBeenCalledWith({
      sort: 'name,asc',
      page: 0,
    });
  });
});