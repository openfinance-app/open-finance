import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import type { LiabilityTranche } from '@/types/liability';
/**
 * Unit tests for PropertyMovementsSection (Task 8)
 *
 * Verifies the Costs section (Capitalized vs Maintenance, fetched via the
 * realEstateId transaction filter) and the Loan movements section
 * (repayments/disbursements of the property's mortgage).
 */
import { screen, within, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { mockAuthentication, renderWithProviders, userEvent } from '@/test/test-utils';
import { PropertyMovementsSection } from '@/components/real-estate/PropertyMovementsSection';
import * as useTransactionsModule from '@/hooks/useTransactions';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { RealEstateProperty } from '@/types/realEstate';
import type { Transaction } from '@/types/transaction';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';

vi.mock('@/components/assets/RecordCostForm', () => ({
  RecordCostForm: ({
    target,
    onClose,
  }: {
    target: { id: number; kind: string };
    onClose: () => void;
  }) => (
    <section aria-label={`Cost for ${target.kind} ${target.id}`}>
      <button onClick={onClose}>Close cost form</button>
    </section>
  ),
}));

const currencyDisplay = vi.hoisted(() => ({ mode: 'base' as AmountDisplayMode }));
vi.mock('@/context/CurrencyDisplayContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/CurrencyDisplayContext')>()),
  useCurrencyDisplay: () => ({ displayMode: currencyDisplay.mode, secondaryCurrency: null }),
}));

vi.mock('@/hooks/useTransactions', async importOriginal => {
  const actual = await importOriginal<typeof useTransactionsModule>();
  return {
    ...actual,
    useTransactions: vi.fn(() => ({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 0 },
      isLoading: false,
      error: null,
    })),
  };
});

vi.mock('@/hooks/useLiabilities', async importOriginal => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return {
    ...actual,
    useLiabilityTransactions: vi.fn(() => ({
      data: [],
      isLoading: false,
      error: null,
    })),
  };
});

const mockUseTransactions = vi.mocked(useTransactionsModule.useTransactions);
const mockUseLiabilityTransactions = vi.mocked(useLiabilitiesModule.useLiabilityTransactions);

