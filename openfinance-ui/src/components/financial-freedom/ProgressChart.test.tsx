import { cloneElement, type ReactElement, type ReactNode } from 'react';
import { beforeEach, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ProgressChart } from '@/components/financial-freedom/ProgressChart';

const chart = vi.hoisted(() => ({ active: true }));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  ComposedChart: ({ data, children }: { data: unknown; children: ReactNode }) => (
    <div>
      <output aria-label="Projected balances">{JSON.stringify(data)}</output>
      {children}
    </div>
  ),
  Line: () => null,
  XAxis: () => null,
  CartesianGrid: () => null,
  Area: () => null,
  YAxis: ({ tickFormatter }: { tickFormatter: (value: number) => string }) => (
    <p>{[500, 2000, 1200000].map(tickFormatter).join(' ')}</p>
  ),
  Legend: ({ formatter }: { formatter: (value: string) => ReactNode }) => (
    <div>{formatter('Projected Savings')}</div>
  ),
  Tooltip: ({ content }: { content: ReactElement<Record<string, unknown>> }) =>
    cloneElement(content, {
      active: chart.active,
      label: '2',
      payload: [
        { value: 12345, dataKey: 'savings', color: 'blue' },
        { value: 20000, dataKey: 'target', color: 'green' },
      ],
    }),
  ReferenceLine: ({ x, y, label }: { x?: number; y?: number; label: { value: string } }) => (
    <output aria-label={label.value}>{x ?? y}</output>
  ),
}));
beforeEach(() => {
  mockAuthentication();
  chart.active = true;
});
it('plots ending balances, keeps contributions separate, and labels target and freedom references in the requested currency', () => {
  renderWithProviders(
    <ProgressChart
      data={[
        {
          year: 2,
          startingBalance: 1000,
          endingBalance: 12345,
          contributions: 10000,
          investmentReturns: 1345,
          withdrawals: 0,
          progressTowardTarget: 61.725,
          targetReached: false,
        },
      ]}
      currentSavings={1000}
      targetAmount={20000}
      currency="GBP"
      yearsToFreedom={3}
    />
  );
  expect(JSON.parse(screen.getByLabelText('Projected balances').textContent!)[0]).toMatchObject({
    year: 2,
    savings: 12345,
    contributions: 10000,
    target: 20000,
  });
  expect(screen.getByLabelText('Target')).toHaveTextContent('20000');
  expect(screen.getByLabelText('Current')).toHaveTextContent('1000');
  expect(screen.getByLabelText('Freedom!')).toHaveTextContent('3');
  expect(screen.getByText('£500 £2K £1.2M')).toBeInTheDocument();
  expect(screen.getByText('£12,345.00')).toBeInTheDocument();
  expect(screen.getByText('£20,000.00')).toBeInTheDocument();
});
it('does not display hover details or a freedom marker when neither is available', () => {
  chart.active = false;
  renderWithProviders(
    <ProgressChart data={[]} currentSavings={0} targetAmount={100} currency="USD" />
  );
  expect(screen.queryByText('Year 2')).not.toBeInTheDocument();
  expect(screen.queryByLabelText('Freedom!')).not.toBeInTheDocument();
});
