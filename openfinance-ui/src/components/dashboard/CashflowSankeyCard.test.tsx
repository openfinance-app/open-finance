import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import CashflowSankeyCard from './CashflowSankeyCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { useCashflowSankey } from '../../hooks/useDashboard';
import type { ICashflowSankey } from '@/types/dashboard';
import { CASHFLOW_DEFICIT_COLOR, CASHFLOW_SURPLUS_COLOR } from '@/constants/colors';

const mockNavigate = vi.fn();
vi.mock('react-router', async importOriginal => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});
vi.mock('../../hooks/useDashboard', () => ({ useCashflowSankey: vi.fn() }));
vi.mock('../ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: number }) => <span>{amount}</span>,
}));

const mockUseCashflowSankey = vi.mocked(useCashflowSankey);

const sankeyData = {
  totalIncome: 1000,
  totalExpenses: 600,
  surplus: 400,
  incomeSources: [{ name: 'Salary', amount: 1000, categoryId: 5 }],
  expenseCategories: [
    { name: 'Groceries', amount: 400, categoryId: 7 },
    { name: 'Uncategorized', amount: 200, categoryId: null },
  ],
  period: 30,
};

describe('CashflowSankeyCard navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockUseCashflowSankey.mockReturnValue({
      data: sankeyData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useCashflowSankey>);
  });

  it('navigates to income transactions from the income pill', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View income transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?type=INCOME&excludeTransfers=true&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates to expense transactions from the expenses pill', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View expense transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?type=EXPENSE&excludeTransfers=true&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates by categoryId when a flow is clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View Salary transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?categoryId=5&type=INCOME&excludeTransfers=true&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });

  it('navigates with noCategory for the Uncategorized flow', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CashflowSankeyCard period={30} navDateRange={{ from: '2026-08-01', to: '2026-08-31' }} />
    );
    await user.click(screen.getByRole('button', { name: 'View Uncategorized transactions' }));
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?noCategory=1&type=EXPENSE&excludeTransfers=true&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });
});

