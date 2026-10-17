import { useDateFormatter } from '@/hooks/useDateFormatter';
import { useId, useState, useCallback, useMemo } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useCashflowSankey } from '@/hooks/useDashboard';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useIsMobile } from '@/hooks/useBreakpoint';
import { buildTransactionsLink, periodToDateRange } from '@/utils/navigation';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { sum } from '@/utils/money';
import {
  compareFlowNodes,
  flowVariation,
  previousSankeyPeriod,
  type ComparedFlowNode,
} from '@/utils/sankey-comparison';
import { SankeyViewport } from '@/components/dashboard/SankeyViewport';
import PeriodLabel from '@/components/dashboard/PeriodLabel';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { useVisibility } from '@/context/VisibilityContext';
import type { ICashflowSankeyNode } from '@/types/dashboard';
import type { DateRange } from '@/components/ui/PeriodSelector';
import { dateRangeToDays, previousEqualPeriod } from '@/utils/dashboard-period';
import {
  CASHFLOW_INCOME_COLORS,
  CASHFLOW_EXPENSE_COLORS,
  CASHFLOW_SURPLUS_COLOR,
  CASHFLOW_DEFICIT_COLOR,
} from '@/constants/colors';

// ─── Colour palettes ───────────────────────────────────────────────────────────
const INCOME_COLORS = CASHFLOW_INCOME_COLORS;
const EXPENSE_COLORS = CASHFLOW_EXPENSE_COLORS;
const SURPLUS_COLOR = CASHFLOW_SURPLUS_COLOR;
const DEFICIT_COLOR = CASHFLOW_DEFICIT_COLOR;

// ─── SVG layout constants ──────────────────────────────────────────────────────
//
//  Key insight: bars are FIXED height (BAR_H).
//  Ribbons taper from full BAR_H on the bar side to a proportional slice
//  of a CAPPED fan at the node side (NODE_FAN_H). This makes the flow
//  visually converge into the node from both sides.
//
//  x layout (SVG_W = 540):
//   [0 .. 100]         income labels  (text-anchor="end")
//   [102 .. 108]       income bars
//   [108 .. NODE_L]    income ribbons
//   [NODE_L .. NODE_R] CASH FLOW node  (200 .. 340)
//   [NODE_R .. EXP_X]  expense ribbons
//   [EXP_X .. EXP_X+6] expense bars
//   [EXP_X+10 .. 540]  expense labels
//
const SVG_W = 560;
// Reserve room for variation labels in both states so toggling cannot rescale the diagram.
const LABEL_MARGIN = 160;
const BAR_W = 14; // clearly visible bars
const BAR_H = 13; // fixed bar height
const ROW_H = 27; // row pitch
const MAX_ROWS = 9;

// Node fan: max total height of all fan slices at the node side.
const NODE_FAN_H = 60;
const NODE_FAN_GAP = 2;
// Taper ratio: the node-side slice is always this fraction of BAR_H at most.
// This ensures even a single-source ribbon visibly narrows as it enters the node.
const NODE_TAPER = 0.45;

// Column x positions
// Income bar sits at INC_BAR_X; ribbon runs from bar's RIGHT edge to NODE_L
const INC_BAR_X = 110;
const INC_LBL_X = INC_BAR_X - 5; // label right-edge
const INC_RIB_L = INC_BAR_X + BAR_W; // 120 — ribbon departs bar's right edge

// Central node
const NODE_L = 215;
const NODE_R = 345;
const NODE_W = NODE_R - NODE_L; // 130
const NODE_H = 36;
const CENTER_X = (NODE_L + NODE_R) / 2; // 280

// Expense bar sits at EXP_BAR_X; ribbon runs from NODE_R to bar's LEFT edge
const EXP_BAR_X = SVG_W - 110 - BAR_W; // 440
const EXP_RIB_R = EXP_BAR_X; // 440 — ribbon arrives at bar's left edge
const EXP_LBL_X = EXP_BAR_X + BAR_W + 5; // 455

// Surplus indicator
const SURPLUS_H = 13;
const SURPLUS_GAP = 14;
const SURPLUS_MAX = SVG_W - EXP_BAR_X - 4;

