import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import BudgetProgressCard from './BudgetProgressCard';
import { MemoryRouter } from 'react-router';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import * as AuthContextModule from '@/context/AuthContext';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const mockNavigate = vi.hoisted(() => vi.fn());

// Mock the hooks
vi.mock('@/hooks/useBudgets', () => ({
  useBudgetSummary: vi.fn(),
}));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number) => `$${amount}`,
  }),
}));

// Mock useAuthContext directly instead of the Provider
vi.mock('@/context/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<any>();
  return {
    ...actual,
    useAuthContext: vi.fn(),
  };
});

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

const mockAuthValue = {
  user: { id: 1, email: 'test@example.com' },
  baseCurrency: 'USD',
  isAuthenticated: true,
  isLoading: false,
  setAuth: vi.fn(),
  updateUser: vi.fn(),
  clearAuth: vi.fn(),
};

import { VisibilityProvider } from '@/context/VisibilityContext';
import { CurrencyDisplayProvider } from '@/context/CurrencyDisplayContext';
import { NumberFormatProvider } from '@/context/NumberFormatContext';

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <VisibilityProvider>
        <NumberFormatProvider>
          <CurrencyDisplayProvider>
            <I18nextProvider i18n={i18n}>
              <MemoryRouter>
                {ui}
              </MemoryRouter>
            </I18nextProvider>
          </CurrencyDisplayProvider>
        </NumberFormatProvider>
      </VisibilityProvider>
    </QueryClientProvider>
  );
};

describe('BudgetProgressCard Smoke Test', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    i18n.changeLanguage('en');
    
    // Default auth mock
    (AuthContextModule.useAuthContext as any).mockReturnValue(mockAuthValue);
  });

  it('renders correctly with data and shows HelpTooltip', async () => {
    const { useBudgetSummary } = await import('@/hooks/useBudgets');
    (useBudgetSummary as any).mockReturnValue({
      data: {
        totalSpent: 500,
        totalBudgeted: 1000,
        averageSpentPercentage: 50,
        totalRemaining: 500,
        activeBudgets: 2,
        totalBudgets: 2,
        budgets: [
          {
            budgetId: 1,
            categoryName: 'Food',
            spent: 200,
            budgeted: 400,
            percentageSpent: 50,
            status: 'ON_TRACK',
          },
        ],
      },
      isLoading: false,
      isError: false,
    });

    renderWithProviders(<BudgetProgressCard />);

    // Check if title is present (using translation key if it's not loading)
    expect(screen.getByRole('heading', { name: /Budget Progress/i })).toBeInTheDocument();

    // Check if HelpTooltip icon is present
    const helpButton = screen.getByRole('button', { name: /track your spending/i });
    expect(helpButton).toBeInTheDocument();
  });

  it('renders loading state without crashing', async () => {
    const { useBudgetSummary } = await import('@/hooks/useBudgets');
    (useBudgetSummary as any).mockReturnValue({
      isLoading: true,
    });

    renderWithProviders(<BudgetProgressCard />);
    expect(screen.getByRole('heading', { name: /Budget Progress/i })).toBeInTheDocument();
  });
});

describe('BudgetProgressCard row navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    i18n.changeLanguage('en');

    // Default auth mock
    (AuthContextModule.useAuthContext as any).mockReturnValue(mockAuthValue);
  });

  it('navigates to the budget detail when a row is clicked', async () => {
    const { useBudgetSummary } = await import('@/hooks/useBudgets');
    (useBudgetSummary as any).mockReturnValue({
      data: {
        totalSpent: 500,
        totalBudgeted: 1000,
        averageSpentPercentage: 50,
        totalRemaining: 500,
        activeBudgets: 1,
        totalBudgets: 1,
        budgets: [
          {
            budgetId: 1,
            categoryName: 'Food',
            spent: 200,
            budgeted: 400,
            percentageSpent: 50,
            status: 'ON_TRACK',
          },
        ],
      },
      isLoading: false,
      isError: false,
    });

    renderWithProviders(<BudgetProgressCard />);

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'View budget details' }));

    expect(mockNavigate).toHaveBeenCalledWith('/budget?open=1');
  });
});
