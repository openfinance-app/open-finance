/**
 * Tests for DisplaySettings component
 *
 * Note: Secondary currency selector has moved to GeneralSettings.
 * DisplaySettings now only manages: theme, date format, and currency display mode.
 * Theme state is owned by ThemeContext; DisplaySettings delegates to it.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/test-utils';
import { DisplaySettings } from '@/components/settings/DisplaySettings';

// Mock the hooks
const mockUseUserSettings = vi.fn();
const mockUseUpdateUserSettings = vi.fn();

vi.mock('@/hooks/useUserSettings', () => ({
  useUserSettings: () => mockUseUserSettings(),
  useUpdateUserSettings: () => mockUseUpdateUserSettings(),
}));

// Mock CurrencyDisplayContext
const mockUseCurrencyDisplay = vi.fn();
vi.mock('@/context/CurrencyDisplayContext', () => ({
  useCurrencyDisplay: () => mockUseCurrencyDisplay(),
  CurrencyDisplayProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

// Mock ThemeContext — DisplaySettings delegates theme changes to it
const mockSetTheme = vi.fn();
const mockUseTheme = vi.fn();
vi.mock('@/context/ThemeContext', () => ({
  useTheme: () => mockUseTheme(),
  ThemeProvider: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe('DisplaySettings', () => {
  const user = userEvent.setup();

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUserSettings.mockReturnValue({
      data: { theme: 'dark', dateFormat: 'MM/DD/YYYY' },
      isLoading: false,
      error: null,
    });
    mockUseUpdateUserSettings.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    });
    mockUseCurrencyDisplay.mockReturnValue({
      displayMode: 'base',
      setDisplayMode: vi.fn(),
    });
    mockUseTheme.mockReturnValue({
      theme: 'dark',
      isLoading: false,
      setTheme: mockSetTheme,
    });
  });

  describe('Currency Display Mode', () => {
    it('should render currency display mode options', () => {
      renderWithProviders(<DisplaySettings />);

      expect(screen.getByText('Base currency')).toBeInTheDocument();
      expect(screen.getByText('Native currency')).toBeInTheDocument();
      expect(screen.getByText('Both currencies')).toBeInTheDocument();
    });

    it('should call setDisplayMode when a mode is selected', async () => {
      const mockSetDisplayMode = vi.fn();
      mockUseCurrencyDisplay.mockReturnValue({
        displayMode: 'base',
        setDisplayMode: mockSetDisplayMode,
      });

      renderWithProviders(<DisplaySettings />);

      const nativeButton = screen.getByRole('button', { name: /Native currency/i });
      await user.click(nativeButton);

      expect(mockSetDisplayMode).toHaveBeenCalledWith('native');
    });

    it('should mark the active mode button as aria-pressed=true', () => {
      mockUseCurrencyDisplay.mockReturnValue({
        displayMode: 'native',
        setDisplayMode: vi.fn(),
      });

      renderWithProviders(<DisplaySettings />);

      // Use getAllByRole to handle potential multiple matches, then find by aria-pressed
      const buttons = screen.getAllByRole('button', { name: /Native currency/i });
      const nativeButton = buttons.find(btn => btn.hasAttribute('aria-pressed'))!;
      expect(nativeButton).toHaveAttribute('aria-pressed', 'true');

      const baseButtons = screen.getAllByRole('button', { name: /Base currency/i });
      const baseButton = baseButtons.find(btn => btn.hasAttribute('aria-pressed'))!;
      expect(baseButton).toHaveAttribute('aria-pressed', 'false');
    });
  });

  describe('Secondary Currency', () => {
    it('should NOT render a secondary currency selector in DisplaySettings', () => {
      renderWithProviders(<DisplaySettings />);

      // Secondary currency has moved to GeneralSettings
      expect(screen.queryByLabelText('Secondary currency')).not.toBeInTheDocument();
      expect(screen.queryByText('Secondary Currency')).not.toBeInTheDocument();
    });
  });

  describe('Loading and error states', () => {
    it('should show loading state', () => {
      mockUseUserSettings.mockReturnValue({
        data: null,
        isLoading: true,
        error: null,
      });

      renderWithProviders(<DisplaySettings />);

      // Should show skeleton loaders
      expect(
        screen
          .getAllByRole('generic', { hidden: true })
          .some(el => el.classList.contains('animate-pulse'))
      ).toBe(true);
    });

    it('should show error state', () => {
      mockUseUserSettings.mockReturnValue({
        data: null,
        isLoading: false,
        error: new Error('Failed to load'),
      });

      renderWithProviders(<DisplaySettings />);

      expect(
        screen.getByText('Failed to load settings. Please refresh the page.')
      ).toBeInTheDocument();
    });
  });

  describe('Theme Selection', () => {
    it('should render theme options', () => {
      renderWithProviders(<DisplaySettings />);

      expect(screen.getByText('Dark')).toBeInTheDocument();
      expect(screen.getByText('Light')).toBeInTheDocument();
    });

    it('should call ThemeContext setTheme when theme is changed to light', async () => {
      renderWithProviders(<DisplaySettings />);

      await user.click(screen.getByRole('button', { name: 'Light theme' }));

      expect(mockSetTheme).toHaveBeenCalledWith('light');
    });

    it('should call ThemeContext setTheme when theme is changed to dark', async () => {
      mockUseTheme.mockReturnValue({
        theme: 'light',
        isLoading: false,
        setTheme: mockSetTheme,
      });

      renderWithProviders(<DisplaySettings />);

      await user.click(screen.getByRole('button', { name: 'Dark theme' }));

      expect(mockSetTheme).toHaveBeenCalledWith('dark');
    });

    it('should show success message immediately after theme change', async () => {
      renderWithProviders(<DisplaySettings />);

      await act(async () => {
        await user.click(screen.getByRole('button', { name: 'Light theme' }));
      });

      await waitFor(() => {
        expect(screen.getByText('Theme updated successfully')).toBeInTheDocument();
      });
    });

    it('should highlight the active theme from ThemeContext', () => {
      mockUseTheme.mockReturnValue({
        theme: 'light',
        isLoading: false,
        setTheme: mockSetTheme,
      });

      renderWithProviders(<DisplaySettings />);

      // The light button should have the active (border-primary) classes when theme is 'light'
      const lightButton = screen.getByRole('button', { name: 'Light theme' });
      expect(lightButton.className).toContain('border-primary');
    });
  });

  describe('Date Format Selection', () => {
    it('should render date format options', () => {
      renderWithProviders(<DisplaySettings />);

      expect(screen.getByText('MM/DD/YYYY')).toBeInTheDocument();
      expect(screen.getByText('DD/MM/YYYY')).toBeInTheDocument();
      expect(screen.getByText('YYYY-MM-DD')).toBeInTheDocument();
    });

    it('should call updateSettings when date format is changed', async () => {
      const mockMutate = vi.fn();
      mockUseUpdateUserSettings.mockReturnValue({
        mutate: mockMutate,
        isPending: false,
      });

      renderWithProviders(<DisplaySettings />);

      await user.click(screen.getByRole('button', { name: /DD\/MM\/YYYY/i }));

      expect(mockMutate).toHaveBeenCalledWith({ dateFormat: 'DD/MM/YYYY' }, expect.any(Object));
    });
  });
});
