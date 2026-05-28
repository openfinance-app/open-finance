import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';
import LiabilitiesPage from '@/pages/LiabilitiesPage';
import type { Liability } from '@/types/liability';

// ---------------------------------------------------------------------------
// Mock liability data
// ---------------------------------------------------------------------------
const mockLiability: Liability = {
  id: 1,
  userId: 1,
  name: 'Mortgage',
  liabilityType: 'MORTGAGE',
  principalAmount: 250000,
  currentBalance: 240000,
  interestRate: 3.5,
  currency: 'USD',
  startDate: '2020-01-01',
  createdAt: '2020-01-01T00:00:00Z',
};

let mockPagedResponse: any = {
  content: [mockLiability],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};
let mockIsLoading = false;
let mockError: Error | null = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

// ---------------------------------------------------------------------------
// Mock hooks
// ---------------------------------------------------------------------------
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => ({ data: [], isLoading: false, error: null }),
  useLiabilitiesPaged: () => ({ data: mockPagedResponse, isLoading: mockIsLoading, error: mockError }),
  useLiability: () => ({ data: null, isLoading: false, error: null }),
  useCreateLiability: () => ({ mutate: vi.fn(), mutateAsync: mockCreateMutateAsync, isPending: false }),
  useUpdateLiability: () => ({ mutate: vi.fn(), mutateAsync: mockUpdateMutateAsync, isPending: false }),
  useDeleteLiability: () => ({ mutate: vi.fn(), mutateAsync: mockDeleteMutateAsync, isPending: false }),
  useAmortizationSchedule: () => ({ data: null, isLoading: false }),
  useLiabilityTotals: () => ({ data: null, isLoading: false }),
  useLiabilityBreakdown: () => ({ data: null, isLoading: false }),
  useLiabilityTransactions: () => ({ data: [], isLoading: false }),
  getLiabilityTypeName: (type: string) => type,
  getLiabilityTypeBadgeVariant: () => 'default',
  calculateTotalInterest: () => 0,
  calculateMonthsRemaining: () => 0,
  formatCurrency: (amount: number, currency = 'USD') => `${currency} ${amount}`,
}));

vi.mock('@/components/liabilities/LiabilityForm', () => ({
  LiabilityForm: ({ onSubmit, onCancel, liability }: any) => (
    <div data-testid="liability-form">
      {liability && <span data-testid="editing-name">{liability.name}</span>}
      <button onClick={() => onSubmit({ name: 'Test', liabilityType: 'MORTGAGE', principalAmount: 100000, currency: 'USD', interestRate: 5, startDate: '2024-01-01' })}>Submit</button>
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/liabilities/LiabilityList', () => ({
  LiabilityList: ({ liabilities, onEdit, onDelete, onViewDetails }: any) => (
    <div data-testid="liability-list">
      {liabilities.map((l: any) => (
        <div key={l.id} data-testid={`liability-${l.id}`}>
          <span>{l.name}</span>
          <button onClick={() => onEdit(l)}>Edit</button>
          <button onClick={() => onDelete(l.id)}>Delete</button>
          <button onClick={() => onViewDetails(l)}>View Details</button>
        </div>
      ))}
    </div>
  ),
}));

vi.mock('@/components/liabilities/LiabilityFilters', () => ({
  LiabilityFilters: ({ onFiltersChange }: any) => (
    <div data-testid="liability-filters">
      <button onClick={() => onFiltersChange({ type: 'MORTGAGE', search: '' })}>Apply Filter</button>
    </div>
  ),
}));

vi.mock('@/components/liabilities/LiabilitySummaryCards', () => ({
  LiabilitySummaryCards: () => <div data-testid="summary-cards">Summary</div>,
}));

vi.mock('@/components/liabilities/LiabilityDetailDialog', () => ({
  LiabilityDetailDialog: ({ liability, onClose }: any) =>
    liability ? (
      <div data-testid="detail-dialog">
        <span>{liability.name} Details</span>
        <button onClick={onClose}>Close</button>
      </div>
    ) : null,
}));

describe('LiabilitiesPage', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    vi.clearAllMocks();
    mockPagedResponse = {
      content: [mockLiability],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mockIsLoading = false;
    mockError = null;
  });

  it('renders the page heading and description', () => {
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.getByText('Liabilities')).toBeInTheDocument();
    expect(screen.getByText(/track and manage/i)).toBeInTheDocument();
  });

  it('displays liability names from data', () => {
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.getByText('Mortgage')).toBeInTheDocument();
  });

  it('shows summary cards when liabilities exist', () => {
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.getByTestId('summary-cards')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.getByText(/failed to load liabilities/i)).toBeInTheDocument();
  });

  it('shows loading skeletons', () => {
    mockIsLoading = true;
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.queryByTestId('liability-list')).not.toBeInTheDocument();
  });

  it('shows empty state when no liabilities and no filters', () => {
    mockPagedResponse = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.getByText(/no liabilities yet/i)).toBeInTheDocument();
  });

  it('opens create dialog and submits', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /add liability/i }));
    await waitFor(() => expect(screen.getByTestId('liability-form')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockCreateMutateAsync).toHaveBeenCalled();
  });

  it('opens edit dialog with existing data', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /edit/i }));
    await waitFor(() => expect(screen.getByTestId('editing-name')).toHaveTextContent('Mortgage'));
  });

  it('submits edit form calling updateLiability', async () => {
    const user = userEvent.setup();
    mockUpdateMutateAsync.mockResolvedValue({});
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /edit/i }));
    await waitFor(() => expect(screen.getByTestId('liability-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /submit/i }));
    expect(mockUpdateMutateAsync).toHaveBeenCalled();
  });

  it('calls deleteLiability on delete', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /delete/i }));
    expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1);
  });

  it('opens detail dialog on view details', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /view details/i }));
    expect(screen.getByTestId('detail-dialog')).toBeInTheDocument();
    expect(screen.getByText('Mortgage Details')).toBeInTheDocument();
  });

  it('closes detail dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /view details/i }));
    await user.click(screen.getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('detail-dialog')).not.toBeInTheDocument();
  });

  it('toggles filter panel', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LiabilitiesPage />);

    expect(screen.queryByTestId('liability-filters')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.getByTestId('liability-filters')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /filters/i }));
    expect(screen.queryByTestId('liability-filters')).not.toBeInTheDocument();
  });

  it('cancels form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LiabilitiesPage />);

    await user.click(screen.getByRole('button', { name: /add liability/i }));
    await waitFor(() => expect(screen.getByTestId('liability-form')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    await waitFor(() => expect(screen.queryByTestId('liability-form')).not.toBeInTheDocument());
  });
});
