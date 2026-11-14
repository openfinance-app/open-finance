import { describe, it, expect } from 'vitest';
import { historyFieldChanges } from './history-changes';

describe('historyFieldChanges', () => {
  it('returns an empty array for missing input', () => {
    expect(historyFieldChanges()).toEqual([]);
    expect(historyFieldChanges(undefined)).toEqual([]);
    expect(historyFieldChanges('')).toEqual([]);
  });

  it('returns an empty array for malformed JSON instead of throwing', () => {
    expect(() => historyFieldChanges('{oops')).not.toThrow();
    expect(historyFieldChanges('{oops')).toEqual([]);
  });

  it('returns an empty array when the payload is not a plain object', () => {
    expect(historyFieldChanges('[]')).toEqual([]);
    expect(historyFieldChanges('[{"before":1}]')).toEqual([]);
    expect(historyFieldChanges('"text"')).toEqual([]);
    expect(historyFieldChanges('42')).toEqual([]);
    expect(historyFieldChanges('null')).toEqual([]);
    expect(historyFieldChanges('true')).toEqual([]);
    expect(historyFieldChanges('{}')).toEqual([]);
  });

  it('extracts before/after pairs per field, preserving field order', () => {
    const json = JSON.stringify({
      amount: { before: '10.00', after: '12.00' },
      memo: { before: 'old', after: 'new' },
    });
    expect(historyFieldChanges(json)).toEqual([
      { field: 'amount', before: '10.00', after: '12.00' },
      { field: 'memo', before: 'old', after: 'new' },
    ]);
  });

  it('fills the missing side with null for created/deleted fields', () => {
    expect(historyFieldChanges('{"a":{"after":2},"b":{"before":1}}')).toEqual([
      { field: 'a', before: null, after: 2 },
      { field: 'b', before: 1, after: null },
    ]);
  });

  it('drops entries that are not change objects', () => {
    const json = JSON.stringify({
      scalar: 5,
      text: 'x',
      nothing: null,
      list: [1, 2],
      unrelated: { other: true },
      real: { before: 1, after: 2 },
    });
    expect(historyFieldChanges(json)).toEqual([{ field: 'real', before: 1, after: 2 }]);
  });

  it('passes structured before/after values through unchanged', () => {
    const json = JSON.stringify({
      splits: { before: [{ amount: '5' }], after: [{ amount: '7' }] },
    });
    expect(historyFieldChanges(json)).toEqual([
      { field: 'splits', before: [{ amount: '5' }], after: [{ amount: '7' }] },
    ]);
  });

  it('keeps falsy-but-real values such as 0, "" and false', () => {
    expect(historyFieldChanges('{"a":{"before":0,"after":""},"b":{"before":false}}')).toEqual([
      { field: 'a', before: 0, after: '' },
      { field: 'b', before: false, after: null },
    ]);
  });
});
