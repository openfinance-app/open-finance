import Decimal from 'decimal.js';

/** Numeric API fields remain suitable for charts; forms use their lossless text equivalents. */
export interface ExactDecimalFields {
  exactValues?: Record<string, string>;
}

export function decimalField<T extends object>(source: T, key: keyof T, fallback = ''): string {
  const exact = (source as ExactDecimalFields).exactValues?.[String(key)];
  if (exact !== undefined) return exact;
  const value = source[key];
  return value == null ? fallback : new Decimal(String(value)).toFixed();
}

/** Extra headroom for the API's 26 integer + 18 fractional digit monetary limits. */
export const ExactDecimal = Decimal.clone({ precision: 80, rounding: Decimal.ROUND_HALF_UP });

export function equalDecimals(a: string | number, b: string | number): boolean {
  try {
    return new ExactDecimal(a).eq(b);
  } catch {
    return false;
  }
}

export function exactSum(values: (number | string)[]): string {
  return values.reduce((total, value) => total.plus(value || 0), new ExactDecimal(0)).toFixed();
}

/** Retains the numeric fallback for older API responses and callers. */
export function decimalValue<T extends object>(
  source: T,
  key: keyof T
): string | number | undefined {
  const value = (source as ExactDecimalFields).exactValues?.[String(key)] ?? source[key];
  return typeof value === 'number' || typeof value === 'string' ? value : undefined;
}

export function distributeExact(
  total: number | string,
  amounts: (number | string)[],
  scale: number
): string[] {
  const factor = new ExactDecimal(10).pow(scale);
  const units = amounts.map(value => new ExactDecimal(value || 0).times(factor).toDecimalPlaces(0));
  let remainder = new ExactDecimal(total)
    .times(factor)
    .toDecimalPlaces(0)
    .minus(units.reduce((sum, value) => sum.plus(value), new ExactDecimal(0)));
  const order = units
    .map((_, index) => index)
    .sort((a, b) => units[b].abs().comparedTo(units[a].abs()) || a - b);
  for (const index of order) {
    if (!remainder.isFinite() || remainder.isZero()) break;
    const adjustment = remainder.isPositive()
      ? remainder
      : ExactDecimal.max(remainder, new ExactDecimal(1).minus(units[index]));
    units[index] = units[index].plus(adjustment);
    remainder = remainder.minus(adjustment);
  }
  return units.map(value => value.dividedBy(factor).toFixed());
}
