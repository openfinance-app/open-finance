import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { InterestRateVariationsSection } from './InterestRateVariationsSection';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

const useInterestRateVariationsMock = vi.fn();
const useInterestEstimateMock = vi.fn();
const createMutateMock = vi.fn();
const deleteMutateMock = vi.fn();

vi.mock('@/hooks/useFormatCurrency', () => ({
  useFormatCurrency: () => ({
    format: (amount: number, currency: string) => `${amount.toFixed(2)} ${currency}`,
  }),
}));

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
        historicalAccumulated: 120,
        estimate: 240,
      },
    });

    createMutateMock.mockImplementation((_payload, options) => {
      options?.onSuccess?.();
    });

    deleteMutateMock.mockImplementation(() => undefined);
    vi.stubGlobal('confirm', vi.fn(() => true));
  });

  it('renders empty state when there are no variations', () => {
    renderWithProviders(
      <InterestRateVariationsSection
        accountId={1}
        accountBalance={1000}
        accountCurrency="USD"
      />
    );

    expect(screen.getByText(/interest rate history|interest\.historyTitle/i)).toBeInTheDocument();
    expect(screen.getByText(/no interest rate variations|interest\.noVariations/i)).toBeInTheDocument();
    expect(screen.getByText('120.00 USD')).toBeInTheDocument();
  });

  it('creates a variation from dialog form submit', async () => {
    const user = userEvent.setup();

    renderWithProviders(
      <InterestRateVariationsSection
        accountId={12}
        accountBalance={2000}
        accountCurrency="EUR"
      />
    );

    await user.click(screen.getByRole('button', { name: /add rate|interest\.addRate/i }));
    const dateInput = await waitFor(() => screen.getByLabelText(/effective date/i));
    fireEvent.change(dateInput, { target: { value: '2026-05-01' } });
    const numberInputs = screen.getAllByRole('spinbutton');
    fireEvent.change(numberInputs[0], { target: { value: '4.5', valueAsNumber: 4.5 } });
    fireEvent.change(numberInputs[1], { target: { value: '10', valueAsNumber: 10 } });

    await user.click(screen.getByRole('button', { name: /save rate|interest\.addDialog\.saveRate/i }));

    expect(createMutateMock).toHaveBeenCalledWith(
      {
        accountId: 12,
        data: {
          rate: 4.5,
          taxRate: 10,
          validFrom: '2026-05-01',
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

    renderWithProviders(
      <InterestRateVariationsSection
        accountId={33}
        accountBalance={5000}
        accountCurrency="USD"
      />
    );

    expect(screen.getByText('3.25%')).toBeInTheDocument();
    const buttons = screen.getAllByRole('button');
    await user.click(buttons[buttons.length - 1]);

    expect(deleteMutateMock).toHaveBeenCalledWith({ accountId: 33, variationId: 9 });
  });
});