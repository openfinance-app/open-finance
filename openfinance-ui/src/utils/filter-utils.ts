/** Count criteria, never pagination or the view's default sort. Zero is a valid bound. */
export function countActiveFilters(values: unknown[]): number {
  return values.filter(
    value => value !== undefined && value !== null && value !== '' && value !== false
  ).length;
}
