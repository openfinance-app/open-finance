/**
 * Pagination constants — single source of truth for page sizes.
 *
 * Previously `DEFAULT_PAGE_SIZE = 20` and the `[10, 20, 50, 100]` options were redeclared
 * per-file, and the "fetch all" heuristic was a bare literal (`10000` in most pages, `1000` in
 * `LiabilitiesPage`).
 */

/** Default page size for paginated list views. */
export const DEFAULT_PAGE_SIZE = 20;

/** Page-size options offered in pagination controls. */
export const PAGE_SIZE_OPTIONS: number[] = [10, 20, 50, 100];

/**
 * "Fetch all" page size — a deliberately large size used to retrieve a full dataset in a single
 * request for client-side aggregation. Not truly unlimited, but far above realistic per-user
 * record counts, and applied consistently instead of ad-hoc `10000`/`1000` literals.
 */
export const FETCH_ALL_PAGE_SIZE = 10000;
