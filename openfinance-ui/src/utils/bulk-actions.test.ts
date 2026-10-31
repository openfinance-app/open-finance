import { beforeEach, describe, expect, it, vi } from 'vitest';
import apiClient from '@/services/apiClient';
import { mockAuthentication } from '@/test/test-utils';
import { runBulkAction } from '@/utils/bulk-actions';
import type { BulkResource } from '@/types/bulk';

vi.mock('@/services/apiClient');

describe('bulk requests', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
    vi.mocked(apiClient.delete).mockResolvedValue({ data: null });
  });

  it.each<BulkResource>([
    'assets',
    'accounts',
    'real-estate',
    'liabilities',
    'transactions',
    'recurring-transactions',
    'payees',
    'categories',
    'transaction-rules',
    'budgets',
  ])('uses the existing %s deletion contract', async resource => {
    await runBulkAction(
      resource,
      [{ id: 7, label: 'Selected record' }],
      undefined,
      vi.fn(),
      'Failed'
    );
    expect(apiClient.delete).toHaveBeenCalledWith(
      `/${resource}/7${resource === 'accounts' ? '/permanent' : ''}`,
      {
        headers: { 'X-Encryption-Session': expect.any(String) },
      }
    );
  });

  it('serializes requests and keeps going after a per-record failure', async () => {
    let finishFirst!: () => void;
    vi.mocked(apiClient.delete)
      .mockImplementationOnce(
        () =>
          new Promise(resolve => {
            finishFirst = () => resolve({ data: null });
          })
      )
      .mockRejectedValueOnce({
        isAxiosError: true,
        response: { data: { message: 'Linked payments' } },
      });
    const progress = vi.fn();
    const task = runBulkAction(
      'liabilities',
      [
        { id: 1, label: 'One' },
        { id: 2, label: 'Two' },
        { id: 3, label: 'Three' },
      ],
      undefined,
      progress,
      'Failed'
    );
    expect(apiClient.delete).toHaveBeenCalledTimes(1);
    finishFirst();
    const result = await task;
    expect(result.succeeded).toEqual([1, 3]);
    expect(result.failed).toEqual([{ item: { id: 2, label: 'Two' }, message: 'Linked payments' }]);
    expect(progress.mock.calls).toEqual([[1], [2], [3]]);
  });

  it('deletes a transfer once while accounting for both selected legs', async () => {
    const result = await runBulkAction(
      'transactions',
      [
        { id: 1, label: 'Out', transferId: 'pair' },
        { id: 2, label: 'In', transferId: 'pair' },
      ],
      undefined,
      vi.fn(),
      'Failed'
    );
    expect(apiClient.delete).toHaveBeenCalledTimes(1);
    expect(result.succeeded).toEqual([1, 2]);
  });

  it('deletes selected descendants before their parents', async () => {
    await runBulkAction(
      'categories',
      [
        { id: 1, label: 'Parent', depth: 0 },
        { id: 2, label: 'Child', depth: 1 },
        { id: 3, label: 'Grandchild', depth: 2 },
      ],
      undefined,
      vi.fn(),
      'Failed'
    );
    expect(vi.mocked(apiClient.delete).mock.calls.map(call => call[0])).toEqual([
      '/categories/3',
      '/categories/2',
      '/categories/1',
    ]);
  });

  it('patches each selected transfer leg without replacing financial fields', async () => {
    vi.mocked(apiClient.patch).mockResolvedValue({ data: {} });
    await runBulkAction(
      'transactions',
      [
        { id: 1, label: 'Out', transferId: 'pair' },
        { id: 2, label: 'In', transferId: 'pair' },
      ],
      { notes: '', isReconciled: false },
      vi.fn(),
      'Failed'
    );
    expect(apiClient.patch).toHaveBeenCalledTimes(2);
    expect(apiClient.patch).toHaveBeenCalledWith(
      '/transactions/1/metadata',
      { notes: '', isReconciled: false },
      expect.anything()
    );
    expect(apiClient.put).not.toHaveBeenCalled();
  });
});
