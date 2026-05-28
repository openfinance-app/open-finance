import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('../../hooks/useDashboard', () => ({
  useDailyCashFlow: vi.fn(() => ({
    data: [
      { date: '2024-01-15', income: 3000, expense: 1500, net: 1500 },
      { date: '2024-01-16', income: 0, expense: 200, net: -200 },
    ],
    isLoading: false,
    isError: false,
  })),
}));

vi.mock('@/components/ui/PrivateAmount', () => ({
  PrivateAmount: ({ children }: any) => <span>{children}</span>,
}));

vi.mock('../ui/Skeleton', () => ({
  Skeleton: () => <div data-testid="skeleton" />,
}));

import DailyCashFlowCalendar from './DailyCashFlowCalendar';
import { useDailyCashFlow } from '../../hooks/useDashboard';

describe('DailyCashFlowCalendar', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
  });

  it('renders calendar', () => {
    renderWithProviders(<DailyCashFlowCalendar baseCurrency="USD" />);
    // Should show day headers (Mon, Tue, etc.)
    expect(screen.getAllByText(/Mon|Tue|Wed|Thu|Fri|Sat|Sun/i).length).toBeGreaterThan(0);
  });

  it('renders navigation buttons', () => {
    renderWithProviders(<DailyCashFlowCalendar baseCurrency="USD" />);
    const buttons = screen.getAllByRole('button');
    expect(buttons.length).toBeGreaterThanOrEqual(2); // prev/next month buttons
  });

  it('shows loading state', () => {
    vi.mocked(useDailyCashFlow).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as any);
    renderWithProviders(<DailyCashFlowCalendar baseCurrency="USD" />);
    expect(screen.getAllByTestId('skeleton').length).toBeGreaterThan(0);
  });

  it('navigates months', async () => {
    const user = userEvent.setup();
    renderWithProviders(<DailyCashFlowCalendar baseCurrency="USD" />);
    const buttons = screen.getAllByRole('button');
    // Click prev month
    await user.click(buttons[0]);
    expect(useDailyCashFlow).toHaveBeenCalled();
  });
});
