import { useEffect, useLayoutEffect, useRef, type ReactNode } from 'react';
import { ChevronDown } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { HelpTooltip } from '@/components/ui/HelpTooltip';

/** Keep optional values mounted, and reveal validation errors before focusing them. */
export function OptionalDetails({
  title,
  description,
  hasErrors = false,
  defaultOpen = false,
  activeCount = 0,
  children,
}: {
  title: string;
  description?: string;
  hasErrors?: boolean;
  defaultOpen?: boolean;
  activeCount?: number;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDetailsElement>(null);
  const { t } = useTranslation('common');
  useLayoutEffect(() => {
    if (hasErrors && ref.current) ref.current.open = true;
  }, [hasErrors]);
  useEffect(() => {
    const details = ref.current;
    const form = details?.closest('form');
    const revealErrors = (): void => {
      if (details && (hasErrors || details.querySelector('[aria-invalid="true"]'))) {
        details.open = true;
      }
    };
    form?.addEventListener('submit', revealErrors, true);
    return () => form?.removeEventListener('submit', revealErrors, true);
  }, [hasErrors]);

  return (
    <div className="relative">
      <details ref={ref} open={defaultOpen || undefined} className="optional-details group">
        <summary className="flex min-h-11 cursor-pointer list-none items-center gap-3 rounded-lg py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary md:min-h-10 [&::-webkit-details-marker]:hidden">
          <span className="min-w-0 flex-1">
            <span className="font-medium text-text-primary">{title}</span>
          </span>
          {hasErrors ? (
            <span className="text-xs font-medium text-error">{t('formLayout.needsAttention')}</span>
          ) : activeCount > 0 ? (
            <span className="shrink-0 rounded-md bg-primary/10 px-2 py-1 text-xs font-medium tabular-nums text-primary">
              {t('formLayout.activeCount', { count: activeCount })}
            </span>
          ) : null}
          {description && <span className="w-3.5 shrink-0" aria-hidden="true" />}
          <ChevronDown
            className="h-4 w-4 shrink-0 text-text-tertiary transition-transform duration-200 group-open:rotate-180 motion-reduce:transition-none"
            aria-hidden="true"
          />
        </summary>
        <div className="optional-details-content space-y-3">{children}</div>
      </details>
      {description && (
        <HelpTooltip text={description} className="absolute right-7 top-3.5 md:top-3" />
      )}
    </div>
  );
}
