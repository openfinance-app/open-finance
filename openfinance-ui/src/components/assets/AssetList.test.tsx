import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, fireEvent, waitFor, within } from '@testing-library/react';
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

  it('links the account from both table rows and mobile cards without opening asset details', () => {
    const view = vi.fn();
    renderWithProviders(
      <AssetList
        assets={[{ ...bitcoin, accountId: 7, accountName: 'BTC account' }]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
        onView={view}
      />
    );
    const links = screen.getAllByRole('link', { name: 'BTC account' });
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link).toHaveAttribute('href', '/accounts?highlight=7');
      fireEvent.click(link, { ctrlKey: true });
    }
    expect(view).not.toHaveBeenCalled();
  });

  it('routes properties to their management view without offering generic deletion', () => {
    const view = vi.fn();
    renderWithProviders(
      <AssetList
        assets={[{ ...bitcoin, type: 'REAL_ESTATE', name: 'Home' }]}
        onEdit={vi.fn()}
        onDelete={vi.fn()}
        onView={view}
      />
    );
    expect(screen.queryByRole('button', { name: /delete asset/i })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /manage property/i }));
    expect(view).toHaveBeenCalled();
  });

  it('keeps a zero-balance holding in the regular table with access to its details', () => {
    const view = vi.fn();
    const closed: Asset = {
      ...bitcoin,
      ownershipStatus: 'CLOSED',
      isActive: false,
      quantity: 0,
      totalValue: 0,
      unrealizedGain: -70000,
      gainPercentage: -1,
    };
    const { container } = renderWithProviders(
      <AssetList assets={[closed]} onEdit={vi.fn()} onDelete={vi.fn()} onView={view} />
    );
    expect(container.querySelector('details')).not.toBeInTheDocument();
    const row = screen.getByRole('row', { name: /Bitcoin/ });
    expect(screen.queryByText('No holdings')).not.toBeInTheDocument();
    expect(screen.queryByText('Inactive')).not.toBeInTheDocument();
    expect(within(row).getByRole('cell', { name: '0', exact: true })).toBeInTheDocument();
    expect(within(row).getByText('N/A')).toBeInTheDocument();
    fireEvent.click(row);
    expect(view).toHaveBeenCalledWith(closed);
  });

  it('keeps a rejected delete open and allows retry without losing the selected asset', async () => {
    const remove = vi
      .fn()
      .mockRejectedValueOnce({
        isAxiosError: true,
        response: { data: { message: 'Reverse linked movements before deleting this holding.' } },
      })
      .mockResolvedValue(undefined);
    renderWithProviders(<AssetList assets={[bitcoin]} onEdit={vi.fn()} onDelete={remove} />);
    fireEvent.click(screen.getAllByRole('button', { name: /delete asset/i })[0]);
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete', exact: true })
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('Reverse linked movements');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete', exact: true })
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(remove).toHaveBeenNthCalledWith(2, bitcoin.id);
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
