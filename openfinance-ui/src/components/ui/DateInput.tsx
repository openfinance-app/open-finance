import { useEffect, useRef, useState } from 'react';
import { CalendarDays } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useUserSettings } from '@/hooks/useUserSettings';
import { formatIsoToDisplay, parseDisplayDate } from '@/utils/date';

interface DateInputProps {
  id?: string;
  /** Current value as ISO date (yyyy-MM-dd). */
  value?: string;
  /** Called with the ISO date (yyyy-MM-dd) or '' when cleared. */
  onChange: (iso: string) => void;
  onBlur?: () => void;
  /** Maximum selectable ISO date; future values beyond it are rejected. */
  max?: string;
  /** Minimum selectable ISO date; values before it are rejected. */
  min?: string;
  error?: string;
  className?: string;
  disabled?: boolean;
}

/**
 * Text date input that displays and parses dates in the user's configured
 * Date Format setting (MM/DD/YYYY, DD/MM/YYYY, or YYYY-MM-DD) while always
 * storing an ISO (yyyy-MM-dd) value. A calendar button opens the native picker.
 */
export function DateInput({
  id,
  value,
  onChange,
  onBlur,
  max,
  min,
  error,
  className,
  disabled,
}: DateInputProps) {
  const { data: settings } = useUserSettings();
  const dateFormat = settings?.dateFormat ?? 'MM/DD/YYYY';
  const nativeRef = useRef<HTMLInputElement>(null);

  const [display, setDisplay] = useState(() => (value ? formatIsoToDisplay(value, dateFormat) : ''));

  // Keep the visible text in sync when the ISO value or format changes externally.
  useEffect(() => {
    setDisplay(value ? formatIsoToDisplay(value, dateFormat) : '');
  }, [value, dateFormat]);

  const commit = (text: string) => {
    const iso = parseDisplayDate(text, dateFormat);
    if (
      iso &&
      (!max || iso <= max) &&
      (!min || iso >= min)
    ) {
      onChange(iso);
    } else if (text.trim() === '') {
      onChange('');
    }
  };

  const handleBlur = () => {
    // Snap the display back to the canonical formatting of the stored value.
    setDisplay(value ? formatIsoToDisplay(value, dateFormat) : '');
    onBlur?.();
  };

  const openPicker = () => {
    const el = nativeRef.current;
    if (!el) return;
    if (typeof el.showPicker === 'function') el.showPicker();
    else el.focus();
  };

  return (
    <div className="relative w-full">
      <input
        id={id}
        type="text"
        inputMode="numeric"
        autoComplete="off"
        disabled={disabled}
        value={display}
        placeholder={dateFormat}
        aria-invalid={error ? 'true' : 'false'}
        onChange={(e) => {
          setDisplay(e.target.value);
          commit(e.target.value);
        }}
        onBlur={handleBlur}
        className={cn(
          'flex h-10 w-full rounded-lg border bg-surface py-2 pl-3 pr-10 text-sm text-text-primary',
          'placeholder:text-text-muted',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background',
          'disabled:cursor-not-allowed disabled:opacity-50 transition-all duration-150',
          error ? 'border-error focus-visible:ring-error' : 'border-border hover:border-border/80',
          className
        )}
      />
      <button
        type="button"
        onClick={openPicker}
        disabled={disabled}
        aria-label="Open date picker"
        className="absolute right-2 top-1/2 -translate-y-1/2 text-text-muted hover:text-primary transition-colors disabled:opacity-50"
      >
        <CalendarDays className="h-4 w-4" />
      </button>
      {/* Visually hidden native picker used only for calendar selection. */}
      <input
        ref={nativeRef}
        type="date"
        tabIndex={-1}
        aria-hidden="true"
        min={min}
        max={max}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        className="sr-only absolute right-2 bottom-0 h-0 w-0"
      />
      {error && <p className="mt-1 text-sm text-error">{error}</p>}
    </div>
  );
}
