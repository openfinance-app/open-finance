import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/Tooltip';
import { useDateFormatter } from '@/hooks/useDateFormatter';
import type { DateRange } from '@/utils/dashboard-period';

interface PeriodLabelProps {
  children: string;
  range?: DateRange;
}

/** Compact period caption with the exact selected dates on hover, focus or tap. */
export default function PeriodLabel({ children, range }: PeriodLabelProps) {
  const [open, setOpen] = useState(false);
  const openAtPointerDown = useRef(false);
  const { t } = useTranslation('dashboard');
  const { date } = useDateFormatter();

  if (!range) return <>{children}</>;

  return (
    <TooltipProvider delayDuration={200}>
      <Tooltip open={open} onOpenChange={setOpen}>
        <TooltipTrigger asChild>
          <button
            type="button"
            className="cursor-help text-left rounded-sm underline decoration-dotted decoration-text-muted/60 underline-offset-4 hover:decoration-text-secondary focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-primary"
            onPointerDown={() => {
              openAtPointerDown.current = open;
            }}
            onClick={event => {
              // Touch focus can open the tooltip before click; toggle from pointer-down.
              event.preventDefault();
              setOpen(event.detail === 0 ? !open : !openAtPointerDown.current);
            }}
          >
            {children}
          </button>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs text-xs">
          {t('period.selectedRange', { from: date(range.from), to: date(range.to) })}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}
