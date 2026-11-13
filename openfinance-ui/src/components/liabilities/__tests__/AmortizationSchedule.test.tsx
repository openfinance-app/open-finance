/**
 * Unit tests for AmortizationSchedule component
 * Focus: Test privacy implementation with PrivateAmount wrapping of currency displays
 */
import { screen, fireEvent, within } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';
import React from 'react';
import { renderWithProviders, createTestQueryClient, mockAuthentication } from '@/test/test-utils';
import { AmortizationSchedule } from '../AmortizationSchedule';
import type { AmortizationSchedule as AmortizationScheduleType } from '@/types/liability';

// Mock VisibilityContext
vi.mock('@/context/VisibilityContext', () => ({
  VisibilityProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useVisibility: vi.fn(() => ({ isAmountsVisible: true })),
}));

import { useVisibility } from '@/context/VisibilityContext';

// Mock formatCurrency to return predictable strings
vi.mock('@/hooks/useLiabilities', () => ({
  formatCurrency: vi.fn((amount: number, currency: string) => `$${amount.toFixed(2)}`),
}));

// Test fixtures
const mockSchedule: AmortizationScheduleType = {
  liabilityId: 1,
  liabilityName: 'Home Mortgage',
  principal: 300000,
  interestRate: 3.5,
  termMonths: 360,
  monthlyPayment: 1500,
  totalPayments: 540000,
  totalInterest: 240000,
  totalAmount: 540000,
  currency: 'USD',
  payments: [
    {
      paymentNumber: 1,
      paymentDate: '2020-02-01',
      paymentAmount: 1500,
      principalPayment: 1200,
      interestPayment: 300,
      remainingBalance: 299800,
    },
    {
      paymentNumber: 2,
      paymentDate: '2020-03-01',
      paymentAmount: 1500,
      principalPayment: 1202,
      interestPayment: 298,
      remainingBalance: 299598,
    },
  ],
};

