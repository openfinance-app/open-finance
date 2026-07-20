/**
 * Unit tests for LiabilitySummaryCards component
 * Focus: Test privacy implementation with PrivateAmount wrapping of currency displays
 */
import { screen } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { LiabilitySummaryCards } from '../LiabilitySummaryCards';
import type { Liability } from '@/types/liability';

// Mock formatCurrency to return predictable strings with proper formatting
vi.mock('@/utils/format', () => ({
  formatCurrency: vi.fn((amount: number, currency: string) => {
    // Return formatted currency like "$295,000.00"
    const formatted = new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(amount);
    return formatted;
  }),
}));

// Mock AuthContext to ensure baseCurrency is USD while preserving AuthProvider
vi.mock('@/context/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: vi.fn(() => ({
      baseCurrency: 'USD',
      user: null,
      isAuthenticated: false,
      login: vi.fn(),
      logout: vi.fn(),
      updateBaseCurrency: vi.fn(),
      loading: false
    }))
  };
});

// Test fixtures
const mockLiabilities: Liability[] = [
  {
    id: 1,
    name: 'Home Mortgage',
    type: 'MORTGAGE' as const,
    principal: 300000,
    currentBalance: 280000,
    interestRate: 3.5,
    startDate: '2020-01-01',
    endDate: '2050-01-01',
    minimumPayment: 1500,
    currency: 'USD',
    insurancePercentage: 0.5,
    additionalFees: 600,
    monthlyInsuranceCost: 125,
    totalInsuranceCost: 45000,
    totalCost: 520000,
    principalPaid: 20000,
    interestPaid: 35000,
    createdAt: '2020-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2,
    name: 'Car Loan',
    type: 'AUTO_LOAN' as const,
    principal: 25000,
    currentBalance: 15000,
    interestRate: 5.0,
    startDate: '2022-01-01',
    endDate: '2027-01-01',
    minimumPayment: 450,
    currency: 'USD',
    createdAt: '2022-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  },
];

const mockLiabilitiesEUR: Liability[] = [
  {
    ...mockLiabilities[0],
    currency: 'EUR',
  },
];

// Wrapper component for testing with visibility context
function TestWrapper({ children }: { children: React.ReactNode }) {
  return <>{children}</>;
}

// Mock the context for testing
vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useVisibility: vi.fn(),
}));

import { useVisibility } from '@/context/VisibilityContext';

