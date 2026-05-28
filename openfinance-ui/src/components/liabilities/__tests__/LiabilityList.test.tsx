/**
 * Unit tests for LiabilityList component
 * Task: Test liability list display with progress bars and responsive cards
 *
 * Requirement 2.1: Test "View Details" button opens unified dialog
 */
import { screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { LiabilityList } from '../LiabilityList';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { Liability } from '@/types/liability';

// Mock the hooks
vi.mock('@/hooks/useLiabilities', async (importOriginal) => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return {
    ...actual,
  };
});

// Mock Lucide icons
vi.mock('lucide-react', () => ({
  Pencil: () => <div data-testid="pencil-icon" />,
  Trash2: () => <div data-testid="trash2-icon" />,
  CreditCard: () => <div data-testid="credit-card-icon" />,
  BarChart2: () => <div data-testid="bar-chart-icon" />,
}));

// Mock VisibilityContext
vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

import { useVisibility } from '@/context/VisibilityContext';

// Mock UI components
vi.mock('@/components/ui/Badge', () => ({
  Badge: ({ children, variant, size }: any) => (
    <span data-testid="badge" data-variant={variant} data-size={size}>
      {children}
    </span>
  ),
}));

vi.mock('@/components/ui/Button', () => ({
  Button: ({ children, variant, size, onClick, 'aria-label': ariaLabel, title }: any) => (
    <button
      data-testid="button"
      data-variant={variant}
      data-size={size}
      onClick={onClick}
      aria-label={ariaLabel}
      title={title}
    >
      {children}
    </button>
  ),
}));

vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onOpenChange, onConfirm, title, description, confirmText, variant }: any) => (
    open ? (
      <div data-testid="confirmation-dialog">
        <h2>{title}</h2>
        <p>{description}</p>
        <button onClick={onConfirm} data-variant={variant}>
          {confirmText}
        </button>
        <button onClick={() => onOpenChange(false)}>Cancel</button>
      </div>
    ) : null
  ),
}));

