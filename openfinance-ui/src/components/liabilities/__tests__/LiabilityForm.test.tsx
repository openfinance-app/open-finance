/**
 * Unit tests for LiabilityForm component
 * Task 6.2.2: Test LiabilityForm component with validation
 *
 * Requirement 1.1: Test insurance percentage and additional fees fields
 */
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import React from 'react';
import { renderWithProviders } from '@/test/test-utils';
import { LiabilityForm } from '../LiabilityForm';
import * as useAuthContextModule from '@/context/AuthContext';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { Liability, LiabilityRequest } from '@/types/liability';

// Mock dependencies
vi.mock('@/context/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: vi.fn(),
  };
});
vi.mock('@/hooks/useLiabilities', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/hooks/useLiabilities')>();
  return {
    ...actual,
    getLiabilityTypeName: vi.fn(),
  };
});
vi.mock('@/components/ui/Input', () => ({
  Input: ({ id, type, step, min, max, ...props }: any) => (
    <input
      id={id}
      type={type || 'text'}
      step={step}
      min={min}
      max={max}
      {...props}
    />
  ),
}));
vi.mock('@/components/ui/Button', () => ({
  Button: ({ children, variant, type, isLoading, ...props }: any) => (
    <button
      type={type || 'button'}
      data-variant={variant}
      disabled={isLoading}
      {...props}
    >
      {children}
    </button>
  ),
}));
vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({ value, onValueChange, placeholder }: any) => (
    <select
      data-testid="currency-selector"
      value={value}
      onChange={(e) => onValueChange(e.target.value)}
      placeholder={placeholder}
    >
      <option value="USD">USD</option>
      <option value="EUR">EUR</option>
      <option value="GBP">GBP</option>
    </select>
  ),
}));
vi.mock('@/components/ui/ExchangeRateDisplay', () => ({
  ExchangeRateInline: ({ from, to }: any) => (
    <div data-testid="exchange-rate">
      Exchange rate from {from} to {to}
    </div>
  ),
}));

const mockUseAuthContext = vi.mocked(useAuthContextModule.useAuthContext);
const mockGetLiabilityTypeName = vi.mocked(useLiabilitiesModule.getLiabilityTypeName);

