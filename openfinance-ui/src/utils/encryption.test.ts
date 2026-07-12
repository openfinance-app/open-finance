import { describe, it, expect, beforeEach } from 'vitest';
import {
  buildEncryptionHeaders,
  clearStoredEncryptionEnabled,
  getEncryptionKey,
  setEncryptionKey,
  clearEncryptionKey,
  setStoredEncryptionEnabled,
} from '@/utils/encryption';

describe('encryption utils', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  describe('getEncryptionKey', () => {
    it('returns null when no key stored', () => {
      expect(getEncryptionKey()).toBeNull();
    });

    it('returns stored key', () => {
      sessionStorage.setItem('encryption_session', 'test-key-123');
      expect(getEncryptionKey()).toBe('test-key-123');
    });
  });

  describe('setEncryptionKey', () => {
    it('stores the key in sessionStorage', () => {
      setEncryptionKey('my-secret-key');
      expect(sessionStorage.getItem('encryption_session')).toBe('my-secret-key');
    });

    it('overwrites existing key', () => {
      setEncryptionKey('old-key');
      setEncryptionKey('new-key');
      expect(sessionStorage.getItem('encryption_session')).toBe('new-key');
    });
  });

  describe('clearEncryptionKey', () => {
    it('removes the key from sessionStorage', () => {
      sessionStorage.setItem('encryption_session', 'test-key');
      clearEncryptionKey();
      expect(sessionStorage.getItem('encryption_session')).toBeNull();
    });

    it('does not throw when no key exists', () => {
      expect(() => clearEncryptionKey()).not.toThrow();
    });
  });

  describe('buildEncryptionHeaders', () => {
    it('returns encryption session header when encryption is enabled and a session exists', () => {
      setEncryptionKey('session-123');

      expect(buildEncryptionHeaders(true)).toEqual({ 'X-Encryption-Session': 'session-123' });
    });

    it('throws when encryption is enabled and a session is missing', () => {
      expect(() => buildEncryptionHeaders(true)).toThrow('Encryption key not found');
    });

    it('returns no encryption header when encryption is disabled', () => {
      expect(buildEncryptionHeaders(false)).toEqual({});
    });

    it('fails closed when encryption mode has not been stored', () => {
      expect(() => buildEncryptionHeaders()).toThrow('Encryption key not found');
    });

    it('uses stored disabled mode when encryption mode argument is omitted', () => {
      sessionStorage.setItem('encryption_enabled', 'false');

      expect(buildEncryptionHeaders()).toEqual({});
    });

    it('uses localStorage disabled mode after sessionStorage is cleared', () => {
      setStoredEncryptionEnabled(false);
      sessionStorage.removeItem('encryption_enabled');

      expect(buildEncryptionHeaders()).toEqual({});
    });

    it('clears stored encryption mode from both storage locations', () => {
      setStoredEncryptionEnabled(false);

      clearStoredEncryptionEnabled();

      expect(sessionStorage.getItem('encryption_enabled')).toBeNull();
      expect(localStorage.getItem('encryption_enabled')).toBeNull();
      expect(() => buildEncryptionHeaders()).toThrow('Encryption key not found');
    });
  });
});