// ─── Types ─────────────────────────────────────────────────────────────────────
interface FlowNodeLayout extends ComparedFlowNode {
  color: string;
  barY: number; // top of fixed-height bar
  isOther?: boolean;
}

// ─── Stack helper ─────────────────────────────────────────────────────────────
/** Place fixed-height bars centred on centerY with ROW_H pitch. */
function stackBars(
  nodes: Array<ComparedFlowNode & { color: string }>,
  centerY: number
): FlowNodeLayout[] {
  if (!nodes.length) return [];
  const blockH = nodes.length * BAR_H + (nodes.length - 1) * (ROW_H - BAR_H);
  let y = centerY - blockH / 2;
  return nodes.map(n => {
    const out = { ...n, barY: y };
    y += ROW_H;
    return out;
  });
}

/**
 * Build proportional fan slices centred on centerY at the node side.
 * Each slice height ∝ amount, with two constraints:
 *   1. Never exceeds NODE_TAPER * BAR_H — always narrower than the bar side,
 *      so every ribbon visibly tapers/converges into the node.
 *   2. Total fan height stays within NODE_FAN_H.
 */
function buildFan(
  nodes: Array<{ amount: number }>,
  total: number,
  centerY: number
): Array<{ t: number; b: number }> {
  const n = nodes.length;
  if (!n) return [];
  const gaps = Math.max(0, n - 1) * NODE_FAN_GAP;
  const fill = NODE_FAN_H - gaps; // total fill budget

  // Max node-side thickness per ribbon — always narrower than bar (NODE_TAPER < 1)
  const maxSliceH = BAR_H * NODE_TAPER;

  // Proportional heights, capped at maxSliceH
  const rawH = nodes.map(node =>
    node.amount === 0
      ? 0
      : Math.min(maxSliceH, Math.max(1, (node.amount / Math.max(total, 1)) * fill))
  );
  const totalRaw = rawH.reduce((s, h) => s + h, 0);
  const totalHeight = totalRaw + gaps;

  let cur = centerY - totalHeight / 2;
  return rawH.map(h => {
    const t = cur;
    cur += h + NODE_FAN_GAP;
    return { t, b: t + h };
  });
}

/**
 * Cubic-bezier filled ribbon between two vertical segments.
 */
function ribbonPath(
  x1: number,
  y1t: number,
  y1b: number,
  x2: number,
  y2t: number,
  y2b: number,
  tension = 0.45
): string {
  const dx = x2 - x1;
  const cx1 = x1 + dx * tension;
  const cx2 = x2 - dx * tension;
  return [
    `M ${x1} ${y1t}`,
    `C ${cx1} ${y1t}, ${cx2} ${y2t}, ${x2} ${y2t}`,
    `L ${x2} ${y2b}`,
    `C ${cx2} ${y2b}, ${cx1} ${y1b}, ${x1} ${y1b}`,
    'Z',
  ].join(' ');
}

// ─── Main component ────────────────────────────────────────────────────────────
interface CashflowSankeyCardProps {
  currency?: string;
  /** Period in days — driven by the global PeriodSelector in DashboardPage */
  period?: number;
  /** Shared compact caption from the global selector, e.g. "last 7d" or "31d". */
  periodLabel?: string;
  /** Optional explicit date range — takes precedence over `period` */
  dateRange?: DateRange;
  /** Resolved by the global selector, preserving calendar versus rolling semantics. */
  comparisonDateRange?: DateRange | null;
  navDateRange?: DateRange;
  isAllTime?: boolean;
}

