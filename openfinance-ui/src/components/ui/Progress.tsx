import * as React from 'react';
import { cn } from '@/lib/utils';

export interface ProgressProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Fill percentage, clamped to 0–100 */
  value?: number;
  /** Fill color class, e.g. bg-primary / bg-success / bg-error */
  indicatorColor?: string;
}

/**
 * Accessible progress bar. The fill animates via transform (GPU-friendly)
 * with an eased transition so value changes feel continuous.
 */
const Progress = React.forwardRef<HTMLDivElement, ProgressProps>(
  ({ className, value, indicatorColor, ...props }, ref) => {
    const clamped = Math.min(Math.max(value ?? 0, 0), 100);
    return (
      <div
        ref={ref}
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(clamped)}
        className={cn(
          'relative h-2 w-full overflow-hidden rounded-full bg-surface-elevated',
          className
        )}
        {...props}
      >
        <div
          className={cn(
            'h-full w-full origin-left rounded-full bg-primary',
            'transition-transform duration-500 ease-[cubic-bezier(0.16,1,0.3,1)]',
            indicatorColor
          )}
          style={{ transform: `scaleX(${clamped / 100})` }}
        />
      </div>
    );
  }
);
Progress.displayName = 'Progress';

export { Progress };
