import { describe, it, expect, beforeEach } from 'vitest';
import { getEncryptionKey, setEncryptionKey, clearEncryptionKey } from '@/utils/encryption';

describe('encryption utils', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  describe('getEncryptionKey', () => {
    it('returns null when no key stored', () => {
      expect(getEncryptionKey()).toBeNull();
    });

    it('returns stored key', () => {
      sessionStorage.setItem('encryption_key', 'test-key-123');
      expect(getEncryptionKey()).toBe('test-key-123');
    });
  });

  describe('setEncryptionKey', () => {
    it('stores the key in sessionStorage', () => {
      setEncryptionKey('my-secret-key');
      expect(sessionStorage.getItem('encryption_key')).toBe('my-secret-key');
    });

    it('overwrites existing key', () => {
      setEncryptionKey('old-key');
      setEncryptionKey('new-key');
      expect(sessionStorage.getItem('encryption_key')).toBe('new-key');
    });
  });

  describe('clearEncryptionKey', () => {
    it('removes the key from sessionStorage', () => {
      sessionStorage.setItem('encryption_key', 'test-key');
      clearEncryptionKey();
      expect(sessionStorage.getItem('encryption_key')).toBeNull();
    });

    it('does not throw when no key exists', () => {
      expect(() => clearEncryptionKey()).not.toThrow();
    });
  });
});
