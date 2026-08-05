import { describe, expect, it } from 'vitest';
import {
  add,
  distributeRemainder,
  divide,
  fromMinorUnits,
  multiply,
  percentage,
  pow,
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

  describe('pow', () => {
    it('computes compound growth for an integer exponent: (1.05)^10', () => {
      // decimal.js exact: 1.6288946267774414063; .toNumber() rounds to nearest
      // double 1.6288946267774413 (canonical round-trip), differing from
      // Math.pow(1.05, 10) = 1.6288946267774422 in the 16th sig fig.
      expect(pow(1.05, 10)).toBeCloseTo(1.6288946267774413, 15);
    });

    it('returns 1 for base^0 (any base, including non-finite-friendly cases)', () => {
      expect(pow(1.05, 0)).toBe(1);
      expect(pow(2, 0)).toBe(1);
    });

    it('returns the base for base^1', () => {
      expect(pow(1.05, 1)).toBe(1.05);
    });

    it('returns 1 for 1^n (any exponent)', () => {
      expect(pow(1, 100)).toBe(1);
    });

    it('handles fractional exponents: sqrt(2) via 2^0.5', () => {
      expect(pow(2, 0.5)).toBeCloseTo(1.4142135623730951, 14);
    });

    it('handles negative exponents: (1.005)^-360 (amortization reciprocal)', () => {
      // Used in useEarlyPayoffCalculator: 1 - pow(1 + monthlyRate, -months).
      // decimal.js is exact for integer exponents (incl. negative); Math.pow(1.005, -360)
      // yields 0.16604192803832987 which differs from decimal.js's exact
      // 0.16604192803832352992 in the 15th sig fig — the precision win that
      // matters at .xx5 rounding boundaries.
      const result = pow(1.005, -360);
      expect(result).toBeGreaterThan(0);
      expect(result).toBeLessThan(1);
      // Pin to the decimal.js exact value (converted to the nearest double).
      expect(result).toBeCloseTo(0.16604192803832352, 15);
    });

    it('composes with add() to keep the base exact before exponentiation', () => {
      // The precision win: `Math.pow(1 + 0.007, 12)` first computes `1 + 0.007`
      // in float (drift at ~1e-16) then exponentiates that drifted base, yielding
      // 1.0873106619155055. `pow(add(1, 0.007), 12)` builds the base as
      // Decimal(1).plus(Decimal('0.007')) = exactly 1.007, then .pow(12) from
      // there, yielding the exact 1.0873106619155067845 — differing from Math.pow
      // in the 16th sig fig, which flips .xx5 rounding boundaries.
      const rate = 0.007;
      const months = 12;
      const composed = pow(add(1, rate), months);
      // (1.007)^12 exact value (decimal.js, converted to nearest double).
      expect(composed).toBeCloseTo(1.0873106619155068, 15);
    });

    it('propagates NaN instead of throwing when the base is undefined', () => {
      expect(Number.isNaN(pow(undefined as unknown as number, 12))).toBe(true);
    });

    it('propagates NaN instead of throwing when the exponent is undefined', () => {
      expect(Number.isNaN(pow(1.05, undefined as unknown as number))).toBe(true);
    });

    it('returns 0 for base 0 raised to a positive exponent', () => {
      expect(pow(0, 5)).toBe(0);
    });

    it('monotonically increases for base > 1 as exponent grows', () => {
      const lower = pow(1.05, 5);
      const higher = pow(1.05, 10);
      expect(higher).toBeGreaterThan(lower);
    });
  });
});

describe('distributeRemainder', () => {
  const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0);

  it('distributes a 0.01 residue so a 3-way split of 100 is exact', () => {
    const result = distributeRemainder(100, [33.33, 33.33, 33.33], 2);
    expect(result).toEqual([33.34, 33.33, 33.33]);
    expect(sum(result)).toBeCloseTo(100, 10);
  });

  it('absorbs a negative residue (over-allocated) exactly', () => {
    const result = distributeRemainder(100, [50.01, 50.0], 2);
    expect(result).toEqual([50.0, 50.0]);
    expect(sum(result)).toBeCloseTo(100, 10);
  });

  it('handles zero-decimal currencies (JPY)', () => {
    const result = distributeRemainder(100, [33, 33, 33], 0);
    expect(sum(result)).toBe(100);
  });

  it('is a no-op when the amounts already sum exactly', () => {
    const result = distributeRemainder(100, [40, 60], 2);
    expect(result).toEqual([40, 60]);
  });
});
