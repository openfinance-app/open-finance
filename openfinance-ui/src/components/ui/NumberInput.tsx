import Decimal from 'decimal.js';
import { useTranslation } from 'react-i18next';
import { useInputValidation } from '@/hooks/useInputValidation';
import { useEffect, useRef, useId, useState, type AriaAttributes } from 'react';
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
  ['aria-invalid']?: AriaAttributes['aria-invalid'];
  ['aria-describedby']?: string;
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
  const normalized = input.trim().replace(/[\u202F\u00A0]/g, ' ');
  if (normalized === '') return '';
  // Validate grouping before removing separators. Never turn malformed drafts
  // such as "12-3" or "1..2" into a different, apparently valid amount.
  const european = /^-?(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d+)?$/;
  const american = /^-?(?:\d+|\d{1,3}(?:,\d{3})+)(?:\.\d+)?$/;
  const french = /^-?(?:\d+|\d{1,3}(?: \d{3})+)(?:,\d+)?$/;
  if (fmt === '1.234,56' && european.test(normalized))
    return normalized.replace(/\./g, '').replace(',', '.');
  if (fmt === '1 234,56' && french.test(normalized))
    return normalized.replace(/ /g, '').replace(',', '.');
  if (fmt === '1,234.56' && american.test(normalized)) return normalized.replace(/,/g, '');
  // Also accept an ungrouped decimal pasted in either common notation.
  if (/^-?\d+(?:[.,]\d+)?$/.test(normalized)) return normalized.replace(',', '.');
  // Preserve pasting well-formed amounts in another supported notation.
  if (american.test(normalized)) return normalized.replace(/,/g, '');
  if (european.test(normalized)) return normalized.replace(/\./g, '').replace(',', '.');
  if (french.test(normalized)) return normalized.replace(/ /g, '').replace(',', '.');
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
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedBy,
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

  const emittedValue = useRef(canonicalValue);
  const previousFormat = useRef(numberFormat);
  const { t } = useTranslation('common');
  const errorId = useId();
  const parsed = parseDisplayToCanonical(display, numberFormat);
  const invalidMessage =
    display.trim() === ''
      ? required
        ? t('input.required')
        : undefined
      : parsed === null
        ? t('input.invalidNumber')
        : min != null && new Decimal(parsed).lt(min)
          ? t('input.numberMin', { value: min })
          : max != null && new Decimal(parsed).gt(max)
            ? t('input.numberMax', { value: max })
            : undefined;
  const {
    ref: inputRef,
    reveal,
    validationError,
  } = useInputValidation(
    disabled || readOnly ? undefined : invalidMessage,
    display !== formatCanonicalToDisplay(canonicalValue, numberFormat)
  );
  const displayedError = validationError || error;

  useEffect(() => {
    if (canonicalValue !== emittedValue.current || numberFormat !== previousFormat.current)
      setDisplay(formatCanonicalToDisplay(canonicalValue, numberFormat));
    emittedValue.current = canonicalValue;
    previousFormat.current = numberFormat;
  }, [canonicalValue, numberFormat]);

  const commit = (text: string) => {
    const next = parseDisplayToCanonical(text, numberFormat);
    if (next !== null) {
      emittedValue.current = next;
      onChange(next);
    }
  };

  const handleBlur = () => {
    reveal();
    if (!invalidMessage && parsed !== null)
      setDisplay(formatCanonicalToDisplay(parsed, numberFormat));
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
        ref={inputRef}
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
        aria-invalid={displayedError ? 'true' : (ariaInvalid ?? 'false')}
        aria-describedby={
          [ariaDescribedBy, displayedError ? errorId : undefined].filter(Boolean).join(' ') ||
          undefined
        }
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
          displayedError
            ? 'border-error focus-visible:ring-error'
            : 'border-border hover:border-border/80',
          className
        )}
      />
      {displayedError && (
        <p id={errorId} role="alert" className="mt-1 text-sm text-error">
          {displayedError}
        </p>
      )}
    </div>
  );
}
