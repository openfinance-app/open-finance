import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import { CompoundInterestCalculator } from '@/components/compound-interest/CompoundInterestCalculator';

// ---------------------------------------------------------------------------
// Mock hooks — prevents any real API calls and provides controlled state
// ---------------------------------------------------------------------------

const mockUseAuthContext = vi.fn();
vi.mock('@/context/AuthContext', async importOriginal => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: () => mockUseAuthContext(),
  };
});

const mockCalculate = vi.fn();
const mockUpdateInput = vi.fn();
const mockResetInputs = vi.fn();
const mockUseCompoundInterest = vi.fn();
vi.mock('@/hooks/useCompoundInterest', () => ({
  useCompoundInterest: () => mockUseCompoundInterest(),
}));

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number) => `$${amount.toFixed(2)}`,
    formatCompact: (amount: number) => `$${amount.toFixed(2)}`,
    formatWithColor: () => null,
  }),
}));

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const defaultInput = {
  principal: 10000,
  annualRate: 7,
  years: 10,
  compoundingFrequency: 12,
  regularContribution: 200,
  contributionAtBeginning: false,
};

const mockResult = {
  finalBalance: 20097.57,
  principal: 10000.0,
  totalContributions: 2400.0,
  totalInterest: 7697.57,
  totalInvested: 12400.0,
  effectiveAnnualRate: 7.229,
  yearlyBreakdown: [
    {
      year: 1,
      startingBalance: 10000.0,
      contributions: 2400.0,
      interestEarned: 845.57,
      cumulativeInterest: 845.57,
      cumulativePrincipal: 10000.0,
      endingBalance: 13245.57,
    },
  ],
};

const defaultHookState = {
  input: defaultInput,
  result: null,
  isLoading: false,
  error: null,
  updateInput: mockUpdateInput,
  resetInputs: mockResetInputs,
  calculate: mockCalculate,
};

describe('CompoundInterestCalculator', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuthContext.mockReturnValue({ baseCurrency: 'USD', isAuthenticated: true });
    mockUseCompoundInterest.mockReturnValue(defaultHookState);
  });

  // -------------------------------------------------------------------------
  // Rendering
  // -------------------------------------------------------------------------
  describe('initial render', () => {
    it('renders the card title', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Compound Interest Calculator/i)).toBeInTheDocument();
    });

    it('renders the Principal input', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByLabelText(/Initial Principal/i)).toBeInTheDocument();
    });

    it('renders the Annual Interest Rate input', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByLabelText(/Annual Interest Rate/i)).toBeInTheDocument();
    });

    it('renders the Duration (years) input', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByLabelText(/Duration/i)).toBeInTheDocument();
    });

    it('renders the Regular Contribution input', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByLabelText(/Regular Contribution/i)).toBeInTheDocument();
    });

    it('renders Calculate and Reset buttons', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByRole('button', { name: /Calculate/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Reset/i })).toBeInTheDocument();
    });

    it('does not show results section before calculation', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.queryByText(/Final Balance/i)).not.toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // User interaction — input changes
  // -------------------------------------------------------------------------
  describe('input changes', () => {
    it('calls updateInput when principal is changed', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      const principalInput = screen.getByLabelText(/Initial Principal/i);
      fireEvent.change(principalInput, { target: { value: '25000' } });

      expect(mockUpdateInput).toHaveBeenCalledWith('principal', 25000);
    });

    it('calls updateInput when annual rate is changed', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      const rateInput = screen.getByLabelText(/Annual Interest Rate/i);
      fireEvent.change(rateInput, { target: { value: '9.5' } });

      expect(mockUpdateInput).toHaveBeenCalledWith('annualRate', 9.5);
    });
  });

  // -------------------------------------------------------------------------
  // Successful calculation — render with result already set
  // -------------------------------------------------------------------------
  describe('after a successful calculation', () => {
    beforeEach(() => {
      mockUseCompoundInterest.mockReturnValue({ ...defaultHookState, result: mockResult });
    });

    it('displays Final Balance result card', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Final Balance/i)).toBeInTheDocument();
    });

    it('displays Total Interest result card', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Total Interest/i)).toBeInTheDocument();
    });

    it('displays Total Invested result card', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Total Invested/i)).toBeInTheDocument();
    });

    it('displays Effective Annual Rate result card', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Effective Annual Rate/i)).toBeInTheDocument();
    });

    it('displays the year-by-year breakdown table', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Year-by-Year Breakdown/i)).toBeInTheDocument();
    });

    it('displays the breakdown table header columns', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText('Year')).toBeInTheDocument();
      expect(screen.getByText(/Start Balance/i)).toBeInTheDocument();
      expect(screen.getByText(/End Balance/i)).toBeInTheDocument();
    });

    it('shows the mock breakdown row with year 1', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText('1')).toBeInTheDocument();
    });

    it('shows the growth chart', () => {
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Investment Growth Over Time/i)).toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // Calculate button interaction
  // -------------------------------------------------------------------------
  describe('calculate button', () => {
    it('calls calculate when the Calculate button is clicked', async () => {
      renderWithProviders(<CompoundInterestCalculator />);

      const form = screen.getByRole('button', { name: /Calculate/i }).closest('form');
      if (!form) throw new Error('Form not found');
      fireEvent.submit(form);

      expect(mockCalculate).toHaveBeenCalledTimes(1);
    });
  });

  // -------------------------------------------------------------------------
  // Reset
  // -------------------------------------------------------------------------
  describe('reset', () => {
    it('calls resetInputs when the Reset button is clicked', async () => {
      renderWithProviders(<CompoundInterestCalculator />);

      await user.click(screen.getByRole('button', { name: /Reset/i }));

      expect(mockResetInputs).toHaveBeenCalledTimes(1);
    });

    it('does not show results after reset (result is null)', () => {
      // Reset state has result: null
      mockUseCompoundInterest.mockReturnValue({ ...defaultHookState, result: null });
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.queryByText(/Final Balance/i)).not.toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // Error handling
  // -------------------------------------------------------------------------
  describe('error handling', () => {
    it('shows an error message when the hook reports an error', () => {
      mockUseCompoundInterest.mockReturnValue({
        ...defaultHookState,
        error: 'Network Error',
      });

      renderWithProviders(<CompoundInterestCalculator />);

      expect(document.querySelector('.text-red-600')).toBeTruthy();
      expect(screen.getByText(/Network Error/i)).toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // Correct values from mock result
  // -------------------------------------------------------------------------
  describe('displays values from result', () => {
    it('renders the effective annual rate from the result', () => {
      mockUseCompoundInterest.mockReturnValue({ ...defaultHookState, result: mockResult });
      renderWithProviders(<CompoundInterestCalculator />);

      // effectiveAnnualRate = 7.229 → toFixed(2) = "7.23"
      expect(screen.getByText(/7\.23%/i)).toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // Loading state
  // -------------------------------------------------------------------------
  describe('loading state', () => {
    it('shows calculating text and disables button while loading', () => {
      mockUseCompoundInterest.mockReturnValue({ ...defaultHookState, isLoading: true });
      renderWithProviders(<CompoundInterestCalculator />);

      expect(screen.getByText(/Calculating/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Calculating/i })).toBeDisabled();
    });
  });
});
