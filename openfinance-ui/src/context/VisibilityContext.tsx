/**
 * VisibilityContext - Manages global amounts visibility state
 *
 * Provides a toggle to hide/show financial amounts across the application
 * for enhanced privacy when sharing screens or using in public settings.
 */
import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';

interface VisibilityContextType {
  /** Whether amounts are currently visible */
  isAmountsVisible: boolean;
  /** Toggle amounts visibility */
  toggleAmountsVisibility: () => void;
  /** Set amounts visibility explicitly */
  setAmountsVisible: (visible: boolean) => void;
}

const VisibilityContext = createContext<VisibilityContextType | undefined>(undefined);

interface VisibilityProviderProps {
  children: ReactNode;
}

const STORAGE_KEY = 'open_finance_amounts_visible';

/**
 * VisibilityProvider component
 * Wraps the application and provides amounts visibility state
 */
export function VisibilityProvider({ children }: VisibilityProviderProps) {
  const [isAmountsVisible, setIsAmountsVisible] = useState<boolean>(() => {
    // Initialize from localStorage, default to true (visible)
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored !== null ? JSON.parse(stored) : true;
    } catch {
      return true;
    }
  });

  /**
   * Persist visibility state to localStorage whenever it changes
   */
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(isAmountsVisible));
    } catch (error) {
      console.error('Failed to persist amounts visibility state:', error);
    }
  }, [isAmountsVisible]);

  /**
   * Toggle amounts visibility
   */
  const toggleAmountsVisibility = useCallback(() => {
    setIsAmountsVisible(prev => !prev);
  }, []);

  /**
   * Set amounts visibility explicitly
   */
  const setAmountsVisible = useCallback((visible: boolean) => {
    setIsAmountsVisible(visible);
  }, []);

  const value: VisibilityContextType = useMemo(
    () => ({
      isAmountsVisible,
      toggleAmountsVisibility,
      setAmountsVisible,
    }),
    [isAmountsVisible, toggleAmountsVisibility, setAmountsVisible]
  );

  return <VisibilityContext.Provider value={value}>{children}</VisibilityContext.Provider>;
}

/**
 * Hook to access visibility context
 * Must be used within VisibilityProvider
 */
export function useVisibility(): VisibilityContextType {
  const context = useContext(VisibilityContext);

  if (context === undefined) {
    throw new Error('useVisibility must be used within VisibilityProvider');
  }

  return context;
}
