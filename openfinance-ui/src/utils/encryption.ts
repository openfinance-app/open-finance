/**
 * Encryption key utilities for budget management
 */

/**
 * Gets the encryption key from session storage
 * Returns null if no key is stored
 */
export function getEncryptionKey(): string | null {
  if (typeof window === 'undefined') return null;
  return sessionStorage.getItem('encryption_session');
}

/**
 * Stores the encryption key in session storage
 */
export function setEncryptionKey(key: string): void {
  if (typeof window === 'undefined') return;
  sessionStorage.setItem('encryption_session', key);
}

/**
 * Removes the encryption key from session storage
 */
export function clearEncryptionKey(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem('encryption_session');
}
