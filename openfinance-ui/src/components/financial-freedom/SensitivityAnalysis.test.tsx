import { beforeEach, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { SensitivityAnalysis } from '@/components/financial-freedom/SensitivityAnalysis';

beforeEach(() => mockAuthentication());
it('keeps each return assumption paired with its own timeline, including a zero-month remainder', () => {
  renderWithProviders(
    <SensitivityAnalysis
      scenarios={[
        {
          label: 'Strong growth',
          returnRate: 8,
          yearsToFreedom: 10,
          monthsToFreedom: 3,
          scenarioType: 'optimistic',
        },
        {
          label: 'Expected growth',
          returnRate: 5,
          yearsToFreedom: 15,
          monthsToFreedom: 0,
          scenarioType: 'baseline',
        },
        {
          label: 'Slow growth',
          returnRate: 2,
          yearsToFreedom: 20,
          monthsToFreedom: 6,
          scenarioType: 'pessimistic',
        },
      ]}
    />
  );
  const optimistic = screen.getByText('Strong growth').parentElement!.parentElement!;
  expect(optimistic).toHaveTextContent('8.0%');
  expect(optimistic).toHaveTextContent('10 years, 3 months');
  const baseline = screen.getByText('Expected growth').parentElement!.parentElement!;
  expect(baseline).toHaveTextContent('5.0%');
  expect(baseline).toHaveTextContent('15 years');
  expect(baseline).not.toHaveTextContent('Months:');
  expect(screen.getByText('Slow growth').parentElement!.parentElement!).toHaveTextContent(
    '20 years, 6 months'
  );
});
it('omits analysis when there are no scenarios', () => {
  renderWithProviders(<SensitivityAnalysis scenarios={[]} />);
  expect(screen.queryByText('Sensitivity Analysis')).not.toBeInTheDocument();
});
