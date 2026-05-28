import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

const mockUpdateBaseCurrency = vi.fn();
const mockUpdateSettings = vi.fn();

let mockSettings: any = { language: 'en', dateFormat: 'MM/DD/YYYY', country: 'US', secondaryCurrency: null };
let mockIsLoading = false;
let mockError: any = null;

vi.mock('@/context/AuthContext', () => ({
  useAuthContext: () => ({
    user: { username: 'TestUser' },
    baseCurrency: 'USD',
  }),
}));

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: mockSettings,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useUpdateBaseCurrency: () => ({
    mutate: mockUpdateBaseCurrency,
    isPending: false,
  }),
  useUpdateUserSettings: () => ({
    mutate: mockUpdateSettings,
    isPending: false,
  }),
}));

vi.mock('@/context/CurrencyDisplayContext', () => ({
  useCurrencyDisplay: () => ({
    secondaryCurrency: null,
    setSecondaryCurrency: vi.fn(),
  }),
}));

vi.mock('@/components/ui/CurrencySelector', () => ({
  CurrencySelector: ({ value, onValueChange }: any) => (
    <div data-testid="currency-selector" data-value={value}>
      <button onClick={() => onValueChange('EUR')}>Select EUR</button>
    </div>
  ),
}));

vi.mock('@/hooks/useCurrency', () => ({
  useCurrencies: () => ({
    data: [
      { code: 'USD', name: 'US Dollar', symbol: '$', isActive: true },
      { code: 'EUR', name: 'Euro', symbol: '\u20AC', isActive: true },
    ],
    isLoading: false,
    isError: false,
  }),
}));

vi.mock('@/components/ui/Select', () => ({
  Select: ({ children }: any) => <div>{children}</div>,
  SelectContent: ({ children }: any) => <div>{children}</div>,
  SelectItem: ({ children, value }: any) => <div data-value={value}>{children}</div>,
  SelectTrigger: ({ children }: any) => <div>{children}</div>,
  SelectValue: ({ children, placeholder }: any) => <div>{children ?? placeholder}</div>,
}));

vi.mock('@/components/common/CountrySelector', () => ({
  CountrySelector: () => <div data-testid="country-selector">CountrySelector</div>,
}));

import { GeneralSettings } from './GeneralSettings';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

describe('GeneralSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSettings = { language: 'en', dateFormat: 'MM/DD/YYYY', country: 'US', secondaryCurrency: null };
    mockIsLoading = false;
    mockError = null;
  });

  it('renders general settings title', () => {
    render(<GeneralSettings />, { wrapper: Wrapper });
    expect(screen.getByText('General Settings')).toBeInTheDocument();
  });

  it('renders base currency selector', () => {
    render(<GeneralSettings />, { wrapper: Wrapper });
    expect(screen.getByTestId('currency-selector')).toBeInTheDocument();
  });

  it('shows current base currency value', () => {
    render(<GeneralSettings />, { wrapper: Wrapper });
    expect(screen.getByTestId('currency-selector')).toHaveAttribute('data-value', 'USD');
  });

  it('renders country selector', () => {
    render(<GeneralSettings />, { wrapper: Wrapper });
    expect(screen.getByTestId('country-selector')).toBeInTheDocument();
  });

  it('renders secondary currency section', () => {
    render(<GeneralSettings />, { wrapper: Wrapper });
    expect(screen.getByText(/secondary/i)).toBeInTheDocument();
  });
});
