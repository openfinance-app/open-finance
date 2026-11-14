import { describe, it, expect, vi } from 'vitest';
import reportWebVitals from './reportWebVitals';

/**
 * `web-vitals` is not installed, so this module is an intentional no-op stub wired in
 * main.tsx. Pin that contract: calling it must be safe and must never invoke the callback
 * with fabricated metrics. If real reporting is restored, this test should be replaced.
 */
describe('reportWebVitals (no-op stub)', () => {
  it('accepts being called with and without a callback', () => {
    expect(() => reportWebVitals()).not.toThrow();
    expect(() => reportWebVitals(undefined)).not.toThrow();
    const onPerfEntry = vi.fn();
    expect(() => reportWebVitals(onPerfEntry)).not.toThrow();
    expect(onPerfEntry).not.toHaveBeenCalled();
  });
});
