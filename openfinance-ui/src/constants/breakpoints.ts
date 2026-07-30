/**
 * Centralized breakpoint constants for behavior that can't use `useBreakpoint`'s
 * media-query-driven boolean hooks (e.g. one-off `window.innerWidth` checks inside event
 * handlers, and third-party libraries with their own breakpoint scale).
 */

/**
 * Viewport width (px) above which paired accordion panels stay in sync when one is toggled
 * (e.g. purchase/rental, market/resale in the buy-vs-rent and rental-simulator tools). Matches
 * Tailwind's `lg` breakpoint, same as `useBreakpoint.ts`.
 */
export const ACCORDION_SYNC_BREAKPOINT = 1024;

/**
 * Narrower accordion-sync breakpoint used by `RegimeComparisonGrid`, whose comparison cards are
 * narrower than the buy-vs-rent tool's panels and so sync at a smaller viewport width (Tailwind
 * `md`). Intentionally distinct from {@link ACCORDION_SYNC_BREAKPOINT} — not a bug.
 */
export const ACCORDION_SYNC_BREAKPOINT_NARROW = 768;

/**
 * `react-grid-layout`'s `breakpoints`/`cols` config for the draggable dashboard card grid
 * (`DashboardPage`). This is a library-specific scale (already distinct from both Tailwind's and
 * `useBreakpoint.ts`'s scales, e.g. `lg: 1200` here vs `lg: 1024` elsewhere, plus an `xxs` tier
 * neither scale has) and cannot be derived from `useBreakpoint` — `react-grid-layout` requires
 * its own paired breakpoints+cols configuration.
 */
export const GRID_LAYOUT_BREAKPOINTS = { lg: 1200, md: 996, sm: 768, xs: 480, xxs: 0 } as const;

/** Column counts paired 1:1 with {@link GRID_LAYOUT_BREAKPOINTS} (same key order). */
export const GRID_LAYOUT_COLS = { lg: 12, md: 10, sm: 6, xs: 4, xxs: 2 } as const;
