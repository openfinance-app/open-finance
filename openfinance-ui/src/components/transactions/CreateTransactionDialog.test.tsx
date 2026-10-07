import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { createTestQueryClient, mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { server } from '@/test/mocks/server';
import type { TransactionRequest } from '@/types/transaction';
import { CreateTransactionDialog } from '@/components/transactions/CreateTransactionDialog';

vi.mock('@/components/transactions/TransactionForm', () => ({
  TransactionForm: ({
    onSubmit,
    onCancel,
    isLoading,
  }: {
    onSubmit: (data: TransactionRequest) => void;
    onCancel: () => void;
    isLoading?: boolean;
  }) => (
    <>
      {(['EXPENSE', 'TRANSFER'] as const).map(type => (
        <button
          key={type}
          disabled={isLoading}
          onClick={() =>
            onSubmit({
              type,
              accountId: 1,
              toAccountId: type === 'TRANSFER' ? 2 : undefined,
              amount: 12.34,
              currency: 'USD',
              date: '2026-10-07',
              description: 'Dashboard payment',
            })
          }
        >
          Save {type}
        </button>
      ))}
      <button onClick={onCancel}>Cancel</button>
    </>
  ),
}));

beforeEach(() => {
  mockAuthentication();
});

describe('CreateTransactionDialog', () => {
  it.each([
    ['EXPENSE', '/transactions'],
    ['TRANSFER', '/transactions/transfer'],
  ])('saves %s and refreshes dashboard data before closing', async (type, endpoint) => {
    const onClose = vi.fn();
    const submitted = vi.fn();
    const queryClient = createTestQueryClient();
    queryClient.setQueryData(['dashboard', 'summary'], { totalTransactions: 1 });
    server.use(
      http.post('/api/v1' + endpoint, async ({ request }) => {
        submitted(await request.json());
        return HttpResponse.json({ id: 2 }, { status: 201 });
      })
    );
    renderWithProviders(<CreateTransactionDialog onClose={onClose} />, { queryClient });
    fireEvent.click(await screen.findByRole('button', { name: 'Save ' + type }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(submitted).toHaveBeenCalledWith(
      expect.objectContaining({ type, accountId: 1, amount: 12.34, currency: 'USD' })
    );
    expect(queryClient.getQueryState(['dashboard', 'summary'])?.isInvalidated).toBe(true);
  });

  it('keeps the form open on failure and allows a successful retry', async () => {
    const onClose = vi.fn();
    let attempts = 0;
    server.use(
      http.post('/api/v1/transactions', () => {
        attempts += 1;
        return attempts === 1
          ? HttpResponse.json({ message: 'Payment could not be saved' }, { status: 503 })
          : HttpResponse.json({ id: 2 }, { status: 201 });
      })
    );
    renderWithProviders(<CreateTransactionDialog onClose={onClose} />);
    fireEvent.click(await screen.findByRole('button', { name: 'Save EXPENSE' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Payment could not be saved');
    expect(onClose).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Save EXPENSE' }));
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(attempts).toBe(2);
  });
});
