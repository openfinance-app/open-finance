import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { AssetList } from '@/components/assets/AssetList';
import type { Asset } from '@/types/asset';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';

const bitcoin: Asset = {
  id: 1,
  userId: 1,
  name: 'Bitcoin',
  type: 'CRYPTO',
  symbol: 'BTC-USD',
  quantity: 1,
  purchasePrice: 70000,
  currentPrice: 73284.26,
  currency: 'EUR',
  purchaseDate: '2026-10-18',
  lastUpdated: '2026-10-20T12:00:00',
  createdAt: '2026-10-18T12:00:00',
  updatedAt: '2026-10-20T12:00:00',
  totalValue: 73284.26,
  totalCost: 70000,
  unrealizedGain: 3284.26,
  gainPercentage: 0.046918,
  holdingDays: 2,
};

describe('Asset column labels', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('uses Prix d’achat for the French purchase column while retaining the total cost', async () => {
    await i18n.changeLanguage('fr');
    renderWithProviders(
      <AssetList
        assets={[{ ...bitcoin, quantity: 2, totalCost: 140000 }]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
      />
    );

    expect(screen.getByRole('columnheader', { name: "Prix d'achat" })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Prix de revient' })).not.toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Bitcoin/ })).toHaveTextContent(/140[,\s]?000/);
  });
});
