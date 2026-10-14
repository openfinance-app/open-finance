import { describe, expect, it } from 'vitest';
import { compareFlowNodes, flowVariation, previousSankeyPeriod } from '@/utils/sankey-comparison';

describe('Sankey comparisons', () => {
  it.each([
    ['2026-10-08', '2026-10-14', '2026-10-01', '2026-10-07'],
    ['2026-01-01', '2026-01-01', '2025-12-31', '2025-12-31'],
    ['2024-02-28', '2024-03-01', '2024-02-25', '2024-02-27'],
    ['2026-03-28', '2026-03-30', '2026-03-25', '2026-03-27'],
  ])(
    'compares %s through %s with the adjacent inclusive period',
    (from, to, previousFrom, previousTo) => {
      expect(previousSankeyPeriod({ from, to })).toEqual({ from: previousFrom, to: previousTo });
    }
  );

  it('matches IDs and uncategorized without adding categories absent from the selected period', () => {
    expect(
      compareFlowNodes(
        [
          { categoryId: 1, name: 'Groceries', amount: 300 },
          { categoryId: 2, name: 'Groceries', amount: 50 },
          { categoryId: null, name: 'Uncategorized', amount: 20 },
        ],
        [
          { categoryId: 1, name: 'Food', amount: 200 },
          { categoryId: 3, name: 'Travel', amount: 100 },
          { name: 'Uncategorized', amount: 30 },
        ]
      )
    ).toEqual([
      { categoryId: 1, name: 'Groceries', amount: 300, previousAmount: 200 },
      { categoryId: 2, name: 'Groceries', amount: 50, previousAmount: 0 },
      { categoryId: null, name: 'Uncategorized', amount: 20, previousAmount: 30 },
    ]);
  });

  it.each([
    [300, 200, 100, 50],
    [100, 200, -100, -50],
    [0, 100, -100, -100],
    [100, 0, 100, null],
    [0, 0, 0, 0],
    [0.3, 0.2, 0.1, 50],
  ])(
    'calculates %s vs %s without zero-division or money rounding artifacts',
    (amount, previous, change, percent) => {
      expect(flowVariation(amount, previous)).toEqual({ change, percent });
    }
  );
});
