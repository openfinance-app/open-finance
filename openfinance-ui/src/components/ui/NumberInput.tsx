import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';
import { useNumberFormat } from '@/context/NumberFormatContext';
import type { NumberFormat } from '@/context/NumberFormatContext';

interface NumberInputProps {
  id?: string;
  /** Canonical value with dot decimal, no thousands separators (e.g. "1234.56" or ""). */
  value?: string | number;
  /** Called with canonical dot-decimal string (or "" when cleared). */
  onChange: (canonical: string) => void;
  onBlur?: () => void;
  placeholder?: string;
  error?: string;
  className?: string;
  disabled?: boolean;
  readOnly?: boolean;
  required?: boolean;
  /** Minimum canonical value (as string/number with dot decimal). */
  min?: string | number;
  /** Maximum canonical value. */
  max?: string | number;
  /** Accessible label for testing when label element not present. */
  ['aria-label']?: string;
  ['data-testid']?: string;
}

type Separators = { thousands: string; decimal: string };

function getSeparators(fmt: NumberFormat): Separators {
  if (fmt === '1.234,56') return { thousands: '.', decimal: ',' };
  if (fmt === '1 234,56') return { thousands: '\u202F', decimal: ',' };
  return { thousands: ',', decimal: '.' };
}

function groupIntegerPart(intPart: string, thousandsSep: string): string {
  const isNegative = intPart.startsWith('-');
  const clean = isNegative ? intPart.slice(1) : intPart;
  if (clean.length <= 3) return intPart;
  const grouped = clean.replace(/\B(?=(\d{3})+(?!\d))/g, thousandsSep);
  return isNegative ? '-' + grouped : grouped;
}

/**
 * Format a canonical dot-decimal string for display according to the user's numberFormat.
 * Returns "" for empty/null, and leaves non-canonical strings untouched.
 */
function formatCanonicalToDisplay(
  canonical: string | number | undefined | null,
  fmt: NumberFormat
): string {
  if (canonical === undefined || canonical === null) return '';
  const raw = String(canonical).trim();
  if (raw === '') return '';
  // If not a plain canonical decimal, return as-is (avoid corrupting partial edits)
  if (!/^-?\d+(\.\d+)?$/.test(raw)) return raw;
  const [intPart, fracPart] = raw.split('.');
  const { thousands, decimal } = getSeparators(fmt);
  const groupedInt = groupIntegerPart(intPart, thousands);
  if (fracPart !== undefined) {
    return `${groupedInt}${decimal}${fracPart}`;
  }
  return groupedInt;
}

/**
 * Parse a localized display string back to canonical dot-decimal string.
 * Returns "" for empty input, canonical string for valid numbers, or null for invalid intermediate states.
 */
function parseDisplayToCanonical(input: string, fmt: NumberFormat): string | null {
  const s = input.trim();
  if (s === '') return '';
  // Normalize narrow NBSPs to regular space for French format handling
  const normalized = s.replace(/\u202F/g, ' ').replace(/\u00A0/g, ' ');
  // If already canonical (dot decimal, no thousands except optional minus), accept directly
  if (/^-?\d+(\.\d+)?$/.test(normalized)) {
    return normalized;
  }
  const { thousands, decimal } = getSeparators(fmt);

  // Strict locale-aware parsing
  try {
    let withoutThousands: string;
    if (fmt === '1 234,56') {
      withoutThousands = normalized.replace(/ /g, '');
    } else {
      const esc = thousands.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      withoutThousands = normalized.replace(new RegExp(esc, 'g'), '');
    }
    let withDotDecimal: string;
    if (decimal !== '.') {
      const escDec = decimal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      withDotDecimal = withoutThousands.replace(new RegExp(escDec, 'g'), '.');
    } else {
      withDotDecimal = withoutThousands;
    }
    // After conversion, should be plain canonical
    if (/^-?\d+(\.\d+)?$/.test(withDotDecimal)) {
      return withDotDecimal;
    }
    // Also handle case where user typed minus sign with spaces etc.
  } catch {
    // fall through to lenient
  }

  // Lenient fallback: treat last dot or comma as decimal, remove other separators
  let lenient = normalized.replace(/ /g, '');
  // Quick reject if contains invalid characters beyond digits, minus, dot, comma
  if (!/^[-.,\d]+$/.test(lenient)) return null;
  // Handle leading minus
  const isNegative = lenient.startsWith('-');
  // Remove all minus not at start for validation (will be re-added)
  lenient = (isNegative ? '-' : '') + lenient.slice(isNegative ? 1 : 0).replace(/-/g, '');
  const lastDot = lenient.lastIndexOf('.');
  const lastComma = lenient.lastIndexOf(',');
  let decimalPos = -1;
  if (lastDot !== -1 && lastComma !== -1) {
    decimalPos = Math.max(lastDot, lastComma);
  } else if (lastDot !== -1) {
    decimalPos = lastDot;
  } else if (lastComma !== -1) {
    decimalPos = lastComma;
  }
  let intPart: string;
  let fracPart: string | null = null;
  if (decimalPos !== -1) {
    intPart = lenient.slice(0, decimalPos);
    fracPart = lenient.slice(decimalPos + 1);
    intPart = intPart.replace(/[.,]/g, '');
    fracPart = fracPart.replace(/[.,]/g, '');
  } else {
    intPart = lenient.replace(/[.,]/g, '');
  }
  if (intPart === '' || intPart === '-') return null;
  const cleanInt = (isNegative ? '-' : '') + intPart.replace(/-/g, '').replace(/^0+(?=\d)/, m => m); // keep digits
  // Validate integer part digits
  const intDigits = cleanInt.startsWith('-') ? cleanInt.slice(1) : cleanInt;
  if (!/^\d+$/.test(intDigits)) return null;
  if (fracPart !== null && fracPart !== '' && !/^\d+$/.test(fracPart)) return null;
  let canonical = cleanInt;
  if (fracPart !== null && fracPart !== '') canonical += '.' + fracPart;
  // For inputs like "1," with trailing decimal and no fraction, we treat as incomplete intermediate -> don't commit
  // If original had trailing decimal separator but no fraction, consider incomplete
  const hadTrailingDecimal = /[.,]\s*$/.test(s);
  if (hadTrailingDecimal && (fracPart === null || fracPart === '')) {
    // Don't commit yet, but input like "1." is incomplete; let user continue typing
    // Return null to indicate not yet commitable, but display remains
    // However if they type "1." and blur, we should snap to "1"
    // So return null here, and onBlur will format canonical
    return null;
  }
  if (/^-?\d+(\.\d+)?$/.test(canonical)) return canonical;
  return null;
}

