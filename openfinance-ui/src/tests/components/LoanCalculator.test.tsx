import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { LoanCalculator } from '@/components/loan-calculator/LoanCalculator';

// ---------------------------------------------------------------------------
// Mock hooks — prevents real state and provides controlled state
// ---------------------------------------------------------------------------

const mockUseAuthContext = vi.fn();
vi.mock('@/context/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/context/AuthContext')>();
  return {
    ...actual,
    useAuthContext: () => mockUseAuthContext(),
  };
});

const mockCalculate = vi.fn();
const mockUpdateInput = vi.fn();
const mockResetInputs = vi.fn();
const mockUseLoanCalculator = vi.fn();
vi.mock('@/hooks/useLoanCalculator', () => ({
  useLoanCalculator: () => mockUseLoanCalculator(),
}));

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number) => `$${amount.toFixed(2)}`,
  }),
}));

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const defaultInput = {
  principal: 200000,
  annualRate: 5.0,
  years: 20,
};

const mockResult = {
  monthlyPayment: 1319.91,
  totalInterest: 116778.40,
  totalPayment: 316778.40,
  amortizationSchedule: [
    // 12 entries for year 1
    ...Array.from({ length: 12 }, (_, i) => ({
      paymentNumber: i + 1,
      paymentAmount: 1319.91,
      principalPortion: 486.57 + i * 2,
      interestPortion: 833.33 - i * 2,
      remainingBalance: 200000 - (486.57 + i * 2) * (i + 1),
      cumulativeInterest: (833.33 - i * 2) * (i + 1),
    })),
  ],
};

const defaultHookState = {
  input: defaultInput,
  result: null,
  updateInput: mockUpdateInput,
  resetInputs: mockResetInputs,
  calculate: mockCalculate,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('LoanCalculator', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuthContext.mockReturnValue({ baseCurrency: 'USD', isAuthenticated: true });
    mockUseLoanCalculator.mockReturnValue(defaultHookState);
  });

  // -------------------------------------------------------------------------
  // Rendering
  // -------------------------------------------------------------------------
  describe('initial render', () => {
    it('renders the card title', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText(/Loan Calculator/i)).toBeInTheDocument();
    });

    it('renders the Loan Amount input', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByLabelText(/Loan Amount/i)).toBeInTheDocument();
    });

    it('renders the Annual Interest Rate input', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByLabelText(/Annual Interest Rate/i)).toBeInTheDocument();
    });

    it('renders the Loan Term input', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByLabelText(/Loan Term/i)).toBeInTheDocument();
    });

    it('renders Calculate and Reset buttons', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByRole('button', { name: /Calculate/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Reset/i })).toBeInTheDocument();
    });

    it('does not show results section before calculation', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.queryByText(/Monthly Payment/i)).not.toBeInTheDocument();
    });
  });

  // -------------------------------------------------------------------------
  // User interaction — inputs
  // -------------------------------------------------------------------------
  describe('input changes', () => {
    it('calls updateInput when principal is changed', () => {
      renderWithProviders(<LoanCalculator />);

      const input = screen.getByLabelText(/Loan Amount/i);
      fireEvent.change(input, { target: { value: '150000' } });

      expect(mockUpdateInput).toHaveBeenCalledWith('principal', 150000);
    });

    it('calls updateInput when annual rate is changed', () => {
      renderWithProviders(<LoanCalculator />);

      const input = screen.getByLabelText(/Annual Interest Rate/i);
      fireEvent.change(input, { target: { value: '3.5' } });

      expect(mockUpdateInput).toHaveBeenCalledWith('annualRate', 3.5);
    });

    it('calls updateInput when years is changed', () => {
      renderWithProviders(<LoanCalculator />);

      const input = screen.getByLabelText(/Loan Term/i);
      fireEvent.change(input, { target: { value: '30' } });

      expect(mockUpdateInput).toHaveBeenCalledWith('years', 30);
    });

    it('calls calculate when Calculate button is clicked', () => {
      renderWithProviders(<LoanCalculator />);

      fireEvent.click(screen.getByRole('button', { name: /Calculate/i }));

      expect(mockCalculate).toHaveBeenCalledTimes(1);
    });

    it('calls resetInputs when Reset button is clicked', () => {
      renderWithProviders(<LoanCalculator />);

      fireEvent.click(screen.getByRole('button', { name: /Reset/i }));

      expect(mockResetInputs).toHaveBeenCalledTimes(1);
    });
  });

  // -------------------------------------------------------------------------
  // Results section
  // -------------------------------------------------------------------------
  describe('after a successful calculation', () => {
    beforeEach(() => {
      mockUseLoanCalculator.mockReturnValue({ ...defaultHookState, result: mockResult });
    });

    it('displays Monthly Payment result card', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText(/Monthly Payment/i)).toBeInTheDocument();
    });

    it('displays Total Interest result card', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText(/Total Interest/i)).toBeInTheDocument();
    });

    it('displays Total Payment result card', () => {
      renderWithProviders(<LoanCalculator />);

      // 'Total Payment' also appears as a table header, so there are multiple matches
      expect(screen.getAllByText(/Total Payment/i).length).toBeGreaterThanOrEqual(1);
    });

    it('displays the Balance Over Time chart', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText(/Balance Over Time/i)).toBeInTheDocument();
    });

    it('displays the Amortization Schedule table', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText(/Amortization Schedule/i)).toBeInTheDocument();
    });

    it('displays the amortization table column headers', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText('Year')).toBeInTheDocument();
      // 'Total Payment' appears in both the result card and the table header
      expect(screen.getAllByText('Total Payment').length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText('Principal Paid')).toBeInTheDocument();
      expect(screen.getByText('Interest Paid')).toBeInTheDocument();
      expect(screen.getByText('Remaining Balance')).toBeInTheDocument();
    });

    it('displays formatted monthly payment value', () => {
      renderWithProviders(<LoanCalculator />);

      expect(screen.getByText('$1,319.91')).toBeInTheDocument();
    });

    it('shows the monthly payment tooltip with ConvertedAmount on hover', async () => {
      renderWithProviders(<LoanCalculator />);

      const trigger = screen.getByText('$1,319.91').closest('[data-state]') as HTMLElement;
      fireEvent.pointerMove(trigger);

      const tooltip = await waitFor(() => screen.getByRole('tooltip'));
      expect(tooltip).toHaveTextContent('$1,319.91');
      expect(tooltip.querySelector('[data-testid="converted-amount"]')).not.toBeNull();
    });

    it('shows the total interest tooltip on hover', async () => {
      renderWithProviders(<LoanCalculator />);

      const trigger = screen.getByText('$116,778.40').closest('[data-state]') as HTMLElement;
      fireEvent.pointerMove(trigger);

      const tooltip = await waitFor(() => screen.getByRole('tooltip'));
      expect(tooltip).toHaveTextContent('$116,778.40');
    });

    it('shows the total payment tooltip on hover', async () => {
      renderWithProviders(<LoanCalculator />);

      const trigger = screen.getByText('$316,778.40').closest('[data-state]') as HTMLElement;
      fireEvent.pointerMove(trigger);

      const tooltip = await waitFor(() => screen.getByRole('tooltip'));
      expect(tooltip).toHaveTextContent('$316,778.40');
    });
  });
});
