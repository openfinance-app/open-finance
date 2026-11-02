import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { AssetSaleForm } from '@/components/assets/AssetSaleForm';
import type { Account } from '@/types/account';

let accounts: Account[] = [];
const sale = vi.fn();
vi.mock('@/hooks/useAccounts', () => ({ useAccounts: () => ({ data: accounts }) }));
vi.mock('@/hooks/useOwnershipOperation', () => ({
  useOwnershipOperation: () => ({ mutateAsync: sale, isPending: false }),
}));

describe('sale opened directly from an asset action', () => {
  beforeEach(() => {
    mockAuthentication();
    accounts = [];
    sale.mockReset().mockResolvedValue({});
  });

  it('retains the investment account while the account options load asynchronously', async () => {
    const onClose = vi.fn();
    const form = (
      <AssetSaleForm
        target={{
          id: 7,
          name: 'Shares',
          quantity: '10',
          currentValue: '1200',
          currency: 'EUR',
          purchaseDate: '2026-01-01',
          financial: true,
          accountId: 2,
        }}
        onClose={onClose}
      />
    );
    const { rerender } = renderWithProviders(form);
    accounts = [{ id: 2, name: 'Broker', currency: 'EUR', type: 'INVESTMENT' } as Account];
    rerender(<AssetSaleForm {...form.props} />);
    expect(screen.getByRole('combobox', { name: 'Receive payment in' })).toHaveValue('2');
    await userEvent.setup().click(screen.getByRole('button', { name: 'Confirm sale' }));
    await waitFor(() =>
      expect(sale).toHaveBeenCalledWith({
        path: '/assets/7/sale',
        data: expect.objectContaining({ accountId: 2, quantity: '10', proceeds: '1200' }),
      })
    );
    expect(onClose).toHaveBeenCalled();
  });
});
