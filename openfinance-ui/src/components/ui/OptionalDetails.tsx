import { useEffect, useRef, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

/** Keep optional values mounted, and reveal validation errors before focusing them. */
export function OptionalDetails({
  title,
  description,
  hasErrors = false,
  children,
}: {
  title: string;
  description?: string;
  hasErrors?: boolean;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDetailsElement>(null);
  useEffect(() => {
    if (hasErrors && ref.current) ref.current.open = true;
  }, [hasErrors]);

  return (
    <div className="relative">
      <details ref={ref} className="group rounded-xl border border-border bg-surface">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4 text-sm font-medium [&::-webkit-details-marker]:hidden">
          <span className={description ? 'pr-8' : undefined}>{title}</span>
          <ChevronDown
            className="h-4 w-4 shrink-0 transition-transform group-open:rotate-180"
            aria-hidden="true"
          />
        </summary>
        <div className="space-y-4 border-t border-border p-4">{children}</div>
      </details>
      {description && (
        <HelpTooltip text={description} className="absolute right-11 top-[1.125rem]" />
      )}
    </div>
  );
}
