/**
 * Unit tests for PropertyMovementsSection (Task 8)
 *
 * Verifies the Costs section (Capitalized vs Maintenance, fetched via the
 * realEstateId transaction filter) and the Loan movements section
 * (repayments/disbursements of the property's mortgage).
 */
import { screen } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { PropertyMovementsSection } from './PropertyMovementsSection';
import * as useTransactionsModule from '@/hooks/useTransactions';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import type { RealEstateProperty } from '@/types/realEstate';
import type { Transaction } from '@/types/transaction';

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
    vi.clearAllMocks();
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
      '/transactions?highlight=101'
    );
    expect(screen.getByText('$15,000.00')).toBeInTheDocument();
  });

  it('renders the repair under Maintenance', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getAllByText('Maintenance').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Roof leak repair')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Roof leak repair/ })).toHaveAttribute(
      'href',
      '/transactions?highlight=102'
    );
    expect(screen.getByText('$500.00')).toBeInTheDocument();
  });

  it('renders the mortgage repayment under Loan movements', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(screen.getByText('Loan movements')).toBeInTheDocument();
    expect(screen.getByText('Mortgage payment')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Mortgage payment/ })).toHaveAttribute(
      'href',
      '/transactions?highlight=103'
    );
    expect(screen.getByText('$1,200.00')).toBeInTheDocument();
    expect(screen.getByText('Repayment')).toBeInTheDocument();
  });

  it('fetches property transactions filtered by realEstateId', () => {
    renderWithProviders(<PropertyMovementsSection property={mockProperty} />);

    expect(mockUseTransactions).toHaveBeenCalledWith(expect.objectContaining({ realEstateId: 7 }));
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
});
