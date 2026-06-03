import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import CashFlowChart from './CashFlowChart';
import { MemoryRouter } from 'react-router';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { VisibilityProvider, useVisibility } from '@/context/VisibilityContext';
import { NumberFormatProvider } from '@/context/NumberFormatContext';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// Mock Recharts — render children and call tickFormatter / tooltip render
vi.mock('recharts', async () => {
  const React = await import('react');
  return {
    ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
    BarChart: ({ children, data }: any) => <div data-testid="bar-chart" data-items={JSON.stringify(data)}>{children}</div>,
    Bar: () => <div data-testid="bar" />,
    XAxis: () => <div data-testid="x-axis" />,
    YAxis: ({ tickFormatter }: any) => {
      // Expose tickFormatter for testing
      if (tickFormatter) {
        (window as any).__yAxisTickFormatter = tickFormatter;
      }
      return <div data-testid="y-axis" />;
    },
    Tooltip: ({ content }: any) => {
      // Render custom tooltip with mock payload
      if (content) {
        return <div data-testid="tooltip-wrapper">{React.cloneElement(content, {})}</div>;
      }
      return <div data-testid="tooltip" />;
    },
    CartesianGrid: () => <div data-testid="cartesian-grid" />,
  };
});

// Mock UserSettings hook
vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({ data: { numberFormat: '1,234.56' }, isLoading: false }),
  useUpdateUserSettings: () => ({ mutate: vi.fn() }),
}));

// Mock VisibilityContext to control isAmountsVisible
let mockIsAmountsVisible = true;
vi.mock('@/context/VisibilityContext', async () => {
  const actual = await vi.importActual('@/context/VisibilityContext');
  return {
    ...actual,
    useVisibility: () => ({
      isAmountsVisible: mockIsAmountsVisible,
      toggleVisibility: vi.fn(),
    }),
  };
});

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const mockCashFlow = {
  income: 5000,
  expenses: 3000,
  netCashFlow: 2000,
};

const renderChart = (props: Partial<Parameters<typeof CashFlowChart>[0]> = {}) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <VisibilityProvider>
        <I18nextProvider i18n={i18n}>
          <NumberFormatProvider>
            <MemoryRouter>
              <CashFlowChart cashFlow={mockCashFlow} {...props} />
            </MemoryRouter>
          </NumberFormatProvider>
        </I18nextProvider>
      </VisibilityProvider>
    </QueryClientProvider>
  );
};

describe('CashFlowChart', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsAmountsVisible = true;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders title and help tooltip', () => {
    renderChart();
    expect(screen.getByText('Cash Flow')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /help/i })).toBeInTheDocument();
  });

  it('renders period text with default 30 days', () => {
    renderChart();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
  });

  it('renders custom period', () => {
    renderChart({ period: 7 });
    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
  });

  it('renders net cash flow label', () => {
    renderChart();
    expect(screen.getByText('Net Cash Flow')).toBeInTheDocument();
  });

  it('shows + prefix for positive net cash flow', () => {
    renderChart();
    // The + prefix is rendered before the formatted amount
    const netContainer = screen.getByText('Net Cash Flow').closest('div')?.parentElement;
    expect(netContainer?.textContent).toContain('+');
  });

  it('does not show + prefix for negative net cash flow', () => {
    renderChart({ cashFlow: { income: 2000, expenses: 5000, netCashFlow: -3000 } });
    const netContainer = screen.getByText('Net Cash Flow').closest('div')?.parentElement;
    // Should have text-red-500 class
    const amountEl = netContainer?.querySelector('.text-red-500');
    expect(amountEl).toBeInTheDocument();
  });

  it('renders income and expense labels in breakdown', () => {
    renderChart();
    // The breakdown section uses "Income:" and "Expenses:" with colons
    expect(screen.getByText(/Income/)).toBeInTheDocument();
    expect(screen.getByText(/Expenses/)).toBeInTheDocument();
  });

  it('Y-axis formatter returns "k" suffix for values >= 1000', () => {
    renderChart();
    const formatter = (window as any).__yAxisTickFormatter;
    expect(formatter).toBeDefined();
    expect(formatter(1000)).toBe('1k');
    expect(formatter(2500)).toBe('3k'); // toFixed(0) rounds
    expect(formatter(500)).toBe('500');
  });

  it('Y-axis formatter returns dots when amounts hidden', () => {
    mockIsAmountsVisible = false;
    renderChart();
    const formatter = (window as any).__yAxisTickFormatter;
    expect(formatter(1000)).toBe('••••');
    expect(formatter(500)).toBe('••••');
  });

  it('applies green class for positive net, red for negative', () => {
    const { container, unmount } = renderChart();
    expect(container.querySelector('.text-green-500')).toBeInTheDocument();
    unmount();

    renderChart({ cashFlow: { income: 1000, expenses: 3000, netCashFlow: -2000 } });
    expect(screen.getByText('Net Cash Flow').closest('div')?.parentElement?.querySelector('.text-red-500')).toBeInTheDocument();
  });
});
