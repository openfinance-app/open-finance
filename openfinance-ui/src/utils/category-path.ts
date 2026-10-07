export interface CategoryPathNode {
  id: number;
  name: string;
  canonicalName?: string;
  parentId?: number | null;
}

/** Resolve exact paths, or bare names only when the caller can disambiguate the matches. */
export function matchingCategories<T extends CategoryPathNode>(categories: T[], path: string): T[] {
  const normalize = (value: string) =>
    value
      .split(':')
      .map(part => part.trim().toLowerCase())
      .join(':');
  const requested = normalize(path);
  return categories.filter(category =>
    (path.includes(':')
      ? [categoryPath(categories, category.id), categoryPath(categories, category.id, 'display')]
      : [category.canonicalName || category.name, category.name]
    ).some(name => normalize(name) === requested)
  );
}

/** Use the full canonical path when a rule persists a category reference by name. */
export function categoryPath(
  categories: CategoryPathNode[],
  id?: number,
  names: 'canonical' | 'display' = 'canonical'
): string {
  const segments: string[] = [];
  const visited = new Set<number>();
  let category = categories.find(candidate => candidate.id === id);
  while (category && !visited.has(category.id)) {
    visited.add(category.id);
    segments.unshift(names === 'display' ? category.name : category.canonicalName || category.name);
    category = categories.find(candidate => candidate.id === category?.parentId);
  }
  return segments.join(':');
}
