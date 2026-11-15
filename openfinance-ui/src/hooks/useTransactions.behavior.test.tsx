import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { useTransactions, useTransactionSummary, getTransferSource } from '@/hooks/useTransactions';
import type { TransactionFilters } from '@/types/transaction';
import apiClient from '@/services/apiClient';
vi.mock('@/services/apiClient');
function Results({ filters }: { filters?: TransactionFilters }) {
  const rows = useTransactions(filters);
  const summary = useTransactionSummary(filters);
  return (
    <output>{rows.isSuccess && summary.isSuccess ? JSON.stringify(rows.data) : 'Loading'}</output>
  );
}
const transaction = {
  id: 3,
  userId: 1,
  accountId: 5,
  type: 'EXPENSE',
  amount: 10,
  currency: 'USD',
  date: '2025-01-01',
  description: 'Food',
  createdAt: '2025-01-01',
  isReconciled: false,
};
describe('transaction query boundaries', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
  });
  it('summary retains every exact financial filter while ignoring list pagination', async () => {
    vi.mocked(apiClient.get).mockImplementation(async url => ({
      data: url.startsWith('/transactions/search') ? [] : {},
    }));
    const filters: TransactionFilters = {
      accountId: 5,
      type: 'EXPENSE',
      noCategory: true,
      noPayee: true,
      keyword: 'A & B.*',
      keywordRegex: true,
      minAmount: '0',
      maxAmount: '0.000000000000000001',
      exactAmount: '0',
      amountCurrencyMode: 'NATIVE',
      realEstateId: 3,
      assetId: 7,
      liabilityId: 8,
      page: 2,
      size: 25,
      sort: 'amount,asc',
      dateFrom: '2025-01-01',
      dateTo: '2025-12-31',
    };
    renderWithProviders(<Results filters={filters} />);
    await waitFor(() => expect(screen.getByRole('status')).not.toHaveTextContent('Loading'));
    const calls = vi.mocked(apiClient.get).mock.calls.map(([url]) => new URL(url, 'http://test'));
    const list = calls.find(url => url.pathname === '/transactions/search')!;
    const summary = calls.find(url => url.pathname === '/transactions/summary')!;
    expect(Object.fromEntries(summary.searchParams)).toEqual({
      accountId: '5',
      type: 'EXPENSE',
      noCategory: 'true',
      noPayee: 'true',
      keyword: 'A & B.*',
      keywordRegex: 'true',
      amountMin: '0',
      amountMax: '0.000000000000000001',
      amountExact: '0',
      amountCurrencyMode: 'NATIVE',
      realEstateId: '3',
      assetId: '7',
      liabilityId: '8',
      dateFrom: '2025-01-01',
      dateTo: '2025-12-31',
    });
    expect(list.searchParams.get('page')).toBe('2');
    expect(list.searchParams.get('size')).toBe('25');
    expect(list.searchParams.get('sort')).toBe('amount,asc');
    for (const [key, value] of summary.searchParams) expect(list.searchParams.get(key)).toBe(value);
  });
  it.each([
    { tags: ' food, , travel ', expected: ['food', 'travel'] },
    { tags: ' , ', expected: undefined },
    { tags: null, expected: undefined },
    { tags: ['food', 'travel'], expected: ['food', 'travel'] },
  ])('normalizes incoming transaction tags: $tags', async ({ tags, expected }) => {
    vi.mocked(apiClient.get).mockImplementation(async url => ({
      data: url.startsWith('/transactions/search') ? [{ ...transaction, tags }] : {},
    }));
    renderWithProviders(<Results />);
    await waitFor(() => expect(screen.getByRole('status')).not.toHaveTextContent('Loading'));
    const payload = JSON.parse(screen.getByRole('status').textContent!);
    expect(payload).toMatchObject({ totalElements: 1, totalPages: 1, number: 0, size: 1 });
    expect(payload.content[0].tags).toEqual(expected);
    expect(payload.content[0].amount).toBe(10);
  });
  it('transfer-source lookup preserves exact foreign amount metadata and normalizes tags', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({
      data: {
        ...transaction,
        currency: 'EUR',
        tags: 'one, two',
        exactValues: { amount: '10.000000000000000001' },
      },
    });
    const source = await getTransferSource('booked-transfer');
    expect(source.tags).toEqual(['one', 'two']);
    expect(source.currency).toBe('EUR');
    expect(source.exactValues?.amount).toBe('10.000000000000000001');
    expect(apiClient.get).toHaveBeenCalledWith(
      '/transactions/transfers/booked-transfer',
      expect.objectContaining({ headers: expect.any(Object) })
    );
  });
});
