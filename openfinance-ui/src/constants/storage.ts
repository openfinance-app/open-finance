/**
 * Centralized Web Storage keys (localStorage / sessionStorage).
 *
 * Single source of truth so the same string is never duplicated or mistyped across the auth,
 * encryption, and locale code. Previously these literals were scattered across `AuthContext`,
 * `apiClient`, `LocaleContext`, `useDashboard`, `ImportWizard`, `encryption.ts` and `useAuth.ts`,
 * with `ENCRYPTION_SESSION_KEY` even defined twice.
 */
export const STORAGE_KEYS = {
  /** JWT auth token (localStorage when "remember me", else sessionStorage). */
  AUTH_TOKEN: 'auth_token',
  /** Serialized authenticated user profile. */
  AUTH_USER: 'auth_user',
  /** Per-session encryption key (sessionStorage only). */
  ENCRYPTION_SESSION: 'encryption_session',
  /** Whether client-side encryption is enabled for the session. */
  ENCRYPTION_ENABLED: 'encryption_enabled',
  /** Timestamp (ms) marking the start of the current session. */
  SESSION_START_TIME: 'session_start_time',
  /** Marks a pending server-side language-preference sync. */
  PENDING_LANGUAGE_SYNC: 'pending_language_sync',
} as const;

export type StorageKey = (typeof STORAGE_KEYS)[keyof typeof STORAGE_KEYS];
