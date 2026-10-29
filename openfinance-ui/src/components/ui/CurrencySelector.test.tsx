import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
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
    Element.prototype.scrollIntoView = vi.fn();
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
    renderWithProviders(<CurrencySelector value="USD" onValueChange={onValueChange} />);
    expect(screen.getByText('USD')).toBeInTheDocument();
  });

  it('opens dropdown and shows currencies when clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

    const trigger = screen.getByRole('button');
    await user.click(trigger);

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
      expect(screen.getByText('GBP')).toBeInTheDocument();
    });
  });

  it('opens with an arrow key and selects a currency with the keyboard', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

    const trigger = screen.getByRole('button');
    trigger.focus();
    await user.keyboard('{ArrowDown}');

    const search = await screen.findByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());
    await user.keyboard('{ArrowDown}{Enter}');

    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('EUR');
    await waitFor(() => expect(trigger).toHaveFocus());
  });

  it('filters inactive currencies by default', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('USD')).toBeInTheDocument();
    });
    expect(screen.queryByText('JPY')).not.toBeInTheDocument();
  });

  it('shows inactive currencies when showInactive is true', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} showInactive />);

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('JPY')).toBeInTheDocument();
    });
  });

  it('calls onValueChange when selecting a currency', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

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
    renderWithProviders(<CurrencySelector value="USD" onValueChange={onValueChange} allowNone />);

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getAllByText(/all currencies/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('allows clearing the currency with the keyboard', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector value="USD" onValueChange={onValueChange} allowNone />);
    screen.getByRole('button').focus();
    await user.keyboard('{ArrowDown}');
    await waitFor(() => expect(screen.getByRole('combobox')).toHaveFocus());
    await user.keyboard('{ArrowUp}{Enter}');

    expect(onValueChange).toHaveBeenCalledExactlyOnceWith(undefined);
  });

  it('disables the trigger when disabled prop is true', () => {
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} disabled />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('filters currencies by search query', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('USD')).toBeInTheDocument();
    });

    const searchInput = screen.getByRole('combobox');
    await user.type(searchInput, 'euro');

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
    });
  });

  it('shows no match for unmatched search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelector onValueChange={onValueChange} />);

    await user.click(screen.getByRole('button'));
    await waitFor(() => {
      expect(screen.getByRole('combobox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('combobox'), 'xyznonexistent');

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
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders with selected currency symbol and code', () => {
    renderWithProviders(<CurrencySelectorCompact value="USD" onValueChange={onValueChange} />);
    expect(screen.getByText('$')).toBeInTheDocument();
    expect(screen.getByText('USD')).toBeInTheDocument();
  });

  it('renders placeholder when no value', () => {
    renderWithProviders(<CurrencySelectorCompact onValueChange={onValueChange} />);
    expect(screen.getByText('Select')).toBeInTheDocument();
  });

  it('shows loading spinner when loading', () => {
    mockIsLoading = true;
    renderWithProviders(<CurrencySelectorCompact onValueChange={onValueChange} />);
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('opens dropdown and shows currencies', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelectorCompact onValueChange={onValueChange} />);

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByText('EUR')).toBeInTheDocument();
    });
  });

  it('selects a currency from the dropdown', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CurrencySelectorCompact onValueChange={onValueChange} />);

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

describe.each([
  { name: 'full', Component: CurrencySelector, lastCode: 'GBP' },
  { name: 'compact', Component: CurrencySelectorCompact, lastCode: 'JPY' },
])('CurrencySelector keyboard navigation ($name)', ({ Component, lastCode }) => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockIsLoading = false;
    mockIsError = false;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('opens with ArrowUp at the last option and selects with Enter', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component onValueChange={onValueChange} />);
    screen.getByRole('button').focus();
    await user.keyboard('{ArrowUp}');

    const search = screen.getByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());
    const option = screen.getByRole('option', { name: new RegExp(lastCode) });
    expect(search).toHaveAttribute('aria-activedescendant', option.id);
    await user.keyboard('{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith(lastCode);
  });

  it('moves both ways, stops at list boundaries, and preserves search focus', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component onValueChange={onValueChange} />);
    await user.click(screen.getByRole('button'));
    const search = screen.getByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());

    await user.keyboard('{ArrowDown}{ArrowDown}');
    expect(search).toHaveAttribute(
      'aria-activedescendant',
      screen.getByRole('option', { name: /EUR/ }).id
    );
    await user.keyboard('{ArrowUp}{ArrowUp}');
    expect(search).toHaveAttribute(
      'aria-activedescendant',
      screen.getByRole('option', { name: /USD/ }).id
    );
    expect(search).toHaveFocus();
    expect(onValueChange).not.toHaveBeenCalled();
    await user.keyboard('{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('USD');
  });

  it('drops a stale highlight when filtering and selects only a visible match', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component onValueChange={onValueChange} />);
    await user.click(screen.getByRole('button'));
    const search = screen.getByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());
    await user.keyboard('{ArrowDown}{ArrowDown}{ArrowDown}');
    await user.type(search, 'euro');

    expect(search).not.toHaveAttribute('aria-activedescendant');
    await user.keyboard('{Enter}');
    expect(onValueChange).not.toHaveBeenCalled();
    await user.keyboard('{ArrowDown}{Enter}');
    expect(onValueChange).toHaveBeenCalledExactlyOnceWith('EUR');
  });

  it('handles empty results and ignores keys during IME composition', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    const onSubmit = vi.fn();
    renderWithProviders(
      <form onSubmit={onSubmit}>
        <Component onValueChange={onValueChange} />
      </form>
    );
    await user.click(screen.getByRole('button'));
    const search = screen.getByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());
    await user.keyboard('{ArrowDown}');
    const activeId = search.getAttribute('aria-activedescendant');

    fireEvent.keyDown(search, { key: 'ArrowDown', isComposing: true });
    expect(search).toHaveAttribute('aria-activedescendant', activeId);
    fireEvent.keyDown(search, { key: 'Enter', isComposing: true });
    expect(onValueChange).not.toHaveBeenCalled();

    await user.type(search, 'zzzzzz');
    await user.keyboard('{ArrowDown}{ArrowUp}{Enter}');
    expect(screen.queryByRole('option')).not.toBeInTheDocument();
    expect(search).not.toHaveAttribute('aria-activedescendant');
    expect(onValueChange).not.toHaveBeenCalled();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('starts at the selected currency and resets search when dismissed', async () => {
    const user = userEvent.setup();
    const onValueChange = vi.fn();
    renderWithProviders(<Component value="EUR" onValueChange={onValueChange} />);
    const trigger = screen.getByRole('button');
    trigger.focus();
    await user.keyboard('{ArrowDown}');
    const search = screen.getByRole('combobox');
    await waitFor(() => expect(search).toHaveFocus());
    expect(search).toHaveAttribute(
      'aria-activedescendant',
      screen.getByRole('option', { name: /EUR/ }).id
    );

    await user.type(search, 'pound');
    await user.keyboard('{Escape}');
    await waitFor(() => expect(trigger).toHaveFocus());
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(onValueChange).not.toHaveBeenCalled();
    await user.keyboard('{ArrowUp}');
    expect(screen.getByRole('combobox')).toHaveValue('');
  });
});
