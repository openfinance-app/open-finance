/**
 * Unit tests for LiabilityBreakdownPanel component
 * Task 22: Test LiabilityBreakdownPanel component showing full cost breakdown
 *
 * Requirement 2.1: Test comprehensive cost breakdown display
 * Requirement 3.2: Test linked transactions summary display
 */
import { screen, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { LiabilityBreakdownPanel } from '../LiabilityBreakdownPanel';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { Liability } from '@/types/liability';

// Mock the hooks
vi.mock('@/hooks/useLiabilities', async importOriginal => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return {
    ...actual,
    useLiabilityBreakdown: vi.fn(),
  };
});

const mockUseLiabilityBreakdown = vi.mocked(useLiabilitiesModule.useLiabilityBreakdown);

// Mock Lucide icons
vi.mock('lucide-react', () => ({
  TrendingDown: () => <div data-testid="trending-down-icon" />,
  DollarSign: () => <div data-testid="dollar-sign-icon" />,
  Shield: () => <div data-testid="shield-icon" />,
  AlertCircle: () => <div data-testid="alert-circle-icon" />,
  CreditCard: () => <div data-testid="credit-card-icon" />,
  RefreshCcw: () => <div data-testid="refresh-ccw-icon" />,
}));

// Mock VisibilityContext
vi.mock('@/context/VisibilityContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/VisibilityContext')>();
  return {
    ...actual,
    useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
  };
});

import { useVisibility } from '@/context/VisibilityContext';

// Test fixtures
const mockLiability: Liability = {
  id: 1,
  name: 'Home Mortgage',
  type: 'MORTGAGE',
  principal: 300000,
  currentBalance: 280000,
  interestRate: 3.5,
  startDate: '2020-01-01',
  endDate: '2050-01-01',
  minimumPayment: 1500,
  currency: 'EUR',
  insurancePercentage: 0.5,
  additionalFees: 600,
  monthlyInsuranceCost: 125,
  totalInsuranceCost: 45000,
  totalCost: 520000,
  principalPaid: 20000,
  interestPaid: 35000,
  createdAt: '2020-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const mockBreakdown = {
  liabilityId: 1,
  name: 'Home Mortgage',
  currency: 'EUR',
  currentBalance: 280000,
  principal: 300000,
  principalPaid: 20000,
  interestPaid: 35000,
  insurancePaid: 5000,
  feesPaid: 600, // one-time fee
  totalPaid: 60600,
  projectedInterest: 150000,
  projectedInsurance: 40000,
  projectedFees: 0, // always 0 now (one-time fee)
  totalProjectedCost: 470000,
  linkedTransactionCount: 3,
  linkedTransactionsTotalAmount: 4500,
};

describe('LiabilityBreakdownPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Loading States', () => {
    it('renders loading skeleton when breakdown is loading', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      } as any);

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      // Should show loading skeleton (3 animated divs)
      const skeletonContainer = document.querySelector('.animate-pulse');
      expect(skeletonContainer).toBeInTheDocument();
      const skeletonDivs = skeletonContainer?.querySelectorAll('.h-28');
      expect(skeletonDivs).toHaveLength(3);
    });
  });

  describe('Error States', () => {
    it('renders error message when breakdown fails to load', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load breakdown'),
      } as any);

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      expect(screen.getByText('Failed to load breakdown. Please try again.')).toBeInTheDocument();
      expect(screen.getByTestId('alert-circle-icon')).toBeInTheDocument();
    });
  });

  describe('Successful Data Rendering', () => {
    beforeEach(() => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      } as any);
    });

    it('renders progress bar with correct percentage', () => {
      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      expect(screen.getByText('Principal Paid Off')).toBeInTheDocument();
      expect(screen.getByText('6.7%')).toBeInTheDocument();
    });

    it('renders amounts paid to date section', () => {
      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      expect(screen.getByText('Amounts Paid to Date')).toBeInTheDocument();
      expect(screen.getByTestId('dollar-sign-icon')).toBeInTheDocument();

      expect(screen.getByText('Principal Paid')).toBeInTheDocument();
      expect(screen.getByText('Interest Paid')).toBeInTheDocument();
      expect(screen.getByText('Insurance Paid')).toBeInTheDocument();
      expect(screen.getByText('One-time Fee (paid)')).toBeInTheDocument();
      expect(screen.getByText('Total Paid')).toBeInTheDocument();
    });

    it('renders projected remaining costs section', () => {
      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      expect(screen.getByText('Projected Remaining Costs')).toBeInTheDocument();
      expect(screen.getByTestId('trending-down-icon')).toBeInTheDocument();

      expect(screen.getByText('Remaining Balance')).toBeInTheDocument();
      expect(screen.getByText('Projected Interest')).toBeInTheDocument();
      expect(screen.getByText('Projected Insurance')).toBeInTheDocument();
      expect(screen.getByText('Total Projected Cost')).toBeInTheDocument();
    });

    it('renders insurance & fees configuration when present', () => {
      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      expect(screen.getByText('Insurance & Fee Configuration')).toBeInTheDocument();
      expect(screen.getByTestId('shield-icon')).toBeInTheDocument();

      expect(screen.getByText('Annual Insurance Rate (0.50%)')).toBeInTheDocument();
      expect(screen.getByText('One-time Fee')).toBeInTheDocument();
    });

    it('does not render insurance & fees section when not configured', () => {
      const liabilityWithoutInsurance = {
        ...mockLiability,
        insurancePercentage: undefined,
        additionalFees: undefined,
      };

      renderWithProviders(<LiabilityBreakdownPanel liability={liabilityWithoutInsurance} />);

      expect(screen.queryByText('Insurance & Fee Configuration')).not.toBeInTheDocument();
    });

    it('conditionally renders insurance and fees in breakdown based on values', () => {
      const breakdownWithZeroInsurance = {
        ...mockBreakdown,
        insurancePaid: 0,
        projectedInsurance: 0,
      };

      mockUseLiabilityBreakdown.mockReturnValue({
        data: breakdownWithZeroInsurance,
        isLoading: false,
        error: null,
      } as any);

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      // Insurance should not appear in paid section when 0
      expect(screen.queryByText('Insurance Paid')).not.toBeInTheDocument();
      // Should not appear in projected when 0
      expect(screen.queryByText('Projected Insurance')).not.toBeInTheDocument();
    });
  });

  describe('Privacy Implementation - PrivateAmount Wrapping', () => {
    it('should wrap currency displays with PrivateAmount when amounts are visible', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      // Check that PrivateAmount components are present
      const privateAmounts = document.querySelectorAll('.transition-all.duration-300');
      expect(privateAmounts.length).toBeGreaterThan(0);
    });

    it('should apply blur effect to currency displays when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      // Check that PrivateAmount components have blur classes
      const blurredAmounts = document.querySelectorAll('.blur-md.select-none');
      expect(blurredAmounts.length).toBeGreaterThan(0);
    });

    it('should set aria-hidden when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(<LiabilityBreakdownPanel liability={mockLiability} />);

      // Check that PrivateAmount spans have aria-hidden=true
      const hiddenSpans = document.querySelectorAll('span[aria-hidden="true"]');
      expect(hiddenSpans.length).toBeGreaterThan(0);
    });
  });
});
