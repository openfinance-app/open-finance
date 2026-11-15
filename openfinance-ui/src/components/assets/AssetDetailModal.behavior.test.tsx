import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import type { ComponentProps, ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { useViewStateStore } from '@/stores/viewState';
import type { Asset } from '@/types/asset';
import i18n from '@/test/i18n-test';
import { AssetDetailModal } from '@/components/assets/AssetDetailModal';
const history = vi.hoisted(() => vi.fn());
vi.mock('@/hooks/useMarketData', () => ({ useHistoricalPrices: history }));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: () => undefined }),
}));
vi.mock('@/components/assets/AssetCostsSection', () => ({
  AssetCostsSection: ({ asset }: { asset: Asset }) => <p>Costs for {asset.id}</p>,
}));
vi.mock('@/components/assets/AssetFinancingSection', () => ({
  AssetFinancingSection: ({ assetId }: { assetId: number }) => <p>Financing for {assetId}</p>,
}));
vi.mock('@/components/assets/AssetGallery', () => ({
  AssetGallery: ({ assetId }: { assetId: number }) => <p>Gallery for {assetId}</p>,
}));
vi.mock('@/components/attachments', () => ({
  AttachmentList: ({ entityId }: { entityId: number }) => <p>Attachments for {entityId}</p>,
  AttachmentUpload: ({ entityId }: { entityId: number }) => <p>Upload for {entityId}</p>,
}));
vi.mock('@/components/assets/OwnershipActions', () => ({
  OwnershipActions: ({
    closed,
    onComplete,
    acquisitionType,
  }: ComponentProps<typeof import('@/components/assets/OwnershipActions').OwnershipActions>) => (
    <section aria-label="Ownership actions">
      {closed ? 'Sold ownership' : acquisitionType}
      {onComplete && <button onClick={onComplete}>Complete plan</button>}
    </section>
  ),
}));
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <>{children}</>,
  LineChart: ({ children }: { children: ReactNode }) => (
    <section aria-label="Market chart">{children}</section>
  ),
  Line: () => null,
  XAxis: () => null,
  CartesianGrid: () => null,
  YAxis: ({ tickFormatter }: { tickFormatter: (value: number) => string }) => (
    <output aria-label="Price axis">{tickFormatter(1000)}</output>
  ),
  Tooltip: ({ formatter }: { formatter: (value: number | undefined) => [string, string] }) => (
    <>
      <output aria-label="Closing quote">{formatter(12.34).join(' ')}</output>
      <output aria-label="Absent quote">{formatter(undefined).join(' ')}</output>
    </>
  ),
}));
const asset: Asset = {
  id: 8,
  userId: 7,
  name: 'Holding',
  type: 'STOCK',
  symbol: 'ACME',
  quantity: 1,
  purchasePrice: 10,
  currentPrice: 12.34,
  currency: 'EUR',
  purchaseDate: '2025-01-01',
  lastUpdated: '2025-01-02T12:00:00',
  createdAt: '',
  updatedAt: '',
  totalCost: 10,
  totalValue: 12.34,
  unrealizedGain: 2.34,
  gainPercentage: 0.234,
  holdingDays: 1,
};
const mount = (
  extras: Partial<Asset> = {},
  callbacks: Partial<ComponentProps<typeof AssetDetailModal>> = {}
) =>
  renderWithProviders(
    <AssetDetailModal asset={{ ...asset, ...extras }} onClose={vi.fn()} {...callbacks} />
  );
const tab = (name: string) =>
  fireEvent.click(screen.getByRole('button', { name: i18n.t(`assets:detail.tabs.${name}`) }));

