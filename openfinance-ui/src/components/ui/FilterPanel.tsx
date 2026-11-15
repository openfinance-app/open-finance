import type { ReactNode } from 'react';
import { RotateCcw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';
import { OptionalDetails } from '@/components/ui/OptionalDetails';

export function FilterPanel({
  children,
  additional,
  additionalCount = 0,
  additionalDescription,
  activeCount,
  onClear,
}: {
  children: ReactNode;
  additional?: ReactNode;
  additionalCount?: number;
  additionalDescription?: string;
  activeCount: number;
  onClear: () => void;
}) {
  const { t } = useTranslation('common');
  return (
    <section
      aria-label={t('formLayout.filterResults')}
      className="filter-panel rounded-xl border border-border bg-surface p-4 sm:p-5"
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h2 className="text-sm font-semibold text-text-primary">
            {t('formLayout.filterResults')}
          </h2>
          <span className="text-xs text-text-secondary" aria-live="polite">
            {activeCount > 0
              ? t('formLayout.activeCount', { count: activeCount })
              : t('formLayout.allResults')}
          </span>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={activeCount === 0}
          onClick={onClear}
          className="shrink-0 gap-1.5 text-text-secondary"
        >
          <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
          {t('formLayout.reset')}
        </Button>
      </div>
      {children}
      {additional && (
        <div className="mt-4">
          <OptionalDetails
            title={t('formLayout.moreFilters')}
            description={additionalDescription}
            activeCount={additionalCount}
          >
            {additional}
          </OptionalDetails>
        </div>
      )}
    </section>
  );
}
