import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PayeeSelector } from './PayeeSelector';

const mockPayees = [
  { id: 1, name: 'Amazon', category: 'shopping', logo: 'amazon.png', active: true },
  { id: 2, name: 'Netflix', category: 'entertainment', logo: null, active: true },
  { id: 3, name: 'Walmart', category: 'groceries', logo: 'walmart.png', active: true },
  { id: 4, name: 'Uber', category: 'transport', logo: null, active: true },
];

let mockLoading = false;
let mockError = false;
let mockData: typeof mockPayees | undefined = mockPayees;
const mockMutateAsync = vi.fn();

vi.mock('@/hooks/usePayees', () => ({
  useActivePayees: () => ({
    data: mockData,
    isLoading: mockLoading,
    isError: mockError,
  }),
  useFindOrCreatePayee: () => ({
    mutateAsync: mockMutateAsync,
  }),
}));

vi.mock('@/utils/selectClickGuard', () => ({
  markSelectInteraction: vi.fn(),
}));

describe('PayeeSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLoading = false;
    mockError = false;
    mockData = mockPayees;
    mockAuthentication();
  });

  it('shows loading state', () => {
    mockLoading = true;
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    expect(screen.getByText(/loading payees/i)).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = true;
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    expect(screen.getByText(/failed to load payees/i)).toBeInTheDocument();
  });

  it('renders combobox trigger', () => {
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('shows placeholder text when no value', () => {
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} placeholder="Pick one" />);
    expect(screen.getByText('Pick one')).toBeInTheDocument();
  });

  it('shows selected payee name', () => {
    renderWithProviders(<PayeeSelector value="Amazon" onValueChange={vi.fn()} />);
    expect(screen.getByText('Amazon')).toBeInTheDocument();
  });

  it('shows payee logo when available', () => {
    renderWithProviders(<PayeeSelector value="Amazon" onValueChange={vi.fn()} />);
    const img = document.querySelector('img');
    expect(img).toBeTruthy();
    expect(img).toHaveAttribute('src', 'amazon.png');
  });

  it('shows default icon when no logo', () => {
    renderWithProviders(<PayeeSelector value="Netflix" onValueChange={vi.fn()} />);
    expect(screen.getByText('Netflix')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('renders disabled state', () => {
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} disabled={true} />);
    const combobox = screen.getByRole('combobox');
    expect(combobox).toBeDisabled();
  });

  it('handles undefined data same as error', () => {
    mockData = undefined;
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    expect(screen.getByText(/failed to load payees/i)).toBeInTheDocument();
  });

  it('applies custom className', () => {
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} className="custom-class" />);
    expect(screen.getByRole('combobox').closest('.custom-class, [class*="custom-class"]') || 
           document.querySelector('.custom-class')).toBeTruthy();
  });

  it('opens popover on click', () => {
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    const combobox = screen.getByRole('combobox');
    fireEvent.click(combobox);
    expect(combobox).toHaveAttribute('aria-expanded', 'true');
  });

  it('handles undefined data same as error', () => {
    mockData = undefined;
    renderWithProviders(<PayeeSelector value="" onValueChange={vi.fn()} />);
    expect(screen.getByText(/failed to load payees/i)).toBeInTheDocument();
  });
});