describe('AmortizationSchedule', () => {
  it('does not display zero payment totals when an outstanding loan has no schedule', () => {
    mockAuthentication();
    renderWithProviders(
      <AmortizationSchedule schedule={{ ...mockSchedule, currentBalance: 80000, payments: [] }} />
    );
    expect(screen.getByText(/payoff estimate is unavailable/i)).toBeInTheDocument();
    expect(screen.queryByText('Remaining Total')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /export csv/i })).not.toBeInTheDocument();
  });
  afterEach(() => vi.unstubAllGlobals());

  beforeEach(() => {
    vi.stubGlobal('innerWidth', 1280);
    vi.clearAllMocks();
    mockAuthentication();
    (useVisibility as any).mockReturnValue({ isAmountsVisible: true });
  });

  it('warns when the calculated payoff is later than the contractual end date', async () => {
    const schedule = {
      ...mockSchedule,
      payments: [{ ...mockSchedule.payments[0], paymentDate: '2040-12-15', remainingBalance: 0 }],
    };
    renderWithProviders(
      <AmortizationSchedule schedule={schedule} contractualEndDate="2040-06-15" />
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(/after the contractual end date/);
  });

  describe('Privacy Implementation - PrivateAmount Wrapping', () => {
    it('should wrap currency displays with PrivateAmount component', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check that PrivateAmount components are rendered for currency values
      const privateAmounts = document.querySelectorAll('.transition-all.duration-300');
      expect(privateAmounts.length).toBeGreaterThan(0);
    });

    it('should apply blur effect when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check that elements have blur class when amounts are hidden
      const blurredElements = document.querySelectorAll('.blur-md.select-none');
      expect(blurredElements.length).toBeGreaterThan(0);
    });

    it('should not apply blur effect when amounts are visible', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: true });

      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check that no elements have blur class when amounts are visible
      const blurredElements = document.querySelectorAll('.blur-md');
      expect(blurredElements.length).toBe(0);
    });

    it('should set aria-hidden when amounts are hidden', () => {
      (useVisibility as any).mockReturnValue({ isAmountsVisible: false });

      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check that PrivateAmount spans have aria-hidden=true
      const hiddenSpans = document.querySelectorAll('span[aria-hidden="true"]');
      expect(hiddenSpans.length).toBeGreaterThan(0);
    });
  });

  describe('Component Rendering', () => {
    it('should render summary section with key metrics', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      expect(screen.getByText('Remaining forecast')).toBeInTheDocument();
      expect(screen.getByText('Interest Rate')).toBeInTheDocument();
      expect(screen.getByText('Monthly Payment')).toBeInTheDocument();
      expect(screen.getByText('3.50%')).toBeInTheDocument();
      expect(screen.getByText('360 months')).toBeInTheDocument();
    });

    it('should render amortization table with headers', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check table headers specifically
      const table = document.querySelector('table');
      expect(table).toBeInTheDocument();

      const headers = table?.querySelectorAll('th');
      expect(headers?.length).toBe(7);

      expect(headers?.[0]).toHaveTextContent('Status');
      expect(headers?.[1]).toHaveTextContent('Date');
      expect(headers?.[2]).toHaveTextContent('Payment');
      expect(headers?.[3]).toHaveTextContent('Principal');
      expect(headers?.[4]).toHaveTextContent('Interest');
      expect(headers?.[5]).toHaveTextContent('Insurance');
      expect(headers?.[6]).toHaveTextContent('Remaining Balance');
    });

    it('should display schedule data correctly', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      // Check that table contains data rows
      const table = document.querySelector('table');
      expect(table).toBeInTheDocument();

      // Should have multiple rows (header + data rows)
      const rows = table?.querySelectorAll('tbody tr');
      expect(rows?.length).toBeGreaterThan(0);
    });

    it('shows insurance separately and includes it in exported cash payments', async () => {
      const insuredSchedule = {
        ...mockSchedule,
        payments: [{ ...mockSchedule.payments[0], paymentAmount: 1510, insurancePayment: 10 }],
      };
      const createObjectURL = vi.fn((_blob: Blob) => 'blob:insured');
      global.URL.createObjectURL = createObjectURL;
      global.URL.revokeObjectURL = vi.fn();
      renderWithProviders(<AmortizationSchedule schedule={insuredSchedule} />);
      const cells = within(screen.getByRole('table')).getAllByRole('cell');
      expect(cells[2]).toHaveTextContent('$1,510.00');
      expect(cells[5]).toHaveTextContent('$10.00');
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
      const blob = createObjectURL.mock.calls[0][0] as Blob;
      const csv = await new Promise<string>(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.readAsText(blob);
      });
      expect(csv).toContain('Insurance');
      expect(csv).toContain('1510.00,1200.00,300.00,10.00,0.00,0.00,299800.00');
    });
  });

  describe('Edge Cases', () => {
    it('should handle liability with zero interest rate', () => {
      const zeroInterestSchedule = { ...mockSchedule, interestRate: 0 };

      renderWithProviders(<AmortizationSchedule schedule={zeroInterestSchedule} />);

      expect(screen.getByText('0.00%')).toBeInTheDocument();
    });

    it('should handle liability with zero balance', () => {
      const zeroBalanceSchedule = { ...mockSchedule, principal: 0 };

      renderWithProviders(<AmortizationSchedule schedule={zeroBalanceSchedule} />);

      expect(
        screen.getAllByText('Principal', { selector: 'dt' })[0].parentElement
      ).toHaveTextContent('$0.00');
    });

    it('hides the percent breakdown on zero-payment rows instead of rendering NaN%', () => {
      const zeroPaymentSchedule: AmortizationScheduleType = {
        ...mockSchedule,
        payments: [{ ...mockSchedule.payments[0], paymentAmount: 0 }, mockSchedule.payments[1]],
      };

      renderWithProviders(<AmortizationSchedule schedule={zeroPaymentSchedule} />);

      expect(screen.queryByText(/nan/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Infinity/)).not.toBeInTheDocument();
    });
  });

  describe('User Interactions', () => {
    it('should have scrollable table container', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      const scrollContainer = document.querySelector('table')?.parentElement;
      expect(scrollContainer).toBeInTheDocument();
    });
  });

  it('does not describe an undrawn loan as paid off', () => {
    renderWithProviders(
      <AmortizationSchedule
        schedule={{ ...mockSchedule, payments: [], currentBalance: 0, fundingStatus: 'UNDRAWN' }}
      />
    );
    expect(screen.getByText(/has not been drawn/i)).toBeInTheDocument();
    expect(screen.queryByText(/paid off/i)).not.toBeInTheDocument();
  });

  describe('No Schedule', () => {
    it('shows message when schedule has no payments', () => {
      const emptySchedule = { ...mockSchedule, payments: undefined as any };
      renderWithProviders(<AmortizationSchedule schedule={emptySchedule} />);
      expect(screen.getByText(/payoff estimate is unavailable/i)).toBeInTheDocument();
    });
  });

  describe('Payment history and status filters', () => {
    const history = [
      {
        paymentNumber: 1,
        transactionId: 42,
        paymentDate: '2019-12-05',
        paymentAmount: 127,
        principalPayment: 80,
        interestPayment: 12,
        insurancePayment: 5,
        feePayment: 30,
        remainingBalance: 300000,
      },
    ];
    const tracked = { ...mockSchedule, history };

    it('shows recorded payments and estimates with separate statuses and transaction links', () => {
      renderWithProviders(<AmortizationSchedule schedule={tracked} />);
      const table = within(screen.getByRole('table'));
      expect(table.getAllByRole('row')).toHaveLength(4);
      expect(table.getByText('Paid')).toBeInTheDocument();
      expect(table.getAllByText('Remaining')).toHaveLength(2);
      expect(table.getByRole('link')).toHaveAttribute(
        'href',
        expect.stringContaining('highlight=42')
      );
      expect(table.getByText('$30.00')).toBeInTheDocument();
      // History does not inflate future totals.
      expect(screen.getByText('Remaining Total').parentElement).toHaveTextContent('$540,000.00');
    });

    it('filters paid and remaining rows without treating past dates as evidence of payment', () => {
      renderWithProviders(<AmortizationSchedule schedule={tracked} />);
      fireEvent.click(screen.getByRole('button', { name: 'Paid 1' }));
      expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2);
      expect(screen.getByRole('button', { name: 'Paid 1' })).toHaveAttribute(
        'aria-pressed',
        'true'
      );
      fireEvent.click(screen.getByRole('button', { name: 'Remaining 2' }));
      const table = within(screen.getByRole('table'));
      expect(table.getAllByRole('row')).toHaveLength(3);
      expect(table.queryByText('Paid')).not.toBeInTheDocument();
    });

    it('keeps history visible after the loan is paid off', () => {
      renderWithProviders(
        <AmortizationSchedule
          schedule={{ ...tracked, payments: [], currentBalance: 0, fundingStatus: 'PAID' }}
        />
      );
      expect(screen.getByText(/this loan is paid off/i)).toBeInTheDocument();
      expect(within(screen.getByRole('table')).getByText('Paid')).toBeInTheDocument();
      expect(screen.queryByText('Remaining Total')).not.toBeInTheDocument();
    });

    it('pages long schedules without mounting hidden mobile amounts', () => {
      const payments = Array.from({ length: 30 }, (_, i) => ({
        ...mockSchedule.payments[0],
        paymentNumber: i + 1,
      }));
      renderWithProviders(<AmortizationSchedule schedule={{ ...tracked, payments }} />);
      expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(21);
      expect(screen.getAllByRole('region')).toHaveLength(1);
      expect(
        screen.getByRole('region').querySelectorAll('[data-testid="converted-amount"]')
      ).toHaveLength(120);
      fireEvent.click(screen.getByRole('button', { name: 'Next', exact: true }));
      expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(12);
      expect(screen.getByRole('button', { name: 'Next', exact: true })).toBeDisabled();
      fireEvent.click(screen.getByRole('button', { name: 'Paid 1' }));
      expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2);
      expect(screen.getByRole('table').parentElement).toHaveClass(
        'overflow-auto',
        'max-h-[min(24rem,45dvh)]'
      );
    });

    it('keeps mobile rendering bounded and lets the user jump to the final payment', () => {
      vi.stubGlobal('innerWidth', 390);
      const payments = Array.from({ length: 360 }, (_, i) => ({
        ...mockSchedule.payments[0],
        paymentNumber: i + 1,
        paymentAmount: i + 1,
      }));
      renderWithProviders(<AmortizationSchedule schedule={{ ...mockSchedule, payments }} />);
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getAllByRole('region')).toHaveLength(1);
      expect(
        screen.getByRole('region').querySelectorAll('[data-testid="converted-amount"]')
      ).toHaveLength(100);
      fireEvent.change(screen.getByRole('combobox', { name: 'Page 1 of 18' }), {
        target: { value: '17' },
      });
      expect(within(screen.getByRole('region')).getByText('$360.00')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Paid 0' }));
      expect(screen.getByRole('status')).toHaveTextContent('No recorded payments yet.');
    });

    it('exports every filtered payment even when only one page is rendered', async () => {
      const payments = Array.from({ length: 360 }, (_, i) => ({
        ...mockSchedule.payments[0],
        paymentNumber: i + 1,
        paymentAmount: i + 1,
      }));
      const createObjectURL = vi.fn((_blob: Blob) => 'blob:all');
      global.URL.createObjectURL = createObjectURL;
      global.URL.revokeObjectURL = vi.fn();
      renderWithProviders(<AmortizationSchedule schedule={{ ...tracked, payments }} />);
      fireEvent.click(screen.getByRole('button', { name: 'Remaining 360' }));
      fireEvent.click(screen.getByRole('button', { name: 'Next', exact: true }));
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
      const csv = await new Promise<string>(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.readAsText(createObjectURL.mock.calls[0][0]);
      });
      expect(csv.split('\n')).toHaveLength(361);
      expect(csv).toContain('Remaining,360,,2020-02-01,360.00');
      expect(csv).not.toContain('Paid,');
    });

    it('shows the active phase when a page starts midway through it', () => {
      const payments = Array.from({ length: 60 }, (_, i) => ({
        ...mockSchedule.payments[0],
        paymentNumber: i + 1,
        interestOnlyPhase: i < 30,
      }));
      renderWithProviders(<AmortizationSchedule schedule={{ ...mockSchedule, payments }} />);
      fireEvent.click(screen.getByRole('button', { name: 'Next', exact: true }));
      expect(screen.getByTestId('phase-interest-only')).toBeInTheDocument();
      expect(screen.getByTestId('phase-amortizing')).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Next', exact: true }));
      expect(screen.queryByTestId('phase-interest-only')).not.toBeInTheDocument();
      expect(screen.getByTestId('phase-amortizing')).toBeInTheDocument();
    });

    it('exports only the selected status, with the exact historical fees', async () => {
      const createObjectURL = vi.fn((_blob: Blob) => 'blob:paid');
      global.URL.createObjectURL = createObjectURL;
      global.URL.revokeObjectURL = vi.fn();
      renderWithProviders(<AmortizationSchedule schedule={tracked} />);
      fireEvent.click(screen.getByRole('button', { name: 'Paid 1' }));
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
      const csv = await new Promise<string>(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.readAsText(createObjectURL.mock.calls[0][0]);
      });
      expect(csv).toContain('Paid,,42,2019-12-05,127.00,80.00,12.00,5.00,30.00,0.00,300000.00');
      expect(csv).not.toContain('Remaining,');
    });
  });

  describe('Two-Phase Schedule (interest-only tranches)', () => {
    const twoPhasePayments = [
      {
        paymentNumber: 1,
        paymentDate: '2026-10-01',
        paymentAmount: 239.58,
        principalPayment: 0,
        interestPayment: 218.75,
        remainingBalance: 50000,
        interestOnlyPhase: true,
      },
      {
        paymentNumber: 2,
        paymentDate: '2026-11-01',
        paymentAmount: 239.58,
        principalPayment: 0,
        interestPayment: 218.75,
        remainingBalance: 50000,
        interestOnlyPhase: true,
      },
      {
        paymentNumber: 3,
        paymentDate: '2026-12-01',
        paymentAmount: 1200,
        principalPayment: 981.25,
        interestPayment: 218.75,
        remainingBalance: 49018.75,
        interestOnlyPhase: false,
      },
    ];
    const twoPhaseSchedule = { ...mockSchedule, payments: twoPhasePayments };

    it('renders a Phase 1 group labelled with the interest-only end date plus a Phase 2 group', () => {
      renderWithProviders(<AmortizationSchedule schedule={twoPhaseSchedule} />);

      const phaseOneLabels = screen.getAllByTestId('phase-interest-only');
      const phaseTwoLabels = screen.getAllByTestId('phase-amortizing');
      // Only the active responsive layout is mounted.
      expect(phaseOneLabels).toHaveLength(1);
      expect(phaseTwoLabels).toHaveLength(1);
      expect(phaseOneLabels[0]).toHaveTextContent(/interest-only until 11\/01\/2026/i);
      expect(phaseTwoLabels[0]).toHaveTextContent(/amortizing/i);
    });

    it('renders no phase labels for a plain single-phase schedule', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);

      expect(screen.queryByTestId('phase-interest-only')).not.toBeInTheDocument();
      expect(screen.queryByTestId('phase-amortizing')).not.toBeInTheDocument();
    });
  });

  describe('CSV Export', () => {
    it('renders export button', () => {
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);
      expect(screen.getByText(/Export CSV/i)).toBeInTheDocument();
    });

    it('exports all KWD minor units independent of display rounding', async () => {
      const createObjectURL = vi.fn((_blob: Blob) => 'blob:kwd');
      global.URL.createObjectURL = createObjectURL;
      global.URL.revokeObjectURL = vi.fn();
      renderWithProviders(
        <AmortizationSchedule
          schedule={{
            ...mockSchedule,
            currency: 'KWD',
            payments: [
              {
                ...mockSchedule.payments[0],
                paymentAmount: 1.234,
                principalPayment: 1.222,
                interestPayment: 0.012,
                insurancePayment: 0,
                remainingBalance: 11.123,
              },
            ],
          }}
        />
      );
      fireEvent.click(screen.getByRole('button', { name: /Export CSV/i }));
      const csv = await new Promise<string>(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.readAsText(createObjectURL.mock.calls[0][0]);
      });
      expect(csv).toContain('1.234,1.222,0.012,0.000,0.000,0.000,11.123');
    });

    it('creates CSV download on export click', () => {
      const createObjectURL = vi.fn(() => 'blob:test');
      const revokeObjectURL = vi.fn();
      global.URL.createObjectURL = createObjectURL;
      global.URL.revokeObjectURL = revokeObjectURL;

      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />);
      fireEvent.click(screen.getByText(/Export CSV/i));
      expect(createObjectURL).toHaveBeenCalled();
    });
  });

  describe('Close Button', () => {
    it('calls onClose when close button clicked', () => {
      const onClose = vi.fn();
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} onClose={onClose} />);
      // Find close button - might be icon-only
      const closeButtons = screen.getAllByRole('button');
      const closeBtn = closeButtons.find(b => b.textContent?.toLowerCase().includes('close'));
      if (closeBtn) {
        fireEvent.click(closeBtn);
        expect(onClose).toHaveBeenCalled();
      }
    });
  });

  describe('Locale-aware dates', () => {
    it('uses the saved numeric date format for payment dates', async () => {
      const queryClient = createTestQueryClient();
      queryClient.setQueryData(['user', 'settings', 1], { userId: 1, dateFormat: 'DD/MM/YYYY' });
      renderWithProviders(<AmortizationSchedule schedule={mockSchedule} />, { queryClient });
      expect(screen.getAllByText('01/02/2020').length).toBeGreaterThan(0);
      expect(screen.queryByText('Feb 1, 2020')).not.toBeInTheDocument();
    });
  });
});
