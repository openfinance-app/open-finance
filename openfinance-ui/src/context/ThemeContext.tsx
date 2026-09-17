/**
 * ThemeContext — global theme state management
 *
 * Responsibilities:
 * - Reads the persisted theme from the backend (UserSettings API) on mount.
 * - Applies the correct class (`light` or `dark`) to <html> immediately and
 *   whenever the user changes the preference via DisplaySettings.
 * - Falls back to `dark` while the settings are still loading (matches the
 *   default CSS variable values so there is no flash).
 * - Exposes `theme` and `setTheme` so any component can read / change the
 *   current theme without prop-drilling.
 *
 * Usage:
 *   Wrap the application with <ThemeProvider> (inside QueryClientProvider and
 *   AuthProvider so the hook can call the API).
 *
 *   const { theme, setTheme } = useTheme();
 */
import { createContext, useContext, useEffect, useState, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';

type Theme = 'dark' | 'light';

interface ThemeContextType {
  /** The currently active theme */
  theme: Theme;
  /** Whether the settings are still being fetched from the backend */
  isLoading: boolean;
  /**
   * Change the theme.  Persists to the backend and applies the class to <html>
   * immediately (optimistic update).  Reverts on API failure.
   */
  setTheme: (newTheme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

/** Apply the theme class to <html> and remove the other one. */
function applyThemeClass(theme: Theme) {
  const root = document.documentElement;
  if (theme === 'light') {
    root.classList.add('light');
    root.classList.remove('dark');
  } else {
    root.classList.add('dark');
    root.classList.remove('light');
  }
}

interface ThemeProviderProps {
  children: ReactNode;
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const { data: settings, isLoading } = useUserSettings();
  const updateSettings = useUpdateUserSettings();

  // Default to dark — matches the CSS variable default values so there's no
  // flash of unstyled content while the API request is in-flight.
  const [theme, setThemeState] = useState<Theme>('dark');

  // Once the backend settings arrive, sync the DOM and local state.
  useEffect(() => {
    if (settings) {
      const persisted: Theme = settings.theme === 'light' ? 'light' : 'dark';
      setThemeState(persisted);
      applyThemeClass(persisted);
    }
  }, [settings]);

  const setTheme = useCallback(
    (newTheme: Theme) => {
      // Optimistic update — instant UI feedback.
      setThemeState(newTheme);
      applyThemeClass(newTheme);

      updateSettings.mutate(
        { theme: newTheme },
        {
          onError: () => {
            // Revert to the previously persisted theme on failure.
            const previous: Theme = settings?.theme === 'light' ? 'light' : 'dark';
            setThemeState(previous);
            applyThemeClass(previous);
          },
        }
      );
    },
    [settings, updateSettings]
  );

  const value = useMemo<ThemeContextType>(
    () => ({ theme, isLoading, setTheme }),
    [theme, isLoading, setTheme]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/**
 * Hook to access the theme context.
 * Must be used inside <ThemeProvider>.
 */
export function useTheme(): ThemeContextType {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}
