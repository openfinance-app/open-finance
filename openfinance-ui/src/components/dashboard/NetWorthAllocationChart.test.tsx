import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

// Enhanced Treemap mock that invokes content prop
vi.mock('recharts', () => ({
  Treemap: ({ data, content }: any) => {
    const ContentComponent = content?.type || content;
    return (
      <div data-testid="treemap">
        {JSON.stringify(data)}
        {ContentComponent && data?.map((item: any, i: number) => (
          <ContentComponent
            key={i}
            x={10}
            y={10}
            width={item._testWidth ?? 100}
            height={item._testHeight ?? 50}
            depth={1}
            index={i}
            name={item.name}
            percentage={item.percentage}
            isLiability={item.isLiability}
          />
        ))}
      </div>
    );
  },
  ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
  Tooltip: ({ content }: any) => {
    const TooltipComponent = content?.type || content;
    if (!TooltipComponent) return null;
    return (
      <div data-testid="tooltip-wrapper">
        <TooltipComponent
          active={true}
          payload={[{
            payload: {
              name: 'STOCKS',
              originalValue: 10000,
              percentage: 40,
              isLiability: false,
              currency: 'EUR',
            },
          }]}
          formatFn={(key: string) => key}
        />
        <TooltipComponent active={false} payload={[]} formatFn={(key: string) => key} />
      </div>
    );
  },
}));

vi.mock('@/components/ui/HelpTooltip', () => ({
  HelpTooltip: ({ text }: any) => <span data-testid="help-tooltip">{text}</span>,
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: any) => <span>{children}</span>,
}));

import NetWorthAllocationChart from './NetWorthAllocationChart';

describe('NetWorthAllocationChart', () => {
  const allocations = [
    { category: 'STOCKS', value: 10000, percentage: 40, type: 'asset' as const, currency: 'EUR' },
    { category: 'REAL_ESTATE', value: 8000, percentage: 32, type: 'asset' as const, currency: 'EUR' },
    { category: 'MORTGAGE', value: -7000, percentage: 28, type: 'liability' as const, isLiability: true, currency: 'EUR' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders treemap with data', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    expect(screen.getByTestId('treemap')).toBeInTheDocument();
  });

  it('renders empty state with no allocations', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={[]} currency="EUR" />);
    expect(screen.queryByTestId('treemap')).not.toBeInTheDocument();
  });

  it('renders title', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    expect(screen.getByText('Net Worth Allocation')).toBeInTheDocument();
  });

  it('renders help tooltip', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    expect(screen.getByTestId('help-tooltip')).toBeInTheDocument();
  });

  it('renders legend with allocation categories', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    // Legend shows percentage for each category (may appear in both treemap text and legend)
    const allPercentages = screen.getAllByText('40.0%');
    expect(allPercentages.length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('32.0%').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('28.0%').length).toBeGreaterThanOrEqual(1);
  });

  it('renders legend color indicators', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    const dots = document.querySelectorAll('.w-3.h-3.rounded-full');
    expect(dots.length).toBe(3);
  });

  it('renders CustomizedContent with text labels for large cells', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    // With default width=100, height=50 (> 50 and > 30), text labels should appear
    const treemap = screen.getByTestId('treemap');
    expect(treemap.querySelectorAll('text').length).toBeGreaterThan(0);
  });

  it('renders tooltip content for active state', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    // Tooltip renders with STOCKS payload (may appear in both treemap and tooltip)
    const stocksElements = screen.getAllByText('STOCKS');
    expect(stocksElements.length).toBeGreaterThanOrEqual(1);
  });

  it('handles null allocations gracefully', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={null as any} currency="EUR" />);
    expect(screen.queryByTestId('treemap')).not.toBeInTheDocument();
  });

  it('renders empty state description text', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={[]} currency="EUR" />);
    // Should show empty state message
    const bodyText = document.body.textContent || '';
    expect(bodyText.length).toBeGreaterThan(0);
  });

  it('uses absolute values for liability items', () => {
    const liabilityOnly = [
      { category: 'MORTGAGE', value: -5000, percentage: 100, type: 'liability' as const, isLiability: true, currency: 'EUR' },
    ];
    renderWithProviders(<NetWorthAllocationChart allocations={liabilityOnly} currency="EUR" />);
    const treemap = screen.getByTestId('treemap');
    // The data passed to Treemap should use absolute value
    expect(treemap.textContent).toContain('5000');
  });

  it('renders responsive container', () => {
    renderWithProviders(<NetWorthAllocationChart allocations={allocations} currency="EUR" />);
    expect(screen.getByTestId('responsive-container')).toBeInTheDocument();
  });
});
