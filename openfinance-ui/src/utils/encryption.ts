const ENCRYPTION_SESSION_KEY = 'encryption_session';
const ENCRYPTION_ENABLED_KEY = 'encryption_enabled';

/**
 * Gets the encryption key from session storage
 * Returns null if no key is stored
 */
export function getEncryptionKey(): string | null {
  if (typeof window === 'undefined') return null;
  return sessionStorage.getItem(ENCRYPTION_SESSION_KEY);
}

/**
 * Stores the encryption key in session storage
 */
export function setEncryptionKey(key: string): void {
  if (typeof window === 'undefined') return;
  sessionStorage.setItem(ENCRYPTION_SESSION_KEY, key);
}

/**
 * Removes the encryption key from session storage
 */
export function clearEncryptionKey(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(ENCRYPTION_SESSION_KEY);
}

export function getStoredEncryptionEnabled(): boolean {
  if (typeof window === 'undefined') return true;
  const storedMode =
    sessionStorage.getItem(ENCRYPTION_ENABLED_KEY) ?? localStorage.getItem(ENCRYPTION_ENABLED_KEY);
  return storedMode === 'false' ? false : true;
}

export function setStoredEncryptionEnabled(encryptionEnabled: boolean): void {
  if (typeof window === 'undefined') return;
  const storedValue = encryptionEnabled ? 'true' : 'false';
  sessionStorage.setItem(ENCRYPTION_ENABLED_KEY, storedValue);
  localStorage.setItem(ENCRYPTION_ENABLED_KEY, storedValue);
}

export function clearStoredEncryptionEnabled(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(ENCRYPTION_ENABLED_KEY);
  localStorage.removeItem(ENCRYPTION_ENABLED_KEY);
}

export function buildEncryptionHeaders(
  encryptionEnabled = getStoredEncryptionEnabled()
): Record<string, string> {
  if (!encryptionEnabled) {
    return {};
  }

  const encryptionKey = getEncryptionKey();
  if (!encryptionKey) {
    throw new Error('Encryption key not found');
  }

  return { 'X-Encryption-Session': encryptionKey };
}
