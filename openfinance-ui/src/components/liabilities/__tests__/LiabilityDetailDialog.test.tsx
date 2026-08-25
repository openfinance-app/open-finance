/**
 * Unit tests for LiabilityDetailDialog component
 * Task: Test unified tabbed dialog for liability details
 *
 * Requirement 2.1: Test total cost hero card and cost breakdown
 * Requirement 3.2: Test linked payments tab
 */
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { act } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import React from 'react';
import { LiabilityDetailDialog } from '../LiabilityDetailDialog';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { Liability } from '@/types/liability';

// Mock VisibilityContext
vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

import { useVisibility } from '@/context/VisibilityContext';

// Mock data
const mockLiability: Liability = {
  id: '1',
  name: 'Home Mortgage',
  description: '30-year fixed mortgage',
  type: 'mortgage',
  principalAmount: 300000,
  currentBalance: 280000,
  interestRate: 3.5,
  currency: 'EUR',
  startDate: '2020-01-01',
  endDate: '2050-01-01',
  paymentFrequency: 'monthly',
  monthlyPayment: 1500,
  accountId: 'acc-1',
  tags: ['home', 'debt'],
  createdAt: '2020-01-01T00:00:00Z',
  updatedAt: '2023-01-01T00:00:00Z',
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
  feesPaid: 600,
  totalPaid: 60600,
  projectedInterest: 150000,
  projectedInsurance: 40000,
  projectedFees: 1800,
  totalProjectedCost: 471800,
  linkedTransactionCount: 1,
  linkedTransactionsTotalAmount: 1500,
};

const mockTransactions = [
  {
    id: 'txn-1',
    date: '2023-01-01',
    description: 'Mortgage Payment',
    amount: 1500,
    type: 'expense',
    categoryId: 'cat-1',
    accountId: 'acc-1',
    liabilityId: '1',
  },
];

// Mock the hooks
vi.mock('@/hooks/useLiabilities', async (importOriginal) => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return {
    ...actual,
    useLiabilityBreakdown: vi.fn(() => ({
      data: null,
      isLoading: false,
      error: null,
    })),
    useAmortizationSchedule: vi.fn(() => ({
      data: null,
      isLoading: false,
      error: null,
    })),
    useLiabilityTransactions: vi.fn(() => ({
      data: [],
      isLoading: false,
      error: null,
    })),
    formatCurrency: vi.fn((amount: number, currency: string = 'EUR') => `€${amount.toFixed(2)}`),
  };
});

const mockUseLiabilityBreakdown = vi.mocked(useLiabilitiesModule.useLiabilityBreakdown);
const mockUseAmortizationSchedule = vi.mocked(useLiabilitiesModule.useAmortizationSchedule);
const mockUseLiabilityTransactions = vi.mocked(useLiabilitiesModule.useLiabilityTransactions);

// Mock the child components
vi.mock('../LiabilityBreakdownPanel', () => ({
  LiabilityBreakdownPanel: ({ liability }: { liability: Liability }) => (
    <div data-testid="liability-breakdown-panel" data-liability-id={liability.id}>
      Breakdown Panel for {liability.name}
    </div>
  ),
}));

vi.mock('../AmortizationSchedule', () => ({
  AmortizationSchedule: ({ schedule }: { schedule: any }) => (
    <div data-testid="amortization-schedule" data-schedule-id={schedule?.liabilityId}>
      Amortization Schedule
    </div>
  ),
}));

// Mock UI components with simple implementations
vi.mock('@/components/ui/Dialog', () => ({
  Dialog: ({ children, open }: any) => open ? <div data-testid="dialog">{children}</div> : null,
  DialogContent: ({ children }: any) => <div data-testid="dialog-content">{children}</div>,
  DialogHeader: ({ children }: any) => <div data-testid="dialog-header">{children}</div>,
  DialogTitle: ({ children }: any) => <div data-testid="dialog-title">{children}</div>,
}));

