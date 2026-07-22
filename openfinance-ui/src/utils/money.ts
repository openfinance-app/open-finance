import Decimal from 'decimal.js';

/**
 * Precise decimal arithmetic helpers for monetary values, backed by `decimal.js`.
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
 * This module is the **single shared source of truth** for precise decimal math on the frontend —
 * every function accepts and returns plain `number`s (converting to/from `Decimal` only inside the
 * function body), so call sites never need to learn the `decimal.js` API or hold onto `Decimal`
 * instances in component state; `number` stays the only type crossing into React/recharts.
 *
 * Rounding is configured to half-away-from-zero, matching the backend's
 * `BigDecimal`/`RoundingMode.HALF_UP` convention, so frontend-computed figures never diverge from
 * the backend for the same inputs.
 */

Decimal.set({ rounding: Decimal.ROUND_HALF_UP });

/**
 * A numeric input accepted by every function in this module. `null`/`undefined` are tolerated
 * (see {@link toDecimal}) since many DTO fields are optional and callers routinely pass them
 * straight through without an explicit `?? 0` guard.
 */
type Numeric = number | string | Decimal | null | undefined;

/**
 * Converts `value` to a `Decimal`, tolerating missing/invalid input the same way native
 * arithmetic does.
 *
 * Raw `decimal.js` **throws** a `DecimalError` on `null`/`undefined`/non-numeric strings (unlike
 * native `+`/`-`/`*`//`, which silently produce `NaN` or coerce). A throw inside a `.map()`/
 * `.reduce()` callback during a React render propagates up and crashes the component — strictly
 * worse than rendering "NaN" text — so every public function here must never throw. Missing or
 * invalid input is therefore converted to `Decimal(NaN)`, which propagates through subsequent
 * operations exactly like native `NaN` does, keeping data-quality problems visible (as "NaN" in
 * the UI) instead of silently substituting a possibly-misleading `0` in a financial app.
 */
function toDecimal(value: Numeric): Decimal {
  if (value instanceof Decimal) return value;
  if (value === null || value === undefined) return new Decimal(NaN);
  try {
    return new Decimal(value);
  } catch {
    return new Decimal(NaN);
  }
}

/** Adds `a + b` precisely (e.g. `add(0.1, 0.2) === 0.3`, not `0.30000000000000004`). */
export function add(a: Numeric, b: Numeric): number {
  return toDecimal(a).plus(toDecimal(b)).toNumber();
}

/** Subtracts `a - b` precisely. */
export function subtract(a: Numeric, b: Numeric): number {
  return toDecimal(a).minus(toDecimal(b)).toNumber();
}

/** Multiplies `a * b` precisely (e.g. for `amount * exchangeRate` FX conversions). */
export function multiply(a: Numeric, b: Numeric): number {
  return toDecimal(a).times(toDecimal(b)).toNumber();
}

/**
 * Divides `a / b` precisely. Dividing by zero mirrors native JS semantics
 * (`Infinity` for a non-zero numerator, `NaN` for `0 / 0`) rather than throwing, so existing
 * call-site behaviour around zero-division is preserved.
 */
export function divide(a: Numeric, b: Numeric): number {
  return toDecimal(a).dividedBy(toDecimal(b)).toNumber();
}

/**
 * Sums `values` precisely, avoiding the float accumulation drift of
 * `values.reduce((a, b) => a + b, 0)` (e.g. `0.1 + 0.2 + 0.3 + 0.15 + 0.05` yields
 * `0.8000000000000002` in raw JS floats). A single missing/invalid entry propagates `NaN` through
 * the whole sum (matching plain `+` semantics) rather than being silently skipped, so a
 * data-quality problem stays visible instead of silently under-counting a real total.
 */
export function sum(values: Numeric[]): number {
  return values.reduce((total: Decimal, v) => total.plus(toDecimal(v)), new Decimal(0)).toNumber();
}

/**
 * Rounds `value` to `decimals` places using round-half-away-from-zero, without the binary
 * floating-point artifacts of `Math.round(x * 10^n) / 10^n` or `.toFixed(n)`.
 */
export function roundToDecimals(value: Numeric, decimals = 2): number {
  return toDecimal(value).toDecimalPlaces(decimals).toNumber();
}

/** Sums `values` precisely (see {@link sum}) and rounds the result to `decimals` places. */
export function sumToDecimals(values: Numeric[], decimals = 2): number {
  return roundToDecimals(sum(values), decimals);
}

/**
 * Computes `part / whole * 100`, rounded to `decimals` places (default 2). Returns `0` when
 * `whole` is `0` instead of `NaN`, matching the common UI convention of showing "0%" rather than
 * a not-a-number value.
 */
export function percentage(part: Numeric, whole: Numeric, decimals = 2): number {
  const wholeDecimal = toDecimal(whole);
  if (wholeDecimal.isZero()) return 0;
  return roundToDecimals(toDecimal(part).dividedBy(wholeDecimal).times(100).toNumber(), decimals);
}

/**
 * Converts `value` to an integer number of minor units (e.g. cents for a 2-decimal currency).
 *
 * Scope: this and {@link fromMinorUnits} hardcode a 2-decimal scale by default, matching every
 * existing call site (fiat amounts). Currency-aware decimal precision (JPY = 0 decimals, BTC = 8
 * decimals) is tracked separately as Critical Theme 3 and is out of scope here.
 */
export function toMinorUnits(value: Numeric, decimals = 2): number {
  return toDecimal(value)
    .times(new Decimal(10).pow(decimals))
    .toDecimalPlaces(0)
    .toNumber();
}

/** Converts an integer number of minor units back to a decimal value. */
export function fromMinorUnits(units: Numeric, decimals = 2): number {
  return toDecimal(units).dividedBy(new Decimal(10).pow(decimals)).toNumber();
}
