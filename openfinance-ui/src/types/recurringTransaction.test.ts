import { describe, it, expect } from 'vitest';
import {
  getFrequencyDisplayName,
  getFrequencyBadgeVariant,
  isDueSoon,
  isOverdue,
  getStatusText,
  getStatusBadgeVariant,
} from '@/types/recurringTransaction';
import type { RecurringTransaction } from '@/types/recurringTransaction';

function makeRecurring(overrides: Partial<RecurringTransaction> = {}): RecurringTransaction {
  return {
    id: 1,
    accountId: 1,
    accountName: 'Checking',
    accountCurrency: 'USD',
    description: 'Netflix',
    amount: 15.99,
    type: 'EXPENSE',
    frequency: 'MONTHLY',
    categoryId: 1,
    categoryName: 'Entertainment',
    payeeId: null,
    payeeName: null,
    isActive: true,
    isDue: false,
    isEnded: false,
    daysUntilNext: 15,
    startDate: '2024-01-01',
    endDate: null,
    nextOccurrence: '2024-02-01',
    lastProcessed: '2024-01-01',
    occurrenceCount: 5,
    ...overrides,
  } as RecurringTransaction;
}

describe('getFrequencyDisplayName', () => {
  it('returns display name for each frequency', () => {
    expect(getFrequencyDisplayName('DAILY')).toBe('Daily');
    expect(getFrequencyDisplayName('WEEKLY')).toBe('Weekly');
    expect(getFrequencyDisplayName('BIWEEKLY')).toBe('Every 2 Weeks');
    expect(getFrequencyDisplayName('MONTHLY')).toBe('Monthly');
    expect(getFrequencyDisplayName('QUARTERLY')).toBe('Quarterly');
    expect(getFrequencyDisplayName('YEARLY')).toBe('Yearly');
  });
});

describe('getFrequencyBadgeVariant', () => {
  it('returns default for DAILY', () => {
    expect(getFrequencyBadgeVariant('DAILY')).toBe('default');
  });

  it('returns secondary for WEEKLY', () => {
    expect(getFrequencyBadgeVariant('WEEKLY')).toBe('secondary');
  });

  it('returns outline for MONTHLY', () => {
    expect(getFrequencyBadgeVariant('MONTHLY')).toBe('outline');
  });
});

describe('isDueSoon', () => {
  it('returns true when within 7 days', () => {
    expect(isDueSoon(makeRecurring({ daysUntilNext: 5 }))).toBe(true);
  });

  it('returns true at exactly 7 days', () => {
    expect(isDueSoon(makeRecurring({ daysUntilNext: 7 }))).toBe(true);
  });

  it('returns false when beyond 7 days', () => {
    expect(isDueSoon(makeRecurring({ daysUntilNext: 15 }))).toBe(false);
  });

  it('returns true at 0 days', () => {
    expect(isDueSoon(makeRecurring({ daysUntilNext: 0 }))).toBe(true);
  });

  it('returns false for negative days', () => {
    expect(isDueSoon(makeRecurring({ daysUntilNext: -1 }))).toBe(false);
  });
});

describe('isOverdue', () => {
  it('returns true when isDue', () => {
    expect(isOverdue(makeRecurring({ isDue: true }))).toBe(true);
  });

  it('returns false when not due', () => {
    expect(isOverdue(makeRecurring({ isDue: false }))).toBe(false);
  });
});

describe('getStatusText', () => {
  it('returns Ended', () => {
    expect(getStatusText(makeRecurring({ isEnded: true }))).toBe('Ended');
  });

  it('returns Paused', () => {
    expect(getStatusText(makeRecurring({ isActive: false }))).toBe('Paused');
  });

  it('returns Due Now', () => {
    expect(getStatusText(makeRecurring({ isDue: true }))).toBe('Due Now');
  });

  it('returns Due Soon', () => {
    expect(getStatusText(makeRecurring({ daysUntilNext: 3 }))).toBe('Due Soon');
  });

  it('returns Active', () => {
    expect(getStatusText(makeRecurring({ daysUntilNext: 15 }))).toBe('Active');
  });
});

describe('getStatusBadgeVariant', () => {
  it('returns outline for ended', () => {
    expect(getStatusBadgeVariant(makeRecurring({ isEnded: true }))).toBe('outline');
  });

  it('returns secondary for paused', () => {
    expect(getStatusBadgeVariant(makeRecurring({ isActive: false }))).toBe('secondary');
  });

  it('returns destructive for due', () => {
    expect(getStatusBadgeVariant(makeRecurring({ isDue: true }))).toBe('destructive');
  });

  it('returns default for due soon', () => {
    expect(getStatusBadgeVariant(makeRecurring({ daysUntilNext: 3 }))).toBe('default');
  });

  it('returns success for active', () => {
    expect(getStatusBadgeVariant(makeRecurring({ daysUntilNext: 15 }))).toBe('success');
  });
});
