import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import InsightsCard from '@/components/dashboard/InsightsCard';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import { InsightType, InsightPriority, type Insight } from '@/types/insight';

// Mock hooks
const mockGenerateMutateAsync = vi.fn();
const mockDismissMutateAsync = vi.fn();
const mockDismissAllMutateAsync = vi.fn();
const mockRefetch = vi.fn();
let mockDismissAllPending = false;
let mockUseTopInsightsReturn: {
  data: Insight[] | undefined;
  isLoading: boolean;
  error: Error | null;
  refetch: typeof mockRefetch;
};

vi.mock('@/hooks/useInsights', () => ({
  useTopInsights: () => mockUseTopInsightsReturn,
  useGenerateInsights: () => ({ mutateAsync: mockGenerateMutateAsync, isPending: false }),
  useDismissInsight: () => ({ mutateAsync: mockDismissMutateAsync, isPending: false }),
  useDismissAllInsights: () => ({
    mutateAsync: mockDismissAllMutateAsync,
    isPending: mockDismissAllPending,
  }),
}));

const makeInsight = (overrides: Partial<Insight> = {}): Insight => ({
  id: 1,
  title: 'High spending in Restaurants',
  description: 'You spent 40% more than usual this week.',
  priority: InsightPriority.HIGH,
  type: InsightType.SPENDING_ANOMALY,
  dismissed: false,
  createdAt: new Date().toISOString(),
  ...overrides,
});

describe('InsightsCard', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    mockAuthentication();
    mockDismissAllPending = false;
    mockDismissAllMutateAsync.mockReset().mockResolvedValue(undefined);
    Element.prototype.scrollIntoView = vi.fn();
    await i18n.changeLanguage('en');
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
    mockUseTopInsightsReturn = {
      data: undefined,
      isLoading: true,
      error: null,
      refetch: mockRefetch,
    };
    renderWithProviders(<InsightsCard />);
    // Should show skeleton/loading indicators, not insight content
    expect(screen.queryByText('High spending in Restaurants')).not.toBeInTheDocument();
  });

  it('renders error state with retry button', () => {
    mockUseTopInsightsReturn = {
      data: undefined,
      isLoading: false,
      error: new Error('fail'),
      refetch: mockRefetch,
    };
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
    expect(screen.queryByRole('button', { name: 'Clear all insights' })).not.toBeInTheDocument();
  });

  it('explains the scope of clearing and allows cancelling without changing insights', () => {
    renderWithProviders(<InsightsCard />);
    fireEvent.click(screen.getByRole('button', { name: 'Clear all insights' }));
    const dialog = screen.getByRole('dialog', { name: 'Clear all insights?' });
    expect(within(dialog).getByText(/including insights not shown on this card/)).toBeVisible();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(mockDismissAllMutateAsync).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByText('High spending in Restaurants')).toBeVisible();
  });

  it('clears all insights with one bulk request after confirmation', async () => {
    renderWithProviders(<InsightsCard />);
    fireEvent.click(screen.getByRole('button', { name: 'Clear all insights' }));
    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockDismissAllMutateAsync).toHaveBeenCalledExactlyOnceWith();
    expect(mockDismissMutateAsync).not.toHaveBeenCalled();
  });

  it('keeps insights on failure and lets the user retry clearing', async () => {
    mockDismissAllMutateAsync.mockRejectedValueOnce(new Error('Network unavailable'));
    renderWithProviders(<InsightsCard />);
    fireEvent.click(screen.getByRole('button', { name: 'Clear all insights' }));
    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not clear insights. Please try again.'
    );
    expect(screen.getByText('High spending in Restaurants')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Clear all' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mockDismissAllMutateAsync).toHaveBeenCalledTimes(2);
  });

  it('prevents repeated requests or closing while clearing is pending', () => {
    const { rerender } = renderWithProviders(<InsightsCard />);
    fireEvent.click(screen.getByRole('button', { name: 'Clear all insights' }));
    mockDismissAllPending = true;
    rerender(<InsightsCard />);
    const dialog = screen.getByRole('dialog');
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: /loading/i })).toBeDisabled();
    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(mockDismissAllMutateAsync).not.toHaveBeenCalled();
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
    mockUseTopInsightsReturn = {
      data: undefined,
      isLoading: false,
      error: new Error('Custom error message'),
      refetch: mockRefetch,
    };
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
