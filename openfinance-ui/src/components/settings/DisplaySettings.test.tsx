import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

const mockMutate = vi.fn();
const mockSetTheme = vi.fn();
const mockSetDisplayMode = vi.fn();
const mockSetNumberFormat = vi.fn();

let mockSettings: any = {
  dateFormat: 'MM/DD/YYYY',
  language: 'en',
};
let mockIsLoading = false;
let mockError: any = null;

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => ({
    data: mockSettings,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useUpdateUserSettings: () => ({
    mutate: mockMutate,
  }),
}));

vi.mock('@/context/ThemeContext', () => ({
  useTheme: () => ({
    theme: 'light',
    setTheme: mockSetTheme,
  }),
}));

vi.mock('@/context/CurrencyDisplayContext', () => ({
  useCurrencyDisplay: () => ({
    displayMode: 'base',
    setDisplayMode: mockSetDisplayMode,
  }),
}));

vi.mock('@/context/NumberFormatContext', () => ({
  useNumberFormat: () => ({
    numberFormat: '1,234.56',
    setNumberFormat: mockSetNumberFormat,
  }),
}));

vi.mock('@/components/settings/LanguageSelector', () => ({
  LanguageSelector: () => <div data-testid="language-selector">LanguageSelector</div>,
}));

import { DisplaySettings } from './DisplaySettings';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

describe('DisplaySettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSettings = { dateFormat: 'MM/DD/YYYY', language: 'en' };
    mockIsLoading = false;
    mockError = null;
  });

  it('renders loading skeleton', () => {
    mockIsLoading = true;
    const { container } = render(<DisplaySettings />, { wrapper: Wrapper });
    expect(container.querySelector('.animate-pulse')).toBeInTheDocument();
  });

  it('renders error state', () => {
    mockError = new Error('fail');
    render(<DisplaySettings />, { wrapper: Wrapper });
    // Component renders error div with text-error class
    const { container } = render(<DisplaySettings />, { wrapper: Wrapper });
    expect(container.querySelector('.text-error')).toBeInTheDocument();
  });

  it('renders theme section', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    expect(screen.getByText('Display Settings')).toBeInTheDocument();
  });

  it('calls setTheme when dark theme button is clicked', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    fireEvent.click(screen.getByLabelText('Dark theme'));
    expect(mockSetTheme).toHaveBeenCalledWith('dark');
  });

  it('renders date format options', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    expect(screen.getByText('MM/DD/YYYY')).toBeInTheDocument();
    expect(screen.getByText('DD/MM/YYYY')).toBeInTheDocument();
    expect(screen.getByText('YYYY-MM-DD')).toBeInTheDocument();
  });

  it('calls updateSettings on date format change', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    const ddmmButton = screen.getByText('DD/MM/YYYY').closest('button');
    fireEvent.click(ddmmButton!);
    expect(mockMutate).toHaveBeenCalledWith(
      { dateFormat: 'DD/MM/YYYY' },
      expect.any(Object)
    );
  });

  it('renders number format examples', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    expect(screen.getByText('US / UK style')).toBeInTheDocument();
    expect(screen.getByText('European style')).toBeInTheDocument();
  });

  it('calls setNumberFormat on number format change', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    const euroButton = screen.getByText('European style').closest('button');
    fireEvent.click(euroButton!);
    expect(mockSetNumberFormat).toHaveBeenCalledWith('1.234,56');
  });

  it('renders currency display mode section', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    expect(screen.getByText('Currency Display')).toBeInTheDocument();
  });

  it('renders language selector', () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    expect(screen.getByTestId('language-selector')).toBeInTheDocument();
  });

  it('shows success message on theme change', async () => {
    render(<DisplaySettings />, { wrapper: Wrapper });
    const darkButton = screen.getByLabelText('Dark theme');
    fireEvent.click(darkButton);
    await waitFor(() => {
      expect(screen.getByText('Theme updated successfully')).toBeInTheDocument();
    });
  });

  it('shows success on date format update', async () => {
    mockMutate.mockImplementation((_data: any, opts: any) => opts.onSuccess());
    render(<DisplaySettings />, { wrapper: Wrapper });
    const isoButton = screen.getByText('YYYY-MM-DD').closest('button');
    fireEvent.click(isoButton!);
    await waitFor(() => {
      expect(screen.getByText('Date format updated successfully')).toBeInTheDocument();
    });
  });

  it('shows error and reverts on date format failure', async () => {
    mockMutate.mockImplementation((_data: any, opts: any) => opts.onError());
    render(<DisplaySettings />, { wrapper: Wrapper });
    const ddmmButton = screen.getByText('DD/MM/YYYY').closest('button');
    fireEvent.click(ddmmButton!);
    await waitFor(() => {
      expect(screen.getByText(/Failed to update date format/)).toBeInTheDocument();
    });
  });
});
