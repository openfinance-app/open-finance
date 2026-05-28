import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import InsightsCard from './InsightsCard';
import { MemoryRouter } from 'react-router';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { InsightType, InsightPriority } from '@/types/insight';

// Mock hooks
const mockGenerateMutateAsync = vi.fn();
const mockDismissMutateAsync = vi.fn();
const mockRefetch = vi.fn();
let mockUseTopInsightsReturn: any;

vi.mock('@/hooks/useInsights', () => ({
  useTopInsights: () => mockUseTopInsightsReturn,
  useGenerateInsights: () => ({ mutateAsync: mockGenerateMutateAsync, isPending: false }),
  useDismissInsight: () => ({ mutateAsync: mockDismissMutateAsync, isPending: false }),
}));

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderWithProviders = (ui: React.ReactElement) => {
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          {ui}
        </MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );
};

const makeInsight = (overrides: any = {}) => ({
  id: 1,
  title: 'High spending in Restaurants',
  description: 'You spent 40% more than usual this week.',
  priority: InsightPriority.HIGH,
  type: InsightType.SPENDING_ANOMALY,
  createdAt: new Date().toISOString(),
  ...overrides,
});

describe('InsightsCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    i18n.changeLanguage('en');
    mockUseTopInsightsReturn = {
      data: [makeInsight()],
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
  });

  it('renders with data', () => {
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText('High spending in Restaurants')).toBeInTheDocument();
  });

  it('renders loading state', () => {
    mockUseTopInsightsReturn = { data: undefined, isLoading: true, error: null, refetch: mockRefetch };
    renderWithProviders(<InsightsCard />);
    // Should show skeleton/loading indicators, not insight content
    expect(screen.queryByText('High spending in Restaurants')).not.toBeInTheDocument();
  });

  it('renders error state with retry button', () => {
    mockUseTopInsightsReturn = { data: undefined, isLoading: false, error: new Error('fail'), refetch: mockRefetch };
    renderWithProviders(<InsightsCard />);
    const retryBtn = screen.getByRole('button', { name: /retry/i });
    expect(retryBtn).toBeInTheDocument();
    fireEvent.click(retryBtn);
    expect(mockRefetch).toHaveBeenCalled();
  });

  it('renders empty state with generate button', () => {
    mockUseTopInsightsReturn = { data: [], isLoading: false, error: null, refetch: mockRefetch };
    renderWithProviders(<InsightsCard />);
    // "Generate Insights" is the translated text
    const generateBtn = screen.getByRole('button', { name: /generate insights/i });
    expect(generateBtn).toBeInTheDocument();
  });

  it('clicking generate calls mutateAsync', async () => {
    mockUseTopInsightsReturn = { data: [], isLoading: false, error: null, refetch: mockRefetch };
    mockGenerateMutateAsync.mockResolvedValue({});
    renderWithProviders(<InsightsCard />);
    const generateBtn = screen.getByRole('button', { name: /generate insights/i });
    fireEvent.click(generateBtn);
    await waitFor(() => expect(mockGenerateMutateAsync).toHaveBeenCalled());
  });

  it('renders multiple priority variants', () => {
    mockUseTopInsightsReturn = {
      data: [
        makeInsight({ id: 1, priority: InsightPriority.HIGH }),
        makeInsight({ id: 2, priority: InsightPriority.MEDIUM, title: 'Medium insight' }),
        makeInsight({ id: 3, priority: InsightPriority.LOW, title: 'Low insight' }),
      ],
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText('High spending in Restaurants')).toBeInTheDocument();
    expect(screen.getByText('Medium insight')).toBeInTheDocument();
    expect(screen.getByText('Low insight')).toBeInTheDocument();
  });

  it('renders different insight type icons', () => {
    mockUseTopInsightsReturn = {
      data: [
        makeInsight({ id: 1, type: InsightType.BUDGET_WARNING }),
        makeInsight({ id: 2, type: InsightType.SAVINGS_OPPORTUNITY, title: 'Save more' }),
        makeInsight({ id: 3, type: InsightType.DEBT_ALERT, title: 'Debt alert' }),
      ],
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText('Save more')).toBeInTheDocument();
    expect(screen.getByText('Debt alert')).toBeInTheDocument();
  });

  it('dismiss button is present for insights', () => {
    renderWithProviders(<InsightsCard />);
    // The dismiss button has title="Dismiss insight"
    const dismissBtns = screen.getAllByTitle(/dismiss/i);
    expect(dismissBtns.length).toBeGreaterThan(0);
  });

  it('clicking insight opens detail modal', () => {
    renderWithProviders(<InsightsCard />);
    const insightTitle = screen.getByText('High spending in Restaurants');
    fireEvent.click(insightTitle);
    // Modal should show description
    expect(screen.getByText('You spent 40% more than usual this week.')).toBeInTheDocument();
  });

  it('renders all insight type icons', () => {
    const allTypes = [
      InsightType.CASH_FLOW_WARNING,
      InsightType.INVESTMENT_SUGGESTION,
      InsightType.TAX_OPTIMIZATION,
    ];
    mockUseTopInsightsReturn = {
      data: allTypes.map((type, i) => makeInsight({ id: i + 10, type, title: `Insight ${type}` })),
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    allTypes.forEach(type => {
      expect(screen.getByText(`Insight ${type}`)).toBeInTheDocument();
    });
  });

  it('renders remaining insight type icons', () => {
    const types = [
      InsightType.GOAL_PROGRESS,
      InsightType.GENERAL_TIP,
      InsightType.REGION_COMPARISON,
    ];
    mockUseTopInsightsReturn = {
      data: types.map((type, i) => makeInsight({ id: i + 20, type, title: `Type ${type}` })),
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    types.forEach(type => {
      expect(screen.getByText(`Type ${type}`)).toBeInTheDocument();
    });
  });

  it('renders tax and recurring billing insight types', () => {
    const types = [
      InsightType.TAX_OBLIGATION,
      InsightType.RECURRING_BILLING,
      InsightType.BUDGET_RECOMMENDATION,
    ];
    mockUseTopInsightsReturn = {
      data: types.map((type, i) => makeInsight({ id: i + 30, type, title: `Bill ${type}` })),
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    types.forEach(type => {
      expect(screen.getByText(`Bill ${type}`)).toBeInTheDocument();
    });
  });

  it('shows priority badge in detail dialog', () => {
    renderWithProviders(<InsightsCard />);
    fireEvent.click(screen.getByText('High spending in Restaurants'));
    // Dialog should show priority badge - multiple matches expected (title + badge)
    const highTexts = screen.getAllByText(/high/i);
    expect(highTexts.length).toBeGreaterThanOrEqual(2);
  });

  it('shows refresh button in success state', () => {
    renderWithProviders(<InsightsCard />);
    const refreshBtn = screen.getByRole('button', { name: /refresh/i });
    expect(refreshBtn).toBeInTheDocument();
  });

  it('calls generate on refresh button click', async () => {
    mockGenerateMutateAsync.mockResolvedValue({});
    renderWithProviders(<InsightsCard />);
    const refreshBtn = screen.getByRole('button', { name: /refresh/i });
    fireEvent.click(refreshBtn);
    await waitFor(() => expect(mockGenerateMutateAsync).toHaveBeenCalled());
  });

  it('shows active count badge', () => {
    mockUseTopInsightsReturn = {
      data: [makeInsight(), makeInsight({ id: 2, title: 'Second' })],
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText(/2/)).toBeInTheDocument();
  });

  it('dismiss calls mutateAsync', async () => {
    mockDismissMutateAsync.mockResolvedValue({});
    renderWithProviders(<InsightsCard />);
    const dismissBtn = screen.getAllByTitle(/dismiss/i)[0];
    fireEvent.click(dismissBtn);
    // Wait for the setTimeout(300ms) in handleDismiss
    await waitFor(() => expect(mockDismissMutateAsync).toHaveBeenCalledWith(1), { timeout: 500 });
  });

  it('renders error message from Error instance', () => {
    mockUseTopInsightsReturn = { data: undefined, isLoading: false, error: new Error('Custom error message'), refetch: mockRefetch };
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText('Custom error message')).toBeInTheDocument();
  });

  it('renders unusual transaction insight type', () => {
    mockUseTopInsightsReturn = {
      data: [makeInsight({ id: 50, type: InsightType.UNUSUAL_TRANSACTION, title: 'Unusual tx' })],
      isLoading: false,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    expect(screen.getByText('Unusual tx')).toBeInTheDocument();
  });
});