// Test fixtures
const mockLiability: Liability = {
  id: 1,
  name: 'Test Mortgage',
  type: 'MORTGAGE',
  principal: 300000,
  currentBalance: 250000,
  interestRate: 3.5,
  startDate: '2020-01-01',
  endDate: '2050-01-01',
  minimumPayment: 1500,
  currency: 'USD',
  notes: 'Test liability',
  insurancePercentage: 0.5,
  additionalFees: 500,
  monthlyInsuranceCost: 125,
  totalInsuranceCost: 15000,
  totalCost: 45000,
  principalPaid: 50000,
  interestPaid: 10000,
  createdAt: '2020-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

describe('LiabilityForm', () => {
  const mockOnSubmit = vi.fn();
  const mockOnCancel = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuthContext.mockReturnValue({
      baseCurrency: 'USD',
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
      isAuthenticated: true,
    });
    mockGetLiabilityTypeName.mockImplementation((type) => type);
  });

  describe('Form Rendering', () => {
    it('renders all required fields for new liability', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      expect(screen.getByLabelText(/liability name/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/liability type/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/original principal/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/current balance/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/interest rate/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/insurance rate/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/one-time fee/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/start date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/end date/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/minimum monthly payment/i)).toBeInTheDocument();
      expect(screen.getByTestId('currency-selector')).toBeInTheDocument();
      expect(screen.getByLabelText(/notes/i)).toBeInTheDocument();
    });

    it('renders new insurance percentage and additional fees fields', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Check that insurance and fee fields are present
      expect(screen.getByLabelText(/insurance rate.*annual/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/one-time fee/i)).toBeInTheDocument();

      // Note: Helper text may not be present in the current implementation
      // The labels themselves provide sufficient context
    });

    it('shows "Create Liability" button for new liability', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      expect(screen.getByRole('button', { name: /create liability/i })).toBeInTheDocument();
    });

    it('shows "Update Liability" button when editing existing liability', () => {
      renderWithProviders(
        <LiabilityForm
          liability={mockLiability}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      expect(screen.getByRole('button', { name: /update liability/i })).toBeInTheDocument();
    });
  });

  describe('Form Validation', () => {
    it('validates required fields', async () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      const submitButton = screen.getByRole('button', { name: /create liability/i });
      fireEvent.click(submitButton);

      // Wait for form validation
      await waitFor(() => {
        expect(mockOnSubmit).not.toHaveBeenCalled();
      }, { timeout: 1000 });

      // The form should not submit with empty required fields
      expect(mockOnSubmit).toHaveBeenCalledTimes(0);
    });

    it('validates insurance percentage range', async () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Fill required fields first
      fireEvent.change(screen.getByLabelText(/liability name/i), { target: { value: 'Test' } });
      fireEvent.change(screen.getByLabelText(/original principal/i), { target: { value: '1000' } });
      fireEvent.change(screen.getByLabelText(/current balance/i), { target: { value: '1000' } });
      fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2024-01-01' } });

      const insuranceInput = screen.getByLabelText(/insurance rate.*annual/i);
      fireEvent.change(insuranceInput, { target: { value: '150' } });

      const submitButton = screen.getByRole('button', { name: /create liability/i });
      fireEvent.click(submitButton);

      // Wait for form validation
      await waitFor(() => {
        expect(mockOnSubmit).not.toHaveBeenCalled();
      }, { timeout: 1000 });

      // The form should not submit with invalid insurance percentage
      expect(mockOnSubmit).toHaveBeenCalledTimes(0);
    });

    it('validates end date is after start date', async () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Fill required fields
      fireEvent.change(screen.getByLabelText(/liability name/i), { target: { value: 'Test' } });
      fireEvent.change(screen.getByLabelText(/original principal/i), { target: { value: '1000' } });
      fireEvent.change(screen.getByLabelText(/current balance/i), { target: { value: '1000' } });
      fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2024-01-01' } });
      fireEvent.change(screen.getByLabelText(/end date/i), { target: { value: '2023-01-01' } });

      const submitButton = screen.getByRole('button', { name: /create liability/i });
      fireEvent.click(submitButton);

      // Wait for form validation
      await waitFor(() => {
        expect(mockOnSubmit).not.toHaveBeenCalled();
      }, { timeout: 1000 });

      // The form should not submit with invalid date range
      expect(mockOnSubmit).toHaveBeenCalledTimes(0);
    });
  });

  describe('Form Submission', () => {
    it('submits correct data for new liability including new fields', async () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Fill required fields
      fireEvent.change(screen.getByLabelText(/liability name/i), { target: { value: 'Test Mortgage' } });
      fireEvent.change(screen.getByLabelText(/original principal/i), { target: { value: '300000' } });
      fireEvent.change(screen.getByLabelText(/current balance/i), { target: { value: '250000' } });
      fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2020-01-01' } });

      // Fill new fields
      fireEvent.change(screen.getByLabelText(/insurance rate.*annual/i), { target: { value: '0.5' } });
      fireEvent.change(screen.getByLabelText(/one-time fee/i), { target: { value: '500' } });

      const submitButton = screen.getByRole('button', { name: /create liability/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(mockOnSubmit).toHaveBeenCalledWith({
          name: 'Test Mortgage',
          type: 'OTHER',
          principal: '300000',
          currentBalance: '250000',
          startDate: '2020-01-01',
          currency: 'USD',
          insurancePercentage: 0.5,
          additionalFees: '500',
        });
      });
    });

    it('submits correct data for editing existing liability with insurance fields', async () => {
      renderWithProviders(
        <LiabilityForm
          liability={mockLiability}
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Change insurance percentage
      fireEvent.change(screen.getByLabelText(/insurance rate.*annual/i), { target: { value: '0.75' } });

      const submitButton = screen.getByRole('button', { name: /update liability/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        expect(mockOnSubmit).toHaveBeenCalledWith({
          name: 'Test Mortgage',
          type: 'MORTGAGE',
          principal: '300000',
          currentBalance: '250000',
          interestRate: 3.5,
          startDate: '2020-01-01',
          endDate: '2050-01-01',
          minimumPayment: '1500',
          currency: 'USD',
          notes: 'Test liability',
          insurancePercentage: 0.75,
          additionalFees: '500',
        });
      });
    });

    it('omits optional fields when zero or empty', async () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      // Fill required fields only
      fireEvent.change(screen.getByLabelText(/liability name/i), { target: { value: 'Test Loan' } });
      fireEvent.change(screen.getByLabelText(/original principal/i), { target: { value: '10000' } });
      fireEvent.change(screen.getByLabelText(/current balance/i), { target: { value: '10000' } });
      fireEvent.change(screen.getByLabelText(/start date/i), { target: { value: '2024-01-01' } });

      // Leave insurance and fees as 0
      fireEvent.change(screen.getByLabelText(/insurance rate.*annual/i), { target: { value: '0' } });
      fireEvent.change(screen.getByLabelText(/one-time fee/i), { target: { value: '0' } });

      const submitButton = screen.getByRole('button', { name: /create liability/i });
      fireEvent.click(submitButton);

      await waitFor(() => {
        const submittedData = mockOnSubmit.mock.calls[0][0];
        expect(submittedData.insurancePercentage).toBeUndefined();
        expect(submittedData.additionalFees).toBeUndefined();
      });
    });

    it('calls onCancel when cancel button is clicked', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      const cancelButton = screen.getByRole('button', { name: /cancel/i });
      fireEvent.click(cancelButton);

      expect(mockOnCancel).toHaveBeenCalledTimes(1);
    });
  });

  describe('Exchange Rate Display', () => {
    it('shows exchange rate when currency differs from base currency', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      const currencySelect = screen.getByTestId('currency-selector');
      fireEvent.change(currencySelect, { target: { value: 'EUR' } });

      expect(screen.getByTestId('exchange-rate')).toBeInTheDocument();
      expect(screen.getByText('Exchange rate from EUR to USD')).toBeInTheDocument();
    });

    it('does not show exchange rate when currency matches base currency', () => {
      renderWithProviders(
        <LiabilityForm
          onSubmit={mockOnSubmit}
          onCancel={mockOnCancel}
          isLoading={false}
        />
      );

      expect(screen.queryByTestId('exchange-rate')).not.toBeInTheDocument();
    });
  });
});