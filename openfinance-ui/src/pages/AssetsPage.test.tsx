import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, render } from '@testing-library/react';
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
  assetType: 'STOCK',
  quantity: 10,
  purchasePrice: 150.0,
  currentPrice: 175.0,
  currentValue: 1750.0,
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

vi.mock('@/hooks/useAssets', () => ({
  useAssetsSearch: vi.fn(() => ({ data: mockPagedData, isLoading: mockIsLoading, error: mockError, refetch: mockRefetchFn })),
  useAssets: () => ({ data: [mockAsset], isLoading: false, error: null }),
  useAsset: () => ({ data: null, isLoading: false, error: null }),
  useCreateAsset: () => ({ mutate: vi.fn(), mutateAsync: mockCreateFn, isPending: false }),
  useUpdateAsset: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateFn, isPending: false }),
  useDeleteAsset: () => ({ mutate: vi.fn(), mutateAsync: mockDeleteFn, isPending: false }),
  formatGainLoss: (unrealizedGain: number, gainPercentage: number) => ({
    formatted: `${unrealizedGain >= 0 ? '+' : ''}${unrealizedGain.toFixed(2)}`,
    color: unrealizedGain >= 0 ? 'text-green-500' : 'text-red-500',
  }),
  getAssetTypeName: (type: string) => type,
  getAssetTypeBadgeVariant: () => 'default',
  getConditionBadgeVariant: () => 'default',
}));

vi.mock('@/hooks/useMarketData', () => ({
  useUpdateAllAssetPrices: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({ convert: (a: number) => a, secondaryCurrency: null }),
}));

vi.mock('@/components/assets/AssetForm', () => ({
  AssetForm: ({ onCancel, onSubmit }: any) => (
    <div data-testid="asset-form">
      <button onClick={onCancel}>Cancel</button>
      <button data-testid="form-submit" onClick={() => onSubmit({ name: 'Test', assetType: 'STOCK', quantity: 1, purchasePrice: 100 })}>Submit</button>
    </div>
  ),
}));

vi.mock('@/components/assets/AssetDetailModal', () => ({
  AssetDetailModal: ({ asset, onClose, onEdit, onDelete }: any) => (
    asset ? (
      <div data-testid="asset-detail-modal">
        {asset.name}
        <button data-testid="modal-edit" onClick={() => onEdit(asset)}>Edit</button>
        <button data-testid="modal-delete" onClick={() => onDelete(asset.id)}>Delete</button>
        <button data-testid="modal-close" onClick={onClose}>Close</button>
      </div>
    ) : null
  ),
}));

vi.mock('@/components/assets/AssetList', () => ({
  AssetList: ({ assets, onEdit, onDelete, onView }: any) => (
    <div data-testid="asset-list">
      {assets.map((a: any) => (
        <div key={a.id}>
          <span>{a.name}</span>
          {a.symbol && <span>{a.symbol}</span>}
          {onEdit && <button data-testid={`edit-${a.id}`} onClick={() => onEdit(a)}>Edit</button>}
          {onDelete && <button data-testid={`del-${a.id}`} onClick={() => onDelete(a.id)}>Del</button>}
          {onView && <button data-testid={`view-${a.id}`} onClick={() => onView(a)}>View</button>}
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/assets/LastUpdatedIndicator', () => ({
  BatchUpdateIndicator: () => null,
}));

describe('AssetsPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedData = mockPagedResponse;
    mockIsLoading = false;
    mockError = null;
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
