import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InterestRateVariationsSection } from './InterestRateVariationsSection';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

const useInterestRateVariationsMock = vi.fn();
const useInterestEstimateMock = vi.fn();
const createMutateMock = vi.fn();
const deleteMutateMock = vi.fn();
const updateMutateMock = vi.fn();

vi.mock('@/hooks/useAccounts', () => ({
  useInterestRateVariations: (...args: unknown[]) => useInterestRateVariationsMock(...args),
  useCreateVariation: () => ({
    mutate: createMutateMock,
    isPending: false,
  }),
  useDeleteVariation: () => ({
    mutate: deleteMutateMock,
    isPending: false,
  }),
  useInterestEstimate: (...args: unknown[]) => useInterestEstimateMock(...args),
  useUpdateVariation: () => ({ mutate: updateMutateMock, isPending: false }),
}));

describe('InterestRateVariationsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();

    useInterestRateVariationsMock.mockReturnValue({
      data: [],
      isLoading: false,
    });

    useInterestEstimateMock.mockReturnValue({
      data: {
        historicalAccumulated: 789.66,
        yearToDate: 294.51,
        estimate: 375.32,
      },
      isLoading: false,
      isError: false,
    });

    createMutateMock.mockImplementation((_payload, options) => {
      options?.onSuccess?.();
    });

    deleteMutateMock.mockImplementation(() => undefined);
    vi.stubGlobal(
      'confirm',
      vi.fn(() => true)
    );
  });

  it('shows the complete rate history and scheduled rates without a year filter', () => {
    useInterestRateVariationsMock.mockReturnValue({
      isLoading: false,
      data: [
        {
          id: 1,
          validFrom: '2020-01-01',
          rate: 4,
          taxRate: 0,
          activeDays: 3,
          interestProduced: 3.75,
        },
        { id: 2, validFrom: '2099-01-01', rate: 6, taxRate: 0, activeDays: 0, interestProduced: 0 },
      ],
    });
    renderWithProviders(<InterestRateVariationsSection accountId={1} accountCurrency="USD" />);
    expect(screen.getByText('3d')).toBeInTheDocument();
    expect(screen.getByText('Scheduled')).toBeInTheDocument();
    expect(screen.getByText('$3.75')).toBeInTheDocument();
    expect(screen.getByText('$789.66')).toBeInTheDocument();
    expect(useInterestEstimateMock).toHaveBeenCalledWith(1);
  });

  it('renders empty state when there are no variations', () => {
    renderWithProviders(<InterestRateVariationsSection accountId={1} accountCurrency="USD" />);

    expect(screen.getByText(/interest rate history|interest\.historyTitle/i)).toBeInTheDocument();
    expect(
      screen.getByText(/no interest rate variations|interest\.noVariations/i)
    ).toBeInTheDocument();
    expect(screen.getByText('$789.66')).toBeInTheDocument();
  });

  it('creates a variation from dialog form submit', async () => {
    const user = userEvent.setup();

    renderWithProviders(<InterestRateVariationsSection accountId={12} accountCurrency="EUR" />);

    await user.click(screen.getByRole('button', { name: /add rate|interest\.addRate/i }));
    const dateInput = await waitFor(() => screen.getByLabelText(/effective date/i));
    fireEvent.change(dateInput, { target: { value: '2026-05-01' } });
    const rateInput = screen.getByRole('textbox', { name: /interest rate \(%\)/i });
    const taxRateInput = screen.getByRole('textbox', { name: /tax rate \(%\)/i });
    fireEvent.change(rateInput, { target: { value: '4.5' } });
    fireEvent.change(taxRateInput, { target: { value: '10' } });

    await user.click(
      screen.getByRole('button', { name: /save rate|interest\.addDialog\.saveRate/i })
    );

    expect(createMutateMock).toHaveBeenCalledWith(
      {
        accountId: 12,
        data: {
          rate: '4.5',
          taxRate: '10',
          validFrom: '2026-05-01',
          creditValueDateMode: 'TRANSACTION_DATE',
          debitValueDateMode: 'TRANSACTION_DATE',
          dayCountBasis: 'ACTUAL_365',
        },
      },
      expect.any(Object)
    );
  });

  it('renders variation rows and deletes a variation', async () => {
    const user = userEvent.setup();
    useInterestRateVariationsMock.mockReturnValue({
      data: [
        {
          id: 9,
          validFrom: '2026-04-01',
          rate: 3.25,
          taxRate: 12,
        },
      ],
      isLoading: false,
    });

    renderWithProviders(<InterestRateVariationsSection accountId={33} accountCurrency="USD" />);

    expect(screen.getByText('3.25%')).toBeInTheDocument();
    const buttons = screen.getAllByRole('button');
    await user.click(buttons[buttons.length - 1]);

    expect(deleteMutateMock).toHaveBeenCalledWith({ accountId: 33, variationId: 9 });
  });

  it('shows current-year accrual, current-year projection and lifetime earnings without duplicate projections', () => {
    renderWithProviders(<InterestRateVariationsSection accountId={7} accountCurrency="EUR" />);
    expect(screen.queryByRole('combobox', { name: 'Year' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Full-year estimate/)).not.toBeInTheDocument();
    expect(screen.getByText('Proj. 1Y Net')).toBeInTheDocument();
    expect(screen.getByText('€375.32')).toBeInTheDocument();
    expect(screen.getByText('Earned to date')).toBeInTheDocument();
    expect(screen.getByText('€294.51')).toBeInTheDocument();
    expect(screen.getByText('Total earned')).toBeInTheDocument();
    expect(screen.getByText('€789.66')).toBeInTheDocument();
  });

  it('edits saved value-date conventions without rounding an exact rate', async () => {
    const exact = '1.123456789012345678';
    useInterestRateVariationsMock.mockReturnValue({
      data: [
        {
          id: 5,
          validFrom: '2020-01-01',
          rate: 1.1234567890123457,
          taxRate: 0,
          exactValues: { rate: exact, taxRate: '0' },
          creditValueDateMode: 'FORTNIGHT',
          debitValueDateMode: 'DAYS_2',
          dayCountBasis: 'ACTUAL_365',
        },
      ],
      isLoading: false,
    });
    renderWithProviders(<InterestRateVariationsSection accountId={7} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Edit rate' }));
    expect(screen.getByRole('textbox', { name: /interest rate \(%\)/i })).toHaveValue(exact);
    expect(screen.getByRole('combobox', { name: 'Credit value date' })).toHaveValue('FORTNIGHT');
    expect(screen.getByRole('combobox', { name: 'Debit value date' })).toHaveValue('DAYS_2');
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Calculation basis' }),
      'THIRTY_360'
    );
    await user.click(screen.getByRole('button', { name: 'Save Rate' }));
    await waitFor(() =>
      expect(updateMutateMock).toHaveBeenCalledWith(
        {
          accountId: 7,
          variationId: 5,
          data: {
            rate: exact,
            taxRate: '0',
            validFrom: '2020-01-01',
            creditValueDateMode: 'FORTNIGHT',
            debitValueDateMode: 'DAYS_2',
            dayCountBasis: 'THIRTY_360',
          },
        },
        expect.any(Object)
      )
    );
  });
});
