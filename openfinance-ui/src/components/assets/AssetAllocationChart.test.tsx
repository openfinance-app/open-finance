import { cloneElement, type ReactElement, type ReactNode } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AssetAllocationChart } from '@/components/assets/AssetAllocationChart';
import type { AssetAllocation } from '@/utils/portfolio';

// Exercise the app's labels, legend and tooltip with the payload contract supplied by Recharts.
const chart = vi.hoisted(() => ({ data: [] as AssetAllocation[], active: true }));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  PieChart: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  Pie: ({
    data,
    label,
    children,
  }: {
    data: AssetAllocation[];
    label: (props: Record<string, number>) => ReactNode;
    children: ReactNode;
  }) => {
    chart.data = data;
    return (
      <svg>
        {children}
        {data.map((item, index) => (
          <g key={item.type}>
            {label({
              cx: 50,
              cy: 50,
              midAngle: index * 180,
              innerRadius: 10,
              outerRadius: 40,
              percent: item.percentage / 100,
            })}
          </g>
        ))}
      </svg>
    );
  },
  Cell: () => null,
  Tooltip: ({ content }: { content: ReactElement<Record<string, unknown>> }) =>
    cloneElement(content, {
      active: chart.active,
      payload: chart.data.map(item => ({ name: item.type, value: item.value, payload: item })),
    }),
  Legend: ({ content }: { content: ReactElement<Record<string, unknown>> }) =>
    cloneElement(content, {
      payload: chart.data.map(item => ({ value: item.type, color: 'blue', payload: item })),
    }),
}));
beforeEach(() => {
  mockAuthentication();
  chart.data = [];
  chart.active = true;
});

it('pairs converted values and asset counts with the correct type and suppresses tiny slice labels', () => {
  renderWithProviders(
    <AssetAllocationChart
      data={[
        { type: 'CRYPTO', value: 960, percentage: 96, count: 3 },
        { type: 'COLLECTIBLE', value: 40, percentage: 4, count: 1 },
      ]}
    />
  );
  expect(screen.getAllByText('Cryptocurrency')).toHaveLength(2);
  expect(screen.getByText('COLLECTIBLE')).toBeInTheDocument();
  expect(screen.getByText('Assets: 3')).toBeInTheDocument();
  expect(screen.getByText(/Value:.*960/)).toBeInTheDocument();
  expect(screen.getByText('96%')).toBeInTheDocument();
  expect(screen.queryByText('4%')).not.toBeInTheDocument();
});
it('hides inactive tooltips and explains when there is no allocation', () => {
  chart.active = false;
  const { rerender } = renderWithProviders(
    <AssetAllocationChart data={[{ type: 'STOCK', value: 100, percentage: 100, count: 1 }]} />
  );
  expect(screen.queryByText(/Assets:/)).not.toBeInTheDocument();
  rerender(<AssetAllocationChart data={[]} />);
  expect(screen.getByText('No allocation data available')).toBeInTheDocument();
});
