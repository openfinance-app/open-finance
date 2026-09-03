import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { Plus, ChevronDown, GripVertical, SlidersHorizontal } from 'lucide-react';
import { Responsive, WidthProvider } from 'react-grid-layout/legacy';
import 'react-grid-layout/css/styles.css';
import 'react-resizable/css/styles.css';
import { useTranslation } from 'react-i18next';
import { GRID_LAYOUT_BREAKPOINTS, GRID_LAYOUT_COLS } from '@/constants/breakpoints';
import { RESIZE_EVENT_DELAY_MS } from '@/constants/timing';

import {
  useDashboardSummary,
  useCashFlow,
  useNetWorthHistory,
  useAssetAllocation,
  usePortfolioPerformance,
  useBorrowingCapacity,
  useNetWorthAllocation,
  useEstimatedInterest,
  useTransactionsByPeriod,
} from '../hooks/useDashboard';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import NetWorthCard from '../components/dashboard/NetWorthCard';
import RecentTransactionsCard from '../components/dashboard/RecentTransactionsCard';
import CashFlowChart from '../components/dashboard/CashFlowChart';
import NetWorthTrendChart from '../components/dashboard/NetWorthTrendChart';
import CurrencyBreakdown from '../components/dashboard/CurrencyBreakdown';
import InstitutionBreakdown from '../components/dashboard/InstitutionBreakdown';
import AssetAllocationChart from '../components/dashboard/AssetAllocationChart';
import PortfolioPerformanceCards from '../components/dashboard/PortfolioPerformanceCards';
import InsightsCard from '../components/dashboard/InsightsCard';
import BorrowingCapacityCard from '../components/dashboard/BorrowingCapacityCard';
import NetWorthAllocationChart from '../components/dashboard/NetWorthAllocationChart';
import DailyCashFlowCalendar from '../components/dashboard/DailyCashFlowCalendar';
import CashflowSankeyCard from '../components/dashboard/CashflowSankeyCard';
import EstimatedInterestCard from '../components/dashboard/EstimatedInterestCard';
import PeriodSelector, { type Period, type DateRange, dateRangeToDays } from '../components/ui/PeriodSelector';
import BudgetProgressCard from '../components/dashboard/BudgetProgressCard';
import RssFeedCard from '../components/dashboard/RssFeedCard';
import BalanceVariationCard from '../components/dashboard/BalanceVariationCard';
import FinancialMap from '../components/dashboard/FinancialMap';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { subtract, percentage } from '@/utils/money';
import { periodToDateRange } from '@/utils/navigation';
import { cn } from '@/lib/utils';

const ResponsiveGridLayout = WidthProvider(Responsive);

type DashboardCardId =
  | 'netWorth'
  | 'insights'
  | 'currency'
  | 'cashFlow'
  | 'cashflowSankey'
  | 'dailyCashFlow'
  | 'recentTransactions'
  | 'netWorthTrend'
  | 'portfolioPerformance'
  | 'assetAllocation'
  | 'borrowingCapacity'
  | 'netWorthAllocation'
  | 'estimatedInterest'
  | 'institutionBreakdown'
  | 'budgetProgress'
  | 'financeNews'
  | 'financialMap'
  | 'balanceVariation';

interface DashboardCardConfig {
  id: DashboardCardId;
  label: string;
  description: string;
  isAvailable: boolean;
  render: () => ReactNode;
}

const DEFAULT_CARD_ORDER: DashboardCardId[] = [
  'netWorth',
  'insights',
  'financeNews',
  'currency',
  'budgetProgress',
  'financialMap',
  'cashFlow',
  'cashflowSankey',
  'dailyCashFlow',
  'recentTransactions',
  'netWorthTrend',
  'portfolioPerformance',
  'assetAllocation',
  'borrowingCapacity',
  'netWorthAllocation',
  'estimatedInterest',
  'institutionBreakdown',
  'balanceVariation',
];

