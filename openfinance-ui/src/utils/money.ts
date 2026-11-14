import type Decimal from 'decimal.js';
import { ExactDecimal } from '@/utils/exact-decimal';

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
 * These helpers use 80 significant decimal digits internally. Numeric results are for
 * chart geometry and legacy numeric consumers; converting back to a JavaScript number can
 * lose digits. Use ExactDecimal/decimalValue/exactSum and decimal strings for recorded inputs,
 * exports and monetary display bindings that must preserve the API's exact values.
 *
 * Explicit rounding uses half-away-from-zero, matching BigDecimal/RoundingMode.HALF_UP.
 * Display preferences never participate in arithmetic.
 */

/**
 * A numeric input accepted by every function in this module. `null`/`undefined` are tolerated
 * (see {@link toDecimal}) since many DTO fields are optional and callers routinely pass them
 * straight through without an explicit `?? 0` guard.
 */
type Numeric = number | string | Decimal | null | undefined;

/**
 * Plain (non-exponential) decimal string: optional leading `-`, at least one digit, optional
 * `.` + digits. Matches what the backend `BigDecimal`/`@Digits` boundary accepts.
 */
const DECIMAL_STRING_PATTERN = /^-?\d+(\.\d+)?$/;

/**
 * Validates that `value` is a plain decimal string (no scientific notation, no thousands
 * separators) safe to carry an amount end-to-end as text — from a form input, through the
 * request payload, into a backend `BigDecimal` — without ever being parsed into a lossy JS
 * `number` along the way. Used by form zod schemas in place of `z.coerce.number()` for amount
 * fields where arbitrary user-entered precision must be preserved exactly.
 */
export function isValidDecimalString(value: string): boolean {
  return DECIMAL_STRING_PATTERN.test(value.trim());
}

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
  if (ExactDecimal.isDecimal(value)) return new ExactDecimal(value);
  if (value === null || value === undefined) return new ExactDecimal(NaN);
  try {
    return new ExactDecimal(value);
  } catch {
    return new ExactDecimal(NaN);
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
 * Raises `base` to the power of `exponent` precisely (e.g. for compound-growth factors
 * `(1 + r)^n` or amortization reciprocals `(1 + r)^-n`).
 *
 * `Decimal.pow` is exact for integer exponents (repeated multiplication) and uses
 * high-precision `e^(n*ln(x))` for fractional/negative exponents. The main precision
 * win over `Math.pow(1 + r, n)` is compositional: pairing with {@link add} builds the
 * base as `Decimal(1).plus(Decimal(r))` (exactly `1 + r` in decimal arithmetic) before
 * exponentiating, so the base never inherits the `1 + r` IEEE-754 drift that
 * `Math.pow` would otherwise amplify.
 */
export function pow(base: Numeric, exponent: Numeric): number {
  return toDecimal(base).pow(toDecimal(exponent)).toNumber();
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
  return values
    .reduce((total: Decimal, v) => total.plus(toDecimal(v)), new ExactDecimal(0))
    .toNumber();
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
  return values
    .reduce<Decimal>((total, value) => total.plus(toDecimal(value)), new ExactDecimal(0))
    .toDecimalPlaces(decimals)
    .toNumber();
}

/**
 * Computes `part / whole * 100`, rounded to `decimals` places (default 2). Returns `0` when
 * `whole` is `0` instead of `NaN`, matching the common UI convention of showing "0%" rather than
 * a not-a-number value.
 */
export function percentage(part: Numeric, whole: Numeric, decimals = 2): number {
  const wholeDecimal = toDecimal(whole);
  if (wholeDecimal.isZero()) return 0;
  return toDecimal(part).dividedBy(wholeDecimal).times(100).toDecimalPlaces(decimals).toNumber();
}

/**
 * Converts `value` to an integer number of minor units (e.g. cents for a 2-decimal currency).
 *
 * Callers allocating payments must supply the currency settlement scale; the default
 * is two places. Never use display preferences to choose a payment allocation scale.
 */
export function toMinorUnits(value: Numeric, decimals = 2): number {
  return toDecimal(value).times(new ExactDecimal(10).pow(decimals)).toDecimalPlaces(0).toNumber();
}

/** Converts an integer number of minor units back to a decimal value. */
export function fromMinorUnits(units: Numeric, decimals = 2): number {
  return toDecimal(units).dividedBy(new ExactDecimal(10).pow(decimals)).toNumber();
}

/**
 * Allocate the full remainder in decimal minor units, largest lines first. Positive
 * lines stay positive; an impossible allocation remains visibly unbalanced.
 * Decimal integer arithmetic also works at crypto scales above Number.MAX_SAFE_INTEGER.
 */
export function distributeRemainder(total: Numeric, amounts: Numeric[], decimals = 2): number[] {
  const factor = new ExactDecimal(10).pow(decimals);
  const units = amounts.map(amount => toDecimal(amount).times(factor).toDecimalPlaces(0));
  let residue = toDecimal(total)
    .times(factor)
    .toDecimalPlaces(0)
    .minus(units.reduce((acc, value) => acc.plus(value), new ExactDecimal(0)));
  const order = units
    .map((_, index) => index)
    .sort((a, b) => units[b].abs().comparedTo(units[a].abs()) || a - b);
  for (const index of order) {
    if (!residue.isFinite() || residue.isZero()) break;
    const adjustment = residue.isPositive()
      ? residue
      : ExactDecimal.max(residue, new ExactDecimal(1).minus(units[index]));
    units[index] = units[index].plus(adjustment);
    residue = residue.minus(adjustment);
  }
  return units.map(value => value.dividedBy(factor).toNumber());
}

/** Fraction digits in the entered value, independent of display preferences. */
export function decimalPlaces(value: Numeric): number {
  return toDecimal(value).decimalPlaces();
}

/** Plain decimal output for exports; avoid native toFixed's binary rounding. */
export function toFixedDecimal(value: Numeric, decimals: number): string {
  return toDecimal(value).toFixed(decimals);
}
