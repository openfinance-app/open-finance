import { beforeEach, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { TimelineProjection } from '@/components/financial-freedom/TimelineProjection';
import type { YearlyProjection } from '@/types/calculator';
import i18n from '@/test/i18n-test';

const projection = (year: number, endingBalance: number): YearlyProjection => ({
  year,
  startingBalance: 0,
  endingBalance,
  contributions: 10,
  investmentReturns: 5,
  withdrawals: 0,
  progressTowardTarget: endingBalance,
  targetReached: endingBalance >= 100,
});
beforeEach(async () => {
  mockAuthentication();
  await i18n.changeLanguage('en');
});

it('marks only the freedom year as the target, with earlier years still building and later years achieved', () => {
  renderWithProviders(
    <TimelineProjection
      projections={[projection(0, 25), projection(1, 50), projection(2, 100), projection(3, 120)]}
      targetAmount={100}
      currency="USD"
      yearsToFreedom={2}
    />
  );
  const rows = screen.getAllByRole('row').slice(1);
  expect(within(rows[0]).getByText(i18n.t('timelineProjection.building'))).toBeInTheDocument();
  expect(within(rows[1]).getByText(i18n.t('timelineProjection.building'))).toBeInTheDocument();
  expect(within(rows[2]).getByText(i18n.t('timelineProjection.targetYear'))).toBeInTheDocument();
  expect(rows[3]).toHaveTextContent(i18n.t('timelineProjection.achieved'));
  expect(rows[0]).toHaveTextContent('25%');
  expect(rows[3]).toHaveTextContent('100%');
});

it('shows immediate freedom for a zero target without an invalid progress percentage', () => {
  const { container } = renderWithProviders(
    <TimelineProjection
      projections={[projection(0, 0)]}
      targetAmount={0}
      currency="USD"
      yearsToFreedom={0}
    />
  );
  expect(screen.getByText(i18n.t('timelineProjection.targetYear'))).toBeInTheDocument();
  expect(container).toHaveTextContent('100%');
  expect(container).not.toHaveTextContent(/NaN|Infinity/);
});
