import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: vi.fn(() => ({
    data: [
      { id: 1, name: 'Checking', institution: { id: 1, name: 'BNP' }, balance: 5000, balanceInBaseCurrency: 5000, currency: 'EUR' },
      { id: 2, name: 'Savings', institution: { id: 1, name: 'BNP' }, balance: 10000, balanceInBaseCurrency: 10000, currency: 'EUR' },
      { id: 3, name: 'Trade', institution: null, balance: 3000, balanceInBaseCurrency: 3000, currency: 'EUR' },
    ],
    isLoading: false,
    error: null,
  })),
}));

vi.mock('@/hooks/useAssets', () => ({
  useAssets: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: any) => <span>{amount}</span>,
}));

import InstitutionBreakdown from './InstitutionBreakdown';

describe('InstitutionBreakdown', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
  });

  it('renders institution names', () => {
    renderWithProviders(<InstitutionBreakdown baseCurrency="EUR" />);
    expect(screen.getByText('BNP')).toBeInTheDocument();
  });

  it('renders total balance', () => {
    renderWithProviders(<InstitutionBreakdown baseCurrency="EUR" />);
    // Grand total: 5000 + 10000 + 3000 = 18000
    expect(screen.getByText('18000')).toBeInTheDocument();
  });

  it('renders the component without crashing', () => {
    const { container } = renderWithProviders(<InstitutionBreakdown baseCurrency="EUR" />);
    expect(container.innerHTML).not.toBe('');
  });
});
