import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import i18n from '@/test/i18n-test';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AssetDetailModal } from '@/components/assets/AssetDetailModal';
import type { Asset } from '@/types/asset';

const history = vi.hoisted(() => vi.fn());
vi.mock('@/hooks/useMarketData', () => ({ useHistoricalPrices: history }));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <>{children}</>,
  LineChart: ({ data }: { data: unknown }) => (
    <output aria-label="Historical closing prices">{JSON.stringify(data)}</output>
  ),
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
}));

const bitcoin: Asset = {
  id: 1,
  userId: 1,
  name: 'Bitcoin',
  type: 'CRYPTO',
  symbol: 'BTC',
  quantity: 1,
  purchasePrice: 50000,
  currentPrice: 73112.8,
  currency: 'EUR',
  purchaseDate: '2026-10-18',
  lastUpdated: '2026-10-19T12:00:00',
  createdAt: '2026-10-18T12:00:00',
  updatedAt: '2026-10-19T12:00:00',
  totalValue: 73112.8,
  totalCost: 50000,
  unrealizedGain: 23112.8,
  gainPercentage: 0.462256,
  holdingDays: 1,
};

describe('Asset price history currencies', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/assets');
    mockAuthentication();
    history.mockReset();
    Element.prototype.scrollIntoView = vi.fn();
    history.mockReturnValue({
      data: [{ date: '2026-10-19', close: 73112.8, currency: 'EUR' }],
      isLoading: false,
      isError: false,
    });
  });
  afterEach(() => window.history.replaceState({}, '', '/'));

  it('updates the historical request when the selected time period changes', async () => {
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={vi.fn()} />);
    const end = new Date();
    for (const [range, months] of [
      ['1M', 1],
      ['6M', 6],
      ['1Y', 12],
    ] as const) {
      await userEvent.click(screen.getByRole('button', { name: range }));
      const start = new Date(end);
      start.setMonth(start.getMonth() - months);
      expect(history).toHaveBeenLastCalledWith(
        'BTC-USD',
        start.toISOString().split('T')[0],
        end.toISOString().split('T')[0],
        'EUR'
      );
    }
  });

  it('edits the selected asset and closes the detail view', async () => {
    const edit = vi.fn();
    const close = vi.fn();
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={close} onEdit={edit} />);
    await userEvent.click(
      screen.getByRole('button', { name: i18n.t('assets:detail.actions.edit') })
    );
    expect(edit).toHaveBeenCalledWith(bitcoin);
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
  });

  it('requires deletion confirmation, retains the dialog after failure, and supports retry', async () => {
    const remove = vi
      .fn()
      .mockRejectedValueOnce(new Error('Still referenced'))
      .mockResolvedValueOnce(undefined);
    const close = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={close} onDelete={remove} />);
    await user.click(screen.getByRole('button', { name: i18n.t('assets:detail.actions.delete') }));
    expect(remove).not.toHaveBeenCalled();
    const confirm = screen.getByRole('dialog', {
      name: i18n.t('assets:detail.deleteConfirm.title'),
    });
    await user.click(
      within(confirm).getByRole('button', { name: i18n.t('assets:detail.deleteConfirm.confirm') })
    );
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(close).not.toHaveBeenCalled();
    await user.click(
      within(confirm).getByRole('button', { name: i18n.t('assets:detail.deleteConfirm.confirm') })
    );
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
    expect(remove.mock.calls).toEqual([[bitcoin.id], [bitcoin.id]]);
  });

  it('closes on Escape and removes its keyboard listener after unmount', async () => {
    const close = vi.fn();
    const view = renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={close} />);
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
    view.unmount();
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
  });

  it('requests BTC-USD prices in EUR and plots the returned EUR value', () => {
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={vi.fn()} />);
    expect(history).toHaveBeenCalledWith('BTC-USD', expect.any(String), expect.any(String), 'EUR');
    expect(screen.getByLabelText('Historical closing prices')).toHaveTextContent('73112.8');
    expect(
      screen.getByRole('button', { name: /daily closing price per unit in EUR/i })
    ).toBeInTheDocument();
  });

  it('requests the displayed base currency without converting the history twice', () => {
    history.mockReturnValue({
      data: [{ date: '2026-10-19', close: 60000, currency: 'GBP' }],
      isLoading: false,
      isError: false,
    });
    renderWithProviders(
      <AssetDetailModal
        asset={{ ...bitcoin, isConverted: true, baseCurrency: 'GBP', exchangeRate: 0.8 }}
        onClose={vi.fn()}
      />
    );
    expect(history).toHaveBeenCalledWith('BTC-USD', expect.any(String), expect.any(String), 'GBP');
    expect(screen.getByLabelText('Historical closing prices')).toHaveTextContent('60000');
    expect(screen.getByLabelText('Historical closing prices')).not.toHaveTextContent('48000');
  });

  it.each(['USD', null])('does not label %s prices as EUR', currency => {
    history.mockReturnValue({
      data: [{ date: '2026-10-19', close: 81875.92, currency }],
      isLoading: false,
      isError: false,
    });
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={vi.fn()} />);
    expect(screen.queryByLabelText('Historical closing prices')).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Price history could not be loaded in EUR');
  });

  it('explains an unavailable history or exchange rate', () => {
    history.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    renderWithProviders(<AssetDetailModal asset={bitcoin} onClose={vi.fn()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Price history could not be loaded in EUR');
    expect(screen.queryByText(/no historical data available/i)).not.toBeInTheDocument();
  });
});