describe('Asset details ownership and history boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({}, '', '/assets');
    Element.prototype.scrollIntoView = vi.fn();
    history.mockReturnValue({
      data: [{ date: '2025-01-01', close: 12.34, currency: 'EUR' }],
      isLoading: false,
      isError: false,
    });
  });

  it.each([
    ['CRYPTO', ' btc ', 'BTC-USD'],
    ['CRYPTO', 'BTC-EUR', 'BTC-EUR'],
    ['STOCK', 'ACME', 'ACME'],
    ['STOCK', '', null],
    ['STOCK', undefined, null],
  ] as const)('uses the correct provider symbol for %s %s', (type, symbol, expected) => {
    mount({ type, symbol });
    expect(history).toHaveBeenLastCalledWith(
      expected,
      expect.any(String),
      expect.any(String),
      'EUR'
    );
    if (!symbol)
      expect(
        screen.queryByRole('heading', { name: i18n.t('assets:detail.sections.priceHistory') })
      ).not.toBeInTheDocument();
  });

  it.each(['loading', 'empty', 'missing', 'mismatch'])(
    'handles %s historical data without showing a misleading chart',
    state => {
      history.mockReturnValue({
        data:
          state === 'missing'
            ? undefined
            : state === 'empty'
              ? []
              : [{ date: '2025-01-01', close: 1, currency: state === 'mismatch' ? 'USD' : 'EUR' }],
        isLoading: state === 'loading',
        isError: false,
      });
      mount();
      expect(screen.queryByLabelText('Market chart')).not.toBeInTheDocument();
      if (state === 'empty' || state === 'missing')
        expect(screen.getByText(i18n.t('assets:detail.noHistoricalData'))).toBeInTheDocument();
      if (state === 'mismatch')
        expect(screen.getByRole('alert')).toHaveTextContent(
          i18n.t('assets:detail.historyError', { currency: 'EUR' })
        );
    }
  );

  it('labels historical chart values and unavailable quotes in the displayed currency', () => {
    mount();
    expect(screen.getByLabelText('Closing quote')).toHaveTextContent('€12.34');
    expect(screen.getByLabelText('Closing quote')).toHaveTextContent(
      i18n.t('assets:detail.closingPrice')
    );
    expect(screen.getByLabelText('Absent quote')).toHaveTextContent('N/A');
    expect(screen.getByLabelText('Price axis')).toHaveTextContent('€1.00K');
  });

  it.each(['base-missing', 'rate-missing'])(
    'keeps the quote currency when conversion metadata is %s',
    missing => {
      mount({
        isConverted: true,
        baseCurrency: missing === 'base-missing' ? undefined : 'USD',
        exchangeRate: missing === 'rate-missing' ? undefined : 2,
      });
      expect(history).toHaveBeenLastCalledWith(
        'ACME',
        expect.any(String),
        expect.any(String),
        'EUR'
      );
    }
  );

  it('plans show target cost and date, suppress performance, and pass the selected asset for completion', () => {
    const complete = vi.fn();
    mount({ acquisitionType: 'PLANNED' }, { onCompletePurchase: complete });
    expect(screen.getByText(i18n.t('assets:plannedNotice'))).toBeInTheDocument();
    expect(screen.getByText(i18n.t('assets:form.targetDate'))).toBeInTheDocument();
    expect(
      screen.queryByText(i18n.t('assets:detail.metrics.currentValue'))
    ).not.toBeInTheDocument();
    expect(screen.queryByText(i18n.t('assets:detail.metrics.gainLoss'))).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Complete plan' }));
    expect(complete).toHaveBeenCalledWith(
      expect.objectContaining({ id: 8, acquisitionType: 'PLANNED' })
    );
  });

  it.each([undefined, 0, 100])(
    'gift performance distinguishes missing received value from %s',
    receivedValue => {
      mount({
        acquisitionType: 'GIFT',
        receivedValue,
        unrealizedGain: receivedValue == null ? undefined : -10,
        gainPercentage: receivedValue == null ? undefined : -0.1,
        accountName: 'Custody account',
        notes: 'Gift from family',
      });
      expect(screen.getByText('Custody account')).toBeInTheDocument();
      expect(screen.getByText('Gift from family')).toBeInTheDocument();
      expect(screen.getByText(i18n.t('assets:form.receivedDate'))).toBeInTheDocument();
      if (receivedValue == null)
        expect(screen.getByText(i18n.t('assets:performanceUnavailable'))).toBeInTheDocument();
      else
        expect(screen.queryByText(i18n.t('assets:performanceUnavailable'))).not.toBeInTheDocument();
    }
  );

  it('sold assets hide editable ownership and live performance while retaining their history', () => {
    mount({ ownershipStatus: 'CLOSED', soldDate: '2025-02-01' }, { onEdit: vi.fn() });
    expect(screen.getByLabelText('Ownership actions')).toHaveTextContent('Sold ownership');
    expect(
      screen.queryByRole('button', { name: i18n.t('assets:detail.actions.edit') })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(i18n.t('assets:detail.metrics.currentValue'))
    ).not.toBeInTheDocument();
    tab('operations');
    expect(screen.getByText('Costs for 8')).toBeInTheDocument();
  });

  it('linked real estate directs edits to the property and prevents deleting its underlying asset', () => {
    mount({ type: 'REAL_ESTATE' }, { onEdit: vi.fn(), onDelete: vi.fn() });
    expect(screen.queryByLabelText('Ownership actions')).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: i18n.t('assets:detail.actions.edit') })
    ).toBeDisabled();
    expect(
      screen.queryByRole('button', { name: i18n.t('assets:detail.actions.delete') })
    ).not.toBeInTheDocument();
  });

  it('opens physical cost, financing, gallery, and attachment tabs for the same asset', () => {
    mount({ type: 'VEHICLE', isPhysical: true, symbol: undefined });
    for (const [name, content] of [
      ['costs', 'Costs for 8'],
      ['financing', 'Financing for 8'],
      ['gallery', 'Gallery for 8'],
      ['attachments', 'Attachments for 8'],
    ]) {
      tab(name);
      expect(screen.getByText(content)).toBeInTheDocument();
    }
    expect(screen.getByText('Upload for 8')).toBeInTheDocument();
  });

  it('a consumed Escape or another key does not dismiss the asset', () => {
    const close = vi.fn();
    mount({}, { onClose: close });
    fireEvent.keyDown(window, { key: 'Enter' });
    const event = new KeyboardEvent('keydown', { key: 'Escape', cancelable: true });
    event.preventDefault();
    fireEvent(window, event);
    expect(close).not.toHaveBeenCalled();
  });

  it('keeps deletion confirmation and the asset open while deletion is pending', async () => {
    let resolve!: () => void;
    const remove = vi.fn(
      () =>
        new Promise<void>(done => {
          resolve = done;
        })
    );
    const close = vi.fn();
    mount({}, { onDelete: remove, onClose: close });
    fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:detail.actions.delete') }));
    const confirmation = screen.getByRole('dialog', {
      name: i18n.t('assets:detail.deleteConfirm.title'),
    });
    fireEvent.click(
      within(confirmation).getByRole('button', {
        name: i18n.t('assets:detail.deleteConfirm.confirm'),
      })
    );
    fireEvent.keyDown(confirmation, { key: 'Escape' });
    expect(confirmation).toBeInTheDocument();
    expect(close).not.toHaveBeenCalled();
    expect(remove).toHaveBeenCalledTimes(1);
    resolve();
    await waitFor(() => expect(close).toHaveBeenCalledOnce());
  });
});
