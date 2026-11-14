import { describe, it, expect } from 'vitest';
import { getImportReviewCounts } from './import-review';
import type { ImportTransactionDTO } from '@/types/import';

const tx = (overrides: Partial<ImportTransactionDTO> = {}): ImportTransactionDTO =>
  ({
    transactionDate: '2026-01-01',
    payee: 'Payee',
    amount: '10.00',
    currency: 'EUR',
    memo: null,
    category: null,
    clearedStatus: 'CLEARED',
    referenceNumber: null,
    accountName: null,
    lineNumber: 1,
    splits: [],
    validationErrors: [],
    potentialDuplicate: false,
    sourceFileName: 'file.csv',
    rawData: null,
    ...overrides,
  }) as ImportTransactionDTO;

describe('getImportReviewCounts', () => {
  it('returns zeros for an empty list', () => {
    expect(getImportReviewCounts([], true)).toEqual({
      importable: 0,
      categorized: 0,
      duplicates: 0,
      ruleSkipped: 0,
      invalid: 0,
    });
  });

  it('counts clean rows as importable and categorized ones separately', () => {
    const counts = getImportReviewCounts([tx(), tx({ category: 'Food' })], true);
    expect(counts).toMatchObject({ importable: 2, categorized: 1, invalid: 0 });
  });

  it('counts a row categorized only when every split has a category', () => {
    const allCategorized = tx({
      splits: [
        { category: 'Food', amount: '5', memo: '' },
        { category: 'Fees', amount: '5', memo: '' },
      ],
    });
    const partiallyCategorized = tx({
      splits: [
        { category: 'Food', amount: '5', memo: '' },
        { category: '', amount: '5', memo: '' },
      ],
    });
    const counts = getImportReviewCounts([allCategorized, partiallyCategorized], true);
    expect(counts.importable).toBe(2);
    expect(counts.categorized).toBe(1);
  });

  it('treats informational prefixes as importable, never invalid', () => {
    for (const prefix of [
      'AUTO-MATCH:',
      'AI_MATCH:',
      'AI_UNAVAILABLE:',
      'CATEGORY_SUGGESTION:',
      'CATEGORY_UNKNOWN:',
      'RULE_MATCH:',
    ]) {
      const counts = getImportReviewCounts([tx({ validationErrors: [`${prefix} detail`] })], true);
      expect(counts, prefix).toMatchObject({ importable: 1, invalid: 0 });
    }
  });

  it('counts unknown validation errors as invalid', () => {
    const counts = getImportReviewCounts(
      [
        tx({ validationErrors: ['AMOUNT_REQUIRED'] }),
        tx({ validationErrors: ['AUTO-MATCH: x', 'BAD_DATE'] }),
      ],
      true
    );
    expect(counts).toMatchObject({ invalid: 2, importable: 0 });
  });

  it('gives RULE_SKIP precedence over invalid and duplicate', () => {
    const counts = getImportReviewCounts(
      [
        tx({
          validationErrors: ['RULE_SKIP: skip-me', 'AMOUNT_REQUIRED'],
          potentialDuplicate: true,
        }),
      ],
      true
    );
    expect(counts).toMatchObject({ ruleSkipped: 1, invalid: 0, duplicates: 0, importable: 0 });
  });

  it('gives invalid precedence over duplicate', () => {
    const counts = getImportReviewCounts(
      [tx({ validationErrors: ['BAD_DATE'], potentialDuplicate: true })],
      true
    );
    expect(counts).toMatchObject({ invalid: 1, duplicates: 0 });
  });

  it('counts duplicates only when skipDuplicates is on', () => {
    const dup = tx({ potentialDuplicate: true, category: 'Food' });
    expect(getImportReviewCounts([dup], true)).toMatchObject({
      duplicates: 1,
      importable: 0,
      categorized: 0,
    });
    expect(getImportReviewCounts([dup], false)).toMatchObject({
      duplicates: 0,
      importable: 1,
      categorized: 1,
    });
  });

  it('detects duplicates from the DUPLICATE: marker even without the flag', () => {
    const counts = getImportReviewCounts(
      [tx({ validationErrors: ['DUPLICATE: matches #42'] })],
      true
    );
    expect(counts).toMatchObject({ duplicates: 1, invalid: 0 });
  });

  it('does not count DUPLICATE: rows as duplicates when skipping is off', () => {
    const counts = getImportReviewCounts(
      [tx({ validationErrors: ['DUPLICATE: matches #42'] })],
      false
    );
    expect(counts).toMatchObject({ duplicates: 0, importable: 1 });
  });

  it('tolerates rows with missing validationErrors or splits from older payloads', () => {
    const legacy = tx();
    delete (legacy as Partial<ImportTransactionDTO>).validationErrors;
    delete (legacy as Partial<ImportTransactionDTO>).splits;
    expect(() => getImportReviewCounts([legacy], true)).not.toThrow();
    expect(getImportReviewCounts([legacy], true)).toMatchObject({ importable: 1, categorized: 0 });
  });

  it('partitions every row into exactly one bucket', () => {
    const rows = [
      tx({ category: 'A' }),
      tx(),
      tx({ validationErrors: ['RULE_SKIP: x'] }),
      tx({ validationErrors: ['BAD'] }),
      tx({ potentialDuplicate: true }),
    ];
    const counts = getImportReviewCounts(rows, true);
    expect(counts.importable + counts.duplicates + counts.ruleSkipped + counts.invalid).toBe(
      rows.length
    );
    expect(counts.categorized).toBeLessThanOrEqual(counts.importable);
  });
});