const mockProperty: RealEstateProperty = {
  id: 7,
  userId: 1,
  name: 'Cabin',
  address: '1 Forest Rd',
  propertyType: 'RESIDENTIAL',
  purchasePrice: 100000,
  purchaseDate: '2024-01-01',
  currentValue: 120000,
  currency: 'USD',
  mortgageId: 42,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

const kitchenTx: Transaction = {
  id: 101,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 15000,
  currency: 'USD',
  date: '2026-01-10',
  description: 'Kitchen renovation',
  isReconciled: false,
  createdAt: '2026-01-10T00:00:00Z',
  realEstateId: 7,
  movementType: 'CAPITAL_IMPROVEMENT',
};

const leakTx: Transaction = {
  id: 102,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 500,
  currency: 'USD',
  date: '2026-02-02',
  description: 'Roof leak repair',
  isReconciled: false,
  createdAt: '2026-02-02T00:00:00Z',
  realEstateId: 7,
  movementType: 'MAINTENANCE',
};

const repaymentTx: Transaction = {
  id: 103,
  userId: 1,
  accountId: 1,
  type: 'EXPENSE',
  amount: 1200,
  currency: 'USD',
  date: '2026-03-01',
  description: 'Mortgage payment',
  isReconciled: false,
  createdAt: '2026-03-01T00:00:00Z',
  liabilityId: 42,
  movementType: 'REPAYMENT',
};

describe('PropertyMovementsSection', () => {
  beforeEach(() => {
    mockAuthentication();
    server.use(
      http.get('*/real-estate/7/drawdowns', () => HttpResponse.json([])),
      http.get('*/real-estate/7/loan-movements', () => HttpResponse.json([]))
    );
    vi.clearAllMocks();
    currencyDisplay.mode = 'base';
    mockUseTransactions.mockReturnValue({
      data: {
        content: [kitchenTx, leakTx],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 2,
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
    mockUseLiabilityTransactions.mockReturnValue({
      data: [repaymentTx],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useLiabilitiesModule.useLiabilityTransactions>);
  });

  it('renders the kitchen improvement under Capitalized', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText('Costs')).toBeInTheDocument();
    // Section title + movement badge both read "Capitalized"
    expect(screen.getAllByText('Capitalized').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Kitchen renovation')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Kitchen renovation/ })).toHaveAttribute(
      'href',
      '/transactions?realEstateId=7&highlight=101'
    );
    expect(screen.getByText('$15,000.00')).toBeInTheDocument();
  });

  it('renders the repair under Maintenance', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getAllByText('Maintenance').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Roof leak repair')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Roof leak repair/ })).toHaveAttribute(
      'href',
      '/transactions?realEstateId=7&highlight=102'
    );
    expect(screen.getByText('$500.00')).toBeInTheDocument();
  });

  it('renders the mortgage repayment under Loan movements', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText('Loan movements')).toBeInTheDocument();
    expect(screen.getByText('Mortgage payment')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Mortgage payment/ })).toHaveAttribute(
      'href',
      '/transactions?liabilityId=42&highlight=103'
    );
    expect(screen.getByText('$1,200.00')).toBeInTheDocument();
    expect(screen.getByText('Repayment')).toBeInTheDocument();
  });

  it('fetches property transactions filtered by realEstateId', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(mockUseTransactions).toHaveBeenCalledWith(expect.objectContaining({ realEstateId: 7 }));
  });

  it.each(['base', 'native', 'both'] as const)(
    'honors %s currency display for acquisition, costs and loan transactions',
    mode => {
      currencyDisplay.mode = mode;
      const conversion = {
        currency: 'XOF',
        baseCurrency: 'EUR',
        exchangeRate: 1 / 655.957,
        isConverted: true,
      };
      const transactions: Transaction[] = [
        {
          ...kitchenTx,
          ...conversion,
          id: 104,
          assetId: 8,
          realEstateId: undefined,
          movementType: undefined,
          type: 'INCOME',
          transferId: 'imported-property-transfer',
          amount: 655957,
          amountInBaseCurrency: 1000,
        },
        { ...kitchenTx, ...conversion, amount: 1311914, amountInBaseCurrency: 2000 },
        { ...leakTx, ...conversion, amount: 1967871, amountInBaseCurrency: 3000 },
      ];
      mockUseTransactions.mockReturnValue({
        data: { content: transactions, totalElements: 3, totalPages: 1, number: 0, size: 200 },
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
      mockUseLiabilityTransactions.mockReturnValue({
        data: [{ ...repaymentTx, ...conversion, amount: 2623828, amountInBaseCurrency: 4000 }],
        isLoading: false,
        error: null,
      } as unknown as ReturnType<typeof useLiabilitiesModule.useLiabilityTransactions>);

      renderWithProviders(
        <PropertyMovementsSection
          property={{ ...mockProperty, assetId: 8, convertedFromHolding: true, currency: 'XOF' }}
        />
      );

      const amounts = [
        ['Acquisition and other payments', '€1,000.00', /655,957/],
        ['Capitalized', '€2,000.00', /1,311,914/],
        ['Maintenance', '€3,000.00', /1,967,871/],
        ['Loan movements', '€4,000.00', /2,623,828/],
      ] as const;
      for (const [section, base, native] of amounts) {
        const list = within(screen.getByRole('region', { name: section, exact: true }));
        if (mode !== 'native') expect(list.getByText(base)).toBeVisible();
        else expect(list.queryByText(base)).not.toBeInTheDocument();
        if (mode !== 'base') expect(list.getByText(native)).toBeVisible();
        else expect(list.queryByText(native)).not.toBeInTheDocument();
      }
    }
  );

  it('shows imported transfers alongside new costs and opens the same property history', () => {
    const imported: Transaction = {
      ...kitchenTx,
      id: 104,
      assetId: 8,
      realEstateId: undefined,
      movementType: undefined,
      type: 'INCOME',
      transferId: 'original-transfer',
      investmentQuantity: 0.5,
      investmentCash: false,
      amount: 500,
      description: 'Imported property contribution',
    };
    mockUseTransactions.mockReturnValue({
      data: {
        content: [imported, kitchenTx],
        totalElements: 2,
        totalPages: 1,
        number: 0,
        size: 200,
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);

    renderWithProviders(
      <PropertyMovementsSection
        property={{ ...mockProperty, assetId: 8, convertedFromHolding: true }}
      />
    );

    expect(
      screen.getByRole('link', { name: /Imported property contribution\s*Transfer/ })
    ).toHaveAttribute('href', '/transactions?realEstateId=7&highlight=104');
    expect(screen.getByRole('link', { name: 'Open transactions' })).toHaveAttribute(
      'href',
      '/transactions?realEstateId=7'
    );
    expect(screen.getByText('Kitchen renovation')).toBeInTheDocument();
    expect(screen.queryByText(/no movements recorded yet/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'View imported transactions' })
    ).not.toBeInTheDocument();
    expect(screen.queryByText('Income')).not.toBeInTheDocument();
  });

  it('fetches loan movements for the property mortgage liability', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(mockUseLiabilityTransactions).toHaveBeenCalledWith(42);
  });

  it('renders the empty state when there are no movements at all', () => {
    mockUseTransactions.mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 1, number: 0, size: 0 },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
    mockUseLiabilityTransactions.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useLiabilitiesModule.useLiabilityTransactions>);

    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText(/no movements recorded yet/i)).toBeInTheDocument();
  });

  it('renders the error banner when the costs query is rejected', () => {
    mockUseTransactions.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Rejected'),
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);

    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText(/failed to load movements/i)).toBeInTheDocument();
    expect(screen.queryByText(/no movements recorded yet/i)).not.toBeInTheDocument();
  });

  it('renders the error banner when the loan movements query is rejected', () => {
    mockUseLiabilityTransactions.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Rejected'),
    } as unknown as ReturnType<typeof useLiabilitiesModule.useLiabilityTransactions>);

    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText(/failed to load movements/i)).toBeInTheDocument();
    expect(screen.queryByText(/no movements recorded yet/i)).not.toBeInTheDocument();
  });

  it('renders the loading skeleton while the loan movements query is loading', () => {
    mockUseLiabilityTransactions.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as unknown as ReturnType<typeof useLiabilitiesModule.useLiabilityTransactions>);

    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    // Costs data is available but loan movements still load → skeleton, not rows
    expect(document.querySelector('.animate-pulse')).toBeInTheDocument();
    expect(screen.queryByText('Kitchen renovation')).not.toBeInTheDocument();
  });
  it.each([
    { isActive: false },
    { soldDate: '2026-01-01' },
    { acquisitionType: 'PLANNED' as const },
  ])('closed or planned properties cannot add costs: %j', fields => {
    renderWithProviders(<PropertyMovementsSection property={{ ...mockProperty, ...fields }} />);
    expect(screen.queryByRole('button', { name: 'Add cost' })).not.toBeInTheDocument();
  });

  it('opens the cost form for the correct property and can close then reopen it', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
    const add = screen.getByRole('button', { name: 'Add cost' });
    await user.click(add);
    expect(add).toBeDisabled();
    expect(screen.getByRole('region', { name: 'Cost for property 7' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Close cost form' }));
    expect(add).toBeEnabled();
    await user.click(add);
    expect(screen.getByRole('region', { name: 'Cost for property 7' })).toBeVisible();
  });

  it.each([false, true])(
    'direct funding keeps reversal and capitalized-cost meaning: %s',
    async reversed => {
      const draw: LiabilityTranche = {
        id: 1,
        liabilityId: 42,
        trancheNo: 2,
        plannedAmount: 12,
        remaining: 0,
        status: 'DRAWN',
        currency: 'USD',
        drawnAmount: 12,
        drawnDate: '2026-01-02',
        capitalizedAmount: reversed ? 12 : undefined,
        reversedDate: reversed ? '2026-01-03' : null,
        exactValues: { drawnAmount: '12.345' },
      };
      server.use(http.get('*/real-estate/7/drawdowns', () => HttpResponse.json([draw])));
      renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
      const list = within(await screen.findByRole('region', { name: 'Funding drawdowns' }));
      expect(list.getByText('$12.35')).toBeVisible();
      if (reversed) {
        expect(list.getByText(/reversed 2026-01-03/)).toBeVisible();
        expect(list.getByText('Added to property cost')).toBeVisible();
      } else expect(list.queryByText('Added to property cost')).not.toBeInTheDocument();
    }
  );

  it.each(['drawdowns', 'loan-movements'])(
    'reports failed %s requests instead of presenting a partial history as complete',
    async endpoint => {
      server.use(
        http.get(`*/real-estate/7/${endpoint}`, () => new HttpResponse(null, { status: 500 }))
      );
      renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
      expect(await screen.findByText(/Failed to load movements/)).toBeVisible();
    }
  );

  it('deduplicates related loans and discards transactions without a loan classification', async () => {
    server.use(
      http.get('*/real-estate/7/loan-movements', () =>
        HttpResponse.json([
          repaymentTx,
          { ...repaymentTx, id: 104, description: 'Unclassified', movementType: null },
          { ...repaymentTx, id: 105, description: 'Unrelated cost', movementType: 'MAINTENANCE' },
          { ...repaymentTx, id: 106, description: 'Loan fee', movementType: 'FEE' },
        ])
      )
    );
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
    expect(await screen.findByText('Loan fee')).toBeVisible();
    expect(screen.getAllByText('Mortgage payment')).toHaveLength(1);
    expect(screen.queryByText('Unclassified')).not.toBeInTheDocument();
    expect(screen.queryByText('Unrelated cost')).not.toBeInTheDocument();
  });

  it('uses a transaction fallback label and the property currency for older cost records', () => {
    mockUseTransactions.mockReturnValue({
      data: { content: [{ ...kitchenTx, description: '', currency: '' }], totalPages: 1 },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
    expect(screen.getByRole('link', { name: /Transaction #101/ })).toHaveAttribute(
      'href',
      '/transactions?realEstateId=7&highlight=101'
    );
    expect(screen.getByText('$15,000.00')).toBeVisible();
  });

  it('ordinary imported holding income is not labeled as a transfer', () => {
    mockUseTransactions.mockReturnValue({
      data: {
        content: [{ ...kitchenTx, assetId: 8, movementType: undefined, type: 'INCOME' }],
        totalPages: 1,
      },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
    renderWithProviders(
      <PropertyMovementsSection
        property={{
          ...mockProperty,
          assetId: 8,
          convertedFromHolding: true,
          mortgageId: undefined,
        }}
      />
    );
    expect(screen.getByRole('link', { name: /Kitchen renovation\s*Income/ })).toBeVisible();
    expect(mockUseLiabilityTransactions).toHaveBeenCalledWith(null);
  });

  it('page navigation requests both directions and prevents stepping beyond the last page', async () => {
    const user = userEvent.setup();
    mockUseTransactions.mockReturnValue({
      data: { content: [kitchenTx], totalPages: 2 },
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTransactionsModule.useTransactions>);
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);
    const previous = screen.getByRole('button', { name: 'Previous' });
    const next = screen.getByRole('button', { name: 'Next' });
    expect(previous).toBeDisabled();
    await user.click(next);
    await waitFor(() =>
      expect(mockUseTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }))
    );
    expect(next).toBeDisabled();
    await user.click(previous);
    await waitFor(() =>
      expect(mockUseTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 }))
    );
  });
});
