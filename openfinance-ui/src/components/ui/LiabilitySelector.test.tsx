import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { LiabilitySelector } from './LiabilitySelector';

const mockLiabilities = [
  { id: 1, name: 'Credit Card A', type: 'CREDIT_CARD', currency: 'USD', currentBalance: 1000 },
  { id: 2, name: 'Mortgage Home', type: 'MORTGAGE', currency: 'EUR', currentBalance: 200000 },
  { id: 3, name: 'Student Loan', type: 'STUDENT_LOAN', currency: 'USD', currentBalance: 50000 },
];

let mockIsLoading = false;
let mockIsError = false;

vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilities: () => ({
    data: mockIsLoading ? undefined : mockIsError ? undefined : mockLiabilities,
    isLoading: mockIsLoading,
    isError: mockIsError,
  }),
  getLiabilityTypeName: (type: string) => {
    const names: Record<string, string> = {
      CREDIT_CARD: 'Credit Card',
      MORTGAGE: 'Mortgage',
      STUDENT_LOAN: 'Student Loan',
    };
    return names[type] || type;
  },
}));

describe('LiabilitySelector', () => {
  const onValueChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockIsLoading = false;
    mockIsError = false;
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(
      <LiabilitySelector onValueChange={onValueChange} />
    );
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockIsError = true;
    renderWithProviders(
      <LiabilitySelector onValueChange={onValueChange} />
    );
    expect(screen.getByText(/failed to load/i)).toBeInTheDocument();
  });

  it('renders with placeholder', () => {
    renderWithProviders(
      <LiabilitySelector onValueChange={onValueChange} placeholder="Choose a liability" />
    );
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('shows selected liability name', () => {
    renderWithProviders(
      <LiabilitySelector value={1} onValueChange={onValueChange} />
    );
    expect(screen.getByText('Credit Card A')).toBeInTheDocument();
  });

  it('shows currency next to selected liability', () => {
    renderWithProviders(
      <LiabilitySelector value={1} onValueChange={onValueChange} />
    );
    expect(screen.getByText('(USD)')).toBeInTheDocument();
  });

  it('respects disabled prop', () => {
    renderWithProviders(
      <LiabilitySelector onValueChange={onValueChange} disabled />
    );
    expect(screen.getByRole('combobox')).toBeDisabled();
  });

  it('applies liabilityFilter', () => {
    renderWithProviders(
      <LiabilitySelector
        onValueChange={onValueChange}
        liabilityFilter={(l) => l.type === 'MORTGAGE'}
      />
    );
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });
});