describe('CashflowSankeyCard comparison and zoom', () => {
  const range = { from: '2026-08-01', to: '2026-08-31' };
  const current: ICashflowSankey = {
    totalIncome: 1200,
    totalExpenses: 475,
    surplus: 725,
    period: 31,
    incomeSources: [{ name: 'Salary', categoryId: 5, amount: 1200 }],
    expenseCategories: [
      { name: 'Groceries', categoryId: 7, amount: 300 },
      { name: 'Uncategorized', categoryId: null, amount: 100 },
      { name: 'Transit', categoryId: 9, amount: 75 },
    ],
  };
  const previous: ICashflowSankey = {
    totalIncome: 1000,
    totalExpenses: 450,
    surplus: 550,
    period: 31,
    incomeSources: [{ name: 'Salary', categoryId: 5, amount: 1000 }],
    expenseCategories: [
      { name: 'Groceries', categoryId: 7, amount: 200 },
      { name: 'Uncategorized', categoryId: null, amount: 200 },
      { name: 'Travel', categoryId: 8, amount: 50 },
    ],
  };

  function mockPeriods(
    now = current,
    before = previous,
    error: Error | null = null,
    isLoading = false
  ) {
    mockUseCashflowSankey.mockImplementation(
      (_period, dateRange) =>
        ({
          data: dateRange?.to === range.to ? now : isLoading ? undefined : before,
          error: dateRange?.to === range.to ? null : error,
          isLoading: dateRange?.to === range.to ? false : isLoading,
        }) as ReturnType<typeof useCashflowSankey>
    );
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    localStorage.setItem('open_finance_amounts_visible', 'true');
    mockPeriods();
  });

  it('shows signed amount and percentage changes, colored by income/expense direction', () => {
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
    expect(
      within(screen.getByRole('button', { name: 'View Groceries transactions' })).getByText(
        '(+$100.00/+50%)'
      )
    ).toHaveAttribute('fill', CASHFLOW_DEFICIT_COLOR);
    expect(screen.getByText('(+$200.00/+20%)')).toHaveAttribute('fill', CASHFLOW_SURPLUS_COLOR);
    expect(screen.getByText('(-$100.00/-50%)')).toHaveAttribute('fill', CASHFLOW_SURPLUS_COLOR);
    expect(
      screen.queryByRole('button', { name: 'View Travel transactions' })
    ).not.toBeInTheDocument();
    expect(screen.getByText('(+$75.00)')).toHaveAttribute('fill', CASHFLOW_DEFICIT_COLOR);
    expect(mockUseCashflowSankey).toHaveBeenCalledWith(
      30,
      { from: '2026-07-01', to: '2026-07-31' },
      true
    );
  });

  it('shows an income decrease in red', () => {
    mockPeriods({ ...current, incomeSources: [{ name: 'Salary', categoryId: 5, amount: 800 }] });
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
    expect(screen.getByText('(-$200.00/-20%)')).toHaveAttribute('fill', CASHFLOW_DEFICIT_COLOR);
  });

  it.each([false, true])(
    'keeps the same flows, canvas and zoom when toggling variations (no income: %s)',
    async noIncome => {
      const user = userEvent.setup();
      mockPeriods(noIncome ? { ...current, totalIncome: 0, incomeSources: [] } : current);
      renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
      const viewport = screen.getByRole('group', { name: 'Cash flow diagram' });
      const toggle = screen.getByRole('button', { name: 'Show/hide variations' });
      await user.click(toggle);
      fireEvent.wheel(viewport, { deltaY: 110, clientX: 100, clientY: 60 });

      const geometry = () => ({
        viewBox: viewport.querySelector('svg')?.getAttribute('viewBox'),
        transform: viewport.firstElementChild?.getAttribute('style'),
        shapes: Array.from(viewport.querySelectorAll('path, rect, line')).map(shape =>
          ['d', 'x', 'y', 'width', 'height', 'x1', 'x2', 'y1', 'y2'].map(attribute =>
            shape.getAttribute(attribute)
          )
        ),
        categories: within(viewport)
          .getAllByRole('button')
          .map(node => node.getAttribute('aria-label')),
      });
      const hiddenGeometry = geometry();
      await user.click(toggle);
      expect(geometry()).toEqual(hiddenGeometry);
      expect(screen.getByRole('button', { name: 'Reset zoom' })).toHaveTextContent('86%');
      expect(
        screen.queryByRole('button', { name: 'View Travel transactions' })
      ).not.toBeInTheDocument();
      if (noIncome) {
        expect(
          screen.queryByRole('button', { name: 'View Salary transactions' })
        ).not.toBeInTheDocument();
      }
      expect(screen.getByText('(+$100.00/+50%)')).toBeInTheDocument();
    }
  );

  it('provides readable category variations on small screens', () => {
    const width = vi.spyOn(window, 'innerWidth', 'get').mockReturnValue(390);
    try {
      renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
      const list = screen.getByRole('list', { name: 'Category details' });
      const groceries = within(list).getByRole('button', { name: 'View Groceries transactions' });
      expect(groceries).toHaveTextContent('Groceries (+$100.00/+50%)');
      expect(within(groceries).getByText('$300.00')).toBeInTheDocument();
      const transit = within(list).getByRole('button', { name: 'View Transit transactions' });
      expect(within(transit).getByText('(+$75.00)')).toBeInTheDocument();
    } finally {
      width.mockRestore();
    }
  });

  it('hides and restores variations with the toggle and disables the comparison query', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
    const toggle = screen.getByRole('button', { name: 'Show/hide variations' });
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(toggle).toHaveAttribute('title', 'Show/hide variations');
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'false');
    expect(screen.queryByText('(+$100.00/+50%)')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'View Travel transactions' })
    ).not.toBeInTheDocument();
    expect(mockUseCashflowSankey).toHaveBeenCalledWith(
      30,
      { from: '2026-07-01', to: '2026-07-31' },
      false
    );
    await user.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('(+$100.00/+50%)')).toBeInTheDocument();
  });

  it('does not expose variation amounts or percentages when amounts are hidden', () => {
    localStorage.setItem('open_finance_amounts_visible', 'false');
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
    expect(screen.queryByText(/\(.*\/.*%\)/)).not.toBeInTheDocument();
    expect(screen.queryByText('(+$75.00)')).not.toBeInTheDocument();
  });

  it.each([
    [true, null, 'Loading comparison…'],
    [false, new Error('offline'), 'Comparison unavailable'],
  ])(
    'keeps the current diagram when the comparison is loading or fails',
    (loading, error, message) => {
      mockPeriods(current, previous, error, loading);
      renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
      expect(screen.getByRole('status')).toHaveTextContent(message);
      expect(
        screen.getByRole('button', { name: 'View Groceries transactions' })
      ).toBeInTheDocument();
      expect(screen.queryByText('(+$100.00/+50%)')).not.toBeInTheDocument();
    }
  );

  it('disables comparison for all time', () => {
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} isAllTime />);
    expect(screen.getByRole('button', { name: 'Show/hide variations' })).toBeDisabled();
    expect(screen.getByText('Select a period to compare')).toBeInTheDocument();
    expect(screen.queryByText('(+$100.00/+50%)')).not.toBeInTheDocument();
  });

  it('compares the same categories in Other even when their ranks change', () => {
    const top = Array.from({ length: 8 }, (_, index) => ({
      categoryId: index + 20,
      name: `Top ${index}`,
      amount: 1000,
    }));
    mockPeriods(
      {
        ...current,
        expenseCategories: [
          ...top,
          { categoryId: 40, name: 'A', amount: 80 },
          { categoryId: 41, name: 'B', amount: 20 },
        ],
      },
      {
        ...previous,
        expenseCategories: [
          ...top,
          { categoryId: 40, name: 'A', amount: 2000 },
          { categoryId: 41, name: 'B', amount: 0 },
          { categoryId: 42, name: 'Previous only', amount: 5000 },
        ],
      }
    );
    renderWithProviders(<CashflowSankeyCard currency="USD" dateRange={range} />);
    expect(screen.getByText('(-$1,900.00/-95%)')).toHaveAttribute('fill', CASHFLOW_SURPLUS_COLOR);
  });

  it('updates the compact caption, date tooltip and comparison when the period changes', async () => {
    const { rerender } = renderWithProviders(
      <CashflowSankeyCard dateRange={range} periodLabel="31d" />
    );
    const customCaption = screen.getByRole('button', { name: '31d', exact: true });
    fireEvent.focus(customCaption);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Selected period: 08/01/2026 → 08/31/2026'
    );
    fireEvent.blur(customCaption);

    rerender(
      <CashflowSankeyCard
        period={7}
        periodLabel="last 7d"
        dateRange={{ from: '2026-10-11', to: '2026-10-17' }}
      />
    );
    fireEvent.focus(screen.getByRole('button', { name: 'last 7d', exact: true }));
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'Selected period: 10/11/2026 → 10/17/2026'
    );
    expect(screen.getByRole('status')).toHaveTextContent('Compared with 10/04/2026 → 10/10/2026');
    expect(mockUseCashflowSankey).toHaveBeenCalledWith(
      7,
      { from: '2026-10-04', to: '2026-10-10' },
      true
    );
  });

  it('zooms with the wheel without Ctrl, blocks page scrolling, clamps, and resets', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CashflowSankeyCard dateRange={range} />);
    const viewport = screen.getByRole('group', { name: 'Cash flow diagram' });
    const event = new WheelEvent('wheel', {
      deltaY: -500,
      clientX: 100,
      clientY: 60,
      cancelable: true,
    });
    fireEvent(viewport, event);
    expect(event.defaultPrevented).toBe(true);
    const reset = screen.getByRole('button', { name: 'Reset zoom' });
    expect(reset).toHaveTextContent('200%');
    expect(viewport.firstElementChild).toHaveStyle({
      transform: 'translate(-100px, -60px) scale(2)',
    });
    fireEvent.wheel(viewport, { deltaY: -10000 });
    expect(reset).toHaveTextContent('300%');
    fireEvent.wheel(viewport, { deltaY: 10000 });
    expect(reset).toHaveTextContent('50%');
    await user.click(reset);
    expect(reset).toHaveTextContent('100%');
    expect(reset).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Zoom in' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Zoom out' })).not.toBeInTheDocument();
  });

  it('supports keyboard zoom and reset without intercepting category navigation', () => {
    renderWithProviders(<CashflowSankeyCard dateRange={range} navDateRange={range} />);
    const viewport = screen.getByRole('group', { name: 'Cash flow diagram' });
    fireEvent.keyDown(viewport, { key: '+' });
    expect(screen.getByRole('button', { name: 'Reset zoom' })).toHaveTextContent('125%');
    fireEvent.keyDown(viewport, { key: 'Home' });
    expect(screen.getByRole('button', { name: 'Reset zoom' })).toBeDisabled();
    fireEvent.keyDown(screen.getByRole('button', { name: 'View Groceries transactions' }), {
      key: 'Enter',
    });
    expect(mockNavigate).toHaveBeenCalledWith(
      '/transactions?categoryId=7&type=EXPENSE&excludeTransfers=true&dateFrom=2026-08-01&dateTo=2026-08-31'
    );
  });
});
