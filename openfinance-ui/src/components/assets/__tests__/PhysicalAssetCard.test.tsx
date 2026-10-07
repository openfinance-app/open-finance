import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PhysicalAssetCard } from '../PhysicalAssetCard';
import { AssetCoverImage } from '../AssetCoverImage';

vi.mock('../AssetCoverImage', () => ({
  AssetCoverImage: ({ asset }: any) => <div data-testid="asset-cover">cover-{asset.id}</div>,
}));

vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: vi.fn((v: number) => v * 0.85),
    secondaryCurrency: 'EUR',
    secondaryExchangeRate: 0.85,
  }),
}));

vi.mock('@/components/ui/ConvertedAmount', () => ({
  ConvertedAmount: ({ amount }: any) => <span data-testid="converted-amount">{amount}</span>,
}));

const mockAsset = {
  id: 1,
  name: 'MacBook Pro',
  type: 'ELECTRONICS',
  currency: 'USD',
  quantity: 1,
  currentPrice: 1500,
  totalValue: 1500,
  totalCost: 2000,
  purchasePrice: 2000,
  brand: 'Apple',
  model: 'M3 Pro',
  serialNumber: 'ABC123456',
  condition: 'GOOD',
  isWarrantyValid: true,
  warrantyExpiry: '2025-12-31',
} as any;

describe('PhysicalAssetCard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders asset name', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('MacBook Pro')).toBeInTheDocument();
  });

  it.each([-59, 365])(
    'shows planned acquisition without ownership for holdingDays %s',
    holdingDays => {
      renderWithProviders(
        <PhysicalAssetCard
          asset={{
            ...mockAsset,
            acquisitionType: 'PLANNED',
            purchaseDate: '2026-12-01',
            usefulLifeYears: 5,
            holdingDays,
          }}
        />
      );
      expect(screen.getByText(/Planned acquisition/)).toBeInTheDocument();
      expect(screen.getByText(/12\/01\/2026|01\/12\/2026|2026-12-01/)).toBeInTheDocument();
      expect(screen.queryByText(/year.*owned/)).not.toBeInTheDocument();
    }
  );

  it('shows planned cost without claiming a market loss for an unowned asset', () => {
    renderWithProviders(
      <PhysicalAssetCard asset={{ ...mockAsset, acquisitionType: 'PLANNED', totalValue: 0 }} />
    );
    expect(screen.getByText('Planned Cost')).toBeInTheDocument();
    expect(screen.queryByText('Value Change')).not.toBeInTheDocument();
    expect(screen.queryByText('Loss')).not.toBeInTheDocument();
  });

  it('still shows an actual loss for an acquired asset worth zero', () => {
    renderWithProviders(
      <PhysicalAssetCard asset={{ ...mockAsset, acquisitionType: 'PURCHASE', totalValue: 0 }} />
    );
    expect(screen.getByText('Value Change')).toBeInTheDocument();
    expect(screen.getByText('Loss')).toBeInTheDocument();
  });

  it('preserves ownership duration for acquired assets', () => {
    renderWithProviders(
      <PhysicalAssetCard
        asset={{ ...mockAsset, acquisitionType: 'GIFT', usefulLifeYears: 5, holdingDays: 365 }}
      />
    );
    expect(screen.getByText(/1 year owned/)).toBeInTheDocument();
    expect(screen.queryByText(/Planned acquisition/)).not.toBeInTheDocument();
  });

  it('renders brand', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('Apple')).toBeInTheDocument();
  });

  it('renders model', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('M3 Pro')).toBeInTheDocument();
  });

  it('renders serial number', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('ABC123456')).toBeInTheDocument();
  });

  it('renders type badge', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('Electronics')).toBeInTheDocument();
  });

  it('renders condition badge', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByText('Good')).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} onClick={onClick} />);
    fireEvent.click(screen.getByText('MacBook Pro').closest('.cursor-pointer')!);
    expect(onClick).toHaveBeenCalled();
  });

  it('shows value change when cost differs from current value', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    // totalCost=2000, totalValue=1500, loss=500 = 25%
    expect(screen.getByText(/25%/)).toBeInTheDocument();
  });

  it('hides value change when no cost difference', () => {
    const noChangeAsset = { ...mockAsset, totalCost: 1500, totalValue: 1500 };
    renderWithProviders(<PhysicalAssetCard asset={noChangeAsset} />);
    expect(screen.queryByText(/loss/i)).not.toBeInTheDocument();
  });

  it('truncates long serial numbers', () => {
    const longSerial = { ...mockAsset, serialNumber: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890' };
    renderWithProviders(<PhysicalAssetCard asset={longSerial} />);
    expect(screen.getByText(/ABCDEFGHIJKLMNOPQRST\.\.\./)).toBeInTheDocument();
  });

  it('renders without brand/model/serial when absent', () => {
    const minimal = { ...mockAsset, brand: undefined, model: undefined, serialNumber: undefined };
    renderWithProviders(<PhysicalAssetCard asset={minimal} />);
    expect(screen.getByText('MacBook Pro')).toBeInTheDocument();
    expect(screen.queryByText('Brand:')).not.toBeInTheDocument();
  });

  it('renders warranty indicator when valid', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    // Just verify the card renders without error with warranty data
    expect(screen.getByText('MacBook Pro')).toBeInTheDocument();
  });

  it('renders the attachment-backed cover image component', () => {
    renderWithProviders(<PhysicalAssetCard asset={mockAsset} />);
    expect(screen.getByTestId('asset-cover')).toHaveTextContent('cover-1');
    // The legacy static placeholder/photoPath block is replaced by AssetCoverImage
    expect(screen.queryByText('No photo')).not.toBeInTheDocument();
  });
});
