// @vitest-environment node
import { describe, it, expect } from 'vitest';
import {
  buildEncryptionHeaders,
  clearEncryptionKey,
  clearStoredEncryptionEnabled,
  getEncryptionKey,
  getStoredEncryptionEnabled,
  setEncryptionKey,
  setStoredEncryptionEnabled,
} from '@/utils/encryption';

/**
 * SSR/no-DOM contract: every helper must be a safe no-op without `window`,
 * and header building must fail closed (never silently send plaintext).
 */
describe('encryption utils without a DOM (SSR)', () => {
  it('runs in an environment without window', () => {
    expect(typeof window).toBe('undefined');
  });

  it('returns null for the encryption key', () => {
    expect(getEncryptionKey()).toBeNull();
  });

  it('treats encryption as enabled (fail-closed default)', () => {
    expect(getStoredEncryptionEnabled()).toBe(true);
  });

  it('makes all storage writes safe no-ops', () => {
    expect(() => setEncryptionKey('k')).not.toThrow();
    expect(() => clearEncryptionKey()).not.toThrow();
    expect(() => setStoredEncryptionEnabled(false)).not.toThrow();
    expect(() => clearStoredEncryptionEnabled()).not.toThrow();
    // Nothing was persisted.
    expect(getEncryptionKey()).toBeNull();
    expect(getStoredEncryptionEnabled()).toBe(true);
  });

  it('refuses to build headers instead of silently skipping encryption', () => {
    expect(() => buildEncryptionHeaders()).toThrow('Encryption key not found');
    expect(buildEncryptionHeaders(false)).toEqual({});
  });
});