describe('LiabilityDetailDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Set default mock returns
    mockUseLiabilityBreakdown.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    });
    mockUseAmortizationSchedule.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    });
    mockUseLiabilityTransactions.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    });
  });

  describe('Dialog Visibility', () => {
    it('does not render when liability prop is null', () => {
      renderWithProviders(
        <LiabilityDetailDialog liability={null} onClose={vi.fn()} />
      );

      expect(screen.queryByTestId('dialog')).not.toBeInTheDocument();
    });

    it('renders dialog with liability name in title when liability is provided', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      expect(screen.getByTestId('dialog')).toBeInTheDocument();
      expect(screen.getByTestId('dialog-title')).toHaveTextContent('Home Mortgage — Details');
    });
  });

  describe('Total Cost Hero', () => {
    it('renders TotalCostHero when breakdown data is available', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      expect(screen.getByText('Total Lifetime Cost')).toBeInTheDocument();
      expect(screen.getByText('€532,400.00')).toBeInTheDocument(); // 60600 + 471800
    });

    it('renders breakdown bar segments for Principal, Interest, Insurance, Fees when > 0', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Check legend items
      expect(screen.getByText('Principal')).toBeInTheDocument();
      expect(screen.getByText('€300,000.00')).toBeInTheDocument();
      expect(screen.getByText('Interest (paid / total)')).toBeInTheDocument();
      expect(screen.getByText('€185,000.00')).toBeInTheDocument(); // 35000 + 150000
      expect(screen.getByText('Insurance (paid / total)')).toBeInTheDocument();
      expect(screen.getByText('€45,000.00')).toBeInTheDocument(); // 5000 + 40000
      // additionalFees is a one-time fee; only feesPaid counts (projectedFees is always 0)
      expect(screen.getByText('One-time Fee')).toBeInTheDocument();
      expect(screen.getByText('€600.00')).toBeInTheDocument(); // feesPaid only
    });

    it('shows a tooltip with the principal amount when hovering the principal bar segment', async () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      const { container } = renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      const principalSegment = container.querySelector('div.bg-primary') as HTMLElement;
      expect(principalSegment).toBeInTheDocument();

      fireEvent.pointerMove(principalSegment);

      const tooltip = await waitFor(() => screen.getByRole('tooltip'));
      expect(tooltip).toHaveTextContent('Principal');
      expect(tooltip).toHaveTextContent('€300,000.00');
      expect(tooltip.querySelector('[data-testid="converted-amount"]')).not.toBeNull();
    });
  });

  describe('Overview Tab', () => {
    it('renders LiabilityBreakdownPanel in Overview tab', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      expect(screen.getByTestId('liability-breakdown-panel')).toBeInTheDocument();
      expect(screen.getByTestId('liability-breakdown-panel')).toHaveAttribute('data-liability-id', '1');
    });
  });

  describe('Amortization Schedule Tab', () => {
    it('shows Amortization Schedule tab when liability has interest rate > 0', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      expect(screen.getByText('Amortization Schedule')).toBeInTheDocument();
    });

    it('does not show Amortization Schedule tab when interest rate is 0', () => {
      const noInterestLiability = { ...mockLiability, interestRate: 0 };

      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={noInterestLiability} onClose={vi.fn()} />
      );

      expect(screen.queryByText('Amortization Schedule')).not.toBeInTheDocument();
    });
  });

  describe('Linked Payments Tab', () => {
    it('shows Linked Payments tab always', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      expect(screen.getByText('Linked Payments')).toBeInTheDocument();
    });

    it('shows empty state message in Linked Payments tab when no transactions', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      mockUseLiabilityTransactions.mockReturnValue({
        data: [],
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Click the Linked Payments tab
      const paymentsTab = screen.getByText('Linked Payments');
      act(() => {
        paymentsTab.click();
      });

      expect(screen.getByText('No transactions linked to this liability yet.')).toBeInTheDocument();
      expect(screen.getByText('Link expense transactions to track payments against this liability.')).toBeInTheDocument();
    });

    it('shows transaction list in Linked Payments tab when transactions exist', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      mockUseLiabilityTransactions.mockReturnValue({
        data: mockTransactions,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Click the Linked Payments tab
      const paymentsTab = screen.getByText('Linked Payments');
      act(() => {
        paymentsTab.click();
      });

      expect(screen.getByText('Mortgage Payment')).toBeInTheDocument();
      expect(screen.getAllByText('€1,500.00')).toHaveLength(2); // One in summary, one in transaction
    });

    it('shows loading skeleton in Linked Payments tab while loading', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      mockUseLiabilityTransactions.mockReturnValue({
        data: undefined,
        isLoading: true,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Click the Linked Payments tab
      const paymentsTab = screen.getByText('Linked Payments');
      act(() => {
        paymentsTab.click();
      });

      const skeletonContainer = document.querySelector('.animate-pulse');
      expect(skeletonContainer).toBeInTheDocument();
    });

    it('shows error state in Linked Payments tab on fetch failure', () => {
      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      mockUseLiabilityTransactions.mockReturnValue({
        data: undefined,
        isLoading: false,
        error: new Error('Failed to load'),
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Click the Linked Payments tab
      const paymentsTab = screen.getByText('Linked Payments');
      act(() => {
        paymentsTab.click();
      });

      expect(screen.getByText('Failed to load linked transactions. Please try again.')).toBeInTheDocument();
    });
  });

  describe('Privacy Implementation - PrivateAmount Wrapping', () => {
    it('should wrap currency displays with PrivateAmount when amounts are visible', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });

      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Check that PrivateAmount components are present
      const privateAmounts = document.querySelectorAll('.transition-all.duration-300');
      expect(privateAmounts.length).toBeGreaterThan(0);
    });

    it('should apply blur effect to currency displays when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Check that PrivateAmount components have blur classes
      const blurredAmounts = document.querySelectorAll('.blur-md.select-none');
      expect(blurredAmounts.length).toBeGreaterThan(0);
    });

    it('should set aria-hidden when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      mockUseLiabilityBreakdown.mockReturnValue({
        data: mockBreakdown,
        isLoading: false,
        error: null,
      });

      renderWithProviders(
        <LiabilityDetailDialog liability={mockLiability} onClose={vi.fn()} />
      );

      // Check that PrivateAmount spans have aria-hidden=true
      const hiddenSpans = document.querySelectorAll('span[aria-hidden="true"]');
      expect(hiddenSpans.length).toBeGreaterThan(0);
    });
  });
});