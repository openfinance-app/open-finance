import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

vi.mock('@/hooks/useInstitutions', () => ({
  useInstitutions: vi.fn(),
}));

vi.mock('@/components/ui/Select', () => ({
  Select: ({ children, disabled }: any) => <div data-disabled={disabled}>{children}</div>,
  SelectContent: ({ children, headerSlot }: any) => <div>{headerSlot}{children}</div>,
  SelectItem: ({ children, value }: any) => <div data-testid={`item-${value}`}>{children}</div>,
  SelectTrigger: ({ children }: any) => <div>{children}</div>,
  SelectValue: ({ children, placeholder }: any) => <div>{children ?? placeholder}</div>,
}));

import { InstitutionSelector } from './InstitutionSelector';
import { useInstitutions } from '@/hooks/useInstitutions';

const mockUseInstitutions = useInstitutions as ReturnType<typeof vi.fn>;

const mockInstitutions = [
  { id: 1, name: 'BNP Paribas', country: 'FR', logo: null, bic: 'BNPA' },
  { id: 2, name: 'Deutsche Bank', country: 'DE', logo: 'https://example.com/db.png', bic: 'DEUT' },
  { id: 3, name: 'Chase', country: 'US', logo: null, bic: 'CHAS' },
];

function Wrapper({ children }: { children: React.ReactNode }) {
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

describe('InstitutionSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseInstitutions.mockReturnValue({
      data: mockInstitutions,
      isLoading: false,
      isError: false,
    });
  });

  it('renders loading state', () => {
    mockUseInstitutions.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('renders error state', () => {
    mockUseInstitutions.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText(/failed|error/i)).toBeInTheDocument();
  });

  it('renders institutions when loaded', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText('BNP Paribas')).toBeInTheDocument();
    expect(screen.getByText('Deutsche Bank')).toBeInTheDocument();
    expect(screen.getByText('Chase')).toBeInTheDocument();
  });

  it('shows None option when allowNone is true', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} allowNone={true} />, { wrapper: Wrapper });
    expect(screen.getByTestId('item-__none__')).toBeInTheDocument();
  });

  it('hides None option when allowNone is false', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} allowNone={false} />, { wrapper: Wrapper });
    expect(screen.queryByTestId('item-__none__')).not.toBeInTheDocument();
  });

  it('shows search input', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('groups institutions by country', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getByText('France')).toBeInTheDocument();
    expect(screen.getByText('Germany')).toBeInTheDocument();
    expect(screen.getByText('United States')).toBeInTheDocument();
  });

  it('renders with disabled state', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} disabled={true} />, { wrapper: Wrapper });
    expect(screen.getByText('BNP Paribas').closest('[data-disabled]')).toHaveAttribute('data-disabled', 'true');
  });

  it('renders None text when no value selected', () => {
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    expect(screen.getAllByText('None').length).toBeGreaterThanOrEqual(1);
  });

  it('renders empty institutions list', () => {
    mockUseInstitutions.mockReturnValue({ data: [], isLoading: false, isError: false });
    render(<InstitutionSelector onValueChange={vi.fn()} />, { wrapper: Wrapper });
    // Should still render the None option
    expect(screen.getByTestId('item-__none__')).toBeInTheDocument();
  });
});
