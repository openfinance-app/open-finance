import type { ImportReviewOptions } from '@/types/import';

/** Read optional review choices from encrypted session metadata returned after authentication. */
export function readImportReviewOptions(metadata?: string): ImportReviewOptions {
  const defaults: ImportReviewOptions = { categoryMappings: {}, skipDuplicates: true };
  if (!metadata) return defaults;
  try {
    const parsed: unknown = JSON.parse(metadata);
    if (!parsed || typeof parsed !== 'object' || !('reviewOptions' in parsed)) return defaults;
    const options = parsed.reviewOptions;
    if (!options || typeof options !== 'object') return defaults;
    const mappings = 'categoryMappings' in options ? options.categoryMappings : null;
    return {
      categoryMappings:
        mappings && typeof mappings === 'object' && !Array.isArray(mappings)
          ? Object.fromEntries(
              Object.entries(mappings).filter(([, id]) => Number.isSafeInteger(id) && id > 0)
            )
          : {},
      skipDuplicates: !('skipDuplicates' in options) || options.skipDuplicates !== false,
    };
  } catch {
    return defaults;
  }
}
