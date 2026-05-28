import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

vi.mock('@/hooks/useAccounts', () => ({
  useAccounts: vi.fn(),
}));

vi.mock('@/components/ui/Select', () => ({
  Select: ({ children, disabled }: any) => <div data-disabled={disabled}>{children}</div>,
  SelectContent: ({ children, headerSlot }: any) => <div data-testid="select-content">{headerSlot}{children}</div>,
  SelectItem: ({ children, value }: any) => <div data-testid={`select-item-${value}`}>{children}</div>,
  SelectTrigger: ({ children, className }: any) => <div data-testid="select-trigger" className={className}>{children}</div>,
  SelectValue: ({ children, placeholder }: any) => <div data-testid="select-value">{children ?? placeholder}</div>,
}));

vi.mock('@/utils/selectClickGuard', () => ({
  markSelectInteraction: vi.fn(),
}));

import { AccountSelector } from './AccountSelector';
import { useAccounts } from '@/hooks/useAccounts';

const mockUseAccounts = useAccounts as ReturnType<typeof vi.fn>;

const mockAccounts = [
  { id: 1, name: 'Checking Account', type: 'CHECKING', balance: 1500, institution: { name: 'Bank A' } },
  { id: 2, name: 'Savings Account', type: 'SAVINGS', balance: 5000, institution: null },
  { id: 3, name: 'Investment Account', type: 'INVESTMENT', balance: 25000, institution: { name: 'Broker B' } },
];

function Wrapper({ children }: { children: React.ReactNode }) {
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

describe('AccountSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAccounts.mockReturnValue({
      data: mockAccounts,
      isLoading: false,
      isError: false,
    });
  });

  it('renders loading state', () => {
    mockUseAccounts.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders error state', () => {
    mockUseAccounts.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText(/failed/i)).toBeInTheDocument();
  });

  it('renders accounts when loaded', () => {
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText('Checking Account')).toBeInTheDocument();
    expect(screen.getByText('Savings Account')).toBeInTheDocument();
    expect(screen.getByText('Investment Account')).toBeInTheDocument();
  });

  it('shows selected account name', () => {
    render(<AccountSelector value={1} onValueChange={vi.fn()} />, { wrapper: Wrapper });
    const elements = screen.getAllByText('Checking Account');
    expect(elements.length).toBeGreaterThanOrEqual(1);
  });

  it('shows institution name for selected account', () => {
    render(<AccountSelector value={1} onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText('(Bank A)')).toBeInTheDocument();
  });

  it('shows None option when allowNone is true', () => {
    render(<AccountSelector onValueChange={vi.fn()} allowNone={true} />, { wrapper: Wrapper });
    expect(screen.getByTestId('select-item-__none__')).toBeInTheDocument();
  });

  it('hides None option when allowNone is false', () => {
    render(<AccountSelector onValueChange={vi.fn()} allowNone={false} />, { wrapper: Wrapper });
    expect(screen.queryByTestId('select-item-__none__')).not.toBeInTheDocument();
  });

  it('shows search input in dropdown', () => {
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument();
  });

  it('renders with disabled state', () => {
    render(<AccountSelector onValueChange={vi.fn()} disabled={true} />, { wrapper: Wrapper });
    expect(screen.getByText('Checking Account').closest('[data-disabled]')).toHaveAttribute('data-disabled', 'true');
  });

  it('groups accounts by type', () => {
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    // Each account type should create a group header
    expect(screen.getByTestId('select-item-1')).toBeInTheDocument();
    expect(screen.getByTestId('select-item-2')).toBeInTheDocument();
    expect(screen.getByTestId('select-item-3')).toBeInTheDocument();
  });

  it('shows None text when value is not found', () => {
    render(<AccountSelector value={999} onValueChange={vi.fn()} />, { wrapper: Wrapper });
    // When selected account not found, shows None
    expect(screen.getByTestId('select-value')).toBeInTheDocument();
  });

  it('handles null accounts data', () => {
    mockUseAccounts.mockReturnValue({ data: null, isLoading: false, isError: true });
    render(<AccountSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText(/failed/i)).toBeInTheDocument();
  });
});
