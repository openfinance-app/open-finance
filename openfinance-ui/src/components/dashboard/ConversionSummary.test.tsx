import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

const mockMutate = vi.fn();

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: vi.fn(() => ({
    data: [
      { id: 1, name: 'EUR Account', currency: 'EUR' },
      { id: 2, name: 'USD Account', currency: 'USD' },
    ],
    isLoading: false,
  })),
}));

vi.mock('@/hooks/useCurrency', () => ({
  useLatestExchangeRate: vi.fn(() => ({
    data: { rate: 1.1, rateDate: new Date().toISOString() },
    isLoading: false,
  })),
  useUpdateExchangeRates: () => ({ mutate: mockMutate, isPending: false }),
}));

import { ConversionSummary } from './ConversionSummary';
import { useAccounts } from '@/hooks/useAccounts';
import { useLatestExchangeRate } from '@/hooks/useCurrency';

describe('ConversionSummary', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
  });

  it('renders converted accounts count', () => {
    renderWithProviders(<ConversionSummary />);
    expect(screen.getByText(/1 account converted/i)).toBeInTheDocument();
  });

  it('shows refresh button', () => {
    renderWithProviders(<ConversionSummary />);
    expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument();
  });

  it('calls mutate on refresh click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<ConversionSummary />);
    await user.click(screen.getByRole('button', { name: /refresh/i }));
    expect(mockMutate).toHaveBeenCalled();
  });

  it('returns null when no foreign accounts', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [{ id: 1, name: 'EUR Account', currency: 'USD' }],
      isLoading: false,
    } as any);
    // baseCurrency from auth is USD, so USD account is not foreign
    const { container } = renderWithProviders(<ConversionSummary />);
    expect(container.innerHTML).toBe('');
  });

  it('returns null when loading', () => {
    vi.mocked(useAccounts).mockReturnValue({ data: undefined, isLoading: true } as any);
    const { container } = renderWithProviders(<ConversionSummary />);
    expect(container.innerHTML).toBe('');
  });

  it('shows stale warning when rate is old', () => {
    vi.mocked(useAccounts).mockReturnValue({
      data: [
        { id: 1, name: 'EUR Account', currency: 'EUR' },
        { id: 2, name: 'USD Account', currency: 'USD' },
      ],
      isLoading: false,
    } as any);
    vi.mocked(useLatestExchangeRate).mockReturnValue({
      data: { rate: 1.1, rateDate: '2020-01-01T00:00:00Z' },
      isLoading: false,
    } as any);
    renderWithProviders(<ConversionSummary />);
    expect(screen.getByText(/outdated/i)).toBeInTheDocument();
  });
});