const STORAGE_KEY = 'open_finance_dashboard_layouts';
const VISIBILITY_STORAGE_KEY = 'open_finance_dashboard_card_visibility';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const generateDefaultLayouts = (): Record<string, any> => ({
  lg: [
    // Row 1 (y=0 to y=5)
    { i: 'netWorth', x: 0, y: 0, w: 4, h: 5, minW: 3, minH: 4 },
    { i: 'insights', x: 4, y: 0, w: 4, h: 5, minW: 3, minH: 4 },
    { i: 'currency', x: 8, y: 0, w: 4, h: 5, minW: 3, minH: 4 },
    { i: 'institutionBreakdown', x: 0, y: 5, w: 4, h: 7, minW: 3, minH: 5 },

    // Budget Progress (y=5 to y=12) — spans 8 cols next to institutionBreakdown
    { i: 'budgetProgress', x: 4, y: 5, w: 8, h: 7, minW: 4, minH: 5 },

    // Financial Map — full-width glance of finances across the globe (y=12 to y=21)
    { i: 'financialMap', x: 0, y: 12, w: 12, h: 9, minW: 4, minH: 7 },

    // Row 2 (y=21 to y=28)
    { i: 'cashFlow', x: 0, y: 21, w: 12, h: 7, minW: 6, minH: 6 },

    // Row 3 - Sankey (y=28 to y=38)
    { i: 'cashflowSankey', x: 0, y: 28, w: 12, h: 10, minW: 6, minH: 6 },

    // Row 4 (y=38 to y=47)
    { i: 'dailyCashFlow', x: 0, y: 38, w: 12, h: 9, minW: 6, minH: 6 },

    // Row 5 (y=47 to y=55)
    { i: 'recentTransactions', x: 0, y: 47, w: 4, h: 8, minW: 3, minH: 5 },
    { i: 'netWorthTrend', x: 4, y: 47, w: 8, h: 8, minW: 4, minH: 5 },

    // Row 6 (y=55 to y=63)
    { i: 'portfolioPerformance', x: 0, y: 55, w: 12, h: 8, minW: 6, minH: 6 },

    // Row 7 (y=63 to y=72)
    { i: 'assetAllocation', x: 0, y: 63, w: 4, h: 9, minW: 3, minH: 6 },
    { i: 'netWorthAllocation', x: 4, y: 63, w: 4, h: 9, minW: 3, minH: 6 },
    { i: 'borrowingCapacity', x: 8, y: 63, w: 4, h: 9, minW: 3, minH: 6 },

    // Row 8 (y=72 to y=81)
    { i: 'estimatedInterest', x: 0, y: 72, w: 4, h: 9, minW: 3, minH: 6 },
    { i: 'financeNews', x: 4, y: 72, w: 4, h: 9, minW: 3, minH: 6 },

    // Row 9 (y=81 to y=91)
    { i: 'balanceVariation', x: 0, y: 81, w: 12, h: 10, minW: 6, minH: 7 },
  ]
});

// Default geometry per card, used as the react-grid-layout `data-grid` fallback so
// cards whose data loads late (or that appear in a lazily-generated breakpoint) are
// placed at their intended size instead of RGL's collapsed 1x1 default.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const DEFAULT_LAYOUT_BY_ID: Record<string, any> = generateDefaultLayouts().lg.reduce(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  (acc: Record<string, any>, item: any) => {
    acc[item.i] = item;
    return acc;
  },
  {}
);

