import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import BalanceVariationCard from '@/components/dashboard/BalanceVariationCard';
import i18n from '@/test/i18n-test';

const query = vi.hoisted(() => ({
  isLoading: false,
  error: null as Error | null,
  data: {
    years: [2024, 2025],
    currency: 'EUR',
    netWorth: [
      { year: 2024, amount: 1000, variationPercentage: null },
      { year: 2025, amount: 1500, variationPercentage: 50 },
    ],
    accounts: [
      { id: 7, name: 'Checking', data: [{ year: 2025, amount: 400, variationPercentage: -20 }] },
    ],
    institutions: [
      { id: 9, name: 'Bank', data: [{ year: 2025, amount: 600, variationPercentage: 10 }] },
    ],
  },
}));
vi.mock('@/hooks/useDashboard', () => ({ useYearlyBalance: () => query }));
beforeEach(async () => {
  mockAuthentication();
  query.isLoading = false;
  query.error = null;
  await i18n.changeLanguage('en');
});

describe('yearly balance variations', () => {
  it('changes scope without mixing account, institution, and total balances', () => {
    renderWithProviders(<BalanceVariationCard currency="USD" />);
    expect(screen.getByText(/1,500/)).toBeInTheDocument();
    const [scope, chart] = screen.getAllByRole('combobox');
    fireEvent.change(scope, { target: { value: 'account:7' } });
    expect(screen.getByText(/400\.00/)).toBeInTheDocument();
    expect(screen.queryByText(/1,500/)).not.toBeInTheDocument();
    expect(screen.getByText('-20.0%')).toBeInTheDocument();
    fireEvent.change(chart, { target: { value: 'line' } });
    expect(screen.getByText(/400\.00/)).toBeInTheDocument();
    fireEvent.change(scope, { target: { value: 'institution:9' } });
    expect(screen.getByText(/600\.00/)).toBeInTheDocument();
    expect(screen.queryByText(/400\.00/)).not.toBeInTheDocument();
  });
  it('shows a data error instead of misleading balance values', () => {
    query.error = new Error('Unavailable');
    renderWithProviders(<BalanceVariationCard />);
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByText(/1,500/)).not.toBeInTheDocument();
  });
});
