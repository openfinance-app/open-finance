import React, { useState } from 'react';
import { Info } from 'lucide-react';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import { cn } from '@/lib/utils';

interface HelpTooltipProps {
  /** The help text to display in the tooltip */
  text: string;
  /** Optional additional CSS classes for the trigger icon */
  className?: string;
  /** Tooltip positioning side (default: top) */
  side?: 'top' | 'right' | 'bottom' | 'left';
}

/**
 * HelpTooltip — a small inline info icon (ⓘ) that shows contextual help text
 * on hover, focus, or tap.
 *
 * Usage:
 * ```tsx
 * <span className="flex items-center gap-1">
 *   Net Worth
 *   <HelpTooltip text="Total assets minus total liabilities in your base currency." />
 * </span>
 * ```
 *
 * Requirement TASK-15.2.4: Tooltips for complex features / contextual help.
 */
export const HelpTooltip: React.FC<HelpTooltipProps> = ({ text, className, side = 'top' }) => {
  const [open, setOpen] = useState(false);
  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip open={open} onOpenChange={setOpen}>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label={text}
            onPointerDown={event => {
              // Keep Radix from closing the tooltip before a touch click can toggle it.
              if (event.pointerType === 'touch') event.preventDefault();
            }}
            onClick={event => {
              event.preventDefault();
              setOpen(value => !value);
            }}
            className={cn(
              'inline-flex items-center cursor-help text-text-secondary hover:text-text-primary transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary rounded-sm',
              className
            )}
          >
            <Info className="h-3.5 w-3.5" aria-hidden="true" />
          </button>
        </TooltipTrigger>
        <TooltipContent side={side} className="max-w-xs text-xs leading-relaxed">
          {text}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
};

export default HelpTooltip;
