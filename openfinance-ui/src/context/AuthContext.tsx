/**
 * AuthContext - Manages global authentication state
 * 
 * Implements TASK-1.3.12:
 * - Manages authentication state (isAuthenticated, user, token)
 * - Provides login/logout functions
 * - Persists authentication across page reloads
 * 
 * Requirements: REQ-2.1.3, REQ-2.1.4 (User Authentication)
 */
import { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import type { User } from '@/types/user';

interface AuthContextType {
  /** Current authenticated user, null if not authenticated */
  user: User | null;
  /** User's base currency for multi-currency conversion (ISO 4217 code, defaults to 'USD') */
  baseCurrency: string;
  /** Whether the user is authenticated */
  isAuthenticated: boolean;
  /** Whether auth state is being checked (e.g., validating stored token) */
  isLoading: boolean;
  /** JWT token */
  token: string | null;
  /** ISO-8601 timestamp of when the current login session started, stored in sessionStorage */
  sessionStartTime: string | null;
  /** Set user and token (called after successful login) */
  setAuth: (user: User, token: string, rememberMe?: boolean) => void;
  /** Merge partial user fields and persist to storage (called after profile/settings updates) */
  updateUser: (partial: Partial<User>) => void;
  /** Clear authentication (called on logout) */
  clearAuth: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

interface AuthProviderProps {
  children: ReactNode;
}

/**
 * AuthProvider component
 * Wraps the application and provides authentication state
 */
export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [sessionStartTime, setSessionStartTime] = useState<string | null>(
    () => sessionStorage.getItem('session_start_time')
  );

  /**
   * Initialize auth state from localStorage or sessionStorage on mount
   * This allows users to stay logged in across page reloads (or browser restarts if "remember me" was checked)
   */
  useEffect(() => {
    const initAuth = () => {
      try {
        // Check localStorage first (persistent sessions with "remember me")
        let storedToken = localStorage.getItem('auth_token');
        let storedUser = localStorage.getItem('auth_user');

        // If not in localStorage, check sessionStorage (session-only)
        if (!storedToken) {
          storedToken = sessionStorage.getItem('auth_token');
          storedUser = sessionStorage.getItem('auth_user');
        }

        if (storedToken && storedUser) {
          const parsedUser = JSON.parse(storedUser) as User;
          setToken(storedToken);
          setUser(parsedUser);
        }
      } catch (error) {
        console.error('Failed to initialize auth state:', error);
        // Clear invalid stored data from both storages
        localStorage.removeItem('auth_token');
        localStorage.removeItem('auth_user');
        sessionStorage.removeItem('auth_token');
        sessionStorage.removeItem('auth_user');
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();

    // Listen to storage events to sync auth state across tabs
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'auth_token') {
        if (e.newValue === null) {
          // Logged out in another tab
          setToken(null);
          setUser(null);
        } else {
          // Logged in on another tab
          const storedUser = localStorage.getItem('auth_user') || sessionStorage.getItem('auth_user');
          if (storedUser) {
            setToken(e.newValue);
            setUser(JSON.parse(storedUser) as User);
          }
        }
      }
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  /**
   * Set authentication state after successful login
   * @param user - Authenticated user object
   * @param token - JWT token
   * @param rememberMe - If true, use localStorage (persist across browser restarts). If false, use sessionStorage (clear on browser close)
   */
  const setAuth = useCallback((user: User, token: string, rememberMe = false) => {
    setUser(user);
    setToken(token);

    // Choose storage based on rememberMe preference
    const storage = rememberMe ? localStorage : sessionStorage;

    // Clear from the opposite storage to avoid conflicts
    const oppositeStorage = rememberMe ? sessionStorage : localStorage;

    // Persist to chosen storage. Note: storing JWT in localStorage has XSS risk;
    // consider using httpOnly cookies for production deployments.
    try {
      storage.setItem('auth_token', token);
      storage.setItem('auth_user', JSON.stringify(user));

      // Record session start time; always in sessionStorage so it clears on tab close
      const now = new Date().toISOString();
      sessionStorage.setItem('session_start_time', now);
      setSessionStartTime(now);

      // Clear from opposite storage
      oppositeStorage.removeItem('auth_token');
      oppositeStorage.removeItem('auth_user');
    } catch (error) {
      // Log but don't throw — UI should remain usable even if persistence fails
      // eslint-disable-next-line no-console
      console.error('Failed to persist auth state:', error);
    }
  }, []);

  /**
   * Merge partial user fields into current user and persist to whichever storage
   * holds the current session. Used to update baseCurrency, email, etc. without
   * a full re-login.
   */
  const updateUser = useCallback((partial: Partial<User>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const updated = { ...prev, ...partial };

      // Persist to whichever storage currently holds the session
      try {
        if (localStorage.getItem('auth_user')) {
          localStorage.setItem('auth_user', JSON.stringify(updated));
        } else if (sessionStorage.getItem('auth_user')) {
          sessionStorage.setItem('auth_user', JSON.stringify(updated));
        }
      } catch (e) {
        console.error('Failed to persist user update to storage:', e);
      }

      return updated;
    });
  }, []);

  /**
   * Clear authentication state on logout
   * Clears from both localStorage and sessionStorage
   */
  const clearAuth = useCallback(() => {
    setUser(null);
    setToken(null);

    // Clear from both storages
    try {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      sessionStorage.removeItem('auth_token');
      sessionStorage.removeItem('auth_user');
      sessionStorage.removeItem('encryption_session');
      sessionStorage.removeItem('session_start_time');
      setSessionStartTime(null);
    } catch (e) {
      // ignore storage errors
    }
  }, []);

  const value: AuthContextType = useMemo(
    () => ({
      user,
      baseCurrency: user?.baseCurrency || 'USD', // Extract base currency from user or default to USD
      isAuthenticated: !!user && !!token,
      isLoading,
      token,
      sessionStartTime,
      setAuth,
      updateUser,
      clearAuth,
    }),
    [user, token, isLoading, sessionStartTime, setAuth, updateUser, clearAuth]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/**
 * Hook to access authentication context
 * Must be used within AuthProvider
 */
export function useAuthContext(): AuthContextType {
  const context = useContext(AuthContext);

  if (context === undefined) {
    throw new Error('useAuthContext must be used within AuthProvider');
  }

  return context;
}
