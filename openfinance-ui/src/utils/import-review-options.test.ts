import { describe, it, expect } from 'vitest';
import { readImportReviewOptions } from './import-review-options';

const DEFAULTS = { categoryMappings: {}, skipDuplicates: true };

describe('readImportReviewOptions', () => {
  it('returns defaults when metadata is absent or empty', () => {
    expect(readImportReviewOptions()).toEqual(DEFAULTS);
    expect(readImportReviewOptions(undefined)).toEqual(DEFAULTS);
    expect(readImportReviewOptions('')).toEqual(DEFAULTS);
  });

  it('returns defaults for malformed JSON instead of throwing', () => {
    expect(() => readImportReviewOptions('{not json')).not.toThrow();
    expect(readImportReviewOptions('{not json')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('undefined')).toEqual(DEFAULTS);
  });

  it('returns defaults when the payload is not an object with reviewOptions', () => {
    expect(readImportReviewOptions('null')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('"text"')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('42')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('[1,2]')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('{}')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('{"other":{}}')).toEqual(DEFAULTS);
  });

  it('returns defaults when reviewOptions itself is not an object', () => {
    expect(readImportReviewOptions('{"reviewOptions":null}')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('{"reviewOptions":42}')).toEqual(DEFAULTS);
    expect(readImportReviewOptions('{"reviewOptions":"x"}')).toEqual(DEFAULTS);
  });

  it('reads valid options', () => {
    const metadata = JSON.stringify({
      reviewOptions: { categoryMappings: { Food: 3, Fees: 7 }, skipDuplicates: false },
    });
    expect(readImportReviewOptions(metadata)).toEqual({
      categoryMappings: { Food: 3, Fees: 7 },
      skipDuplicates: false,
    });
  });

  it('keeps only positive safe-integer category ids', () => {
    const metadata = JSON.stringify({
      reviewOptions: {
        categoryMappings: {
          keep: 5,
          zero: 0,
          negative: -2,
          fractional: 1.5,
          stringId: '9',
          nullId: null,
          unsafe: Number.MAX_SAFE_INTEGER + 2,
        },
      },
    });
    expect(readImportReviewOptions(metadata).categoryMappings).toEqual({ keep: 5 });
  });

  it('ignores a categoryMappings value that is not a plain object', () => {
    for (const mappings of ['[1,2]', '"x"', '42', 'null']) {
      const result = readImportReviewOptions(`{"reviewOptions":{"categoryMappings":${mappings}}}`);
      expect(result.categoryMappings, mappings).toEqual({});
    }
    expect(readImportReviewOptions('{"reviewOptions":{}}').categoryMappings).toEqual({});
  });

  it('treats skipDuplicates as opt-out only on explicit false', () => {
    const wrap = (value: string) => `{"reviewOptions":{"skipDuplicates":${value}}}`;
    expect(readImportReviewOptions(wrap('false')).skipDuplicates).toBe(false);
    expect(readImportReviewOptions(wrap('true')).skipDuplicates).toBe(true);
    expect(readImportReviewOptions(wrap('0')).skipDuplicates).toBe(true);
    expect(readImportReviewOptions(wrap('"false"')).skipDuplicates).toBe(true);
    expect(readImportReviewOptions(wrap('null')).skipDuplicates).toBe(true);
    expect(readImportReviewOptions('{"reviewOptions":{}}').skipDuplicates).toBe(true);
  });

  it('returns a fresh object per call so callers cannot poison the defaults', () => {
    const first = readImportReviewOptions();
    first.categoryMappings.Poisoned = 1;
    expect(readImportReviewOptions().categoryMappings).toEqual({});
  });
});
