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
  STOCK: '#5e84b0', // steel blue
  ETF: '#8a80cc', // muted violet
  CRYPTO: '#c5a254', // aged brass
  BOND: '#3db28e', // verdigris
  MUTUAL_FUND: '#c96f7d', // mineral pink
  REAL_ESTATE: '#7d8fa3', // slate
  COMMODITY: '#c07a45', // burnt sienna
  VEHICLE: '#7ba3cc', // light steel blue
  JEWELRY: '#ddb15e', // pale brass
  COLLECTIBLE: '#9d94d6', // pale violet
  ELECTRONICS: '#5f97a5', // steel cyan
  FURNITURE: '#7ba88a', // sage
  OTHER: '#6f7884', // steel gray
};

/** Fallback color cycle for asset types not present in {@link ASSET_TYPE_COLORS}. */
export const ASSET_TYPE_COLOR_FALLBACKS: string[] = Object.values(ASSET_TYPE_COLORS);

/** Colors for {@link import('@/types/alert').AlertSeverity} (budget-alert severity). */
export const ALERT_SEVERITY_COLORS = {
  exceeded: '#e07368', // oxblood signal
  critical: '#d29a40', // aged amber
  warning: '#c5a254', // brass
} as const;

/** Colors for {@link import('@/types/notification').NotificationSeverity}. */
export const NOTIFICATION_SEVERITY_COLORS = {
  CRITICAL: '#e07368', // oxblood signal
  WARNING: '#d29a40', // aged amber
  INFO: '#5e84b0', // steel blue
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
  '#3db28e',
  '#5cc2a3',
  '#7dd3b8',
  '#a3e0cd',
  '#2e9d7c',
];

/** Cashflow Sankey diagram — expense-side ribbon color cycle. */
export const CASHFLOW_EXPENSE_COLORS: string[] = [
  '#c96f7d',
  '#45a191',
  '#5e84b0',
  '#c5a254',
  '#8a80cc',
  '#e07368',
  '#6f7884',
  '#c07a45',
  '#7ba3cc',
  '#9d94d6',
];

/** Cashflow Sankey diagram — net surplus/deficit bar colors. */
export const CASHFLOW_SURPLUS_COLOR = '#3db28e';
export const CASHFLOW_DEFICIT_COLOR = '#e07368';

/** Net-worth treemap — liability cell shades (darkest-to-lightest red). */
export const NET_WORTH_TREEMAP_LIABILITY_COLORS: string[] = ['#E07368', '#C75A4F', '#A84438'];

/** Net-worth treemap — asset cell colors (diverse palette). */
export const NET_WORTH_TREEMAP_ASSET_COLORS: string[] = [
  '#3DB28E',
  '#5E84B0',
  '#8A80CC',
  '#C5A254',
  '#C96F7D',
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
