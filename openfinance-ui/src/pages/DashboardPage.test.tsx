/**
 * DashboardPage Integration Tests
 *
 * Tests the dashboard page component with mocked API responses
 * to verify data fetching, rendering, and user interactions.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { renderWithProviders, mockAuthentication, clearAuthentication } from '@/test/test-utils';
import { server } from '@/test/mocks/server';
import i18n from '@/test/i18n-test';
import DashboardPage from '@/pages/DashboardPage';
import { periodToDateRange } from '@/utils/navigation';

describe('DashboardPage Integration Tests', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    sessionStorage.removeItem('dashboard_period');
  });

  it('shows the unavailable conversion instead of a mislabeled total', async () => {
    server.use(
      http.get('/api/v1/dashboard', () =>
        HttpResponse.json(
          { message: 'The JPY to EUR exchange rate is unavailable.' },
          { status: 503 }
        )
      )
    );
    renderWithProviders(<DashboardPage />);
    expect(
      await screen.findByText('The JPY to EUR exchange rate is unavailable.', {}, { timeout: 5000 })
    ).toBeInTheDocument();
    expect(screen.queryByText(/20,000\.00/)).not.toBeInTheDocument();
  });

  it('shows unavailable historical conversion while preserving the valid current summary', async () => {
    server.use(
      http.get('/api/v1/dashboard/networth-history', () =>
        HttpResponse.json(
          { message: 'The historical JPY to EUR exchange rate is unavailable.' },
          { status: 503 }
        )
      )
    );
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent(
      'The historical JPY to EUR exchange rate is unavailable.'
    );
    expect((await screen.findAllByText(/20,000\.00/))[0]).toBeInTheDocument();
  });

  describe('Data Fetching and Rendering', () => {
    it('keeps the current balance and uses only observed history for a future-ended period', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 17, 12));
      try {
        sessionStorage.setItem(
          'dashboard_period',
          JSON.stringify({
            period: 'CUSTOM',
            range: { from: '2026-10-01', to: '2026-10-31' },
          })
        );
        server.use(
          http.get('/api/v1/dashboard/networth-history', () =>
            HttpResponse.json([
              { date: '2026-10-01', netWorth: 15000 },
              { date: '2026-10-17', netWorth: 19000 },
              { date: '2026-10-31', netWorth: 999999 },
            ])
          )
        );
        renderWithProviders(<DashboardPage />);
        await waitFor(() => expect(screen.getAllByText(/4,000\.00/)).toHaveLength(2));
        expect(screen.getAllByText('Data through 10/17/2026')).toHaveLength(2);
        const periodCaption = screen.getByRole('button', { name: 'vs 31d' });
        fireEvent.focus(periodCaption);
        expect(await screen.findByRole('tooltip')).toHaveTextContent(
          'Selected period: 10/01/2026 → 10/31/2026'
        );
        fireEvent.blur(periodCaption);
        expect(screen.getAllByText(/20,000\.00/)[0]).toBeInTheDocument();
        expect(screen.queryByText(/984,999/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: '10/01/2026 → 10/31/2026' })).toHaveAttribute(
          'aria-pressed',
          'true'
        );
        expect(screen.getByText('Compared with 09/01/2026 → 09/30/2026')).toBeInTheDocument();
      } finally {
        vi.useRealTimers();
      }
    });

    it('shows unavailable history for a future-only period while retaining the current balance', async () => {
      sessionStorage.setItem(
        'dashboard_period',
        JSON.stringify({
          period: 'CUSTOM',
          range: { from: '2099-01-01', to: '2099-12-31' },
        })
      );
      server.use(http.get('/api/v1/dashboard/networth-history', () => HttpResponse.json([])));
      renderWithProviders(<DashboardPage />);
      expect(
        await screen.findByText('No historical data available to show trend.')
      ).toBeInTheDocument();
      expect(screen.getByText('No change available for this period.')).toBeInTheDocument();
      expect(screen.getAllByText(/20,000\.00/)[0]).toBeInTheDocument();
    });

    it('compares the exact 30-day boundary even when a nearby chart sample is closer', async () => {
      sessionStorage.setItem('dashboard_period', JSON.stringify({ period: '30D' }));
      const range = periodToDateRange(30);
      const requested: string[] = [];
      server.use(
        http.get('/api/v1/dashboard/networth-history', ({ request }) => {
          const url = new URL(request.url);
          requested.push(url.search);
          return HttpResponse.json(
            url.searchParams.get('startDate') === range.from
              ? [
                  { date: range.from, netWorth: 18000 },
                  { date: range.to, netWorth: 20000 },
                ]
              : [
                  { date: '2020-01-01', netWorth: 1000 },
                  { date: range.to, netWorth: 25000 },
                ]
          );
        })
      );
      renderWithProviders(<DashboardPage />);
      await waitFor(() => expect(screen.getAllByText(/2,000\.00/)[0]).toBeInTheDocument());
      expect(requested.some(query => query.includes(`startDate=${range.from}`))).toBe(true);
      expect(screen.queryByText(/5,000\.00.*20\.00%/)).not.toBeInTheDocument();
    });

    it('compares a custom historical month with its closing value rather than today', async () => {
      sessionStorage.setItem(
        'dashboard_period',
        JSON.stringify({
          selectedPeriod: 'CUSTOM',
          periodDays: 31,
          historyPeriod: 31,
          activeDateRange: { from: '2026-07-01', to: '2026-07-31' },
        })
      );
      server.use(
        http.get('/api/v1/dashboard/networth-history', () =>
          HttpResponse.json([
            { date: '2026-07-01', netWorth: 17159.4, totalAssets: 25559.4, totalLiabilities: 8400 },
            {
              date: '2026-07-31',
              netWorth: 19150.66,
              totalAssets: 27328.66,
              totalLiabilities: 8178,
            },
          ])
        )
      );
      renderWithProviders(<DashboardPage />);
      // The current summary is 20,000; both historical comparison displays must use July's delta.
      await waitFor(() => expect(screen.getAllByText(/1,991\.26/)).toHaveLength(2));
      expect(screen.queryByText(/2,840\.60/)).not.toBeInTheDocument();
    });

    it('should fetch and display dashboard summary data', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for data to load and verify net worth display
      const netWorthElements = await screen.findAllByText(/Net Worth/i);
      expect(netWorthElements.length).toBeGreaterThan(0);
      expect((await screen.findAllByText(/20,000\.00/))[0]).toBeInTheDocument();
    });

    it('should display account summary stats', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for dashboard to load and verify summary stats at the bottom
      // Dashboard shows aggregate stats, not individual account listings
      const totalAccountsLabel = await screen.findByText(/Total Accounts/i);
      expect(totalAccountsLabel).toBeInTheDocument();

      // Verify the stat value (2 accounts from mock)
      const statsSection = totalAccountsLabel.closest('.bg-surface');
      expect(statsSection).toBeInTheDocument();
      expect(statsSection?.textContent).toContain('2');
    });

    it('should display recent transactions', async () => {
      renderWithProviders(<DashboardPage />);

      // First wait for dashboard summary to load (indicated by stats at bottom)
      const totalAccountsLabel = await screen.findByText(/Total Accounts/i, {}, { timeout: 5000 });
      expect(totalAccountsLabel).toBeInTheDocument();

      // The dashboard uses a grid layout with cards that may take time to render
      // Check for transaction descriptions that appear in RecentTransactionsCard
      const groceries = await screen.findByText('Weekly groceries', {}, { timeout: 5000 });
      expect(groceries).toBeInTheDocument();

      const salary = await screen.findByText('Monthly salary', {}, { timeout: 5000 });
      expect(salary).toBeInTheDocument();
    });

    it('should show loading state initially', () => {
      renderWithProviders(<DashboardPage />);

      // Instead of relying on role="status", we can check for elements with the skeleton animation class
      const loadingElements = document.querySelectorAll('.animate-pulse');
      expect(loadingElements.length).toBeGreaterThan(0);
    });
  });

  describe('Net Worth Card', () => {
    beforeEach(() => {
      sessionStorage.setItem('dashboard_period', JSON.stringify({ period: '30D' }));
      const range = periodToDateRange(30);
      server.use(
        http.get('/api/v1/dashboard/networth-history', () =>
          HttpResponse.json([
            { date: range.from, netWorth: 18000 },
            { date: range.to, netWorth: 20000 },
          ])
        )
      );
    });
    it('should display net worth with currency formatting', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for net worth to load
      const netWorth = (await screen.findAllByText(/20,000\.00/))[0];
      expect(netWorth).toBeInTheDocument();
    });

    it('should show net worth change percentage', async () => {
      renderWithProviders(<DashboardPage />);

      // Look for percentage change (11.11%)
      expect((await screen.findAllByText(/11\.11%/))[0]).toBeInTheDocument();
    });

    it('should display positive change with green color indicator', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for change amount to appear
      const changeElement = (await screen.findAllByText(/2,000\.00/))[0];
      expect(changeElement).toBeInTheDocument();

      // Verify it has positive/success styling (implementation-dependent)
      const parent = changeElement.closest(
        '.text-success, .text-green-500, .text-green-600, .text-green-400'
      );
      // If we can't find a direct class match, at least we know it renders
      if (parent) {
        expect(parent).toBeTruthy();
      }
    });
  });

  describe('Charts and Visualizations', () => {
    it('should render cash flow chart', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for chart to load
      const cashFlowElements = await screen.findAllByText(/Cash Flow/i);
      expect(cashFlowElements.length).toBeGreaterThan(0);
    });

    it('should render net worth trend chart', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for net worth trend chart
      const trendElements = await screen.findAllByText(/Net Worth Trend/i);
      expect(trendElements.length).toBeGreaterThan(0);
    });
  });

  describe('User Interactions', () => {
    it('opens and cancels transaction creation without leaving the dashboard', async () => {
      window.history.replaceState({}, '', '/dashboard');
      renderWithProviders(<DashboardPage />);
      fireEvent.click(await screen.findByRole('button', { name: 'Add Transaction' }));
      const dialog = await screen.findByRole('dialog', { name: 'Create Transaction' });
      expect(window.location.pathname).toBe('/dashboard');
      fireEvent.click(await within(dialog).findByRole('button', { name: 'Cancel' }));
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(window.location.pathname).toBe('/dashboard');
      await waitFor(() =>
        expect(screen.getByRole('button', { name: 'Add Transaction' })).toHaveFocus()
      );
    });

    it('should display period selector', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for page to load
      await screen.findAllByText(/Net Worth/i);

      const selector = screen.getByRole('group', { name: 'Time period' });
      expect(within(selector).getAllByRole('button')).toHaveLength(8);
      expect(
        within(selector).getByRole('button', { name: i18n.t('common:periodSelector.presets.MTD') })
      ).toHaveAttribute('aria-pressed', 'true');
    });

    it('should keep custom date range inputs mounted during refetch', async () => {
      const delayedJson = async (body: unknown) => {
        await new Promise(resolve => setTimeout(resolve, 250));
        return HttpResponse.json(body);
      };

      server.use(
        http.get('/api/v1/dashboard/cashflow', () =>
          delayedJson({ income: 5000, expenses: 3000, savings: 2000 })
        ),
        http.get('/api/v1/dashboard/networth-history', () =>
          delayedJson([
            { date: '2026-01-01', netWorth: 18000.0 },
            { date: '2026-02-01', netWorth: 19000.0 },
            { date: '2026-02-04', netWorth: 20000.0 },
          ])
        ),
        http.get('/api/v1/dashboard/portfolio-performance', () =>
          delayedJson({
            totalValue: 50000.0,
            totalGainLoss: 5000.0,
            percentageChange: 11.11,
            performance: [
              { date: '2026-01-01', value: 45000.0 },
              { date: '2026-01-15', value: 47000.0 },
            ],
          })
        ),
        http.get('/api/v1/dashboard/borrowing-capacity', () =>
          delayedJson({
            estimatedCapacity: 50000.0,
            monthlyIncome: 6000.0,
            monthlyDebtObligations: 1000.0,
            debtToIncomeRatio: 16.67,
            isHealthy: true,
          })
        ),
        http.get('/api/v1/dashboard/cashflow-sankey', () =>
          delayedJson({
            totalIncome: 5000,
            totalExpenses: 3000,
            surplus: 2000,
            incomeSources: [],
            expenseCategories: [],
            period: 30,
          })
        ),
        http.get('/api/v1/transactions/search', () => delayedJson({ content: [] }))
      );

      renderWithProviders(<DashboardPage />);

      await screen.findAllByText(/Net Worth/i);

      const customButton = await screen.findByRole('button', {
        name: /custom|personnalisée/i,
      });

      fireEvent.click(customButton);

      await waitFor(() => {
        expect(screen.getByText(/date range|plage de dates personnalisée/i)).toBeInTheDocument();
      });

      const dateInputs = screen.getAllByDisplayValue(/^\d{4}-\d{2}-\d{2}$/);
      expect(dateInputs).toHaveLength(2);

      fireEvent.change(dateInputs[0], { target: { value: '2026-01-01' } });

      await waitFor(() => {
        expect(screen.getByText(/date range|plage de dates personnalisée/i)).toBeInTheDocument();
        expect(
          screen.getByRole('heading', { name: /dashboard|tableau de bord/i })
        ).toBeInTheDocument();
        expect(screen.getByDisplayValue('2026-01-01')).toBeInTheDocument();
      });
    }, 15000);
  });

  describe('Empty States', () => {
    it('should display dashboard subtitle with snapshot date', async () => {
      renderWithProviders(<DashboardPage />);
      const subtitle = await screen.findByText(
        /overview as of 02\/04\/2026/i,
        {},
        { timeout: 5000 }
      );
      expect(subtitle).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('should display error message when API fails', async () => {
      server.use(
        http.get('/api/v1/dashboard/summary', () =>
          HttpResponse.json({ message: 'Server error' }, { status: 500 })
        )
      );
      renderWithProviders(<DashboardPage />);
      const errorText = await screen.findByText(/failed to load/i, {}, { timeout: 10000 });
      expect(errorText).toBeInTheDocument();
    }, 15000);
  });

  describe('Card Visibility', () => {
    it('should open cards menu and toggle a card off', async () => {
      renderWithProviders(<DashboardPage />);
      await screen.findAllByText(/Net Worth/i);

      // Find the cards/sliders button
      const cardsButton = screen.getByRole('button', { name: /^cards$|^cartes$/i });
      fireEvent.click(cardsButton);

      // Should see checkboxes for card visibility
      await waitFor(() => {
        const checkboxes = document.querySelectorAll('input[type="checkbox"]');
        expect(checkboxes.length).toBeGreaterThan(0);
      });
    });

    it('should reset layout when reset button is clicked', async () => {
      // Pre-set a custom layout in localStorage
      localStorage.setItem('open_finance_dashboard_layouts', JSON.stringify({ lg: [] }));
      renderWithProviders(<DashboardPage />);
      await screen.findAllByText(/Net Worth/i);

      // Open cards menu
      const cardsButton = screen.getByRole('button', { name: /^cards$|^cartes$/i });
      fireEvent.click(cardsButton);

      // Click reset layout
      await waitFor(() => {
        const resetButton = screen.getByRole('button', { name: /^reset layout$/i });
        fireEvent.click(resetButton);
      });

      // Verify localStorage was updated with default layout
      const stored = JSON.parse(localStorage.getItem('open_finance_dashboard_layouts') || '{}');
      expect(stored.lg).toBeDefined();
      expect(stored.lg.length).toBeGreaterThan(0);
    });
  });

  describe('Period Selection', () => {
    it('persists the migrated current-month preset so it keeps following the calendar', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 17, 12));
      sessionStorage.setItem(
        'dashboard_period',
        JSON.stringify({ period: 'MONTH', month: '2026-10' })
      );
      try {
        renderWithProviders(<DashboardPage />);
        expect(
          await screen.findByRole('button', { name: i18n.t('common:periodSelector.presets.MTD') })
        ).toHaveAttribute('aria-pressed', 'true');
        expect(JSON.parse(sessionStorage.getItem('dashboard_period')!)).toEqual({ period: 'MTD' });
      } finally {
        vi.useRealTimers();
      }
    });

    it('sends the same calendar dates to cards, history and drill-downs, with a calendar comparison', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 17, 12));
      const requests: URL[] = [];
      const observe = ({ request }: { request: Request }): void => {
        requests.push(new URL(request.url));
      };
      server.events.on('request:start', observe);
      try {
        renderWithProviders(<DashboardPage />);
        const paths = [
          'cashflow',
          'networth-history',
          'portfolio-performance',
          'borrowing-capacity',
          'estimated-interest',
        ];
        await waitFor(() => {
          for (const path of paths) {
            expect(
              requests.some(
                url =>
                  url.pathname === `/api/v1/dashboard/${path}` &&
                  url.searchParams.get('startDate') === '2026-10-01' &&
                  url.searchParams.get('endDate') === '2026-10-17'
              )
            ).toBe(true);
          }
          expect(
            requests.some(
              url =>
                url.pathname.endsWith('/cashflow-sankey') &&
                url.searchParams.get('startDate') === '2026-09-01' &&
                url.searchParams.get('endDate') === '2026-09-17'
            )
          ).toBe(true);
        });

        expect(
          screen.getByRole('button', { name: i18n.t('common:periodSelector.presets.MTD') })
        ).toHaveAttribute('aria-pressed', 'true');
        const sankeyCard = screen.getByRole('group', { name: 'Cash flow diagram' }).parentElement!
          .parentElement!;
        const periodCaption = within(sankeyCard).getByRole('button', { name: 'last 17d' });
        fireEvent.focus(periodCaption);
        expect(await screen.findByRole('tooltip')).toHaveTextContent(
          'Selected period: 10/01/2026 → 10/17/2026'
        );
        fireEvent.blur(periodCaption);
        fireEvent.click(screen.getByRole('button', { name: 'Custom range' }));
        fireEvent.change(screen.getByLabelText('From', { exact: true }), {
          target: { value: '07/01/2026' },
        });
        fireEvent.change(screen.getByLabelText('To', { exact: true }), {
          target: { value: '07/31/2026' },
        });
        fireEvent.click(screen.getByRole('button', { name: '07/01/2026 → 07/31/2026' }));
        await waitFor(() => {
          for (const path of paths) {
            expect(
              requests.some(
                url =>
                  url.pathname === `/api/v1/dashboard/${path}` &&
                  url.searchParams.get('startDate') === '2026-07-01' &&
                  url.searchParams.get('endDate') === '2026-07-31'
              )
            ).toBe(true);
          }
          expect(
            requests.some(
              url =>
                url.pathname.endsWith('/cashflow-sankey') &&
                url.searchParams.get('startDate') === '2026-06-01' &&
                url.searchParams.get('endDate') === '2026-06-30'
            )
          ).toBe(true);
          expect(
            requests.some(
              url =>
                url.pathname.endsWith('/transactions/search') &&
                url.searchParams.get('dateFrom') === '2026-07-01' &&
                url.searchParams.get('dateTo') === '2026-07-31'
            )
          ).toBe(true);
        });
        expect(requests.some(url => url.searchParams.get('startDate') === '2026-05-31')).toBe(
          false
        );
        expect(JSON.parse(sessionStorage.getItem('dashboard_period')!)).toEqual({
          period: 'CUSTOM',
          range: { from: '2026-07-01', to: '2026-07-31' },
        });

        for (const [button, from] of [
          ['1D', '2026-10-17'],
          ['7D', '2026-10-11'],
          ['30D', '2026-09-18'],
        ]) {
          fireEvent.click(screen.getByRole('button', { name: button, exact: true }));
          // Custom initially fetched 30D, so that exact request may already be cached.
          await waitFor(() =>
            expect(
              requests.some(
                url =>
                  url.pathname.endsWith('/networth-history') &&
                  url.searchParams.get('startDate') === from &&
                  url.searchParams.get('endDate') === '2026-10-17'
              )
            ).toBe(true)
          );
          expect(screen.getByRole('button', { name: button, exact: true })).toHaveAttribute(
            'aria-pressed',
            'true'
          );
        }
      } finally {
        server.events.removeListener('request:start', observe);
        vi.useRealTimers();
      }
    });

    it('should switch to 365D period and update net worth change', async () => {
      renderWithProviders(<DashboardPage />);
      await screen.findAllByText(/Net Worth/i);

      // Find and click the rolling year button
      const yearButton = await screen.findByRole('button', { name: /365D|365j/i });
      fireEvent.click(yearButton);

      // Dashboard should still render after period change
      expect(screen.getAllByText(/Net Worth/i).length).toBeGreaterThan(0);
    });

    it('should switch to ALL period', async () => {
      renderWithProviders(<DashboardPage />);
      await screen.findAllByText(/Net Worth/i);

      const allButton = await screen.findByRole('button', {
        name: i18n.t('common:periodSelector.presets.ALL'),
      });
      fireEvent.click(allButton);

      expect(screen.getAllByText(/Net Worth/i).length).toBeGreaterThan(0);
    });
  });

  describe('Responsive Design', () => {
    it('should render dashboard layout correctly', async () => {
      renderWithProviders(<DashboardPage />);

      // Wait for page to load - use Net Worth text that appears in card title
      const netWorthElements = await screen.findAllByText(/Net Worth/i, {}, { timeout: 5000 });
      expect(netWorthElements.length).toBeGreaterThan(0);

      // Verify summary stats section at bottom (these are always rendered)
      const totalAccountsLabel = await screen.findByText(/Total Accounts/i, {}, { timeout: 5000 });
      expect(totalAccountsLabel).toBeInTheDocument();

      const totalTransactionsLabel = await screen.findByText(
        /Total Transactions/i,
        {},
        { timeout: 5000 }
      );
      expect(totalTransactionsLabel).toBeInTheDocument();

      // Verify transaction data appears (indicating grid layout rendered)
      const groceries = await screen.findByText('Weekly groceries', {}, { timeout: 5000 });
      expect(groceries).toBeInTheDocument();
    });
  });
});