// Test fixtures
const mockLiability: Liability = {
  id: 1,
  name: 'Home Mortgage',
  type: 'MORTGAGE' as const,
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

describe('LiabilityList', () => {
  const mockOnEdit = vi.fn();
  const mockOnDelete = vi.fn();
  const mockOnViewDetails = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('View Details Button', () => {
    it('renders "View Details" button when onViewDetails prop is provided', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const viewDetailsButton = screen.getByRole('button', { name: /view liability details/i });
      expect(viewDetailsButton).toBeInTheDocument();
      expect(screen.getByTestId('bar-chart-icon')).toBeInTheDocument();
    });

    it('calls onViewDetails with the correct liability when "View Details" button is clicked', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const viewDetailsButton = screen.getByRole('button', { name: /view liability details/i });
      fireEvent.click(viewDetailsButton);

      expect(mockOnViewDetails).toHaveBeenCalledTimes(1);
      expect(mockOnViewDetails).toHaveBeenCalledWith(mockLiability);
    });

    it('does NOT render "View Details" button when onViewDetails is undefined', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
        />
      );

      expect(screen.queryByRole('button', { name: /view liability details/i })).not.toBeInTheDocument();
      expect(screen.queryByTestId('bar-chart-icon')).not.toBeInTheDocument();
    });
  });

  describe('Calendar/Schedule Button', () => {
    it('does NOT render separate Calendar/schedule button (regression test)', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Should not have any calendar or schedule related buttons
      const buttons = screen.getAllByTestId('button');
      const hasCalendarButton = buttons.some(button =>
        button.textContent?.toLowerCase().includes('calendar') ||
        button.textContent?.toLowerCase().includes('schedule')
      );
      expect(hasCalendarButton).toBe(false);
    });
  });

  describe('Edit and Delete Buttons', () => {
    it('renders Edit and Delete buttons correctly', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByRole('button', { name: /edit liability/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /delete liability/i })).toBeInTheDocument();
      expect(screen.getByTestId('pencil-icon')).toBeInTheDocument();
      expect(screen.getByTestId('trash2-icon')).toBeInTheDocument();
    });

    it('calls onEdit with correct liability when Edit button is clicked', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const editButton = screen.getByRole('button', { name: /edit liability/i });
      fireEvent.click(editButton);

      expect(mockOnEdit).toHaveBeenCalledTimes(1);
      expect(mockOnEdit).toHaveBeenCalledWith(mockLiability);
    });

    it('opens delete confirmation dialog when Delete button is clicked', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const deleteButton = screen.getByRole('button', { name: /delete liability/i });
      fireEvent.click(deleteButton);

      expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();
      expect(screen.getByText('Delete Liability')).toBeInTheDocument();
      expect(screen.getByText(`Are you sure you want to delete "Home Mortgage"? This action cannot be undone.`)).toBeInTheDocument();
    });
  });

  describe('Empty State', () => {
    it('shows empty state when liabilities array is empty', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('No liabilities found. Create your first liability!')).toBeInTheDocument();
      expect(screen.getByTestId('credit-card-icon')).toBeInTheDocument();
    });
  });

  describe('Progress Bar Calculation', () => {
    it('calculates progress percentage correctly', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Principal: 300000, Current Balance: 280000
      // Paid: 300000 - 280000 = 20000
      // Progress: (20000 / 300000) * 100 = 6.67%
      expect(screen.getByText('Paid Off')).toBeInTheDocument();
      expect(screen.getByText('6.7%')).toBeInTheDocument();
    });

    it('shows 0% progress when principal is 0', () => {
      const zeroPrincipalLiability = { ...mockLiability, principal: 0 };

      renderWithProviders(
        <LiabilityList
          liabilities={[zeroPrincipalLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('0.0%')).toBeInTheDocument();
    });
  });

  describe('Liability Display', () => {
    it('displays liability name and type badge', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('Home Mortgage')).toBeInTheDocument();
      expect(screen.getByTestId('badge')).toHaveTextContent('Mortgage');
    });

    it('displays notes when present', () => {
      const liabilityWithNotes = { ...mockLiability, notes: 'Primary home loan' };
      renderWithProviders(
        <LiabilityList
          liabilities={[liabilityWithNotes]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('Primary home loan')).toBeInTheDocument();
    });

    it('displays balance information', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('Current Balance')).toBeInTheDocument();
      // Check currency appears in body text (may be split by PrivateAmount)
      const body = document.body.textContent || '';
      expect(body).toContain('€280,000');
      expect(screen.getByText('Original Principal')).toBeInTheDocument();
      expect(body).toContain('€300,000');
    });

    it('displays interest rate when present', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // When both interestRate and insurancePercentage are present, column is labelled "Interest / Insurance"
      expect(screen.getByText('Interest / Insurance')).toBeInTheDocument();
      expect(screen.getByText(/3\.50%/)).toBeInTheDocument();
    });

    it('displays monthly payment when present and > 0', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('Monthly Payment')).toBeInTheDocument();
      // Check currency appears in body text (may be split by PrivateAmount)
      const body = document.body.textContent || '';
      expect(body).toContain('€1,500');
    });

    it('displays insurance rate when present and > 0', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Insurance rate is shown inline with interest rate: "3.50% / 0.50%"
      // The column header is "Interest / Insurance" (checked in the interest rate test)
      expect(screen.getByText(/0\.50%/)).toBeInTheDocument();
    });

    it('does not display insurance rate when 0 or undefined', () => {
      const liabilityWithoutInsurance = { ...mockLiability, insurancePercentage: undefined };
      const liabilityWithZeroInsurance = { ...mockLiability, insurancePercentage: 0 };

      const { rerender } = renderWithProviders(
        <LiabilityList
          liabilities={[liabilityWithoutInsurance]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.queryByText('Insurance Rate')).not.toBeInTheDocument();
      expect(screen.queryByText('0.00%')).not.toBeInTheDocument();

      rerender(
        <LiabilityList
          liabilities={[liabilityWithZeroInsurance]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.queryByText('Insurance Rate')).not.toBeInTheDocument();
    });

    it('displays end date information', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText(/months remaining/)).toBeInTheDocument();
      expect(screen.getByText(/Ends/)).toBeInTheDocument();
    });
  });

  describe('Multiple Liabilities', () => {
    it('shows correct count when multiple liabilities', () => {
      const liabilities = [mockLiability, { ...mockLiability, id: 2, name: 'Car Loan' }];

      renderWithProviders(
        <LiabilityList
          liabilities={liabilities}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      expect(screen.getByText('Showing 2 liabilities')).toBeInTheDocument();
    });
  });

  describe('Privacy Implementation - PrivateAmount Wrapping', () => {
    it('should wrap currency displays with PrivateAmount when amounts are visible', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });

      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Check that PrivateAmount components are present (they have transition classes)
      const privateAmounts = document.querySelectorAll('.transition-all.duration-300');
      expect(privateAmounts.length).toBeGreaterThan(0);
    });

    it('should apply blur effect to currency displays when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Check that PrivateAmount components have blur classes
      const blurredAmounts = document.querySelectorAll('.blur-md.select-none');
      expect(blurredAmounts.length).toBeGreaterThan(0);
    });

    it('should set aria-hidden when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      // Check that PrivateAmount spans have aria-hidden=true
      const hiddenSpans = document.querySelectorAll('span[aria-hidden="true"]');
      expect(hiddenSpans.length).toBeGreaterThan(0);
    });
  });

  describe('Delete confirmation', () => {
    it('calls onDelete when confirmation is confirmed', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const deleteButton = screen.getByRole('button', { name: /delete liability/i });
      fireEvent.click(deleteButton);
      expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();

      // Click the confirm button inside the dialog
      const confirmBtn = screen.getByText('Delete');
      fireEvent.click(confirmBtn);
      expect(mockOnDelete).toHaveBeenCalledWith(1);
    });

    it('closes dialog when Cancel is clicked', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          onViewDetails={mockOnViewDetails}
        />
      );

      const deleteButton = screen.getByRole('button', { name: /delete liability/i });
      fireEvent.click(deleteButton);
      expect(screen.getByTestId('confirmation-dialog')).toBeInTheDocument();

      fireEvent.click(screen.getByText('Cancel'));
      expect(screen.queryByTestId('confirmation-dialog')).not.toBeInTheDocument();
    });
  });

  describe('Highlighted liability', () => {
    it('renders highlighted liability with highlight styling', () => {
      Element.prototype.scrollIntoView = vi.fn();

      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          highlightedId={1}
        />
      );

      // Check that the highlighted card has special styling
      const body = document.body.innerHTML;
      expect(body).toContain('ring');
    });

    it('applies highlight styling to matching liability', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
          highlightedId={1}
        />
      );

      // The highlighted card should have ring styling
      const cards = document.querySelectorAll('[class*="ring"]');
      expect(cards.length).toBeGreaterThan(0);
    });
  });

  describe('Liability without end date', () => {
    it('does not show remaining months when endDate is undefined', () => {
      const noEndDate = { ...mockLiability, endDate: undefined };
      renderWithProviders(
        <LiabilityList
          liabilities={[noEndDate]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
        />
      );
      expect(screen.queryByText(/months remaining/)).not.toBeInTheDocument();
    });
  });

  describe('Liability with past end date', () => {
    it('shows overdue text for past end dates', () => {
      const pastEnd = { ...mockLiability, endDate: '2020-01-01' };
      renderWithProviders(
        <LiabilityList
          liabilities={[pastEnd]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
        />
      );
      // Should show ended/overdue text since end date is in the past
      expect(screen.getByText(/Ends|Ended/)).toBeInTheDocument();
    });
  });

  describe('Single liability count', () => {
    it('shows singular text for one liability', () => {
      renderWithProviders(
        <LiabilityList
          liabilities={[mockLiability]}
          onEdit={mockOnEdit}
          onDelete={mockOnDelete}
        />
      );
      expect(screen.getByText(/Showing 1 liabilit/)).toBeInTheDocument();
    });
  });
});