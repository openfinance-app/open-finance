/**
 * Date Localization Test
 *
 * Verifies that date-fns locale switching works correctly with LocaleContext.
 * Tests relative date formatting in English and French.
 *
 * Related: .spec/i18n-localization/tasks.md (Task 4.1.6)
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import { enUS, fr } from 'date-fns/locale';
import i18n from '@/test/i18n-test';
import { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { createTestQueryClient, mockAuthentication } from '@/test/test-utils';
import { LocaleProvider } from '@/context/LocaleContext';
import { formatRelativeDate, useRelativeDateFormatter } from '@/utils/date';

/**
 * Wrapper that provides i18n contexts
 */
function DateTestWrapper({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(createTestQueryClient);
  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <LocaleProvider>{children}</LocaleProvider>
      </QueryClientProvider>
    </I18nextProvider>
  );
}

describe('Date Localization', () => {
  beforeEach(async () => {
    mockAuthentication();
    // Reset to English before each test
    await i18n.changeLanguage('en');
  });

  describe('formatRelativeDate (standalone function)', () => {
    it('should format relative dates in English with enUS locale', () => {
      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

      const result = formatRelativeDate(twoDaysAgo, enUS);

      expect(result).toMatch(/2 days ago/);
    });

    it('should format relative dates in French with fr locale', () => {
      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

      const result = formatRelativeDate(twoDaysAgo, fr);

      expect(result).toMatch(/il y a 2 jours/);
    });

    it('should format hours in English', () => {
      const twoHoursAgo = new Date();
      twoHoursAgo.setHours(twoHoursAgo.getHours() - 2);

      const result = formatRelativeDate(twoHoursAgo, enUS);

      expect(result).toMatch(/about 2 hours ago/);
    });

    it('should format hours in French', () => {
      const twoHoursAgo = new Date();
      twoHoursAgo.setHours(twoHoursAgo.getHours() - 2);

      const result = formatRelativeDate(twoHoursAgo, fr);

      expect(result).toMatch(/il y a environ 2 heures/);
    });

    it('should format recent dates (less than a minute) in English', () => {
      const now = new Date();

      const result = formatRelativeDate(now, enUS);

      expect(result).toMatch(/less than a minute ago/);
    });

    it('should format recent dates (less than a minute) in French', () => {
      const now = new Date();

      const result = formatRelativeDate(now, fr);

      // French uses RIGHT SINGLE QUOTATION MARK (U+2019, char code 8217)
      const expected = `il y a moins d${String.fromCharCode(8217)}une minute`;
      expect(result).toBe(expected);
    });

    it('should format months in English', () => {
      const threeMonthsAgo = new Date();
      threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);

      const result = formatRelativeDate(threeMonthsAgo, enUS);

      expect(result).toMatch(/3 months ago/);
    });

    it('should format months in French', () => {
      const threeMonthsAgo = new Date();
      threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);

      const result = formatRelativeDate(threeMonthsAgo, fr);

      expect(result).toMatch(/il y a 3 mois/);
    });

    it('should default to English when no locale provided', () => {
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      const result = formatRelativeDate(yesterday);

      // Should use English by default
      expect(result).toMatch(/yesterday|1 day ago/);
    });
  });

  describe('useRelativeDateFormatter hook', () => {
    it('should use English locale when i18n language is English', async () => {
      await i18n.changeLanguage('en');

      const { result } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

      const formatted = result.current(twoDaysAgo);

      expect(formatted).toMatch(/2 days ago/);
    });

    it('should use French locale when i18n language is French', async () => {
      await i18n.changeLanguage('fr');

      const { result } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

      const formatted = result.current(twoDaysAgo);

      expect(formatted).toMatch(/il y a 2 jours/);
    });

    it('should update formatting when locale changes', async () => {
      await i18n.changeLanguage('en');

      const { result, rerender } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      // Initial English formatting
      let formatted = result.current(yesterday);
      expect(formatted).toMatch(/yesterday|1 day ago/);

      // Change to French
      await i18n.changeLanguage('fr');
      rerender();

      // Should now format in French
      await waitFor(() => {
        formatted = result.current(yesterday);
        expect(formatted).toMatch(/hier|il y a 1 jour/);
      });
    });

    it('should handle string dates', async () => {
      await i18n.changeLanguage('en');

      const { result } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
      const dateString = twoDaysAgo.toISOString();

      const formatted = result.current(dateString);

      expect(formatted).toMatch(/2 days ago/);
    });

    it('should handle Date objects', async () => {
      await i18n.changeLanguage('fr');

      const { result } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const twoDaysAgo = new Date();
      twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);

      const formatted = result.current(twoDaysAgo);

      expect(formatted).toMatch(/il y a 2 jours/);
    });
  });

  describe('Locale Consistency', () => {
    it('should use correct date-fns locale for English (en-US)', () => {
      const date = new Date('2024-01-01');
      const result = formatRelativeDate(date, enUS);

      // English should use "ago" suffix and English month names
      expect(result).toMatch(/ago/);
    });

    it('should use correct date-fns locale for French', () => {
      const date = new Date('2024-01-01');
      const result = formatRelativeDate(date, fr);

      // French should use "il y a" prefix and French vocabulary
      expect(result).toMatch(/il y a/);
    });

    it('should handle edge case: exactly 1 day ago in English', () => {
      const oneDayAgo = new Date();
      oneDayAgo.setDate(oneDayAgo.getDate() - 1);
      oneDayAgo.setHours(oneDayAgo.getHours() - 1); // Ensure > 24h

      const result = formatRelativeDate(oneDayAgo, enUS);

      // Should use singular form
      expect(result).toMatch(/1 day ago|yesterday/);
    });

    it('should handle edge case: exactly 1 day ago in French', () => {
      const oneDayAgo = new Date();
      oneDayAgo.setDate(oneDayAgo.getDate() - 1);
      oneDayAgo.setHours(oneDayAgo.getHours() - 1); // Ensure > 24h

      const result = formatRelativeDate(oneDayAgo, fr);

      // Should use singular form
      expect(result).toMatch(/il y a 1 jour|hier/);
    });
  });

  describe('Integration with LocaleContext', () => {
    it('should respect locale changes throughout the application', async () => {
      // Start in English
      await i18n.changeLanguage('en');

      const { result: hook1 } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      const testDate = new Date();
      testDate.setDate(testDate.getDate() - 5);

      // English formatting
      expect(hook1.current(testDate)).toMatch(/5 days ago/);

      // Change to French
      await i18n.changeLanguage('fr');

      // New hook should use French
      const { result: hook2 } = renderHook(() => useRelativeDateFormatter(), {
        wrapper: DateTestWrapper,
      });

      expect(hook2.current(testDate)).toMatch(/il y a 5 jours/);
    });
  });
});
