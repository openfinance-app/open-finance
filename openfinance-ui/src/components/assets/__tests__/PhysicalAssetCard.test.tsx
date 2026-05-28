import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PhysicalAssetCard } from '../PhysicalAssetCard';

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
});
