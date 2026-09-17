/**
 * AnimatedNumber - Counts a numeric value up/down with an eased ramp.
 *
 * Used for hero figures (net worth, totals) so data changes feel alive
 * rather than snapping. Rendering is fully text-based each frame, so it
 * composes with any formatter (currency, percentage, plain).
 *
 * Accessibility: honours prefers-reduced-motion (jumps straight to the
 * target value) and keeps the full final value in the accessible text.
 */
import { useEffect, useRef, useState } from 'react';
import { cn } from '@/lib/utils';

export interface AnimatedNumberProps {
  /** Target numeric value */
  value: number;
  /** Formats the animated value for display each frame */
  format: (value: number) => string;
  /** Animation duration in ms (default 900) */
  duration?: number;
  className?: string;
}

/** Deceleration curve matching the app's motion tokens. */
const easeOutExpo = (t: number): number => (t === 1 ? 1 : 1 - Math.pow(2, -10 * t));

export function AnimatedNumber({ value, format, duration = 900, className }: AnimatedNumberProps) {
  const [display, setDisplay] = useState(() => format(value));
  const fromRef = useRef(0);
  const rafRef = useRef<number>(null);

  useEffect(() => {
    const prefersReduced =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReduced) {
      fromRef.current = value;
      setDisplay(format(value));
      return;
    }

    // Skip the ramp for hidden tabs (rAF is throttled) and no-op changes
    const from = fromRef.current;
    if (from === value) return;
    if (typeof document !== 'undefined' && document.hidden) {
      fromRef.current = value;
      setDisplay(format(value));
      return;
    }

    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const current = from + (value - from) * easeOutExpo(progress);
      setDisplay(format(current));
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = value;
      }
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
      fromRef.current = value;
    };
    // `format` is expected to be a stable pure formatter
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, duration]);

  return (
    <span className={cn('number-display', className)} aria-live="off">
      {display}
    </span>
  );
}
