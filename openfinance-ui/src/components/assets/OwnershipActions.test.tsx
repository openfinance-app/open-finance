import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { OwnershipActions } from '@/components/assets/OwnershipActions';
import type { SaleTarget } from '@/components/assets/AssetSaleForm';
import i18n from '@/test/i18n-test';

vi.mock('@/components/assets/AssetBuyForm', () => ({
  AssetBuyForm: ({ onClose }: { onClose: () => void }) => (
    <button onClick={onClose}>Cancel purchase</button>
  ),
}));
vi.mock('@/components/assets/AssetSaleForm', () => ({
  AssetSaleForm: ({ onClose }: { onClose: () => void }) => (
    <button onClick={onClose}>Cancel sale</button>
  ),
}));
vi.mock('@/components/assets/LinkPurchasePayment', () => ({
  LinkPurchasePayment: ({ assetId }: { assetId: number }) => (
    <button>Link purchase {assetId}</button>
  ),
}));
const target: SaleTarget = {
  id: 1,
  name: 'Holding',
  currency: 'EUR',
  quantity: '2',
  currentValue: '100',
  purchaseDate: '2024-01-01',
};
const tr = (key: string) => i18n.t(`assets:ownership.${key}`);

beforeEach(() => mockAuthentication());
describe('Ownership action eligibility', () => {
  it.each(['PURCHASE', 'GIFT', 'PLANNED'])(
    'hides actions for inactive %s assets',
    acquisitionType => {
      renderWithProviders(
        <OwnershipActions target={target} acquisitionType={acquisitionType} inactive />
      );
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    }
  );
  it.each([true, false])(
    'planned purchases expose only an available completion callback (%s)',
    enabled => {
      const complete = vi.fn();
      renderWithProviders(
        <OwnershipActions
          target={target}
          acquisitionType="PLANNED"
          onComplete={enabled ? complete : undefined}
        />
      );
      if (enabled) {
        fireEvent.click(screen.getByRole('button', { name: tr('completePurchase') }));
        expect(complete).toHaveBeenCalledOnce();
      } else expect(screen.queryByRole('button')).not.toBeInTheDocument();
    }
  );
  it.each([undefined, 'PURCHASE', 'GIFT'])(
    'payment linking follows acquisition type %s when trading is hidden',
    acquisitionType => {
      renderWithProviders(
        <OwnershipActions
          target={target}
          acquisitionType={acquisitionType}
          showTradeActions={false}
        />
      );
      if (acquisitionType !== 'GIFT')
        expect(screen.getByRole('button', { name: 'Link purchase 1' })).toBeVisible();
      else expect(screen.queryByRole('button')).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: tr('sell') })).not.toBeInTheDocument();
    }
  );
  it.each([true, false])('financial holdings require a linked account to buy more (%s)', linked => {
    renderWithProviders(
      <OwnershipActions
        target={{ ...target, financial: true, accountId: linked ? 3 : undefined }}
      />
    );
    expect(screen.queryByRole('button', { name: 'Link purchase 1' })).not.toBeInTheDocument();
    if (linked) {
      fireEvent.click(screen.getByRole('button', { name: tr('buyMore') }));
      fireEvent.click(screen.getByRole('button', { name: 'Cancel purchase' }));
      expect(screen.getByRole('button', { name: tr('buyMore') })).toBeVisible();
    } else expect(screen.queryByRole('button', { name: tr('buyMore') })).not.toBeInTheDocument();
  });
  it('returns from an abandoned sale to the eligible actions', () => {
    renderWithProviders(<OwnershipActions target={target} />);
    fireEvent.click(screen.getByRole('button', { name: tr('sell') }));
    expect(screen.queryByRole('button', { name: 'Link purchase 1' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel sale' }));
    expect(screen.getByRole('button', { name: tr('sell') })).toBeVisible();
  });
  it('reminds owners that selling a property does not repay its debt', () => {
    renderWithProviders(<OwnershipActions target={{ ...target, property: true }} closed />);
    expect(screen.getByText(tr('debtRetained'))).toBeVisible();
    expect(screen.getByText(tr('unknownGain'))).toBeVisible();
  });
});
