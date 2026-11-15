import { describe, expect, it } from 'vitest';
import { matchingCategories } from '@/utils/category-path';

describe('matchingCategories', () => {
  const categories = [
    { id: 1, name: 'Maison', canonicalName: 'Home' },
    { id: 2, name: 'Ménage', canonicalName: 'Cleaning', parentId: 1 },
    { id: 3, name: 'Loisirs', canonicalName: 'Leisure' },
    { id: 4, name: 'Ménage', canonicalName: 'Cleaning', parentId: 3 },
  ];

  it.each(['Home:Cleaning', 'Maison:Ménage', 'Home > Cleaning', 'Maison > Ménage'])(
    'resolves a canonical or display path without confusing duplicate leaf names (%s)',
    path => {
      expect(matchingCategories(categories, path)).toEqual([categories[1]]);
    }
  );

  it('keeps ambiguous bare names available for the caller to reject', () => {
    expect(matchingCategories(categories, 'Ménage')).toEqual([categories[1], categories[3]]);
  });

  it.each(['Informatique', 'Computing', ' informatique '])(
    'prefers the exact root over a nested category with the same name (%s)',
    path => {
      const root = { id: 5, name: 'Informatique', canonicalName: 'Computing' };
      const nested = { ...root, id: 6, parentId: 3 };

      expect(matchingCategories([...categories, nested, root], path)).toEqual([root]);
    }
  );

  it.each(['Loisirs:Informatique', 'Leisure:Computing'])(
    'preserves an explicit nested path when its leaf name also exists at the root (%s)',
    path => {
      const root = { id: 5, name: 'Informatique', canonicalName: 'Computing', parentId: null };
      const nested = { ...root, id: 6, parentId: 3 };

      expect(matchingCategories([...categories, root, nested], path)).toEqual([nested]);
    }
  );

  it('preserves an exact category name containing a literal separator', () => {
    const literal = { id: 5, name: 'Home > Cleaning' };
    expect(matchingCategories([...categories, literal], literal.name)).toEqual([literal]);
  });
});
