import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { RecordCostForm } from '@/components/assets/RecordCostForm';

const { save, accountState } = vi.hoisted(() => ({
  save: vi.fn(),
  accountState: {
    data: [
      { id: 1, name: 'Checking', currency: 'USD' },
      { id: 2, name: 'Euro account', currency: 'EUR' },
    ],
    isLoading: false,
    isError: false,
  },
}));
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => accountState }));
vi.mock('@/hooks/useTransactions', () => ({
  useCreateTransaction: () => ({ mutateAsync: save, isPending: false, error: null }),
}));

beforeEach(() => {
  mockAuthentication();
  save.mockReset().mockResolvedValue({ id: 12 });
  Element.prototype.scrollIntoView = vi.fn();
});

async function fillCost(): Promise<void> {
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Engine repair' } });
  fireEvent.change(screen.getByLabelText('Amount (USD)'), {
    target: { value: '120.000000000000000001' },
  });
  await userEvent.selectOptions(screen.getByLabelText('Paid from'), '1');
}

describe('Record cost from the item', () => {
  it.each(['asset', 'property'] as const)(
    'records a %s cost with exactly one target and an exact amount',
    async kind => {
      const close = vi.fn();
      renderWithProviders(
        <RecordCostForm
          target={{ id: 7, name: 'My item', currency: 'USD', kind }}
          onClose={close}
        />
      );
      expect(screen.queryByRole('option', { name: /Euro account/ })).not.toBeInTheDocument();
      await fillCost();
      await userEvent.click(screen.getByRole('button', { name: 'Save expense' }));
      await waitFor(() => expect(save).toHaveBeenCalledOnce());
      expect(save.mock.calls[0][0]).toEqual({
        accountId: 1,
        type: 'EXPENSE',
        amount: '120.000000000000000001',
        currency: 'USD',
        description: 'Engine repair',
        date: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        movementType: 'MAINTENANCE',
        ...(kind === 'asset' ? { assetId: 7 } : { realEstateId: 7 }),
      });
      expect(close).toHaveBeenCalledOnce();
    }
  );

  it('explains the value effect of improvements and saves their classification', async () => {
    renderWithProviders(
      <RecordCostForm
        target={{ id: 7, name: 'Home', currency: 'USD', kind: 'property' }}
        onClose={vi.fn()}
      />
    );
    await fillCost();
    await userEvent.selectOptions(
      screen.getByLabelText('What was this cost for?'),
      'CAPITAL_IMPROVEMENT'
    );
    const help = screen.getByRole('button', { name: /amount invested and the tracked value/ });
    await userEvent.hover(help);
    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'amount invested and the tracked value'
    );
    await userEvent.unhover(help);
    await userEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    await waitFor(() =>
      expect(save).toHaveBeenCalledWith(
        expect.objectContaining({ movementType: 'CAPITAL_IMPROVEMENT' })
      )
    );
  });

  it('keeps entries and the form open when saving fails', async () => {
    save.mockRejectedValue(new Error('Offline'));
    const close = vi.fn();
    renderWithProviders(
      <RecordCostForm
        target={{ id: 7, name: 'Car', currency: 'USD', kind: 'asset' }}
        onClose={close}
      />
    );
    await fillCost();
    await userEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    await waitFor(() => expect(save).toHaveBeenCalledOnce());
    expect(close).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Description')).toHaveValue('Engine repair');
  });

  it('blocks empty or invalid costs without posting an expense', async () => {
    renderWithProviders(
      <RecordCostForm
        target={{ id: 7, name: 'Car', currency: 'USD', kind: 'asset' }}
        onClose={vi.fn()}
      />
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await screen.findByText('Enter an amount greater than zero.')).toBeVisible();
    expect(save).not.toHaveBeenCalled();
  });
});
