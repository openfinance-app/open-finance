import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PhysicalAssetCard } from '@/components/assets/PhysicalAssetCard';
import type { Asset } from '@/types/asset';
vi.mock('@/components/assets/AssetCoverImage', () => ({ AssetCoverImage: () => null }));
vi.mock('@/components/assets/AssetTradeActions', () => ({ AssetTradeActions: () => null }));
vi.mock('@/components/ui/CurrencyAmount', () => ({
  CurrencyAmount: ({ amount }: { amount: string | number }) => <span>amount:{amount}</span>,
}));
vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: { amount: string | number }) => <span>current:{amount}</span>,
}));
const asset: Asset = {
  id: 1,
  userId: 1,
  name: 'Collectible',
  type: 'COLLECTIBLE',
  currency: 'USD',
  quantity: 1,
  purchasePrice: 100,
  currentPrice: 120,
  totalValue: 120,
  totalCost: 100,
  unrealizedGain: 20,
  gainPercentage: 20,
  holdingDays: 365,
  purchaseDate: '2025-01-01',
  lastUpdated: '2026-01-01',
  createdAt: '2025-01-01',
  updatedAt: '2026-01-01',
};

describe('physical asset lifecycle and exact performance', () => {
  beforeEach(mockAuthentication);
  it('shows appreciation with a positive sign and caps the retained-value bar at 100%', () => {
    const { container } = renderWithProviders(<PhysicalAssetCard asset={asset} />);
    expect(screen.getByText('Value Change')).toBeVisible();
    expect(screen.getByText('amount:20').parentElement).toHaveTextContent('+20% (+amount:20)');
    expect(container.querySelector('[style="width: 100%;"]')).toBeInTheDocument();
  });
  it('uses exact values to detect a gain below the number representation precision', () => {
    renderWithProviders(
      <PhysicalAssetCard
        asset={{
          ...asset,
          totalCost: 1e16,
          totalValue: 1e16,
          exactValues: { totalCost: '10000000000000000', totalValue: '10000000000000001' },
        }}
      />
    );
    expect(screen.getByText('Value Change')).toBeVisible();
    expect(screen.getByText('amount:1').parentElement).toHaveTextContent('+0% (+amount:1)');
    expect(screen.getByText('current:10000000000000001')).toBeVisible();
  });
  it('does not invent a gain for a gift without a recorded acquisition value', () => {
    renderWithProviders(
      <PhysicalAssetCard asset={{ ...asset, acquisitionType: 'GIFT', totalCost: 0 }} />
    );
    expect(screen.queryByText('Value Change')).not.toBeInTheDocument();
    expect(screen.getByText('current:120')).toBeVisible();
  });
  it('measures a gift against its recorded performance basis rather than zero purchase cost', () => {
    renderWithProviders(
      <PhysicalAssetCard
        asset={{ ...asset, acquisitionType: 'GIFT', totalCost: 0, performanceBasis: 100 }}
      />
    );
    expect(screen.getByText('amount:20').parentElement).toHaveTextContent('+20%');
  });
  it.each([true, false])('shows warranty state and date when warranty is valid = %s', valid => {
    renderWithProviders(
      <PhysicalAssetCard
        asset={{ ...asset, warrantyExpiration: '2026-05-10', isWarrantyValid: valid }}
      />
    );
    expect(screen.getByText(valid ? 'Active' : 'Expired')).toBeVisible();
    expect(screen.getByText(new RegExp(valid ? 'Expires:' : 'Expired:'))).toHaveTextContent(
      '05/10/2026'
    );
  });
  it.each([84, 85])(
    'hides the overlaid cost label at the 85%% retained-value boundary: %s',
    retained => {
      renderWithProviders(<PhysicalAssetCard asset={{ ...asset, totalValue: retained }} />);
      expect(screen.getAllByText('amount:100')).toHaveLength(retained < 85 ? 2 : 1);
    }
  );
  it.each([
    { ownershipStatus: 'CLOSED' as const, soldDate: undefined },
    { acquisitionType: 'PLANNED' as const, purchaseDate: '' },
  ])('missing optional lifecycle dates never produce Invalid Date: %j', fields => {
    renderWithProviders(<PhysicalAssetCard asset={{ ...asset, ...fields }} />);
    expect(screen.queryByText(/Invalid Date/)).not.toBeInTheDocument();
    expect(screen.queryByText('Value Change')).not.toBeInTheDocument();
  });
});