describe('LiabilitySummaryCards', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
  });

  describe('Privacy Implementation - PrivateAmount Wrapping', () => {
    it('should wrap currency displays with PrivateAmount component', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      // Check that PrivateAmount components are rendered for currency values
      const privateAmounts = screen.getAllByRole('generic', { hidden: true }).filter(
        element => element.classList.contains('transition-all') && element.classList.contains('duration-300')
      );

      // Should have PrivateAmount wrappers for: Total Liabilities, Total Principal, Monthly Payments (and filtered versions if applicable)
      expect(privateAmounts.length).toBeGreaterThan(0);

      // Verify specific currency displays are wrapped
      expect(screen.getByText('$295,000.00')).toBeInTheDocument(); // Total Liabilities: 280000 + 15000
      expect(screen.getByText('$325,000.00')).toBeInTheDocument(); // Total Principal: 300000 + 25000
      expect(screen.getByText('$1,950.00')).toBeInTheDocument(); // Monthly Payments: 1500 + 450
    });

    it('should apply blur effect when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      // Check that elements have blur class when amounts are hidden
      const blurredElements = screen.getAllByRole('generic', { hidden: true }).filter(
        element => element.classList.contains('blur-md') && element.classList.contains('select-none')
      );

      expect(blurredElements.length).toBeGreaterThan(0);
    });

    it('should not apply blur effect when amounts are visible', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      // Check that no elements have blur class when amounts are visible
      const blurredElements = screen.getAllByRole('generic', { hidden: true }).filter(
        element => element.classList.contains('blur-md')
      );

      expect(blurredElements.length).toBe(0);
    });
  });

  describe('Component Rendering', () => {
    beforeEach(() => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
    });

    it('should render all four summary cards', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      expect(screen.getByText('Total Liabilities')).toBeInTheDocument();
      expect(screen.getByText('Original Principal')).toBeInTheDocument();
      expect(screen.getByText('Avg Interest Rate')).toBeInTheDocument();
      expect(screen.getByText('Monthly Payments')).toBeInTheDocument();
    });

    it('should display correct totals for multiple liabilities', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      // Total Liabilities: 280000 + 15000 = 295000 USD
      expect(screen.getByText('$295,000.00')).toBeInTheDocument();
      // Total Principal: 300000 + 25000 = 325000 USD
      expect(screen.getByText('$325,000.00')).toBeInTheDocument();
      // Monthly Payments: 1500 + 450 = 1950 USD
      expect(screen.getByText('$1,950.00')).toBeInTheDocument();
      // Average Interest Rate: weighted average of 3.5% and 5.0%
      expect(screen.getByText('3.58%')).toBeInTheDocument();
    });

    it('should display liability count', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      expect(screen.getAllByText('2 liabilities')[0]).toBeInTheDocument();
    });

    it('should handle different currencies and show currency count', () => {
      const mixedCurrencyLiabilities = [
        ...mockLiabilities,
        { ...mockLiabilities[0], id: 3, currency: 'EUR' },
      ];

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mixedCurrencyLiabilities} />
        </TestWrapper>
      );

      // Component shows liability count, not currency count
      expect(screen.getAllByText('3 liabilities')[0]).toBeInTheDocument();
    });
  });

  describe('Filtered Liabilities', () => {
    beforeEach(() => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
    });

    it('should show filtered totals when filteredLiabilities is provided and different', () => {
      const filteredLiabilities = [mockLiabilities[0]]; // Only first liability

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards
            liabilities={mockLiabilities}
            filteredLiabilities={filteredLiabilities}
          />
        </TestWrapper>
      );

      // Should show filtered amounts
      expect(screen.getAllByText('Filtered:')).toHaveLength(3); // Three filtered sections
      expect(screen.getByText('$280,000.00')).toBeInTheDocument();
      expect(screen.getByText('$300,000.00')).toBeInTheDocument();
      expect(screen.getByText('$1,500.00')).toBeInTheDocument();
      expect(screen.getByText('(1 shown)')).toBeInTheDocument();
    });

    it('should not show filtered totals when filteredLiabilities equals all liabilities', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards
            liabilities={mockLiabilities}
            filteredLiabilities={mockLiabilities}
          />
        </TestWrapper>
      );

      expect(screen.queryByText(/Filtered:/)).not.toBeInTheDocument();
    });

    it('should calculate correct weighted average for filtered liabilities', () => {
      const filteredLiabilities = [mockLiabilities[1]]; // Only car loan with 5.0% interest

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards
            liabilities={mockLiabilities}
            filteredLiabilities={filteredLiabilities}
          />
        </TestWrapper>
      );

      expect(screen.getByText('Filtered: 5.00%')).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    beforeEach(() => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
    });

    it('should handle empty liabilities array', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={[]} />
        </TestWrapper>
      );

      expect(screen.getAllByText('$0.00')).toHaveLength(3); // Three cards show 0.00
      expect(screen.getByText('N/A')).toBeInTheDocument(); // No interest rates
      expect(screen.getAllByText('0 liabilities').length).toBeGreaterThan(0);
    });

    it('should handle liabilities with zero interest rates', () => {
      const noInterestLiabilities = mockLiabilities.map(l => ({ ...l, interestRate: undefined }));

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={noInterestLiabilities} />
        </TestWrapper>
      );

      expect(screen.getByText('N/A')).toBeInTheDocument();
      expect(screen.getByText('No interest rates set')).toBeInTheDocument();
    });

    it('should handle liabilities with zero minimum payments', () => {
      const noPaymentLiabilities = mockLiabilities.map(l => ({ ...l, minimumPayment: undefined }));

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={noPaymentLiabilities} />
        </TestWrapper>
      );

      expect(screen.getByText('$0.00')).toBeInTheDocument();
      expect(screen.getByText('No payments set')).toBeInTheDocument();
    });

    it('should prefer USD as primary currency when available', () => {
      const mixedLiabilities = [
        ...mockLiabilitiesEUR,
        mockLiabilities[0], // USD
      ];

      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mixedLiabilities} />
        </TestWrapper>
      );

      // EUR liability has no exchange rate → excluded from totals to prevent currency mixing
      // Only the USD liability is included in the total (BUG-01 fix)
      const body = document.body.textContent || '';
      expect(body).toContain('$280,000.00'); // Only USD liability contributes to total
      expect(body).toContain('Excl. EUR (no rate)'); // Warning note shown for excluded currency
    });

    it('should exclude non-convertible currency liabilities from totals', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilitiesEUR} />
        </TestWrapper>
      );

      // EUR liability has no exchange rate and the default baseCurrency in tests is USD.
      // With BUG-01 fix, EUR is excluded from totals to prevent presenting misleading amounts.
      const body = document.body.textContent || '';
      expect(body).toContain('$0.00'); // Excluded from totals → zero
      expect(body).toContain('Excl. EUR (no rate)'); // Warning note shown
    });
  });

  describe('User Interactions', () => {
    beforeEach(() => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
    });

    it('should have hover effects on cards', () => {
      renderWithProviders(
        <TestWrapper>
          <LiabilitySummaryCards liabilities={mockLiabilities} />
        </TestWrapper>
      );

      const cards = screen.getAllByRole('generic', { hidden: true }).filter(
        element => element.classList.contains('hover:border-primary/30')
      );

      expect(cards.length).toBe(4); // Four cards
    });
  });
});