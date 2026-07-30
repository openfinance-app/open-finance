/**
 * Centralized chart/UI color palettes.
 *
 * Consolidates hex-color maps and arrays that were previously duplicated or scattered across
 * components — most notably `ASSET_TYPE_COLORS`, which was defined identically in both
 * `utils/portfolio.ts` and `components/assets/AssetAllocationChart.tsx`.
 *
 * Scope: this module covers "palette" groups (maps/arrays shared or duplicated across files).
 * One-off single hex literals used decoratively in individual calculator/chart components are
 * intentionally left as-is — they aren't duplicated and converting them carries a
 * disproportionate visual-regression risk for the dedup benefit.
 */

/** Asset-type → color map used by portfolio charts (dashboard pie chart, allocation chart). */
export const ASSET_TYPE_COLORS: Record<string, string> = {
  STOCK: '#3b82f6', // blue
  ETF: '#8b5cf6', // purple
  CRYPTO: '#f59e0b', // amber
  BOND: '#10b981', // green
  MUTUAL_FUND: '#ec4899', // pink
  REAL_ESTATE: '#06b6d4', // cyan
  COMMODITY: '#f97316', // orange
  OTHER: '#6b7280', // gray
};

/** Fallback color cycle for asset types not present in {@link ASSET_TYPE_COLORS}. */
export const ASSET_TYPE_COLOR_FALLBACKS: string[] = Object.values(ASSET_TYPE_COLORS);

/** Colors for {@link import('@/types/alert').AlertSeverity} (budget-alert severity). */
export const ALERT_SEVERITY_COLORS = {
  exceeded: '#ef4444', // red-500
  critical: '#f59e0b', // amber-500
  warning: '#eab308', // yellow-500
} as const;

/** Colors for {@link import('@/types/notification').NotificationSeverity}. */
export const NOTIFICATION_SEVERITY_COLORS = {
  CRITICAL: '#ef4444', // red-500
  WARNING: '#f59e0b', // amber-500
  INFO: '#3b82f6', // blue-500
} as const;

/** Swatch palette offered in the category color picker. */
export const CATEGORY_COLOR_SWATCHES: string[] = [
  '#EF4444',
  '#F97316',
  '#F59E0B',
  '#84CC16',
  '#22C55E',
  '#14B8A6',
  '#06B6D4',
  '#3B82F6',
  '#6366F1',
  '#8B5CF6',
  '#A855F7',
  '#EC4899',
  '#F43F5E',
  '#6B7280',
  '#10B981',
];

/** Fallback swatch color when a category has none set. */
export const CATEGORY_COLOR_FALLBACK = '#6B7280';

/** Cashflow Sankey diagram — income-side ribbon color cycle. */
export const CASHFLOW_INCOME_COLORS: string[] = [
  '#10b981',
  '#34d399',
  '#6ee7b7',
  '#a7f3d0',
  '#059669',
];

/** Cashflow Sankey diagram — expense-side ribbon color cycle. */
export const CASHFLOW_EXPENSE_COLORS: string[] = [
  '#ec4899',
  '#14b8a6',
  '#3b82f6',
  '#f59e0b',
  '#8b5cf6',
  '#ef4444',
  '#6b7280',
  '#f97316',
  '#60a5fa',
  '#a78bfa',
];

/** Cashflow Sankey diagram — net surplus/deficit bar colors. */
export const CASHFLOW_SURPLUS_COLOR = '#10b981';
export const CASHFLOW_DEFICIT_COLOR = '#ef4444';

/** Net-worth treemap — liability cell shades (darkest-to-lightest red). */
export const NET_WORTH_TREEMAP_LIABILITY_COLORS: string[] = ['#EF4444', '#DC2626', '#B91C1C'];

/** Net-worth treemap — asset cell colors (diverse palette). */
export const NET_WORTH_TREEMAP_ASSET_COLORS: string[] = [
  '#10B981',
  '#3B82F6',
  '#8B5CF6',
  '#F59E0B',
  '#EC4899',
];

/**
 * Sparkline trend colors, expressed as CSS custom-property references so they automatically
 * follow the active theme (matches `--color-success` / `--color-text-muted` / `--color-error` in
 * `index.css`, which these hex values were previously hardcoded duplicates of).
 */
export const SPARKLINE_COLORS = {
  positive: 'var(--color-success)',
  neutral: 'var(--color-text-muted)',
  negative: 'var(--color-error)',
} as const;
