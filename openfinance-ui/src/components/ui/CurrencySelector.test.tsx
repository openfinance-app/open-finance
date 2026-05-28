import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';
import { CurrencySelector, CurrencySelectorCompact } from './CurrencySelector';

const mockCurrencies = [
  { code: 'USD', name: 'US Dollar', symbol: '$', isActive: true },
  { code: 'EUR', name: 'Euro', symbol: '\u20AC', isActive: true },
  { code: 'GBP', name: 'British Pound', symbol: '\u00A3', isActive: true },
  { code: 'JPY', name: 'Japanese Yen', symbol: '\u00A5', isActive: false },
];

let mockIsLoading = false;
let mockIsError = false;

vi.mock('@/hooks/useCurrency', () => ({
  useCurrencies: () => ({
    data: mockIsLoading ? undefined : mockIsError ? undefined : mockCurrencies,
    isLoading: mockIsLoading,
    isError: mockIsError,
  }),
}));

describe('CurrencySelector', () => {
  const onValueChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockIsLoading = false;
    mockIsError = false;
  });

  it('renders with placeholder when no value selected', () => {
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} placeholder="Pick a currency" />
    );
    expect(screen.getByText('Pick a currency')).toBeInTheDocument();
  });

  it('shows loading spinner when loading', () => {
    mockIsLoading = true;
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows error when load fails', () => {
    mockIsError = true;
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);
    expect(screen.getByText(/error|fail/i)).toBeInTheDocument();
  });

  it('displays selected currency with code', () => {
    renderWithProviders(
      <CurrencySelector value="USD" onValueChange={onValueChange} />
    );
    expect(screen.getByText('USD')).toBeInTheDocument();
  });

  it('opens dropdown and shows currencies when clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} />
    );

    const trigger = screen.getByRole('button');
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
      expect(screen.getByText('GBP')).toBeInTheDocument();
    });
  });

  it('filters inactive currencies by default', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('USD')).toBeInTheDocument();
    });
    expect(screen.queryByText('JPY')).not.toBeInTheDocument();
  });

  it('shows inactive currencies when showInactive is true', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} showInactive />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('JPY')).toBeInTheDocument();
    });
  });

  it('calls onValueChange when selecting a currency', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
    });

    const eurButtons = screen.getAllByText('EUR');
    const eurOption = eurButtons.find(
      el => el.closest('button[type="button"]') && el.closest('[class*="overflow-y-auto"]')
    );
    if (eurOption) {
      await user.click(eurOption.closest('button')!);
    }

    expect(onValueChange).toHaveBeenCalledWith('EUR');
  });

  it('shows allowNone option when enabled', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector value="USD" onValueChange={onValueChange} allowNone />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getAllByText(/all currencies/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('disables the trigger when disabled prop is true', () => {
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} disabled />
    );

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('filters currencies by search query', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('USD')).toBeInTheDocument();
    });

    const searchInput = screen.getByRole('textbox');
    await user.type(searchInput, 'euro');

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
    });
  });

  it('shows no match for unmatched search', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelector onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));
    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox'), 'xyznonexistent');

    await waitFor(() => {
      expect(screen.queryByText('USD')).not.toBeInTheDocument();
    });
  });
});

describe('CurrencySelectorCompact', () => {
  const onValueChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockIsLoading = false;
    mockIsError = false;
  });

  it('renders with selected currency symbol and code', () => {
    renderWithProviders(
      <CurrencySelectorCompact value="USD" onValueChange={onValueChange} />
    );
    expect(screen.getByText('$')).toBeInTheDocument();
    expect(screen.getByText('USD')).toBeInTheDocument();
  });

  it('renders placeholder when no value', () => {
    renderWithProviders(
      <CurrencySelectorCompact onValueChange={onValueChange} />
    );
    expect(screen.getByText('Select')).toBeInTheDocument();
  });

  it('shows loading spinner when loading', () => {
    mockIsLoading = true;
    renderWithProviders(
      <CurrencySelectorCompact onValueChange={onValueChange} />
    );
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('opens dropdown and shows currencies', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelectorCompact onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
    });
  });

  it('selects a currency from the dropdown', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CurrencySelectorCompact onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('GBP')).toBeInTheDocument();
    });

    const gbpOption = screen.getByText('GBP').closest('button');
    if (gbpOption) {
      await user.click(gbpOption);
    }

    expect(onValueChange).toHaveBeenCalledWith('GBP');
  });
});
