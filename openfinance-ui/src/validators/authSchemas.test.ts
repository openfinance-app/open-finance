import { describe, it, expect } from 'vitest';
import {
  createLoginSchema,
  createRegisterSchema,
  loginSchema,
  registerSchema,
} from './authSchemas';

describe('authSchemas', () => {
  it('should validate a correct registration payload', () => {
    const data = {
      username: 'user_123',
      email: 'user@example.com',
      password: 'Aa1@abcd',
      confirmPassword: 'Aa1@abcd',
      masterPassword: 'Master1@3456',
      confirmMasterPassword: 'Master1@3456',
    };

    const parsed = registerSchema.safeParse(data);
    expect(parsed.success).toBe(true);
  });

  it('should reject mismatched passwords', () => {
    const data = {
      username: 'u',
      email: 'bad',
      password: 'Aa1@abcd',
      confirmPassword: 'different',
      masterPassword: 'Master1@3456',
      confirmMasterPassword: 'Master1@3456',
    };

    const result = registerSchema.safeParse(data);
    expect(result.success).toBe(false);
    if (!result.success) {
      // Expect at least one issue for confirmPassword
      expect(result.error.issues.some(i => i.path.includes('confirmPassword'))).toBe(true);
    }
  });

  it('should enforce password complexity', () => {
    const data = {
      username: 'gooduser',
      email: 'ok@example.com',
      password: 'short',
      confirmPassword: 'short',
      masterPassword: 'shortmaster',
      confirmMasterPassword: 'shortmaster',
    };

    const result = registerSchema.safeParse(data);
    expect(result.success).toBe(false);
    if (!result.success) {
      // Should include password validation issues
      // Check for password path (minLength error)
      expect(result.error.issues.some(i => i.path.includes('password'))).toBe(true);
      // Check for masterPassword path (minLength error)
      expect(result.error.issues.some(i => i.path.includes('masterPassword'))).toBe(true);
    }
  });

  it('should validate login schema', () => {
    const ok = loginSchema.safeParse({ username: 'x', password: 'y', masterPassword: 'z' });
    expect(ok.success).toBe(true);

    const bad = loginSchema.safeParse({ username: '', password: '', masterPassword: '' });
    expect(bad.success).toBe(false);
  });

  it('should allow registration without master password when encryption is disabled', () => {
    const result = createRegisterSchema(false).safeParse({
      username: 'user_123',
      email: 'user@example.com',
      password: 'Aa1@abcd',
      confirmPassword: 'Aa1@abcd',
    });

    expect(result.success).toBe(true);
  });

  it('should reject registration without master password when encryption is enabled', () => {
    const result = createRegisterSchema(true).safeParse({
      username: 'user_123',
      email: 'user@example.com',
      password: 'Aa1@abcd',
      confirmPassword: 'Aa1@abcd',
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some(i => i.path.includes('masterPassword'))).toBe(true);
    }
  });

  it('should reject mismatched passwords when encryption is disabled', () => {
    const result = createRegisterSchema(false).safeParse({
      username: 'user_123',
      email: 'user@example.com',
      password: 'Aa1@abcd',
      confirmPassword: 'Different1@',
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some(i => i.path.includes('confirmPassword'))).toBe(true);
    }
  });

  it('should reject weak passwords when encryption is disabled', () => {
    const result = createRegisterSchema(false).safeParse({
      username: 'user_123',
      email: 'user@example.com',
      password: 'weak',
      confirmPassword: 'weak',
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some(i => i.path.includes('password'))).toBe(true);
    }
  });

  it('should allow login without master password when encryption is disabled', () => {
    const result = createLoginSchema(false).safeParse({
      username: 'user_123',
      password: 'Aa1@abcd',
      rememberMe: false,
    });

    expect(result.success).toBe(true);
  });

  it('should reject login without master password when encryption is enabled', () => {
    const result = createLoginSchema(true).safeParse({
      username: 'user_123',
      password: 'Aa1@abcd',
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some(i => i.path.includes('masterPassword'))).toBe(true);
    }
  });
});
