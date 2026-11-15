import { beforeEach, afterEach, expect, it } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AssetPerformanceTable } from '@/components/assets/AssetPerformanceTable';
import type { Asset } from '@/types/asset';
import i18n from '@/test/i18n-test';

const asset = (
  id: number,
  name: string,
  type: Asset['type'],
  totalValue: number,
  totalCost: number,
  unrealizedGain: number | null,
  gainPercentage: number | null
): Asset => ({
  id,
  name,
  type,
  totalValue,
  totalCost,
  unrealizedGain,
  gainPercentage,
  userId: 1,
  quantity: 1,
  purchasePrice: totalCost,
  currentPrice: totalValue,
  currency: 'USD',
  purchaseDate: '2025-01-01',
  lastUpdated: '2026-01-01',
  createdAt: '2025-01-01',
  updatedAt: '2026-01-01',
  holdingDays: 365,
});
const assets = [
  asset(1, 'zebra', 'STOCK', 120, 100, 20, 20),
  asset(2, 'Alpha', 'BOND', 90, 80, null, null),
  asset(3, 'Beta', 'CRYPTO', 50, 70, -20, -28),
];
beforeEach(() => mockAuthentication());
afterEach(() => window.history.replaceState({}, '', '/'));

it('sorts both directions by each performance metric without mutating the source holdings', async () => {
  renderWithProviders(<AssetPerformanceTable assets={assets} />);
  const user = userEvent.setup();
  const order = () =>
    within(screen.getByRole('table'))
      .getAllByRole('row')
      .slice(1)
      .map(row => within(row).getAllByRole('cell')[0].textContent?.replace(/\s+/g, ' ').trim());
  const check = (names: string[]) =>
    names.forEach((name, index) => expect(order()[index]).toContain(name));
  check(['zebra', 'Alpha', 'Beta']);
  const columns = [
    ['name', ['Alpha', 'Beta', 'zebra']],
    ['type', ['Alpha', 'Beta', 'zebra']],
    ['costBasis', ['Beta', 'Alpha', 'zebra']],
    ['currentValue', ['Beta', 'Alpha', 'zebra']],
    ['gainLoss', ['Alpha', 'Beta', 'zebra']],
    ['return', ['Alpha', 'Beta', 'zebra']],
  ] as const;
  for (const [key, expected] of columns) {
    const button = screen.getByRole('button', { name: i18n.t(`assets:table.${key}`) });
    await user.click(button);
    check([...expected]);
    await user.click(button);
    check([...expected].reverse());
  }
  expect(assets.map(item => item.id)).toEqual([1, 2, 3]);
  await user.click(within(screen.getByRole('table')).getByText('Alpha'));
  expect(window.location.pathname).toBe('/assets/2');
});
it('opens the same holding from its mobile card and explains an empty portfolio', async () => {
  const { rerender } = renderWithProviders(<AssetPerformanceTable assets={assets} />);
  await userEvent.click(screen.getAllByText('Beta')[1]);
  expect(window.location.pathname).toBe('/assets/3');
  rerender(<AssetPerformanceTable assets={[]} />);
  expect(screen.getByText('No assets to display')).toBeInTheDocument();
});

it.each([
  ['currentValue', 'totalValue'],
  ['costBasis', 'totalCost'],
  ['gainLoss', 'unrealizedGain'],
  ['return', 'gainPercentage'],
] as const)('sorts exact %s in both directions', (column, field) => {
  const lower = {
    ...assets[0],
    id: 11,
    name: 'Lower',
    [field]: 9007199254740992,
    exactValues: { [field]: '9007199254740992.01' },
  };
  const higher = {
    ...lower,
    id: 12,
    name: 'Higher',
    exactValues: { [field]: '9007199254740992.02' },
  };
  renderWithProviders(<AssetPerformanceTable assets={[higher, lower]} />);
  const button = screen.getByRole('button', { name: i18n.t(`assets:table.${column}`) });
  fireEvent.click(button);
  expect(screen.getAllByRole('row')[1]).toHaveTextContent('Lower');
  fireEvent.click(button);
  expect(screen.getAllByRole('row')[1]).toHaveTextContent('Higher');
});

it('preserves exact converted cost and unit prices', () => {
  renderWithProviders(
    <AssetPerformanceTable
      assets={[
        {
          ...assets[0],
          isConverted: true,
          baseCurrency: 'EUR',
          exchangeRate: 1,
          totalCost: 9007199254740992,
          purchasePrice: 9007199254740992,
          currentPrice: 9007199254740992,
          exactValues: {
            totalCost: '9007199254740992.01',
            purchasePrice: '9007199254740992.03',
            currentPrice: '9007199254740992.05',
            exchangeRate: '1.000000000000000001',
          },
        },
      ]}
    />
  );
  const row = screen.getAllByRole('row')[1];
  for (const cents of ['02', '04', '06'])
    expect(row).toHaveTextContent(`9,007,199,254,740,992.${cents}`);
});
