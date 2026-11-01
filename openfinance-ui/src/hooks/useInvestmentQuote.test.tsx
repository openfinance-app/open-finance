import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import apiClient from '@/services/apiClient';
import { mockAuthentication, renderWithProviders, createTestQueryClient } from '@/test/test-utils';
import { useInvestmentQuote } from '@/hooks/useInvestmentQuote';
import { decimalField } from '@/utils/exact-decimal';
import type { Transaction } from '@/types/transaction';

vi.mock('@/services/apiClient');

function Preview({ transaction }: { transaction?: Transaction }) {
  const [amount, setAmount] = useState('377.65');
  const [date, setDate] = useState('2026-06-24');
  const preview = useInvestmentQuote(
    { assetId: 8, operation: 'REMOVE', pricing: 'HISTORICAL' },
    amount,
    'EUR',
    date,
    transaction
  );
  return (
    <>
      <label>
        Amount
        <input value={amount} onChange={event => setAmount(event.target.value)} />
      </label>
      <label>
        Date
        <input value={date} onChange={event => setDate(event.target.value)} />
      </label>
      <output aria-label="Units">
        {preview.data ? decimalField(preview.data, 'quantity') : 'Pending'}
      </output>
      <button
        disabled={!preview.data || (!preview.isSaved && (preview.isFetching || preview.isError))}
      >
        Save
      </button>
    </>
  );
}

describe('investment quote preview', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    mockAuthentication();
  });

  it('uses the persisted estimate without fetching a new historical price', () => {
    renderWithProviders(
      <Preview
        transaction={
          {
            id: 2825,
            assetId: 8,
            amount: 377.65,
            currency: 'EUR',
            investmentPrice: 100000,
            investmentValue: 377.65,
            investmentPriceSource: 'HISTORICAL_CLOSE',
            investmentPriceDate: '2026-06-24',
            investmentCurrency: 'EUR',
            investmentQuantity: -0.0037765,
            exactValues: { investmentQuantity: '-0.003776500000000123' },
          } as Transaction
        }
      />,
      { queryClient: createTestQueryClient() }
    );
    expect(screen.getByLabelText('Units')).toHaveTextContent('0.003776500000000123');
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    expect(apiClient.get).not.toHaveBeenCalledWith(
      expect.stringContaining('investment-quote'),
      expect.anything()
    );
  });

  it('clears the old estimate immediately when amount or date changes', async () => {
    const get = vi.mocked(apiClient.get);
    get.mockImplementation(async url => {
      if (url.includes('investment-quote')) return { data: { quantity: '0.0037765' } };
      return { data: {} };
    });
    renderWithProviders(<Preview />, { queryClient: createTestQueryClient() });
    await waitFor(() => expect(screen.getByLabelText('Units')).toHaveTextContent('0.0037765'));
    get.mockImplementation(url =>
      url.includes('investment-quote') ? new Promise(() => {}) : Promise.resolve({ data: {} })
    );
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '400' } });
    expect(screen.getByLabelText('Units')).toHaveTextContent('Pending');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2026-06-25' } });
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    await waitFor(() =>
      expect(get).toHaveBeenCalledWith(
        '/assets/8/investment-quote',
        expect.objectContaining({
          params: expect.objectContaining({ amount: '400', date: '2026-06-25' }),
        })
      )
    );
  });
});
