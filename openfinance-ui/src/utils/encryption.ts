import { STORAGE_KEYS } from '@/constants/storage';

/**
 * Gets the encryption key from session storage
 * Returns null if no key is stored
 */
export function getEncryptionKey(): string | null {
  if (typeof window === 'undefined') return null;
  return sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_SESSION);
}

/**
 * Stores the encryption key in session storage
 */
export function setEncryptionKey(key: string): void {
  if (typeof window === 'undefined') return;
  sessionStorage.setItem(STORAGE_KEYS.ENCRYPTION_SESSION, key);
}

/**
 * Removes the encryption key from session storage
 */
export function clearEncryptionKey(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(STORAGE_KEYS.ENCRYPTION_SESSION);
}

export function getStoredEncryptionEnabled(): boolean {
  if (typeof window === 'undefined') return true;
  const storedMode =
    sessionStorage.getItem(STORAGE_KEYS.ENCRYPTION_ENABLED) ?? localStorage.getItem(STORAGE_KEYS.ENCRYPTION_ENABLED);
  return storedMode === 'false' ? false : true;
}

export function setStoredEncryptionEnabled(encryptionEnabled: boolean): void {
  if (typeof window === 'undefined') return;
  const storedValue = encryptionEnabled ? 'true' : 'false';
  sessionStorage.setItem(STORAGE_KEYS.ENCRYPTION_ENABLED, storedValue);
  localStorage.setItem(STORAGE_KEYS.ENCRYPTION_ENABLED, storedValue);
}

export function clearStoredEncryptionEnabled(): void {
  if (typeof window === 'undefined') return;
  sessionStorage.removeItem(STORAGE_KEYS.ENCRYPTION_ENABLED);
  localStorage.removeItem(STORAGE_KEYS.ENCRYPTION_ENABLED);
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
