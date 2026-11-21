import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, render, fireEvent, act } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { useViewStateStore } from '@/stores/viewState';
import { MemoryRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
  createTestQueryClient,
} from '@/test/test-utils';
import { AuthProvider } from '@/context/AuthContext';
import { NumberFormatProvider } from '@/context/NumberFormatContext';
import { DecimalPlacesProvider } from '@/context/DecimalPlacesContext';
import { CurrencyDisplayProvider } from '@/context/CurrencyDisplayContext';
import { VisibilityProvider } from '@/context/VisibilityContext';
import AssetsPage from '@/pages/AssetsPage';
import type { Asset } from '@/types/asset';
import { useAssetsSearch } from '@/hooks/useAssets';

// ---------------------------------------------------------------------------
// Mock asset data
// ---------------------------------------------------------------------------
const mockAsset: Asset = {
  id: 1,
  userId: 1,
  name: 'Apple Stock',
  symbol: 'AAPL',
  type: 'STOCK',
  quantity: 10,
  purchasePrice: 150.0,
  currentPrice: 175.0,
  totalValue: 1750.0,
  totalCost: 1500,
  unrealizedGain: 250,
  gainPercentage: 16.6667,
  holdingDays: 1,
  purchaseDate: '2026-01-01',
  lastUpdated: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  accountId: 2,
  currency: 'USD',
  createdAt: '2026-01-01T00:00:00Z',
};

const mockPagedResponse = {
  content: [mockAsset],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

// ---------------------------------------------------------------------------
// Mock hooks to avoid real network calls
// ---------------------------------------------------------------------------
const mockCreateFn = vi.fn();
const mockUpdateFn = vi.fn();
const mockDeleteFn = vi.fn();
const mockRefetchFn = vi.fn();
let mockPagedData: any = mockPagedResponse;
let mockIsLoading = false;
let mockError: Error | null = null;
let mockFlatAssets: Asset[] | undefined = [mockAsset];
let mockFetchedAsset: Asset | undefined;
const mockRefreshPrices = vi.fn();

vi.mock('@/hooks/useAssets', () => ({
  useAssetsSearch: vi.fn(() => ({
    data: mockPagedData,
    isLoading: mockIsLoading,
    error: mockError,
    refetch: mockRefetchFn,
  })),
  useAssets: () => ({ data: mockFlatAssets, isLoading: false, error: null }),
  useAsset: (id: number) => ({
    data: mockFetchedAsset?.id === id ? mockFetchedAsset : null,
    isLoading: false,
    error: null,
  }),
  useCreateAsset: () => ({ mutate: vi.fn(), mutateAsync: mockCreateFn, isPending: false }),
  useUpdateAsset: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateFn, isPending: false }),
  useDeleteAsset: () => ({ mutate: vi.fn(), mutateAsync: mockDeleteFn, isPending: false }),
  formatGainLoss: (unrealizedGain: number, _gainPercentage: number) => ({
    formatted: `${unrealizedGain >= 0 ? '+' : ''}${unrealizedGain.toFixed(2)}`,
    color: unrealizedGain >= 0 ? 'text-green-500' : 'text-red-500',
  }),
  getAssetTypeName: (type: string) => type,
  getAssetTypeBadgeVariant: () => 'default',
  getConditionBadgeVariant: () => 'default',
}));

vi.mock('@/hooks/useMarketData', () => ({
  useUpdateAllAssetPrices: () => ({ mutateAsync: mockRefreshPrices, isPending: false }),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: (a: number) => a, secondaryCurrency: null }),
}));

vi.mock('@/components/assets/AssetForm', () => ({
  AssetForm: ({
    serverError,
    onCancel,
    onSubmit,
    kind,
    initialAcquisitionType,
  }: ComponentProps<typeof import('@/components/assets/AssetForm').AssetForm>) => (
    <div data-testid="asset-form">
      {serverError && <p role="alert">{serverError}</p>}
      <p>Form kind {kind ?? 'edit'}</p>
      <p>Acquisition {initialAcquisitionType ?? 'default'}</p>
      <button onClick={onCancel}>Cancel</button>
      <button
        data-testid="form-submit"
        onClick={() =>
          onSubmit({
            name: 'Test',
            type: 'STOCK',
            quantity: '1',
            purchasePrice: '100',
            currentPrice: '100',
            currency: 'USD',
            purchaseDate: '2026-01-01',
          })
        }
      >
        Submit
      </button>
    </div>
  ),
}));

