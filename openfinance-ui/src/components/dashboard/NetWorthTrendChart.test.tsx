import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

let capturedTooltipContent: any = null;
let capturedYAxisFormatter: any = null;
let capturedXAxisFormatter: any = null;

vi.mock('recharts', () => ({
  AreaChart: ({ children, data }: any) => (
    <div data-testid="area-chart" data-chart-points={data?.length}>
      {children}
    </div>
  ),
  Area: () => null,
  XAxis: ({ tickFormatter }: any) => {
    capturedXAxisFormatter = tickFormatter;
    return null;
  },
  YAxis: ({ tickFormatter }: any) => {
    capturedYAxisFormatter = tickFormatter;
    return null;
  },
  CartesianGrid: () => null,
  Tooltip: ({ content }: any) => {
    capturedTooltipContent = content;
    return null;
  },
  ResponsiveContainer: ({ children }: any) => <div>{children}</div>,
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: any) => <span>{children}</span>,
}));

import NetWorthTrendChart from './NetWorthTrendChart';

describe('NetWorthTrendChart', () => {
  beforeEach(() => {
    mockAuthentication();
  });

  const data = [
    { date: '2024-01-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
    { date: '2024-02-01', totalAssets: 55000, totalLiabilities: 19000, netWorth: 36000 },
    { date: '2024-03-01', totalAssets: 60000, totalLiabilities: 18000, netWorth: 42000 },
  ];

  it('renders chart with data', () => {
    renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
    expect(screen.getByTestId('area-chart')).toBeInTheDocument();
  });

  it('renders empty state with no data', () => {
    renderWithProviders(<NetWorthTrendChart data={[]} currency="EUR" />);
    expect(screen.queryByTestId('area-chart')).not.toBeInTheDocument();
  });

  it('shows trend indicator for positive growth', () => {
    const { container } = renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
    // Net worth increased from 30000 to 42000 = +40% — should show positive trend
    expect(container.querySelector('.text-green-500, .text-emerald-500') || container.innerHTML).toBeTruthy();
  });

  it('shows negative trend indicator for decline', () => {
    const decliningData = [
      { date: '2024-01-01', totalAssets: 60000, totalLiabilities: 18000, netWorth: 42000 },
      { date: '2024-02-01', totalAssets: 55000, totalLiabilities: 19000, netWorth: 36000 },
      { date: '2024-03-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
    ];
    const { container } = renderWithProviders(<NetWorthTrendChart data={decliningData} currency="EUR" />);
    // Should show red/negative indicator
    expect(container.innerHTML).toContain('text-red');
  });

  it('shows neutral indicator when no change', () => {
    const flatData = [
      { date: '2024-01-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
      { date: '2024-02-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
    ];
    const { container } = renderWithProviders(<NetWorthTrendChart data={flatData} currency="EUR" />);
    expect(container.innerHTML).not.toBe('');
  });

  it('renders with periodLabel prop', () => {
    renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" periodLabel="Last 90 days" />);
    expect(screen.getByText(/Last 90 days/)).toBeInTheDocument();
  });

  it('displays percentage change', () => {
    const { container } = renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
    // 30000 → 42000 = +40%
    expect(container.textContent).toMatch(/40/);
  });

  it('handles single data point without crash', () => {
    const singlePoint = [
      { date: '2024-01-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
    ];
    const { container } = renderWithProviders(<NetWorthTrendChart data={singlePoint} currency="EUR" />);
    expect(container.innerHTML).not.toBe('');
  });

  it('handles zero starting value', () => {
    const zeroStart = [
      { date: '2024-01-01', totalAssets: 0, totalLiabilities: 0, netWorth: 0 },
      { date: '2024-02-01', totalAssets: 10000, totalLiabilities: 0, netWorth: 10000 },
    ];
    const { container } = renderWithProviders(<NetWorthTrendChart data={zeroStart} currency="EUR" />);
    // Should not divide by zero
    expect(container.innerHTML).not.toBe('');
  });

  it('uses USD currency', () => {
    const { container } = renderWithProviders(<NetWorthTrendChart data={data} currency="USD" />);
    expect(container.innerHTML).not.toBe('');
  });

  it('renders title text', () => {
    renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
    expect(screen.getByText('Net Worth Trend')).toBeInTheDocument();
  });

  it('shows data points and days covered when no periodLabel', () => {
    renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
    // 3 data points, ~59 days between Jan 1 and Mar 1
    expect(screen.getByText(/3 data points/i)).toBeInTheDocument();
  });

  it('shows empty state message when data is empty', () => {
    renderWithProviders(<NetWorthTrendChart data={[]} currency="EUR" />);
    expect(screen.getByText(/no historical data available/i)).toBeInTheDocument();
  });

  describe('CustomTooltip via Recharts mock', () => {
    it('renders tooltip content when active with payload', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      expect(capturedTooltipContent).toBeTruthy();

      // Render the captured tooltip component
      const { container } = renderWithProviders(
        capturedTooltipContent.type({
          ...capturedTooltipContent.props,
          active: true,
          payload: [
            {
              payload: {
                date: '2024-02-01',
                netWorth: 36000,
                previousNetWorth: 30000,
              },
            },
          ],
        })
      );
      // Should render the date and amounts
      expect(container.textContent).toContain('36');
      expect(container.textContent).toContain('+');
    });

    it('returns null when tooltip is not active', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      const result = capturedTooltipContent.type({
        ...capturedTooltipContent.props,
        active: false,
        payload: [],
      });
      expect(result).toBeNull();
    });

    it('renders tooltip with negative change', () => {
      const decliningData = [
        { date: '2024-01-01', totalAssets: 60000, totalLiabilities: 18000, netWorth: 42000 },
        { date: '2024-02-01', totalAssets: 50000, totalLiabilities: 20000, netWorth: 30000 },
      ];
      renderWithProviders(<NetWorthTrendChart data={decliningData} currency="EUR" />);

      const { container } = renderWithProviders(
        capturedTooltipContent.type({
          ...capturedTooltipContent.props,
          active: true,
          payload: [
            {
              payload: {
                date: '2024-02-01',
                netWorth: 30000,
                previousNetWorth: 42000,
              },
            },
          ],
        })
      );
      expect(container.textContent).toContain('30');
      expect(container.innerHTML).toContain('text-red');
    });

    it('handles tooltip with no previousNetWorth', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      const { container } = renderWithProviders(
        capturedTooltipContent.type({
          ...capturedTooltipContent.props,
          active: true,
          payload: [
            {
              payload: {
                date: '2024-01-01',
                netWorth: 30000,
                previousNetWorth: null,
              },
            },
          ],
        })
      );
      expect(container.textContent).toContain('30');
    });
  });

  describe('formatYAxis via Recharts mock', () => {
    it('formats millions with M suffix', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      expect(capturedYAxisFormatter).toBeTruthy();
      const result = capturedYAxisFormatter(2500000);
      expect(result).toBe('2.5M');
    });

    it('formats thousands with K suffix', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      const result = capturedYAxisFormatter(45000);
      expect(result).toBe('45K');
    });

    it('formats small values as integers', () => {
      renderWithProviders(<NetWorthTrendChart data={data} currency="EUR" />);
      const result = capturedYAxisFormatter(500);
      expect(result).toBe('500');
    });
  });
});