/**
 * Locale-aware numeric input that displays according to the user's numberFormat
 * (1,234.56 / 1.234,56 / 1 234,56) while always storing a canonical dot-decimal string.
 */
export function NumberInput({
  id,
  value,
  onChange,
  onBlur,
  placeholder,
  error,
  className,
  disabled,
  readOnly,
  required,
  min,
  max,
  'aria-label': ariaLabel,
  'data-testid': dataTestId,
}: NumberInputProps) {
  let numberFormat: NumberFormat = '1,234.56';
  try {
    // May throw outside provider (e.g. in some test setups); fallback to default
    const ctx = useNumberFormat();
    numberFormat = ctx.numberFormat;
  } catch {
    numberFormat = '1,234.56';
  }

  const canonicalValue = value === undefined || value === null ? '' : String(value);

  const [display, setDisplay] = useState(() =>
    formatCanonicalToDisplay(canonicalValue, numberFormat)
  );

  // Keep display in sync when canonical value or format changes externally
  useEffect(() => {
    setDisplay(formatCanonicalToDisplay(canonicalValue, numberFormat));
  }, [canonicalValue, numberFormat]);

  const commit = (text: string) => {
    const trimmed = text.trim();
    if (trimmed === '') {
      onChange('');
      return;
    }
    const parsed = parseDisplayToCanonical(text, numberFormat);
    if (parsed !== null && parsed !== '') {
      onChange(parsed);
    } else if (parsed === '') {
      onChange('');
    }
    // If parsed is null (invalid/incomplete), do not call onChange — keep previous canonical
  };

  const handleBlur = () => {
    setDisplay(formatCanonicalToDisplay(canonicalValue, numberFormat));
    onBlur?.();
  };

  // Format placeholder if it looks like a canonical number (e.g. "0.00")
  const displayPlaceholder = (() => {
    if (!placeholder) return undefined;
    const p = placeholder.trim();
    if (/^-?\d+(\.\d+)?$/.test(p)) {
      return formatCanonicalToDisplay(p, numberFormat);
    }
    return placeholder;
  })();

  return (
    <div className="relative w-full">
      <input
        id={id}
        type="text"
        inputMode="decimal"
        autoComplete="off"
        disabled={disabled}
        readOnly={readOnly}
        required={required}
        min={min}
        max={max}
        value={display}
        placeholder={displayPlaceholder}
        aria-invalid={error ? 'true' : 'false'}
        aria-label={ariaLabel}
        data-testid={dataTestId}
        onChange={e => {
          setDisplay(e.target.value);
          commit(e.target.value);
        }}
        onBlur={handleBlur}
        className={cn(
          'flex h-10 w-full rounded-lg border bg-surface py-2 px-3 text-sm text-text-primary',
          'placeholder:text-text-muted',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          'disabled:cursor-not-allowed disabled:opacity-50 transition-all duration-150',
          error ? 'border-error focus-visible:ring-error' : 'border-border hover:border-border/80',
          className
        )}
      />
      {error && <p className="mt-1 text-sm text-error">{error}</p>}
    </div>
  );
}
