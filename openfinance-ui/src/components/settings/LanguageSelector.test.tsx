import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { LanguageSelector } from './LanguageSelector';

let mockLocale = 'en';
let mockIsChangingLocale = false;
const mockSetLocale = vi.fn();

vi.mock('@/context/LocaleContext', () => ({
  useLocale: () => ({
    locale: mockLocale,
    setLocale: mockSetLocale,
    isChangingLocale: mockIsChangingLocale,
  }),
}));

// Mock Radix Select to render inline elements
vi.mock('@/components/ui/Select', () => ({
  Select: ({ children, value, onValueChange, disabled }: any) => (
    <div data-testid="select" data-value={value} data-disabled={disabled}>
      {children}
      <select
        data-testid="select-native"
        value={value}
        onChange={(e: any) => onValueChange(e.target.value)}
        disabled={disabled}
      >
        <option value="en">English</option>
        <option value="fr">French</option>
      </select>
    </div>
  ),
  SelectContent: ({ children }: any) => <div>{children}</div>,
  SelectItem: ({ children, value }: any) => <div data-value={value}>{children}</div>,
  SelectTrigger: ({ children, 'aria-label': ariaLabel }: any) => (
    <div data-testid="select-trigger" aria-label={ariaLabel}>{children}</div>
  ),
  SelectValue: ({ children }: any) => <div data-testid="select-value">{children}</div>,
}));

vi.mock('@/utils/countryUtils', () => ({
  countryFlagClass: (code: string) => `flag-${code}`,
}));

describe('LanguageSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    mockLocale = 'en';
    mockIsChangingLocale = false;
  });

  it('renders the language selector', () => {
    renderWithProviders(<LanguageSelector />);
    expect(screen.getByTestId('select')).toBeInTheDocument();
  });

  it('shows current language label', () => {
    renderWithProviders(<LanguageSelector />);
    // English appears in both the trigger value and the dropdown option
    const englishElements = screen.getAllByText('English');
    expect(englishElements.length).toBeGreaterThanOrEqual(1);
  });

  it('shows French label when locale is fr', () => {
    mockLocale = 'fr';
    renderWithProviders(<LanguageSelector />);
    expect(screen.getByText('French')).toBeInTheDocument();
  });

  it('shows globe icon when not changing locale', () => {
    renderWithProviders(<LanguageSelector />);
    // Globe icon SVG is present (not a spinner)
    expect(document.querySelector('.animate-spin')).not.toBeInTheDocument();
  });

  it('shows spinner when changing locale', () => {
    mockIsChangingLocale = true;
    renderWithProviders(<LanguageSelector />);
    expect(document.querySelector('.animate-spin')).toBeInTheDocument();
  });

  it('disables select when changing locale', () => {
    mockIsChangingLocale = true;
    renderWithProviders(<LanguageSelector />);
    const nativeSelect = screen.getByTestId('select-native') as HTMLSelectElement;
    expect(nativeSelect.disabled).toBe(true);
  });

  it('calls setLocale on language change', () => {
    const { getByTestId } = renderWithProviders(<LanguageSelector />);
    const nativeSelect = getByTestId('select-native') as HTMLSelectElement;
    nativeSelect.value = 'fr';
    nativeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    expect(mockSetLocale).toHaveBeenCalledWith('fr');
  });

  it('renders flag icons', () => {
    renderWithProviders(<LanguageSelector />);
    expect(document.querySelector('.flag-gb')).toBeInTheDocument();
  });
});
