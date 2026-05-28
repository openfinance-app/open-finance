import { describe, it, expect } from 'vitest';
import { getNotificationColor, getNotificationIcon } from '@/types/notification';

describe('getNotificationColor', () => {
  it('returns red for CRITICAL', () => {
    expect(getNotificationColor('CRITICAL')).toBe('#ef4444');
  });

  it('returns amber for WARNING', () => {
    expect(getNotificationColor('WARNING')).toBe('#f59e0b');
  });

  it('returns blue for INFO', () => {
    expect(getNotificationColor('INFO')).toBe('#3b82f6');
  });
});

describe('getNotificationIcon', () => {
  it('returns chart for STALE_QUOTES', () => {
    expect(getNotificationIcon('STALE_QUOTES')).toBe('📊');
  });

  it('returns exchange for STALE_EXCHANGE_RATES', () => {
    expect(getNotificationIcon('STALE_EXCHANGE_RATES')).toBe('💱');
  });

  it('returns tag for UNCATEGORIZED_TRANSACTIONS', () => {
    expect(getNotificationIcon('UNCATEGORIZED_TRANSACTIONS')).toBe('🏷️');
  });

  it('returns person for UNLINKED_PAYEE', () => {
    expect(getNotificationIcon('UNLINKED_PAYEE')).toBe('👤');
  });

  it('returns warning for LOW_BALANCE', () => {
    expect(getNotificationIcon('LOW_BALANCE')).toBe('⚠️');
  });

  it('returns money for BUDGET_ALERT', () => {
    expect(getNotificationIcon('BUDGET_ALERT')).toBe('💰');
  });
});