export default function CashflowSankeyCard({
  currency = DEFAULT_CURRENCY,
  period = 30,
  periodLabel,
  dateRange,
  comparisonDateRange,
  navDateRange,
  isAllTime = false,
}: CashflowSankeyCardProps) {
  const { t, i18n } = useTranslation('dashboard');
  const { date: formatDate } = useDateFormatter();
  const { isAmountsVisible } = useVisibility();
  const isMobile = useIsMobile();
  const { format } = useFormatCurrency();
  const navigate = useNavigate();
  const gradId = useId().replace(/:/g, '');

  const navToType = useCallback(
    (type: 'INCOME' | 'EXPENSE') =>
      navigate(buildTransactionsLink({ type, excludeTransfers: true, dateRange: navDateRange })),
    [navigate, navDateRange]
  );

  const navToFlow = useCallback(
    (node: ICashflowSankeyNode, type: 'INCOME' | 'EXPENSE') => {
      if (node.categoryId != null) {
        navigate(
          buildTransactionsLink({
            categoryId: node.categoryId,
            excludeTransfers: true,
            type,
            dateRange: navDateRange,
          })
        );
      } else {
        navigate(
          buildTransactionsLink({
            noCategory: true,
            excludeTransfers: true,
            type,
            dateRange: navDateRange,
          })
        );
      }
    },
    [navigate, navDateRange]
  );

  const [showVariations, setShowVariations] = useState(true);
  const currentRange = dateRange ?? periodToDateRange(period);
  const selectedPeriodLabel =
    periodLabel ??
    (isAllTime
      ? t('period.allTime')
      : t(dateRange ? 'period.days' : 'period.lastNDays', {
          days: dateRange ? dateRangeToDays(dateRange) : period,
        }));
  const previousRange =
    comparisonDateRange ??
    (dateRange ? previousSankeyPeriod(currentRange) : previousEqualPeriod(currentRange));
  const { data, isLoading, error } = useCashflowSankey(
    period,
    isAllTime ? dateRange : currentRange
  );
  const comparison = useCashflowSankey(period, previousRange, showVariations && !isAllTime);
  const previousData =
    showVariations && !isAllTime && !comparison.error ? comparison.data : undefined;
  const showComparison = previousData != null && isAmountsVisible;
  const percentFormatter = useMemo(
    () =>
      new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1, signDisplay: 'exceptZero' }),
    [i18n.language]
  );

  // ── Layout computation ──────────────────────────────────────────────────────
  const layout = useMemo(() => {
    if (!data) return null;

    function collapseNodes(
      nodes: ComparedFlowNode[],
      palette: string[]
    ): Array<ComparedFlowNode & { color: string }> {
      const sorted = [...nodes].sort((a, b) => b.amount - a.amount);
      if (sorted.length <= MAX_ROWS) {
        return sorted.map((n, i) => ({ ...n, color: n.color ?? palette[i % palette.length] }));
      }
      const top = sorted
        .slice(0, MAX_ROWS - 1)
        .map((n, i) => ({ ...n, color: n.color ?? palette[i % palette.length] }));
      const rest = sorted.slice(MAX_ROWS - 1);
      const otherLabel = t('cashflowSankey.other');
      return [
        ...top,
        {
          name: otherLabel,
          amount: sum(rest.map(n => n.amount)),
          previousAmount: previousData ? sum(rest.map(n => n.previousAmount ?? 0)) : undefined,
          color: '#6b7280',
          icon: null,
          categoryId: null,
          isOther: true,
        } as ComparedFlowNode & { color: string },
      ];
    }

    const translateNode = (n: ComparedFlowNode) => {
      let name = n.name;
      if (name === 'Uncategorized' || name === 'uncategorized')
        name = t('cashflowSankey.uncategorized');
      if (name === 'UNSPECIFIED' || name === 'unspecified') name = t('cashflowSankey.unspecified');
      return { ...n, name };
    };

    const incNodes = collapseNodes(
      compareFlowNodes(data.incomeSources, previousData?.incomeSources).map(translateNode),
      INCOME_COLORS
    );
    const expNodes = collapseNodes(
      compareFlowNodes(data.expenseCategories, previousData?.expenseCategories).map(translateNode),
      EXPENSE_COLORS
    );

    // SVG height: fit the taller column + padding + optional surplus zone
    const nRows = Math.max(incNodes.length, expNodes.length, 1);
    const colH = nRows * ROW_H - (ROW_H - BAR_H); // exact stack block height
    const PAD_TOP = 28;
    const PAD_BOT = 28;
    const extraBot = data.surplus !== 0 ? SURPLUS_H + SURPLUS_GAP + 18 : 0;
    const svgH = PAD_TOP + colH + PAD_BOT + extraBot;

    // Vertical centre of the flow area (excluding surplus zone)
    const midY = PAD_TOP + colH / 2;

    // Stack bars
    const incLayout = stackBars(incNodes, midY);
    const expLayout = stackBars(expNodes, midY);

    // Build fans (compact, proportional slices at node, centred on midY)
    const incTotal = sum(incNodes.map(n => n.amount)) || 1;
    const expTotal = sum(expNodes.map(n => n.amount)) || 1;
    const incFan = buildFan(incNodes, incTotal, midY);
    const expFan = buildFan(expNodes, expTotal, midY);

    // Surplus bar Y (below last expense bar)
    const lastExp = expLayout[expLayout.length - 1];
    const surplusY = lastExp ? lastExp.barY + BAR_H + SURPLUS_GAP : PAD_TOP + colH + 10;

    return { incLayout, expLayout, incFan, expFan, svgH, midY, surplusY };
  }, [data, previousData, t]);

  // ── Loading ─────────────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col">
        <div className="flex items-center justify-between mb-4">
          <div className="h-5 w-40 bg-surface-elevated rounded animate-pulse" />
          <div className="h-7 w-16 bg-surface-elevated rounded animate-pulse" />
        </div>
        <div className="flex-1 bg-surface-elevated rounded animate-pulse" />
      </div>
    );
  }

  if (error || !data || !layout) {
    return (
      <div className="bg-surface rounded-lg p-6 border border-border h-full flex flex-col items-center justify-center">
        <p className="text-text-secondary text-sm">{t('cashflowSankey.noData')}</p>
      </div>
    );
  }

  const { incLayout, expLayout, incFan, expFan, svgH, midY, surplusY } = layout;
  const hasSurplus = data.surplus > 0;
  const surplusColor = hasSurplus ? SURPLUS_COLOR : DEFICIT_COLOR;

  const surplusRatio = Math.min(Math.abs(data.surplus) / Math.max(data.totalExpenses, 1), 1);
  const surplusBarW = Math.max(BAR_W * 2, surplusRatio * SURPLUS_MAX);

  // Bar centre-Y
  const barCY = (n: FlowNodeLayout) => n.barY + BAR_H / 2;

  const renderVariation = (node: FlowNodeLayout, type: 'INCOME' | 'EXPENSE', html = false) => {
    if (!showComparison || node.previousAmount == null) return null;
    const { change, percent } = flowVariation(node.amount, node.previousAmount);
    const favorable = type === 'EXPENSE' ? change < 0 : change > 0;
    const color = change === 0 ? '#9ca3af' : favorable ? SURPLUS_COLOR : DEFICIT_COLOR;
    const signedAmount = `${change > 0 ? '+' : ''}${format(change, currency)}`;
    const percentSuffix = percent == null ? '' : `/${percentFormatter.format(percent)}%`;
    const label = ` (${signedAmount}${percentSuffix})`;
    return html ? <span style={{ color }}>{label}</span> : <tspan fill={color}>{label}</tspan>;
  };

  return (
    <div className="bg-surface rounded-lg p-4 border border-border h-full flex flex-col">
      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between gap-2 flex-wrap mb-3 pr-8 flex-shrink-0">
        <div className="flex items-center gap-2">
          <h3 className="plate-label ">{t('cashflowSankey.title')}</h3>
          <button
            type="button"
            onClick={() => setShowVariations(visible => !visible)}
            aria-pressed={showVariations && !isAllTime}
            disabled={isAllTime}
            title={t('cashflowSankey.toggleVariations')}
            className="inline-flex items-center justify-center rounded-md p-1 text-text-secondary hover:text-text-primary hover:bg-surface-elevated transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {showVariations && !isAllTime ? (
              <Eye className="h-4 w-4" />
            ) : (
              <EyeOff className="h-4 w-4" />
            )}
            <span className="sr-only">{t('cashflowSankey.toggleVariations')}</span>
          </button>
        </div>
        {/* Period label — read-only, driven by global selector */}
        <span className="text-xs text-text-secondary">
          <PeriodLabel range={isAllTime ? undefined : currentRange}>
            {selectedPeriodLabel}
          </PeriodLabel>
        </span>
      </div>

      {/* ── Summary pills ──────────────────────────────────────────────────── */}
      <div className="flex gap-3 mb-3 flex-shrink-0">
        <button
          type="button"
          onClick={() => navToType('INCOME')}
          className="flex-1 bg-success/10 rounded px-3 py-1.5 text-center hover:bg-success/20 transition-colors"
          aria-label={t('cashflowSankey.viewIncome')}
        >
          <div className="text-xs text-text-secondary mb-0.5">{t('cashflowSankey.income')}</div>
          <div className="text-sm font-bold text-success font-mono">
            <ConvertedAmount amount={data.totalIncome} currency={currency} inline />
          </div>
        </button>
        <button
          type="button"
          onClick={() => navToType('EXPENSE')}
          className="flex-1 bg-error/10 rounded px-3 py-1.5 text-center hover:bg-error/20 transition-colors"
          aria-label={t('cashflowSankey.viewExpenses')}
        >
          <div className="text-xs text-text-secondary mb-0.5">{t('cashflowSankey.expenses')}</div>
          <div className="text-sm font-bold text-error font-mono">
            <ConvertedAmount amount={data.totalExpenses} currency={currency} inline />
          </div>
        </button>
        <div
          className="flex-1 rounded px-3 py-1.5 text-center"
          style={{ background: hasSurplus ? 'rgba(16,185,129,0.1)' : 'rgba(239,68,68,0.1)' }}
        >
          <div className="text-xs text-text-secondary mb-0.5">
            {hasSurplus ? t('cashflowSankey.surplus') : t('cashflowSankey.deficit')}
          </div>
          <div className="text-sm font-bold font-mono" style={{ color: surplusColor }}>
            {hasSurplus ? '+' : '-'}
            <ConvertedAmount amount={Math.abs(data.surplus)} currency={currency} inline />
          </div>
        </div>
      </div>

      <div className="flex justify-end mb-2 text-xs text-text-secondary">
        {isAllTime ? (
          <span>{t('cashflowSankey.noComparisonPeriod')}</span>
        ) : (
          <span
            role="status"
            aria-hidden={!showVariations}
            className={showVariations ? undefined : 'invisible'}
          >
            {comparison.error
              ? t('cashflowSankey.comparisonUnavailable')
              : comparison.isLoading
                ? t('cashflowSankey.loadingComparison')
                : t('cashflowSankey.comparedWith', {
                    from: formatDate(previousRange.from),
                    to: formatDate(previousRange.to),
                  })}
          </span>
        )}
      </div>

      <SankeyViewport>
        <svg
          viewBox={`-${LABEL_MARGIN} 0 ${SVG_W + LABEL_MARGIN * 2} ${svgH}`}
          width="100%"
          height="100%"
          preserveAspectRatio="xMidYMid meet"
          style={{ display: 'block' }}
        >
          <defs>
            {/* Income ribbons: vivid at bar → fade to near-transparent at node */}
            {incLayout.map((n, i) => (
              <linearGradient key={`ig${i}`} id={`${gradId}ig${i}`} x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor={n.color} stopOpacity={1.0} />
                <stop offset="50%" stopColor={n.color} stopOpacity={0.55} />
                <stop offset="85%" stopColor={n.color} stopOpacity={0.18} />
                <stop offset="100%" stopColor={n.color} stopOpacity={0.05} />
              </linearGradient>
            ))}
            {/* Expense ribbons: near-transparent at node → vivid at bar */}
            {expLayout.map((n, i) => (
              <linearGradient key={`eg${i}`} id={`${gradId}eg${i}`} x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stopColor={n.color} stopOpacity={0.05} />
                <stop offset="15%" stopColor={n.color} stopOpacity={0.18} />
                <stop offset="50%" stopColor={n.color} stopOpacity={0.55} />
                <stop offset="100%" stopColor={n.color} stopOpacity={1.0} />
              </linearGradient>
            ))}
            {/* Surplus/Deficit fill */}
            <linearGradient id={`${gradId}sur`} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor={surplusColor} stopOpacity={0.4} />
              <stop offset="100%" stopColor={surplusColor} stopOpacity={0.9} />
            </linearGradient>
          </defs>

          {/* ── Income ribbons (bar-right+gap → NODE_L fan) ───────────────── */}
          {incLayout.map((n, i) => (
            <path
              key={`ir${i}`}
              d={ribbonPath(INC_RIB_L, n.barY, n.barY + BAR_H, NODE_L, incFan[i].t, incFan[i].b)}
              fill={`url(#${gradId}ig${i})`}
              visibility={n.amount === 0 ? 'hidden' : undefined}
              className="cursor-pointer"
              onClick={() => {
                if (!n.isOther) navToFlow(n, 'INCOME');
              }}
            />
          ))}

          {/* ── Expense ribbons (NODE_R fan → bar-left-gap) ───────────────── */}
          {expLayout.map((n, i) => (
            <path
              key={`er${i}`}
              d={ribbonPath(NODE_R, expFan[i].t, expFan[i].b, EXP_RIB_R, n.barY, n.barY + BAR_H)}
              fill={`url(#${gradId}eg${i})`}
              visibility={n.amount === 0 ? 'hidden' : undefined}
              className="cursor-pointer"
              onClick={() => {
                if (!n.isOther) navToFlow(n, 'EXPENSE');
              }}
            />
          ))}

          {/* ── Income bars (on top of ribbons, with background blocker) ─── */}
          {incLayout.map((n, i) => {
            const cy = barCY(n);
            return (
              <g
                key={`ib${i}`}
                role="button"
                tabIndex={0}
                className="cursor-pointer"
                aria-label={n.isOther ? undefined : t('cashflowSankey.viewFlow', { name: n.name })}
                onClick={() => {
                  if (!n.isOther) navToFlow(n, 'INCOME');
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    if (!n.isOther) navToFlow(n, 'INCOME');
                  }
                }}
              >
                {/* Dark background rect to block ribbon bleed */}
                <rect
                  x={INC_BAR_X - 2}
                  y={n.barY - 1}
                  width={BAR_W + 4}
                  height={BAR_H + 2}
                  fill="#111827"
                  rx={3}
                />
                <rect x={INC_BAR_X} y={n.barY} width={BAR_W} height={BAR_H} fill={n.color} rx={2} />
                <text
                  x={INC_LBL_X}
                  y={cy - 3}
                  textAnchor="end"
                  dominantBaseline="auto"
                  fontSize={10}
                  fill="#e5e7eb"
                  fontWeight={500}
                >
                  <title>{n.name}</title>
                  {n.name.length > 20 ? n.name.slice(0, 19) + '…' : n.name}
                  {renderVariation(n, 'INCOME')}
                </text>
                <text
                  x={INC_LBL_X}
                  y={cy + 4}
                  textAnchor="end"
                  dominantBaseline="hanging"
                  fontSize={9}
                  fill="#9ca3af"
                >
                  {isAmountsVisible ? format(n.amount, currency) : '••••'}
                </text>
              </g>
            );
          })}

          {/* ── Expense bars (on top of ribbons, with background blocker) ── */}
          {expLayout.map((n, i) => {
            const cy = barCY(n);
            return (
              <g
                key={`eb${i}`}
                role="button"
                tabIndex={0}
                className="cursor-pointer"
                aria-label={n.isOther ? undefined : t('cashflowSankey.viewFlow', { name: n.name })}
                onClick={() => {
                  if (!n.isOther) navToFlow(n, 'EXPENSE');
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    if (!n.isOther) navToFlow(n, 'EXPENSE');
                  }
                }}
              >
                {/* Dark background rect to block ribbon bleed */}
                <rect
                  x={EXP_BAR_X - 2}
                  y={n.barY - 1}
                  width={BAR_W + 4}
                  height={BAR_H + 2}
                  fill="#111827"
                  rx={3}
                />
                <rect x={EXP_BAR_X} y={n.barY} width={BAR_W} height={BAR_H} fill={n.color} rx={2} />
                <text
                  x={EXP_LBL_X}
                  y={cy - 3}
                  textAnchor="start"
                  dominantBaseline="auto"
                  fontSize={10}
                  fill="#e5e7eb"
                  fontWeight={500}
                >
                  <title>{n.name}</title>
                  {n.name.length > 20 ? n.name.slice(0, 19) + '…' : n.name}
                  {renderVariation(n, 'EXPENSE')}
                </text>
                <text
                  x={EXP_LBL_X}
                  y={cy + 4}
                  textAnchor="start"
                  dominantBaseline="hanging"
                  fontSize={9}
                  fill="#9ca3af"
                >
                  {isAmountsVisible ? format(n.amount, currency) : '••••'}
                </text>
              </g>
            );
          })}

          {/* ── Surplus / Deficit indicator ────────────────────────────────── */}
          {data.surplus !== 0 &&
            (() => {
              const last = expLayout[expLayout.length - 1];
              const lineX = EXP_BAR_X + BAR_W / 2;
              return (
                <g>
                  {last && (
                    <line
                      x1={lineX}
                      y1={last.barY + BAR_H}
                      x2={lineX}
                      y2={surplusY}
                      stroke={surplusColor}
                      strokeWidth={1}
                      strokeDasharray="3 3"
                      strokeOpacity={0.4}
                    />
                  )}
                  <rect
                    x={EXP_BAR_X}
                    y={surplusY}
                    width={SURPLUS_MAX}
                    height={SURPLUS_H}
                    fill={surplusColor}
                    fillOpacity={0.07}
                    rx={3}
                  />
                  <rect
                    x={EXP_BAR_X}
                    y={surplusY}
                    width={surplusBarW}
                    height={SURPLUS_H}
                    fill={`url(#${gradId}sur)`}
                    rx={3}
                  />
                  <text
                    x={EXP_BAR_X + surplusBarW / 2}
                    y={surplusY + SURPLUS_H / 2}
                    textAnchor="middle"
                    dominantBaseline="middle"
                    fontSize={8}
                    fill="white"
                    fontWeight={700}
                    opacity={0.92}
                  >
                    {hasSurplus
                      ? `▲ ${t('cashflowSankey.surplus')}`
                      : `▼ ${t('cashflowSankey.deficit')}`}{' '}
                    {isAmountsVisible ? format(Math.abs(data.surplus), currency) : '••••'}
                  </text>
                </g>
              );
            })()}

          {/* ── Central CASH FLOW node (top layer) ─────────────────────────── */}
          <rect
            x={NODE_L}
            y={midY - NODE_H / 2}
            width={NODE_W}
            height={NODE_H}
            rx={6}
            fill="#1e2d3d"
            stroke="#4b6280"
            strokeWidth={1.5}
          />
          <text
            x={CENTER_X}
            y={midY}
            textAnchor="middle"
            dominantBaseline="middle"
            fontSize={9}
            fill="#cbd5e1"
            fontWeight={700}
            letterSpacing={1.5}
          >
            {t('cashflowSankey.centerLabel')}
          </text>
        </svg>
      </SankeyViewport>

      {isMobile && (
        <div
          role="list"
          aria-label={t('cashflowSankey.categoryDetails')}
          className="h-48 shrink-0 overflow-y-auto border-t border-border mt-2 pt-2 text-xs"
        >
          {(['INCOME', 'EXPENSE'] as const).map(type => (
            <div key={type} role="listitem">
              <p className="text-text-secondary font-semibold py-1">
                {t(type === 'INCOME' ? 'cashflowSankey.income' : 'cashflowSankey.expenses')}
              </p>
              {(type === 'INCOME' ? incLayout : expLayout).map(node => (
                <button
                  key={node.isOther ? 'other' : (node.categoryId ?? 'uncategorized')}
                  type="button"
                  disabled={node.isOther}
                  onClick={() => navToFlow(node, type)}
                  aria-label={t('cashflowSankey.viewFlow', { name: node.name })}
                  className="w-full flex flex-wrap items-baseline gap-x-1 py-1 text-left hover:bg-surface-elevated rounded"
                >
                  <span
                    className="inline-block w-2 h-2 rounded-sm shrink-0"
                    style={{ backgroundColor: node.color }}
                  />
                  <span>{node.name}</span>
                  {renderVariation(node, type, true)}
                  <span className="ml-auto text-text-secondary font-mono">
                    {isAmountsVisible ? format(node.amount, currency) : '••••'}
                  </span>
                </button>
              ))}
            </div>
          ))}
        </div>
      )}

      {/* ── Zoom hint ──────────────────────────────────────────────────────── */}
      <p className="text-center text-xs text-text-muted mt-1 flex-shrink-0 select-none">
        {t('cashflowSankey.zoomHint')}
      </p>
    </div>
  );
}
