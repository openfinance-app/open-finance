import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { cn } from '@/lib/utils';

export function ActiveFilterRow({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  const { t } = useTranslation('common');
  return (
    <div
      role="group"
      aria-label={t('formLayout.activeFilters')}
      className={cn('flex flex-wrap gap-2 empty:hidden', className)}
    >
      {children}
    </div>
  );
}

export function FilterChip({
  label,
  to,
  onRemove,
}: {
  label: string;
  to?: string;
  onRemove: () => void;
}) {
  const { t } = useTranslation('common');
  const removeLabel = t('formLayout.removeFilter', { name: label });
  const chip =
    'inline-flex min-h-11 max-w-full items-center rounded-md border border-primary/50 bg-primary/10 text-sm font-medium text-primary md:min-h-9';
  const focus =
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background';
  const icon = <X className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />;

  if (to) {
    return (
      <div className={chip}>
        <Link
          to={to}
          className={cn(
            'flex min-w-0 items-center self-stretch rounded-l-md pl-3 text-start hover:underline',
            focus
          )}
        >
          <span className="break-words">{label}</span>
        </Link>
        <button
          type="button"
          aria-label={removeLabel}
          onClick={onRemove}
          className={cn(
            'inline-flex w-11 shrink-0 items-center justify-center self-stretch rounded-r-md hover:bg-primary/20 md:w-9',
            focus
          )}
        >
          {icon}
        </button>
      </div>
    );
  }

  return (
    <button
      type="button"
      aria-label={removeLabel}
      onClick={onRemove}
      className={cn(chip, 'gap-2 px-3 text-start hover:bg-primary/20', focus)}
    >
      <span className="min-w-0 break-words">{label}</span>
      {icon}
    </button>
  );
}
