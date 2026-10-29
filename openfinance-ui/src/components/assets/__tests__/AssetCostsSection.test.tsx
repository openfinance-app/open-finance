/**
 * Unit tests for AssetCostsSection (Task 8)
 *
 * Verifies the asset Costs list: improvement / maintenance transactions
 * fetched via the assetId transaction filter.
 */
import { screen } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { AssetCostsSection } from '../AssetCostsSection';
import * as useTransactionsModule from '@/hooks/useTransactions';
import type { Asset } from '@/types/asset';
import type { Transaction } from '@/types/transaction';

vi.mock('@/hooks/useTransactions', async importOriginal => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return {
    ...actual,
    useTransactions: vi.fn(() => ({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 0 },
      isLoading: false,
      error: null,
    })),
  };
});

const mockUseTransactions = vi.mocked(useTransactionsModule.useTransactions);

const mockAsset = {
  id: 3,
  name: 'Vintage Car',
  type: 'VEHICLE',
  quantity: 1,
  purchasePrice: 20000,
  currentPrice: 25000,
  totalValue: 25000,
  totalCost: 20000,
  unrealizedGain: 5000,
  gainPercentage: 0.25,
  currency: 'USD',
  purchaseDate: '2024-01-01',
  holdingDays: 300,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
} as unknown as Asset;

const restorationTx: Transaction = {
  id: 201,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 8000,
  currency: 'USD',
  date: '2026-01-15',
  description: 'Engine restoration',
  isReconciled: false,
  createdAt: '2026-01-15T00:00:00Z',
  assetId: 3,
  movementType: 'CAPITAL_IMPROVEMENT',
};

const tiresTx: Transaction = {
  id: 202,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 600,
  currency: 'USD',
  date: '2026-02-20',
  description: 'New tires',
  isReconciled: false,
  createdAt: '2026-02-20T00:00:00Z',
  assetId: 3,
  movementType: 'MAINTENANCE',
};

describe('AssetCostsSection', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    mockUseTransactions.mockReturnValue({
      data: {
        content: [restorationTx, tiresTx],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 2,
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
  });

  it('renders improvement costs under the capitalized list', () => {
    renderWithProviders(<AssetCostsSection asset={mockAsset} />);

    expect(screen.getByText('Costs')).toBeInTheDocument();
    expect(screen.getByText('Engine restoration')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Engine restoration/ })).toHaveAttribute(
      'href',
      '/transactions?highlight=201'
    );
    expect(screen.getByText('$8,000.00')).toBeInTheDocument();
  });

  it('renders upkeep costs under the maintenance list', () => {
    renderWithProviders(<AssetCostsSection asset={mockAsset} />);

    expect(screen.getByText('New tires')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /New tires/ })).toHaveAttribute(
      'href',
      '/transactions?highlight=202'
    );
    expect(screen.getByText('$600.00')).toBeInTheDocument();
  });

  it('directs investment costs to transactions instead of offering maintenance', () => {
    renderWithProviders(
      <AssetCostsSection asset={{ ...mockAsset, type: 'STOCK', isPhysical: false }} />
    );
    expect(screen.queryByRole('button', { name: 'Add cost' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open transactions' })).toHaveAttribute(
      'href',
      '/transactions'
    );
  });

  it('fetches transactions filtered by assetId', () => {
    renderWithProviders(<AssetCostsSection asset={mockAsset} />);

    expect(mockUseTransactions).toHaveBeenCalledWith(expect.objectContaining({ assetId: 3 }));
  });

  it('renders the empty state when the asset has no linked costs', () => {
    mockUseTransactions.mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 0 },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);

    renderWithProviders(<AssetCostsSection asset={mockAsset} />);

    expect(screen.getByText(/no costs recorded yet/i)).toBeInTheDocument();
  });

  it('renders the error banner when the costs query is rejected', () => {
    mockUseTransactions.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Rejected'),
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);

    renderWithProviders(<AssetCostsSection asset={mockAsset} />);

    expect(screen.getByText(/failed to load costs/i)).toBeInTheDocument();
    expect(screen.queryByText(/no costs recorded yet/i)).not.toBeInTheDocument();
  });
});