export default function DashboardPage() {

  const { t } = useTranslation('dashboard');
  useDocumentTitle(t('title'));

  // ── Period state ────────────────────────────────────────────────────────────
  const [periodState, setPeriodState] = useState<{
    selectedPeriod: Period;
    periodDays: number;
    historyPeriod: number;
    activeDateRange: DateRange | undefined;
  }>(() => {
    const saved = sessionStorage.getItem('dashboard_period');
    if (saved) {
      try {
        return JSON.parse(saved);
      } catch (e) {
        // ignore
      }
    }
    return {
      selectedPeriod: '1M',
      periodDays: 30,
      historyPeriod: 90,
      activeDateRange: undefined,
    };
  });
  const { selectedPeriod, periodDays, historyPeriod, activeDateRange } = periodState;

  // ── Layout / UI state ───────────────────────────────────────────────────────
  const [isCardMenuOpen, setIsCardMenuOpen] = useState(false);
  const cardMenuRef = useRef<HTMLDivElement>(null);
  // True once the user actually drags/resizes a card. Mount-time and breakpoint
  // auto-generation echoes from react-grid-layout must NOT overwrite the saved
  // layout (they arrive compacted/regenerated and would clobber the user's
  // arrangement on refresh); only genuine interactions are persisted.
  const layoutInteractedRef = useRef(false);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [layouts, setLayouts] = useState<Record<string, any>>(() => {
    const defaultLayouts = generateDefaultLayouts();
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        let repaired = false;
        
        if (parsed.lg && Array.isArray(parsed.lg)) {
          const defaultLg = defaultLayouts.lg;
          const map = new Map<string, any>(parsed.lg.map((item: any) => [item.i, item]));

          defaultLg.forEach((defItem: any) => {
             const existing = map.get(defItem.i);
             if (!existing || (existing.w === 1 && existing.h === 1)) {
                map.set(defItem.i, defItem);
                repaired = true;
             } else {
                existing.minW = defItem.minW;
                existing.minH = defItem.minH;
                if (existing.w < defItem.minW) existing.w = defItem.minW;
                if (existing.h < defItem.minH) existing.h = defItem.minH;
             }
          });
          
          parsed.lg = Array.from(map.values());
        }
        
        // A collapsed (1x1) card in ANY non-lg breakpoint means that breakpoint was
        // generated before a late-loading card mounted. Flag it so those breakpoints
        // are dropped and regenerated from the repaired lg (data-grid keeps late
        // cards correctly sized during regeneration).
        if (!repaired) {
          repaired = Object.keys(parsed).some(
            (k) =>
              k !== 'lg' &&
              Array.isArray(parsed[k]) &&
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              parsed[k].some((it: any) => it.w === 1 && it.h === 1)
          );
        }

        if (repaired) {
          // If we repaired items, clear other breakpoints so they regenerate based on lg
          Object.keys(parsed).forEach(k => {
             if (k !== 'lg') delete parsed[k];
          });
          localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed));
        }
        
        return parsed;
      } catch {
        return defaultLayouts;
      }
    }
    return defaultLayouts;
  });

  const [cardVisibility, setCardVisibility] = useState<Record<DashboardCardId, boolean>>(() => {
    const defaults = DEFAULT_CARD_ORDER.reduce((acc, cardId) => {
      acc[cardId] = true;
      return acc;
    }, {} as Record<DashboardCardId, boolean>);
    const saved = localStorage.getItem(VISIBILITY_STORAGE_KEY);
    if (saved) {
      try {
        return { ...defaults, ...(JSON.parse(saved) as Record<DashboardCardId, boolean>) };
      } catch {
        // ignore malformed persisted visibility
      }
    }
    return defaults;
  });

  // ── Period change handler ───────────────────────────────────────────────────
  const handlePeriodChange = (period: Period, days: number | null, dateRange?: DateRange) => {
    let newState;
    if (period === 'CUSTOM' && dateRange) {
      const customDays = dateRangeToDays(dateRange);
      newState = {
        selectedPeriod: period,
        periodDays: customDays,
        historyPeriod: customDays,
        activeDateRange: dateRange,
      };
    } else {
      const cashFlowDays = days ?? 36500;
      let historyDays: number;
      switch (period) {
        case '1D': historyDays = 7; break;
        case '7D': historyDays = 30; break;
        case '1M': historyDays = 90; break;
        case 'YTD': historyDays = days ?? 365; break;
        case '1Y': historyDays = 365; break;
        case 'ALL': historyDays = 36500; break;
        default: historyDays = 365;
      }
      newState = {
        selectedPeriod: period,
        periodDays: cashFlowDays,
        historyPeriod: historyDays,
        activeDateRange: undefined,
      };
    }
    
    setPeriodState(newState);
    sessionStorage.setItem('dashboard_period', JSON.stringify(newState));
  };

  // ── Period label ────────────────────────────────────────────────────────────
  /** Human-readable label for the currently selected period */
  const periodLabel = useMemo((): string => {
    if (selectedPeriod === 'CUSTOM' && activeDateRange) {
      return t('period.custom', { from: activeDateRange.from, to: activeDateRange.to });
    }
    switch (selectedPeriod) {
      case '1D': return t('period.last1d');
      case '7D': return t('period.last7d');
      case '1M': return t('period.last30d');
      case 'YTD': return t('period.ytd');
      case '1Y': return t('period.last1y');
      case 'ALL': return t('period.allTime');
      default: return t('period.lastNDays', { days: periodDays });
    }
  }, [selectedPeriod, activeDateRange, periodDays, t]);

  // ── Date range used for card → transactions deep links ─────────────────────
  const navDateRange: DateRange = useMemo(
    () => activeDateRange ?? periodToDateRange(periodDays),
    [activeDateRange, periodDays]
  );

  // ── Data fetching ───────────────────────────────────────────────────────────
  // All period-aware hooks receive both `periodDays` and the optional `activeDateRange`.
  // When activeDateRange is set the hooks send startDate/endDate; otherwise they send `period`.
  // React Query will automatically re-fetch when either parameter changes.
  //
  // No manual refetch needed — React Query invalidates the ['dashboard'] key whenever
  // the user creates/updates/deletes accounts or transactions (see useAccounts &
  // useTransactions mutation hooks).
  const { data: summary, isLoading: summaryLoading, error: summaryError } = useDashboardSummary();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(summary?.baseCurrency);
  const { data: cashFlow, isLoading: cashFlowLoading } = useCashFlow(periodDays, activeDateRange);
  const { data: netWorthHistory, isLoading: historyLoading } = useNetWorthHistory(historyPeriod, activeDateRange);
  const { data: assetAllocations, isLoading: allocationLoading } = useAssetAllocation();
  const { data: portfolioPerformances, isLoading: performanceLoading } = usePortfolioPerformance(periodDays, activeDateRange);
  const { data: borrowingCapacity, isLoading: borrowingLoading } = useBorrowingCapacity(periodDays, activeDateRange);
  const { data: netWorthAllocations, isLoading: netWorthAllocationLoading } = useNetWorthAllocation();
  const { data: estimatedInterest } = useEstimatedInterest(selectedPeriod === 'CUSTOM' ? '1M' : selectedPeriod);
  const { data: periodTransactions, isLoading: transactionsLoading } = useTransactionsByPeriod(periodDays, activeDateRange);

  // ── Period change computed from history chart (BUG-D1) ─────────────────────
  const periodChange = useMemo(() => {
    if (!netWorthHistory || netWorthHistory.length < 2 || summary?.netWorth?.netWorth == null) return null;
    // Find the data point closest to periodDays ago (not just the first point
    // in the history, which may span a wider window for chart context).
    // eslint-disable-next-line react-hooks/rules-of-hooks -- Date.now() is intentionally used to find closest historical data point
    const targetTime = Date.now() - periodDays * 86_400_000;
    let closest = netWorthHistory[0];
    let closestDiff = Math.abs(new Date(closest.date).getTime() - targetTime);
    for (const point of netWorthHistory) {
      const diff = Math.abs(new Date(point.date).getTime() - targetTime);
      if (diff < closestDiff) {
        closestDiff = diff;
        closest = point;
      }
    }
    if (closest.netWorth === 0) return null;
    const changeAmount = subtract(summary.netWorth.netWorth ?? 0, closest.netWorth);
    const changePercent = percentage(changeAmount, Math.abs(closest.netWorth));
    return { amount: changeAmount, percentage: changePercent };
  }, [netWorthHistory, summary?.netWorth?.netWorth, periodDays]);

  // ── Close card menu on outside click ───────────────────────────────────────
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (cardMenuRef.current && !cardMenuRef.current.contains(event.target as Node)) {
        setIsCardMenuOpen(false);
      }
    };
    if (isCardMenuOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isCardMenuOpen]);

  // ── Dashboard card configurations ──────────────────────────────────────────
  const dashboardCards: DashboardCardConfig[] = useMemo(() => {
    if (!summary) return [];
    return [
      {
        id: 'netWorth',
        label: t('cards.netWorth.label'),
        description: t('cards.netWorth.description'),
        isAvailable: true,
        render: () => <NetWorthCard netWorth={summary.netWorth} periodLabel={periodLabel} periodChange={periodChange} />,
      },
      {
        id: 'insights',
        label: t('cards.insights.label'),
        description: t('cards.insights.description'),
        isAvailable: true,
        render: () => <InsightsCard />,
      },
      {
        id: 'currency',
        label: t('cards.currency.label'),
        description: t('cards.currency.description'),
        isAvailable: true,
        render: () => <CurrencyBreakdown baseCurrency={summary.baseCurrency} />,
      },
      {
        id: 'institutionBreakdown',
        label: t('cards.institutionBreakdown.label'),
        description: t('cards.institutionBreakdown.description'),
        isAvailable: true,
        render: () => <InstitutionBreakdown baseCurrency={summary.baseCurrency} />,
      },
      {
        id: 'budgetProgress',
        label: t('cards.budgetProgress.label'),
        description: t('cards.budgetProgress.description'),
        isAvailable: true,
        render: () => <BudgetProgressCard />,
      },
      {
        id: 'financialMap',
        label: t('cards.financialMap.label'),
        description: t('cards.financialMap.description'),
        isAvailable: true,
        render: () => <FinancialMap baseCurrency={summary.baseCurrency} />,
      },
      {
        id: 'cashFlow',
        label: t('cards.cashFlow.label'),
        description: t('cards.cashFlow.description'),
        isAvailable: Boolean(cashFlow),
        render: () =>
          cashFlow ? (
            <CashFlowChart
              cashFlow={cashFlow}
              period={periodDays}
              currency={summary.baseCurrency}
              navDateRange={navDateRange}
            />
          ) : null,
      },
      {
        id: 'cashflowSankey',
        label: t('cards.cashflowSankey.label'),
        description: t('cards.cashflowSankey.description'),
        isAvailable: true,
        render: () => (
          <CashflowSankeyCard
            currency={summary.baseCurrency}
            period={periodDays}
            dateRange={activeDateRange}
            navDateRange={navDateRange}
          />
        ),
      },
      {
        id: 'dailyCashFlow',
        label: t('cards.dailyCashFlow.label'),
        description: t('cards.dailyCashFlow.description'),
        isAvailable: true,
        render: () => <DailyCashFlowCalendar baseCurrency={summary.baseCurrency} />,
      },
      {
        id: 'recentTransactions',
        label: t('cards.recentTransactions.label'),
        description: t('cards.recentTransactions.description'),
        isAvailable: true,
        render: () => <RecentTransactionsCard
          transactions={periodTransactions ?? []}
          periodLabel={periodLabel}
          isLoading={transactionsLoading}
        />,
      },
      {
        id: 'netWorthTrend',
        label: t('cards.netWorthTrend.label'),
        description: t('cards.netWorthTrend.description'),
        isAvailable: Boolean(netWorthHistory && netWorthHistory.length > 0),
        render: () => netWorthHistory && netWorthHistory.length > 0
          ? <NetWorthTrendChart data={netWorthHistory} currency={summary.baseCurrency} />
          : null,
      },
      {
        id: 'portfolioPerformance',
        label: t('cards.portfolioPerformance.label'),
        description: t('cards.portfolioPerformance.description'),
        isAvailable: Boolean(portfolioPerformances && portfolioPerformances.length > 0),
        render: () => portfolioPerformances && portfolioPerformances.length > 0
          ? <PortfolioPerformanceCards performances={portfolioPerformances} periodLabel={periodLabel} />
          : null,
      },
      {
        id: 'assetAllocation',
        label: t('cards.assetAllocation.label'),
        description: t('cards.assetAllocation.description'),
        isAvailable: Boolean(assetAllocations && assetAllocations.length > 0),
        render: () => assetAllocations && assetAllocations.length > 0
          ? <AssetAllocationChart allocations={assetAllocations} currency={summary.baseCurrency} />
          : null,
      },
      {
        id: 'borrowingCapacity',
        label: t('cards.borrowingCapacity.label'),
        description: t('cards.borrowingCapacity.description'),
        isAvailable: Boolean(borrowingCapacity),
        render: () => borrowingCapacity
          ? <BorrowingCapacityCard capacity={borrowingCapacity} />
          : null,
      },
      {
        id: 'netWorthAllocation',
        label: t('cards.netWorthAllocation.label'),
        description: t('cards.netWorthAllocation.description'),
        isAvailable: Boolean(netWorthAllocations && netWorthAllocations.length > 0),
        render: () => netWorthAllocations && netWorthAllocations.length > 0
          ? <NetWorthAllocationChart allocations={netWorthAllocations} currency={summary.baseCurrency} />
          : null,
      },
      {
        id: 'estimatedInterest',
        label: t('cards.estimatedInterest.label'),
        description: t('cards.estimatedInterest.description'),
        isAvailable: Boolean(estimatedInterest && estimatedInterest.accounts?.length > 0),
        render: () => estimatedInterest
          ? <EstimatedInterestCard
            summary={estimatedInterest}
            period={selectedPeriod === 'CUSTOM' ? '1M' : selectedPeriod}
          />
          : null,
      },
      {
        id: 'financeNews',
        label: 'Finance News',
        description: 'Latest finance news from top sources',
        isAvailable: true,
        render: () => <RssFeedCard />,
      },
      {
        id: 'balanceVariation',
        label: t('cards.balanceVariation.label'),
        description: t('cards.balanceVariation.description'),
        isAvailable: true,
        render: () => <BalanceVariationCard currency={summary.baseCurrency} />,
      },
    ];
  }, [
    summary, cashFlow, periodDays, activeDateRange, netWorthHistory,
    portfolioPerformances, assetAllocations, borrowingCapacity,
    netWorthAllocations, estimatedInterest, selectedPeriod,
    periodLabel, periodChange, periodTransactions, transactionsLoading, t,
    navDateRange,
  ]);

  const cardById = useMemo(() =>
    dashboardCards.reduce((acc, card) => {
      acc[card.id] = card;
      return acc;
    }, {} as Record<DashboardCardId, DashboardCardConfig>),
    [dashboardCards]);

  // ── Layout handlers ─────────────────────────────────────────────────────────
  // react-grid-layout reports only the currently-rendered cards. Cards that are
  // hidden, unavailable for the selected period, or absent during loading are
  // omitted from `allLayouts`. Merge incoming positions onto the previously saved
  // layout so those absent cards keep their slots instead of being auto-placed at
  // the bottom when they reappear (fixes layout loss on navigation & period change).
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handleLayoutChange = (currentLayout: any, allLayouts: any) => {
    // Ignore the transient empty layout RGL emits before children mount / while data loads.
    if (!currentLayout || currentLayout.length === 0) return;

    setLayouts((prev) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const merged: Record<string, any> = { ...prev };
      Object.keys(allLayouts).forEach((bp) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const incoming: any[] = allLayouts[bp] ?? [];
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const prevItems: any[] = prev[bp] ?? [];
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const incomingById = new Map<string, any>(incoming.map((it) => [it.i, it]));
        // Keep every previously-known card, updating positions for the ones RGL reported.
        const mergedItems = prevItems.map((it) => incomingById.get(it.i) ?? it);
        // Append any brand-new cards not present in the previous layout.
        incoming.forEach((it) => {
          if (!mergedItems.some((m) => m.i === it.i)) mergedItems.push(it);
        });
        merged[bp] = mergedItems;
      });
      // Persist only after a real user drag/resize. Mount-time / breakpoint
      // regeneration echoes update in-memory state for a smooth render but must
      // never be written back, or the saved arrangement is lost on refresh.
      if (layoutInteractedRef.current) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
      }
      return merged;
    });
  };

  const handleResetLayout = () => {
    const defaultLayout = generateDefaultLayouts();
    setLayouts(defaultLayout);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(defaultLayout));
    const allVisible = DEFAULT_CARD_ORDER.reduce((acc, cardId) => {
      acc[cardId] = true;
      return acc;
    }, {} as Record<DashboardCardId, boolean>);
    setCardVisibility(allVisible);
    localStorage.removeItem(VISIBILITY_STORAGE_KEY);
  };

  // ── Loading skeleton ────────────────────────────────────────────────────────
  if (!summary && (summaryLoading || cashFlowLoading || historyLoading || allocationLoading || performanceLoading || borrowingLoading || netWorthAllocationLoading)) {
    return (
      <div className="animate-pulse space-y-6">
        <div className="h-8 bg-surface-elevated rounded w-48"></div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div className="h-64 bg-surface-elevated rounded-lg"></div>
          <div className="h-64 bg-surface-elevated rounded-lg"></div>
          <div className="h-64 bg-surface-elevated rounded-lg"></div>
          <div className="lg:col-span-2 h-96 bg-surface-elevated rounded-lg"></div>
          <div className="h-96 bg-surface-elevated rounded-lg"></div>
        </div>
      </div>
    );
  }

  // ── Error states ────────────────────────────────────────────────────────────
  if (summaryError) {
    const isDecryptionError =
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (summaryError as any).response?.status === 400 &&
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (summaryError as any).response?.data?.message?.includes('Decryption failed');

    if (isDecryptionError) {
      return (
        <div className="bg-red-500/10 border border-red-500/50 rounded-lg p-6">
          <h3 className="text-xl font-semibold text-red-500 mb-2">{t('errors.cannotDecrypt.title')}</h3>
          <p className="text-text-secondary mb-4">{t('errors.cannotDecrypt.description')}</p>
          <button
            onClick={() => {
              sessionStorage.clear();
              localStorage.clear();
              window.location.href = '/login';
            }}
            className="px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors font-semibold"
          >
            {t('logoutAndTryAgain', { ns: 'common' })}
          </button>
        </div>
      );
    }
    return (
      <div className="bg-red-500/10 border border-red-500/50 rounded-lg p-6 text-center">
        <p className="text-red-500 font-semibold mb-2">{t('errors.loadFailed')}</p>
        <p className="text-text-secondary text-sm mb-4">
          {summaryError instanceof Error ? summaryError.message : t('errors.unexpected')}
        </p>
      </div>
    );
  }

  if (!summary) return null;

  return (
    <>
      {/* ── Page header ──────────────────────────────────────────────────── */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
        <div>
          <h1 className="text-3xl font-bold text-text-primary mb-1">{t('title')}</h1>
          <p className="text-text-secondary text-sm">{t('subtitle', { date: summary.snapshotDate })}</p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          {/* Cards visibility menu */}
          <div className="relative" ref={cardMenuRef}>
            <button
              onClick={() => setIsCardMenuOpen((prev) => !prev)}
              className="px-3 py-2 bg-surface-elevated text-text-secondary rounded-lg hover:bg-surface-elevated/80 transition-colors flex items-center gap-2"
            >
              <SlidersHorizontal className="h-4 w-4" />
              <span className="hidden sm:inline text-sm">{t('cardsButton')}</span>
              <ChevronDown className={cn('h-4 w-4 transition-transform', isCardMenuOpen && 'rotate-180')} />
            </button>

            {isCardMenuOpen && (
              <div className="absolute right-0 mt-2 w-64 bg-surface border border-border rounded-lg shadow-lg p-3 z-50 animate-in fade-in slide-in-from-top-2 duration-200">
                <div className="flex items-center justify-between mb-3">
                  <p className="text-xs uppercase tracking-wide text-text-secondary">{t('showCards')}</p>
                  <button onClick={handleResetLayout} className="text-xs text-primary hover:underline">
                    {t('resetLayout')}
                  </button>
                </div>
                <div className="space-y-2 max-h-64 overflow-y-auto scrollbar-thin pr-1">
                  {dashboardCards.map((card) => (
                    <label
                      key={card.id}
                      className={cn(
                        'flex items-start gap-2 rounded-md px-2 py-1.5',
                        card.isAvailable
                          ? 'cursor-pointer hover:bg-surface-elevated'
                          : 'cursor-not-allowed opacity-60',
                      )}
                    >
                      <input
                        type="checkbox"
                        className="mt-1 accent-primary"
                        checked={cardVisibility[card.id]}
                        onChange={() => {
                          setCardVisibility((prev) => {
                            const next = { ...prev, [card.id]: !prev[card.id] };
                            localStorage.setItem(VISIBILITY_STORAGE_KEY, JSON.stringify(next));
                            return next;
                          });
                          setTimeout(() => window.dispatchEvent(new Event('resize')), RESIZE_EVENT_DELAY_MS);
                        }}
                        disabled={!card.isAvailable}
                      />
                      <span className="flex-1">
                        <span className="block text-sm text-text-primary">{card.label}</span>
                        <span className="block text-xs text-text-secondary">{card.description}</span>
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Add Transaction */}
          <button
            onClick={() => window.dispatchEvent(new CustomEvent('open-transaction-modal'))}
            className="px-4 py-2 bg-primary text-black font-semibold rounded-lg hover:bg-primary/90 transition-colors flex items-center gap-2"
          >
            <Plus className="h-5 w-5" />
            <span className="hidden sm:inline">{t('addTransaction')}</span>
          </button>
        </div>
      </div>

      {/* ── Global period selector ────────────────────────────────────────── */}
      <div className="mb-6 flex justify-center">
        <PeriodSelector selectedPeriod={selectedPeriod} activeDateRange={activeDateRange} onPeriodChange={handlePeriodChange} />
      </div>

      {/* ── Cards grid ───────────────────────────────────────────────────── */}
      <div className="-mx-2">
        <ResponsiveGridLayout
          className="layout"
          layouts={layouts}
          breakpoints={GRID_LAYOUT_BREAKPOINTS}
          cols={GRID_LAYOUT_COLS}
          rowHeight={40}
          onLayoutChange={handleLayoutChange}
          onDragStart={() => {
            layoutInteractedRef.current = true;
          }}
          onResizeStart={() => {
            layoutInteractedRef.current = true;
          }}
          draggableHandle=".drag-handle"
          margin={[16, 16]}
        >
          {DEFAULT_CARD_ORDER.filter(cardId => {
            const card = cardById[cardId];
            return card && cardVisibility[cardId] && card.isAvailable;
          }).map((cardId) => {
            const card = cardById[cardId];
            return (
              <div key={card.id} data-grid={DEFAULT_LAYOUT_BY_ID[card.id]} className="relative group flex flex-col h-full rounded-lg overflow-hidden">
                <div className="drag-handle absolute right-3 top-3 z-10 p-1 bg-surface/80 rounded cursor-move text-text-secondary opacity-0 group-hover:opacity-100 transition-opacity hover:text-primary">
                  <GripVertical className="h-4 w-4" />
                </div>
                <div className="flex-1 h-full w-full">
                  {card.render()}
                </div>
              </div>
            );
          })}
        </ResponsiveGridLayout>
      </div>

      {/* ── Summary stats bar ────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6">
        <div className="bg-surface rounded-lg p-4 border border-border">
          <div className="text-xs text-text-secondary mb-1">{t('stats.totalAccounts')}</div>
          <div className="text-2xl font-bold text-text-primary">{summary.totalAccounts}</div>
        </div>
        <div className="bg-surface rounded-lg p-4 border border-border">
          <div className="text-xs text-text-secondary mb-1">{t('stats.totalTransactions')}</div>
          <div className="text-2xl font-bold text-text-primary">{summary.totalTransactions}</div>
        </div>
        <div className="bg-surface rounded-lg p-4 border border-border">
          <div className="text-xs text-text-secondary mb-1">{t('stats.totalAssets')}</div>
          <ConvertedAmount
            amount={summary.netWorth.totalAssets}
            currency={summary.baseCurrency}
            isConverted={false}
            secondaryAmount={convert(summary.netWorth.totalAssets)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            className="text-2xl font-bold text-green-500 font-mono"
          />
        </div>
        <div className="bg-surface rounded-lg p-4 border border-border">
          <div className="text-xs text-text-secondary mb-1">{t('stats.totalLiabilities')}</div>
          <ConvertedAmount
            amount={summary.netWorth.totalLiabilities}
            currency={summary.baseCurrency}
            isConverted={false}
            secondaryAmount={convert(summary.netWorth.totalLiabilities)}
            secondaryCurrency={secCurrency}
            secondaryExchangeRate={secondaryExchangeRate}
            className="text-2xl font-bold text-red-500 font-mono"
          />
        </div>
      </div>
    </>
  );
}
