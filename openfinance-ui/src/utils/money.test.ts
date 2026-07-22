import { describe, expect, it } from 'vitest';
import {
  add,
  divide,
  fromMinorUnits,
  multiply,
  percentage,
  roundToDecimals,
  subtract,
  sum,
  sumToDecimals,
  toMinorUnits,
} from './money';

/**
 * Precise decimal arithmetic helpers, backed by `decimal.js`.
 *
 * JavaScript numbers are IEEE-754 doubles, so ordinary floating-point arithmetic on money
 * (multiplication, division, and summation) can silently drift by fractions of a cent and,
 * worse, cause `Math.round`/`.toFixed` to round the wrong way at exact `.xx5` boundaries:
 *
 * ```
 * Math.round(1.005 * 100) / 100 // -> 1        (should be 1.01)
 * (1.005).toFixed(2)            // -> "1.00"   (should be "1.01")
 * [0.1, 0.2].reduce((a, b) => a + b, 0) // -> 0.30000000000000004
 * ```
 *
 * These tests pin the exact (round-half-away-from-zero, matching the backend's
 * `BigDecimal`/`RoundingMode.HALF_UP`) behaviour for every operation.
 */
describe('money', () => {
  describe('add', () => {
    it('adds 0.1 + 0.2 to exactly 0.3 (not 0.30000000000000004)', () => {
      expect(add(0.1, 0.2)).toBe(0.3);
    });

    it('adds negative values', () => {
      expect(add(-5, 3)).toBe(-2);
    });
  });

  describe('subtract', () => {
    it('subtracts without float artifacts (0.3 - 0.1 = 0.2, not 0.19999999999999998)', () => {
      expect(subtract(0.3, 0.1)).toBe(0.2);
    });
  });

  describe('multiply', () => {
    it('multiplies an amount by an exchange rate precisely', () => {
      // 19.99 * 1.1 === 21.989 in raw doubles due to float representation; exact answer is 21.989
      expect(multiply(19.99, 1.1)).toBe(21.989);
    });

    it('multiplies 1.005 * 100 to exactly 100.5 (raw float gives 100.49999999999999)', () => {
      expect(multiply(1.005, 100)).toBe(100.5);
    });
  });

  describe('divide', () => {
    it('divides precisely', () => {
      expect(divide(10, 4)).toBe(2.5);
    });

    it('returns Infinity when dividing a non-zero value by zero (matches native JS)', () => {
      expect(divide(5, 0)).toBe(Infinity);
    });

    it('returns NaN when dividing zero by zero (matches native JS)', () => {
      expect(Number.isNaN(divide(0, 0))).toBe(true);
    });
  });

  describe('graceful handling of missing/invalid values (never throws)', () => {
    // Raw `decimal.js` throws a DecimalError on `null`/`undefined` (`new Decimal(undefined)`),
    // unlike native `+`/`*`/`-`/`/` which silently propagate `NaN` (or coerce `null` to 0).
    // A throw inside a `.map()`/`.reduce()` during a React render would crash the component —
    // strictly worse than rendering "NaN" text — so every money.ts function must tolerate
    // missing/invalid input the same way native arithmetic does: never throw, propagate NaN.

    it('add propagates NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(add(undefined as unknown as number, 5))).toBe(true);
    });

    it('add propagates NaN instead of throwing when given null', () => {
      expect(Number.isNaN(add(null as unknown as number, 5))).toBe(true);
    });

    it('multiply propagates NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(multiply(undefined as unknown as number, 5))).toBe(true);
    });

    it('subtract propagates NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(subtract(undefined as unknown as number, 5))).toBe(true);
    });

    it('divide propagates NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(divide(undefined as unknown as number, 5))).toBe(true);
    });

    it('sum propagates NaN (does not throw or silently skip) when one entry is undefined', () => {
      // Matches `[1, undefined, 2].reduce((a, b) => a + b, 0)` semantics: a single bad entry
      // poisons the whole sum to NaN rather than being silently dropped, so data-quality bugs
      // stay visible instead of silently under-counting a real financial total.
      expect(Number.isNaN(sum([1, undefined as unknown as number, 2]))).toBe(true);
    });

    it('percentage returns NaN instead of throwing when part is undefined', () => {
      expect(Number.isNaN(percentage(undefined as unknown as number, 100))).toBe(true);
    });

    it('roundToDecimals returns NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(roundToDecimals(undefined as unknown as number))).toBe(true);
    });

    it('toMinorUnits returns NaN instead of throwing when given undefined', () => {
      expect(Number.isNaN(toMinorUnits(undefined as unknown as number))).toBe(true);
    });
  });

  describe('sum', () => {
    it('sums several values without float accumulation drift', () => {
      // Plain `[0.1, 0.2, 0.3, 0.15, 0.05].reduce((a, b) => a + b, 0)` yields
      // 0.8000000000000002 in JS.
      expect(sum([0.1, 0.2, 0.3, 0.15, 0.05])).toBe(0.8);
    });

    it('returns 0 for an empty list', () => {
      expect(sum([])).toBe(0);
    });
  });

  describe('percentage', () => {
    it('computes part/whole as a percentage, rounded to 2 decimals by default', () => {
      expect(percentage(25, 75)).toBe(33.33);
    });

    it('returns 0 when the whole is 0 (avoids NaN in the UI)', () => {
      expect(percentage(10, 0)).toBe(0);
    });

    it('supports a custom decimal scale', () => {
      expect(percentage(1, 3, 4)).toBe(33.3333);
    });
  });

  describe('roundToDecimals', () => {
    it('rounds 1.005 up to 1.01 (classic float rounding-boundary bug)', () => {
      expect(roundToDecimals(1.005)).toBe(1.01);
    });

    it('rounds 2.675 up to 2.68', () => {
      expect(roundToDecimals(2.675)).toBe(2.68);
    });

    it('rounds negative values half-away-from-zero (matches BigDecimal HALF_UP)', () => {
      expect(roundToDecimals(-1.005)).toBe(-1.01);
    });

    it('leaves whole numbers unchanged', () => {
      expect(roundToDecimals(70)).toBe(70);
    });
  });

  describe('sumToDecimals', () => {
    it('sums 0.1 + 0.2 to exactly 0.3 (not 0.30000000000000004)', () => {
      expect(sumToDecimals([0.1, 0.2])).toBe(0.3);
    });

    it('returns 0 for an empty list', () => {
      expect(sumToDecimals([])).toBe(0);
    });
  });

  describe('toMinorUnits / fromMinorUnits', () => {
    it('converts a decimal amount to integer minor units (cents)', () => {
      expect(toMinorUnits(1.005)).toBe(101);
      expect(toMinorUnits(25.5)).toBe(2550);
    });

    it('round-trips back to the original precise decimal value', () => {
      expect(fromMinorUnits(toMinorUnits(2.675))).toBe(2.68);
      expect(fromMinorUnits(101)).toBe(1.01);
    });
  });
});
