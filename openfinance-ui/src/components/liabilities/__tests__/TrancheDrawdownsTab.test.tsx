/**
 * Unit tests for TrancheDrawdownsTab (Task 8)
 *
 * Verifies the Drawdowns tab of the liability detail dialog: tranche rows with
 * T{n} label, status badge, planned/drawn/remaining amounts, linked property
 * label and interest-only flag. Since Task 9 also covers the "Add tranche"
 * form with the interest-only fields.
 */
import { screen, act, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { renderWithProviders } from '@/test/test-utils';
import { TrancheDrawdownsTab } from '../TrancheDrawdownsTab';
import * as useTranchesModule from '@/hooks/useTranches';
import * as useLiabilitiesModule from '@/hooks/useLiabilities';
import * as useAccountsModule from '@/hooks/useAccounts';
import type { Liability, LiabilityTranche } from '@/types/liability';

vi.mock('@/hooks/useTranches', async importOriginal => {
  const actual = await importOriginal<typeof useTranchesModule>();
  return {
    ...actual,
    useTranches: vi.fn(() => ({ data: [], isLoading: false, error: null })),
    useCreateTranche: vi.fn(),
  };
});

vi.mock('@/hooks/useLiabilities', async importOriginal => {
  const actual = await importOriginal<typeof useLiabilitiesModule>();
  return { ...actual, useDisburseLiability: vi.fn() };
});

vi.mock('@/hooks/useAccounts', async importOriginal => {
  const actual = await importOriginal<typeof useAccountsModule>();
  return { ...actual, useAccounts: vi.fn() };
});

const mockUseTranches = vi.mocked(useTranchesModule.useTranches);
const mockUseCreateTranche = vi.mocked(useTranchesModule.useCreateTranche);
const mockUseDisburseLiability = vi.mocked(useLiabilitiesModule.useDisburseLiability);
const mockUseAccounts = vi.mocked(useAccountsModule.useAccounts);

const mockLiability: Liability = {
  id: 5,
  name: 'Construction loan',
  type: 'LOAN',
  principal: 150000,
  currentBalance: 90000,
  currency: 'USD',
  startDate: '2025-01-01',
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
};

const mockTranches: LiabilityTranche[] = [
  {
    id: 1,
    liabilityId: 5,
    trancheNo: 1,
    plannedAmount: 100000,
    drawnAmount: 100000,
    remaining: 40000,
    status: 'DRAWN',
    realEstateId: 7,
    currency: 'USD',
  },
  {
    id: 2,
    liabilityId: 5,
    trancheNo: 2,
    plannedAmount: 50000,
    drawnAmount: null,
    remaining: 50000,
    status: 'PLANNED',
    interestOnly: true,
    currency: 'USD',
  },
  {
    id: 3,
    liabilityId: 5,
    trancheNo: 3,
    plannedAmount: 20000,
    drawnAmount: null,
    remaining: 20000,
    status: 'PLANNED',
    realEstateId: 8,
    currency: 'USD',
  },
];

const mockAccounts = [
  {
    id: 3,
    name: 'Checking',
    currency: 'USD',
    type: 'CHECKING',
    balance: 5000,
    userId: 1,
    isActive: true,
    createdAt: '2025-01-01',
  },
];

describe('TrancheDrawdownsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseTranches.mockReturnValue({
      data: mockTranches,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTranchesModule.useTranches>);
    mockUseCreateTranche.mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue({}),
      isLoading: false,
      isError: false,
    } as any);
    mockUseDisburseLiability.mockReturnValue({
      mutateAsync: vi.fn().mockResolvedValue({}),
      isLoading: false,
      isError: false,
    } as any);
    mockUseAccounts.mockReturnValue({
      data: mockAccounts,
      isLoading: false,
      isError: false,
    } as any);
  });

  it('renders one row per tranche with its T{n} label and status badge', () => {
    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    expect(screen.getByText('T1')).toBeInTheDocument();
    expect(screen.getByText('T2')).toBeInTheDocument();
    expect(screen.getByText('Drawn')).toBeInTheDocument();
    expect(screen.getAllByText('Planned')).toHaveLength(2); // T2 + T3
  });

  it('renders planned, drawn and remaining amounts for each tranche', () => {
    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    expect(screen.getAllByText('$100,000.00').length).toBeGreaterThanOrEqual(2); // planned + drawn of T1
    expect(screen.getByText('$40,000.00')).toBeInTheDocument(); // remaining T1
    expect(screen.getAllByText('$50,000.00').length).toBeGreaterThanOrEqual(2); // planned + remaining of T2
  });

  it('shows the linked property label when a tranche carries a realEstateId', () => {
    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    expect(screen.getByText('Property #7')).toBeInTheDocument();
  });

  it('shows the interest-only flag on interest-only tranches', () => {
    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    expect(screen.getByText('Interest-only')).toBeInTheDocument();
  });

  it('renders the empty state when the liability has no tranches', () => {
    mockUseTranches.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTranchesModule.useTranches>);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    expect(screen.getByText(/no planned drawdowns/i)).toBeInTheDocument();
  });

  // ── Add tranche (Task 9: interest-only UI) ────────────────────────────────

  it('opens the add-tranche form from the empty state', () => {
    mockUseTranches.mockReturnValue({
      data: [],
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useTranchesModule.useTranches>);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    fireEvent.click(screen.getByRole('button', { name: /add tranche/i }));

    expect(screen.getByLabelText(/planned amount/i)).toBeInTheDocument();
    expect(screen.getByLabelText('Interest-only')).toBeInTheDocument();
  });

  it('creates an interest-only tranche with its end date', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    mockUseCreateTranche.mockReturnValue({
      mutateAsync,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    fireEvent.click(screen.getByRole('button', { name: /add tranche/i }));

    await act(async () => {
      fireEvent.change(screen.getByLabelText(/planned amount/i), {
        target: { value: '50000' },
      });
      const interestOnly = screen.getByLabelText('Interest-only') as HTMLInputElement;
      interestOnly.click();
      fireEvent.change(screen.getByLabelText('Interest-only until'), {
        target: { value: '2027-06-01' },
      });
    });
    await act(async () => {
      screen.getByRole('button', { name: /^add$/i }).click();
    });

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith({
      liabilityId: 5,
      request: expect.objectContaining({
        plannedAmount: '50000',
        interestOnly: true,
        interestOnlyUntil: '2027-06-01',
        currency: 'USD',
      }),
    });
  });

  it('creates an open-ended interest-only tranche without an end date (until is optional)', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    mockUseCreateTranche.mockReturnValue({
      mutateAsync,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    fireEvent.click(screen.getByRole('button', { name: /add tranche/i }));

    await act(async () => {
      fireEvent.change(screen.getByLabelText(/planned amount/i), {
        target: { value: '40000' },
      });
      const interestOnly = screen.getByLabelText('Interest-only') as HTMLInputElement;
      interestOnly.click();
    });
    await act(async () => {
      screen.getByRole('button', { name: /^add$/i }).click();
    });

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith({
      liabilityId: 5,
      request: expect.objectContaining({
        plannedAmount: '40000',
        interestOnly: true,
        interestOnlyUntil: undefined,
      }),
    });
  });

  it('creates a plain tranche without the interest-only fields', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    mockUseCreateTranche.mockReturnValue({
      mutateAsync,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    fireEvent.click(screen.getByRole('button', { name: /add tranche/i }));

    await act(async () => {
      fireEvent.change(screen.getByLabelText(/planned amount/i), {
        target: { value: '25000' },
      });
    });
    await act(async () => {
      screen.getByRole('button', { name: /^add$/i }).click();
    });

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith({
      liabilityId: 5,
      request: expect.objectContaining({ plannedAmount: '25000', interestOnly: false }),
    });
  });

  // ── Draw action (tranche drawdown) ────────────────────────────────────────

  it('shows a Draw action on PLANNED tranches only', () => {
    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    const drawButtons = screen.getAllByRole('button', { name: /^draw$/i });
    expect(drawButtons).toHaveLength(2); // T2 and T3 are PLANNED
  });

  it('draws a PLANNED tranche to an account with the remaining amount prefilled', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    mockUseDisburseLiability.mockReturnValue({
      mutateAsync,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    // T2 row (PLANNED) — open its draw form
    const row = screen.getByText('T2').closest('.divide-y > div') as HTMLElement;
    await act(async () => {
      withinRow(row, /^draw$/i).click();
    });

    // Amount prefilled with the remaining planned amount (NumberInput groups thousands)
    const prefilled = (screen.getByLabelText(/amount/i) as HTMLInputElement).value;
    expect(Number(prefilled.replace(/,/g, ''))).toBe(50000);

    await act(async () => {
      fireEvent.change(screen.getByLabelText('Date'), { target: { value: '2025-06-15' } });
      fireEvent.change(screen.getByLabelText(/account/i), { target: { value: '3' } });
    });
    await act(async () => {
      screen.getByRole('button', { name: /confirm disbursement/i }).click();
    });

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith({
      liabilityId: 5,
      request: {
        trancheId: 2,
        toAccountId: 3,
        amount: '50000',
        date: '2025-06-15',
        directRealEstateId: undefined,
      },
    });
  });

  it('routes the drawdown directly to the linked property when that route is chosen', async () => {
    const mutateAsync = vi.fn().mockResolvedValue({});
    mockUseDisburseLiability.mockReturnValue({
      mutateAsync,
      isLoading: false,
      isError: false,
    } as any);

    renderWithProviders(<TrancheDrawdownsTab liability={mockLiability} />);

    // T3 row (PLANNED with linked property)
    const row = screen.getByText('T3').closest('.divide-y > div') as HTMLElement;
    await act(async () => {
      withinRow(row, /^draw$/i).click();
    });

    await act(async () => {
      fireEvent.change(screen.getByLabelText(/route/i), { target: { value: 'property' } });
      fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '15000' } });
    });
    await act(async () => {
      screen.getByRole('button', { name: /confirm disbursement/i }).click();
    });

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(mutateAsync).toHaveBeenCalledWith({
      liabilityId: 5,
      request: {
        trancheId: 3,
        toAccountId: undefined,
        amount: '15000',
        date: expect.any(String),
        directRealEstateId: 8,
      },
    });
  });
});

/** Finds a button by name within a tranche row. */
function withinRow(row: HTMLElement, name: RegExp): HTMLElement {
  const button = Array.from(row.querySelectorAll('button')).find(b =>
    name.test(b.textContent ?? '')
  );
  if (!button) throw new Error(`Button ${name} not found in row`);
  return button;
}