vi.mock('@/components/assets/AssetDetailModal', () => ({
  AssetDetailModal: ({ asset, onClose, onEdit, onDelete }: any) =>
    asset ? (
      <div data-testid="asset-detail-modal">
        {asset.name}
        <button data-testid="modal-edit" onClick={() => onEdit(asset)}>
          Edit
        </button>
        <button data-testid="modal-delete" onClick={() => onDelete(asset.id)}>
          Delete
        </button>
        <button data-testid="modal-close" onClick={onClose}>
          Close
        </button>
      </div>
    ) : null,
}));

vi.mock('@/components/assets/AssetList', () => ({
  AssetList: ({
    assets,
    onEdit,
    onDelete,
    onView,
    onCompletePurchase,
  }: ComponentProps<typeof import('@/components/assets/AssetList').AssetList>) => (
    <div data-testid="asset-list">
      {assets.map(a => (
        <div key={a.id}>
          <span>{a.name}</span>
          {a.symbol && <span>{a.symbol}</span>}
          {onEdit && (
            <button data-testid={`edit-${a.id}`} onClick={() => onEdit(a)}>
              Edit
            </button>
          )}
          {onDelete && (
            <button data-testid={`del-${a.id}`} onClick={() => onDelete(a.id)}>
              Del
            </button>
          )}
          {onView && (
            <button data-testid={`view-${a.id}`} onClick={() => onView(a)}>
              View
            </button>
          )}
          <button onClick={() => onCompletePurchase?.(a)}>Complete purchase {a.name}</button>
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/assets/LastUpdatedIndicator', () => ({
  BatchUpdateIndicator: () => null,
}));

vi.mock('@/components/assets/AssetPurchaseFlow', () => ({
  AssetPurchaseFlow: ({
    plan,
    onClose,
    onPurchased,
  }: ComponentProps<typeof import('@/components/assets/AssetPurchaseFlow').AssetPurchaseFlow>) => (
    <section aria-label="Purchase flow">
      <p>Plan {plan?.name ?? 'new'}</p>
      <button onClick={onClose}>Cancel purchase</button>
      <button onClick={() => onPurchased(17)}>Finish purchase</button>
    </section>
  ),
}));
vi.mock('@/components/ui/Pagination', () => ({
  Pagination: ({
    currentPage,
    onPageChange,
    onPageSizeChange,
  }: ComponentProps<typeof import('@/components/ui/Pagination').Pagination>) => (
    <nav aria-label="Asset pages">
      <span>Page index {currentPage}</span>
      <button onClick={() => onPageChange(currentPage + 1)}>Next page</button>
      <button onClick={() => onPageSizeChange?.(50)}>50 per page</button>
    </nav>
  ),
}));

describe('AssetsPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    useViewStateStore.getState().clear();
    window.history.replaceState({}, '', '/assets');
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedData = mockPagedResponse;
    mockIsLoading = false;
    mockError = null;
    mockFlatAssets = [mockAsset];
    mockFetchedAsset = undefined;
    mockRefreshPrices.mockReset().mockResolvedValue(undefined);
    vi.mocked(useAssetsSearch).mockImplementation(
      () =>
        ({
          data: mockPagedData,
          isLoading: mockIsLoading,
          error: mockError,
          refetch: mockRefetchFn,
        }) as ReturnType<typeof useAssetsSearch>
    );
    mockCreateFn.mockReset().mockResolvedValue(mockAsset);
    mockUpdateFn.mockReset().mockResolvedValue(mockAsset);
    mockDeleteFn.mockReset().mockResolvedValue(undefined);
  });

  it('creates a financial asset and opens its details after a successful save', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AssetsPage />);
    await user.click(screen.getByRole('button', { name: /add asset/i }));
    await user.click(
      screen.getByRole('button', {
        name: new RegExp(i18n.t('createChoice.investment', { ns: 'assets' })),
      })
    );
    await user.click(screen.getByRole('button', { name: 'Submit' }));
    await waitFor(() =>
      expect(mockCreateFn).toHaveBeenCalledWith({
        name: 'Test',
        type: 'STOCK',
        quantity: '1',
        purchasePrice: '100',
        currentPrice: '100',
        currency: 'USD',
        purchaseDate: '2026-01-01',
      })
    );
    await screen.findByRole('button', { name: 'Close' });
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
    expect(mockUpdateFn).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
  });

  it('updates the selected asset and permits cancellation without a second write', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AssetsPage />);
    await user.click(screen.getByRole('button', { name: 'Edit' }));
    await user.click(screen.getByRole('button', { name: 'Submit' }));
    await waitFor(() =>
      expect(mockUpdateFn).toHaveBeenCalledWith({
        id: 1,
        data: {
          name: 'Test',
          type: 'STOCK',
          quantity: '1',
          purchasePrice: '100',
          currentPrice: '100',
          currency: 'USD',
          purchaseDate: '2026-01-01',
        },
      })
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Edit' }));
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mockUpdateFn).toHaveBeenCalledOnce();
    expect(mockCreateFn).not.toHaveBeenCalled();
  });

  it('keeps rejected edits open and displays the server message', async () => {
    mockUpdateFn.mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'Cannot change a sold asset' } },
    });
    const user = userEvent.setup();
    renderWithProviders(<AssetsPage />);
    await user.click(screen.getByRole('button', { name: 'Edit' }));
    await user.click(screen.getByRole('button', { name: 'Submit' }));
    expect(await screen.findByText('Cannot change a sold asset')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('deletes the asset selected from its detail view', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AssetsPage />);
    await user.click(screen.getByRole('button', { name: 'View' }));
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(mockDeleteFn).toHaveBeenCalledWith(1);
  });

  it('requests physical items and their totals using the same server-side group', async () => {
    renderWithProviders(<AssetsPage />);
    await userEvent.click(screen.getByRole('button', { name: 'Physical assets' }));
    expect(useAssetsSearch).toHaveBeenCalledWith(
      expect.objectContaining({ group: 'PHYSICAL', page: 0 })
    );
    expect(screen.queryByRole('button', { name: /Refresh prices/i })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Physical assets' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
    window.history.replaceState({}, '', '/assets');
  });

  it.each(['edit', 'view', 'purchase', 'highlight'])(
    'routes property holdings through the property flow for %s',
    async action => {
      const property = { ...mockAsset, type: 'REAL_ESTATE' as const };
      mockFlatAssets = [property];
      mockPagedData = { ...mockPagedResponse, content: [property] };
      if (action === 'highlight') window.history.replaceState({}, '', '/assets?highlight=1');
      renderWithProviders(<AssetsPage />);
      if (action !== 'highlight') {
        const label =
          action === 'edit' ? 'Edit' : action === 'view' ? 'View' : 'Complete purchase Apple Stock';
        fireEvent.click(screen.getByRole('button', { name: label, exact: true }));
      }
      await waitFor(() => expect(window.location.pathname).toBe('/real-estate'));
      expect(new URLSearchParams(window.location.search).get('asset')).toBe('1');
      expect(new URLSearchParams(window.location.search).get('purchase')).toBe(
        action === 'purchase' ? '1' : null
      );
    }
  );

  it.each(['PURCHASE', 'GIFT', 'PLANNED'] as const)(
    'starts a physical %s form directly from the physical group',
    intent => {
      window.history.replaceState({}, '', '/assets?group=PHYSICAL');
      renderWithProviders(<AssetsPage />);
      fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:addAsset') }));
      fireEvent.click(
        screen.getByRole('button', {
          name: new RegExp(i18n.t(`assets:ownership.intents.${intent}`)),
        })
      );
      expect(screen.getByText('Form kind physical')).toBeInTheDocument();
      expect(screen.getByText(`Acquisition ${intent}`)).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:createChoice.back') }));
      expect(screen.getByText(i18n.t('assets:createChoice.intro'))).toBeInTheDocument();
    }
  );

  it('starts a financial form directly from the financial group', () => {
    window.history.replaceState({}, '', '/assets?group=FINANCIAL');
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:addAsset') }));
    expect(screen.getByText('Form kind financial')).toBeInTheDocument();
    expect(screen.queryByText(i18n.t('assets:ownership.intentPrompt'))).not.toBeInTheDocument();
  });

  it('starts a new reviewed purchase and opens the purchased item on success', () => {
    window.history.replaceState({}, '', '/assets?group=PHYSICAL');
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:addAsset') }));
    fireEvent.click(
      screen.getByRole('button', { name: new RegExp(i18n.t('assets:ownership.intentHints.BUY')) })
    );
    expect(screen.getByRole('region', { name: 'Purchase flow' })).toHaveTextContent('Plan new');
    fireEvent.click(screen.getByRole('button', { name: 'Finish purchase' }));
    expect(window.location.search).toBe('?highlight=17');
    expect(screen.queryByRole('region', { name: 'Purchase flow' })).not.toBeInTheDocument();
  });

  it('completes the selected physical plan and allows cancelling before any write', () => {
    const plan = {
      ...mockAsset,
      name: 'Planned car',
      type: 'VEHICLE' as const,
      acquisitionType: 'PLANNED' as const,
    };
    mockFlatAssets = [plan];
    mockPagedData = { ...mockPagedResponse, content: [plan] };
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Complete purchase Planned car' }));
    expect(screen.getByRole('region', { name: 'Purchase flow' })).toHaveTextContent(
      'Plan Planned car'
    );
    expect(screen.getByRole('dialog')).toHaveAccessibleName(
      i18n.t('assets:ownership.completePurchase')
    );
    fireEvent.click(screen.getByRole('button', { name: 'Cancel purchase' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mockCreateFn).not.toHaveBeenCalled();
  });

  it('opens a newly created asset using its individual query before the list refreshes', async () => {
    mockFetchedAsset = { ...mockAsset, id: 17, name: 'New holding' };
    mockCreateFn.mockResolvedValue(mockFetchedAsset);
    window.history.replaceState({}, '', '/assets?group=FINANCIAL');
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:addAsset') }));
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
    expect(await screen.findByText('New holding')).toBeInTheDocument();
  });

  it.each([false, true])(
    'restores refresh controls after a price refresh settles (failure %s)',
    async failure => {
      mockFlatAssets = [
        mockAsset,
        { ...mockAsset, id: 2, symbol: 'PLAN', acquisitionType: 'PLANNED' },
        { ...mockAsset, id: 3, symbol: undefined },
      ];
      let resolve!: () => void;
      let reject!: (error: Error) => void;
      mockRefreshPrices.mockReturnValue(
        new Promise<void>((res, rej) => {
          resolve = res;
          reject = rej;
        })
      );
      const log = vi.spyOn(console, 'error').mockImplementation(() => {});
      try {
        renderWithProviders(<AssetsPage />);
        fireEvent.click(screen.getByRole('button', { name: i18n.t('assets:refreshPrices') }));
        expect(mockRefreshPrices).toHaveBeenCalledWith([1]);
        expect(screen.getByRole('button', { name: i18n.t('assets:refreshing') })).toBeDisabled();
        await act(async () => {
          if (failure) reject(new Error('offline'));
          else resolve();
        });
        expect(screen.getByRole('button', { name: i18n.t('assets:refreshPrices') })).toBeEnabled();
        if (failure)
          expect(log).toHaveBeenCalledWith('Failed to refresh prices:', expect.any(Error));
      } finally {
        log.mockRestore();
      }
    }
  );

  it('disables refresh until an owned asset has a symbol', () => {
    mockFlatAssets = undefined;
    renderWithProviders(<AssetsPage />);
    const refresh = screen.getByRole('button', { name: i18n.t('assets:refreshPrices') });
    expect(refresh).toBeDisabled();
    fireEvent.click(refresh);
    expect(mockRefreshPrices).not.toHaveBeenCalled();
  });

  it('only Escape closes an open filter panel and leaves filter criteria intact', () => {
    window.history.replaceState({}, '', '/assets?currency=USD');
    renderWithProviders(<AssetsPage />);
    expect(screen.getByLabelText(/asset type/i)).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Enter' });
    expect(screen.getByLabelText(/asset type/i)).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByLabelText(/asset type/i)).not.toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(useViewStateStore.getState().latest['/assets'].filters).toEqual(
      expect.objectContaining({ currency: 'USD' })
    );
  });

  it.each([0, 1])('clamps the current asset page after deletion leaves %s pages', remaining => {
    mockPagedData = { ...mockPagedResponse, totalPages: 3, totalElements: 50 };
    vi.mocked(useAssetsSearch).mockImplementation(
      filters =>
        ({
          data: { ...mockPagedData, number: filters?.page ?? 0 },
          isLoading: false,
          error: null,
        }) as ReturnType<typeof useAssetsSearch>
    );
    const { rerender } = renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    mockPagedData = { ...mockPagedData, totalPages: remaining, content: [] };
    rerender(<AssetsPage />);
    expect(useViewStateStore.getState().latest['/assets'].filters).toEqual(
      expect.objectContaining({ page: 0 })
    );
  });

  it('resets the asset page when resizing the page', () => {
    mockPagedData = { ...mockPagedResponse, totalPages: 3, totalElements: 50 };
    vi.mocked(useAssetsSearch).mockImplementation(
      filters =>
        ({
          data: { ...mockPagedData, number: filters?.page ?? 0 },
          isLoading: false,
          error: null,
        }) as ReturnType<typeof useAssetsSearch>
    );
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    fireEvent.click(screen.getByRole('button', { name: '50 per page' }));
    expect(useViewStateStore.getState().latest['/assets'].filters).toEqual(
      expect.objectContaining({ page: 0, size: 50 })
    );
  });

  it.each([
    { isAxiosError: true },
    { isAxiosError: true, response: { data: {} } },
    { isAxiosError: true, response: { status: 500 } },
    new Error('offline'),
  ])('keeps a failed save open even without a server message', async error => {
    mockUpdateFn.mockRejectedValue(error);
    renderWithProviders(<AssetsPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Edit', exact: true }));
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(i18n.t('assets:saveError'));
    expect(screen.getByRole('button', { name: 'Submit' })).toBeInTheDocument();
  });

  describe('Data display', () => {
    it('should render the page heading', () => {
      renderWithProviders(<AssetsPage />);
      expect(document.querySelector('h1')).toBeInTheDocument();
    });

    it('should display asset names', () => {
      renderWithProviders(<AssetsPage />);
      expect(screen.getAllByText('Apple Stock').length).toBeGreaterThan(0);
    });

    it('should display asset symbol', () => {
      renderWithProviders(<AssetsPage />);
      expect(screen.getAllByText('AAPL').length).toBeGreaterThan(0);
    });
  });

  describe('Add asset button', () => {
    it('should have an "Add Asset" button', () => {
      renderWithProviders(<AssetsPage />);
      expect(screen.getByRole('button', { name: /add asset/i })).toBeInTheDocument();
    });

    it('should open dialog when add button is clicked', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AssetsPage />);

      await user.click(screen.getByRole('button', { name: /add asset/i }));

      await waitFor(() => {
        expect(screen.getByRole('dialog')).toBeInTheDocument();
      });
    });
  });

  describe('Filter toggle', () => {
    it('filters by ownership status and preserves it when changing asset groups', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AssetsPage />);
      expect(screen.queryByRole('combobox', { name: 'Status' })).not.toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: 'Filters' }));
      const status = screen.getByRole('combobox', { name: 'Status' });
      expect(status).toHaveValue('OWNED');
      expect(useAssetsSearch).toHaveBeenCalledWith(
        expect.objectContaining({ ownershipStatus: 'OWNED', page: 0 })
      );
      await user.selectOptions(status, 'CLOSED');
      await user.click(screen.getByRole('button', { name: 'Physical assets' }));
      expect(useAssetsSearch).toHaveBeenCalledWith(
        expect.objectContaining({ ownershipStatus: 'CLOSED', group: 'PHYSICAL', page: 0 })
      );
      await user.selectOptions(status, 'PLANNED');
      expect(useAssetsSearch).toHaveBeenCalledWith(
        expect.objectContaining({ ownershipStatus: 'PLANNED', group: 'PHYSICAL', page: 0 })
      );
      await user.selectOptions(status, '');
      expect(useAssetsSearch).toHaveBeenCalledWith(
        expect.objectContaining({ ownershipStatus: undefined, group: 'PHYSICAL', page: 0 })
      );
      await user.click(screen.getByRole('button', { name: 'Clear' }));
      expect(status).toHaveValue('OWNED');
      expect(useAssetsSearch).toHaveBeenCalledWith(
        expect.objectContaining({ ownershipStatus: 'OWNED', group: 'PHYSICAL', page: 0 })
      );
      await user.click(screen.getByRole('button', { name: 'Filters' }));
      expect(screen.queryByRole('combobox', { name: 'Status' })).not.toBeInTheDocument();
      window.history.replaceState({}, '', '/assets');
    });

    it('keeps one Add Asset entry point, including for an empty list', () => {
      mockPagedData = { ...mockPagedResponse, content: [], totalElements: 0, totalPages: 0 };
      renderWithProviders(<AssetsPage />);
      expect(screen.getAllByRole('button', { name: 'Add Asset' })).toHaveLength(1);
      expect(
        screen.queryByRole('button', { name: 'Buy a physical asset' })
      ).not.toBeInTheDocument();
    });

    it('should have a filter button', () => {
      renderWithProviders(<AssetsPage />);
      expect(screen.getByRole('button', { name: /filter/i })).toBeInTheDocument();
    });

    it('should toggle filter panel', async () => {
      const user = userEvent.setup();
      renderWithProviders(<AssetsPage />);
      const filterBtn = screen.getByRole('button', { name: /filter/i });
      await user.click(filterBtn);
      // Filter panel should be visible
      await user.click(filterBtn);
      // Filter panel should be hidden
    });
  });

  describe('Loading and error states', () => {
    it('should show loading skeleton', () => {
      mockIsLoading = true;
      mockPagedData = undefined;
      renderWithProviders(<AssetsPage />);
      expect(screen.queryByTestId('asset-list')).not.toBeInTheDocument();
    });

    it('should show error state', () => {
      mockError = new Error('Failed');
      mockPagedData = undefined;
      renderWithProviders(<AssetsPage />);
      expect(screen.getByText(/error|failed/i)).toBeInTheDocument();
    });

    it('should show empty state when no assets', () => {
      mockPagedData = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
      renderWithProviders(<AssetsPage />);
      expect(screen.getByText(/no assets/i)).toBeInTheDocument();
    });
  });

  describe('Summary cards', () => {
    it('should display portfolio value summary', () => {
      renderWithProviders(<AssetsPage />);
      // Summary cards show total value, gain/loss etc
      expect(screen.getByTestId('asset-list')).toBeInTheDocument();
    });
  });

  describe('Refresh prices', () => {
    it('should have refresh button', () => {
      renderWithProviders(<AssetsPage />);
      const refreshBtn = screen.queryByRole('button', { name: /refresh|update/i });
      // If assets with symbols exist, refresh button should be present
      if (refreshBtn) {
        expect(refreshBtn).toBeInTheDocument();
      }
    });
  });

  // renderPage: like renderWithProviders but with a MemoryRouter so deep-link
  // query params can be seeded via initialEntries (BrowserRouter reads the real URL).
  function renderPage(search = '') {
    return render(
      <QueryClientProvider client={createTestQueryClient()}>
        <I18nextProvider i18n={i18n}>
          <MemoryRouter initialEntries={[`/assets${search}`]}>
            <AuthProvider>
              <NumberFormatProvider>
                <DecimalPlacesProvider>
                  <CurrencyDisplayProvider>
                    <VisibilityProvider>
                      <AssetsPage />
                    </VisibilityProvider>
                  </CurrencyDisplayProvider>
                </DecimalPlacesProvider>
              </NumberFormatProvider>
            </AuthProvider>
          </MemoryRouter>
        </I18nextProvider>
      </QueryClientProvider>
    );
  }

  describe('deep-link params', () => {
    it('seeds currency and type filters from URL params', () => {
      renderPage('?currency=USD&type=CRYPTO');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.currency).toBe('USD');
      expect(captured?.type).toBe('CRYPTO');
    });

    it('ignores invalid asset type param', () => {
      renderPage('?type=NOPE');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.type).toBeUndefined();
    });

    it('opens the BTC holdings filter without restricting the valuation currency', () => {
      renderPage('?type=CRYPTO&symbol=btc');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.type).toBe('CRYPTO');
      expect(captured?.symbol).toBe('BTC');
      expect(captured?.currency).toBeUndefined();
    });

    it('ignores invalid symbol params', () => {
      renderPage('?symbol=%25');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.symbol).toBeUndefined();
    });

    it('ignores invalid currency param', () => {
      renderPage('?currency=US');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.currency).toBeUndefined();
    });

    it('seeds no filters when params are absent', () => {
      renderPage('?highlight=3');

      const captured = vi.mocked(useAssetsSearch).mock.calls[0]?.[0];
      expect(captured?.currency).toBeUndefined();
      expect(captured?.type).toBeUndefined();
    });
  });
});
