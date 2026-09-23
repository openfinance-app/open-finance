import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { useCashFlowHistory } from '@/hooks/useDashboard';
import CashFlowCalendar from '@/components/dashboard/CashFlowCalendar';

vi.mock('@/hooks/useDashboard', () => ({ useCashFlowHistory: vi.fn() }));
const mockNavigate = vi.fn();
const refetch = vi.fn();
vi.mock('react-router', async importOriginal => ({
  ...(await importOriginal<typeof import('react-router')>()),
  useNavigate: () => mockNavigate,
}));

function mockResult(overrides: Partial<ReturnType<typeof useCashFlowHistory>> = {}): void {
  vi.mocked(useCashFlowHistory).mockImplementation(
    granularity =>
      ({
        data:
          granularity === 'DAY'
            ? [{ date: '2024-02-29', income: 100, expense: 40 }]
            : granularity === 'MONTH'
              ? Array.from({ length: 12 }, (_, index) => ({
                  date: `2024-${String(index + 1).padStart(2, '0')}-01`,
                  income: index === 1 ? 1234.56 : 0,
                  expense: index === 1 ? 1500 : 0,
                }))
              : Array.from({ length: 10 }, (_, index) => ({
                  date: `${2015 + index}-01-01`,
                  income: 100,
                  expense: 40,
                })),
        isLoading: false,
        isError: false,
        refetch,
        ...overrides,
      }) as ReturnType<typeof useCashFlowHistory>
  );
}

describe('CashFlowCalendar', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2024, 1, 29, 12));
    mockAuthentication();
    localStorage.setItem('open_finance_amounts_visible', 'true');
    Element.prototype.scrollIntoView = vi.fn();
    await i18n.changeLanguage('en');
    mockResult();
  });

  afterEach(async () => {
    vi.useRealTimers();
    await i18n.changeLanguage('en');
  });

  it('keeps the daily calendar as the default, including leap day', () => {
    renderWithProviders(<CashFlowCalendar baseCurrency="USD" />);
    expect(screen.getByText('Cash Flow History')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Cash flow grouping' })).toHaveValue('DAY');
    expect(screen.getByText('Mon')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'View transactions for 2024-02-29' })
    ).toBeInTheDocument();
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('DAY', 2024, 2);
    expect(screen.queryByRole('figure')).not.toBeInTheDocument();
  });

  it('navigates months without skipping over a shorter month and returns to today', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar />);
    await user.click(screen.getByRole('button', { name: 'Next month' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('DAY', 2024, 3);
    await user.click(screen.getByRole('button', { name: 'Previous month' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('DAY', 2024, 2);
    await user.click(screen.getByRole('button', { name: 'Previous month' }));
    await user.click(screen.getByRole('button', { name: 'Today' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('DAY', 2024, 2);
  });

  it('opens daily transactions with transfers excluded to match the totals', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar />);
    await user.click(screen.getByRole('button', { name: 'View transactions for 2024-02-29' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?dateFrom=2024-02-29&dateTo=2024-02-29&excludeTransfers=true'
    );
  });

  it('shows monthly totals and a chart together, with a negative net and leap-year drill-down', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar baseCurrency="USD" />);
    await user.selectOptions(screen.getByRole('combobox'), 'MONTH');
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('MONTH', 2024, 2);
    expect(screen.getAllByRole('button', { name: /^View transactions for/ })).toHaveLength(12);
    expect(
      screen.getByRole('figure', { name: 'Income, expenses and net cash flow' })
    ).toBeInTheDocument();
    const february = screen.getByRole('button', { name: 'View transactions for February 2024' });
    expect(february).toHaveTextContent('1,234.56');
    expect(february).toHaveTextContent('1,500.00');
    expect(february).toHaveTextContent('-$265.44');
    await user.click(february);
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?dateFrom=2024-02-01&dateTo=2024-02-29&excludeTransfers=true'
    );
    await user.click(screen.getByRole('button', { name: 'Previous year' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('MONTH', 2023, 2);
  });

  it('shows yearly totals with the chart, navigates ten years, and keeps daily mode available', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar />);
    await user.selectOptions(screen.getByRole('combobox'), 'YEAR');
    expect(screen.getByText('2015–2024')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^View transactions for/ })).toHaveLength(10);
    expect(screen.getByRole('figure')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'View transactions for 2024' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?dateFrom=2024-01-01&dateTo=2024-12-31&excludeTransfers=true'
    );
    await user.click(screen.getByRole('button', { name: 'Previous 10 years' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('YEAR', 2014, 2);
    await user.click(screen.getByRole('button', { name: 'Next 10 years' }));
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('YEAR', 2024, 2);
    await user.selectOptions(screen.getByRole('combobox'), 'DAY');
    expect(screen.getByText('Mon')).toBeInTheDocument();
    expect(screen.queryByRole('figure')).not.toBeInTheDocument();
  });

  it('hides monthly amounts when privacy is enabled', async () => {
    localStorage.setItem('open_finance_amounts_visible', 'false');
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar baseCurrency="USD" />);
    await user.selectOptions(screen.getByRole('combobox'), 'MONTH');
    const february = screen.getByRole('button', { name: 'View transactions for February 2024' });
    const income = within(february).getByText('$1,234.56');
    expect(income).toHaveClass('blur-md');
    expect(income).toHaveAttribute('aria-hidden', 'true');
    expect(within(february).getByText('-$265.44')).toHaveAttribute('aria-hidden', 'true');
  });

  it('keeps navigation available when loading fails and lets the user retry', async () => {
    mockResult({ isError: true });
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar />);
    expect(screen.getByRole('alert')).toHaveTextContent('Failed to load cash flow data');
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(refetch).toHaveBeenCalledOnce();
    await user.selectOptions(screen.getByRole('combobox'), 'YEAR');
    expect(useCashFlowHistory).toHaveBeenLastCalledWith('YEAR', 2024, 2);
  });

  it('shows an accessible loading state', () => {
    mockResult({ data: undefined, isLoading: true });
    renderWithProviders(<CashFlowCalendar />);
    expect(screen.getByRole('status', { name: 'Loading cash flow' })).toBeInTheDocument();
  });

  it('translates the selector, periods and chart in French', async () => {
    await i18n.changeLanguage('fr');
    const user = userEvent.setup();
    renderWithProviders(<CashFlowCalendar />);
    expect(screen.getByText('Historique des flux de trésorerie')).toBeInTheDocument();
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Regroupement des flux de trésorerie' }),
      'MONTH'
    );
    expect(
      screen.getByRole('button', { name: 'Voir les transactions pour février 2024' })
    ).toBeInTheDocument();
    expect(
      within(screen.getByRole('combobox')).getByRole('option', { name: 'Par année' })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('figure', { name: 'Revenus, dépenses et flux de trésorerie net' })
    ).toBeInTheDocument();
  });
});
