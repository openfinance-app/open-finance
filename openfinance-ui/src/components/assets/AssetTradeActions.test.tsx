import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { AssetTradeActions } from '@/components/assets/AssetTradeActions';
import type { Asset } from '@/types/asset';
import type { SaleTarget } from '@/components/assets/AssetSaleForm';

vi.mock('@/components/assets/AssetBuyForm', () => ({
  AssetBuyForm: ({ target }: { target: SaleTarget }) => <p>Buy target {target.id}</p>,
}));
vi.mock('@/components/assets/AssetSaleForm', () => ({
  AssetSaleForm: ({ target }: { target: SaleTarget }) => (
    <p>
      {target.property ? 'Property' : 'Asset'} sale target {target.id}, quantity {target.quantity}
    </p>
  ),
}));

const holding = {
  id: 1,
  name: 'Bitcoin',
  type: 'CRYPTO',
  accountId: 2,
  quantity: 0.00688795,
  totalValue: 477.389,
  currency: 'EUR',
  purchaseDate: '2026-01-01',
  ownershipStatus: 'OWNED',
} as Asset;

describe('asset actions', () => {
  beforeEach(mockAuthentication);
  it('opens an exact-quantity sale without triggering the surrounding asset card', async () => {
    const onView = vi.fn();
    renderWithProviders(
      <div onClick={onView}>
        <AssetTradeActions asset={holding} />
      </div>
    );
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sell: Bitcoin' }));
    expect(screen.getByText('Asset sale target 1, quantity 0.00688795')).toBeInTheDocument();
    expect(onView).not.toHaveBeenCalled();
  });
  it('offers Buy more but no Sell for an exhausted investment', () => {
    renderWithProviders(
      <AssetTradeActions asset={{ ...holding, quantity: 0, ownershipStatus: 'CLOSED' }} />
    );
    expect(screen.getByRole('button', { name: 'Buy more: Bitcoin' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sell: Bitcoin' })).not.toBeInTheDocument();
  });
  it('does not offer trades on unlinked investments or disposed physical assets', () => {
    renderWithProviders(
      <>
        <AssetTradeActions asset={{ ...holding, accountId: undefined }} />
        <AssetTradeActions asset={{ ...holding, type: 'VEHICLE', ownershipStatus: 'CLOSED' }} />
      </>
    );
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
  it('routes a planned purchase through the existing completion flow', async () => {
    const complete = vi.fn();
    const plan = { ...holding, type: 'VEHICLE', acquisitionType: 'PLANNED' } as Asset;
    renderWithProviders(<AssetTradeActions asset={plan} onCompletePurchase={complete} />);
    await userEvent
      .setup()
      .click(screen.getByRole('button', { name: 'Complete purchase: Bitcoin' }));
    expect(complete).toHaveBeenCalledWith(plan);
    expect(screen.queryByRole('button', { name: 'Sell: Bitcoin' })).not.toBeInTheDocument();
  });
  it('uses the same backing asset sale endpoint as the property detail view', async () => {
    renderWithProviders(
      <AssetTradeActions
        asset={{ ...holding, type: 'REAL_ESTATE', realEstateId: 42, quantity: 1 }}
      />
    );
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sell: Bitcoin' }));
    expect(screen.getByText('Property sale target 1, quantity 1')).toBeInTheDocument();
  });
});
