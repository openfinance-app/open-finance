/**
 * RegexToggle
 *
 * Small icon button used inside search inputs to let the user switch a keyword
 * search field between plain-text ("contains") matching and regular-expression
 * matching. Meant to be placed absolutely inside the same relative container as
 * the search `Input` (to the left of any existing clear/spinner icon).
 */
import { Regex } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';

export interface RegexToggleProps {
  /** Whether regex mode is currently enabled. */
  enabled: boolean;
  /** Called with the new value when the user toggles regex mode. */
  onChange: (enabled: boolean) => void;
  /** Additional classes for positioning within the parent input wrapper. */
  className?: string;
}

export function RegexToggle({ enabled, onChange, className }: RegexToggleProps) {
  const { t } = useTranslation('common');

  return (
    <button
      type="button"
      aria-pressed={enabled}
      aria-label={t('regexSearch.toggleLabel')}
      title={enabled ? t('regexSearch.enabledTooltip') : t('regexSearch.disabledTooltip')}
      onClick={() => onChange(!enabled)}
      className={cn(
        'flex items-center justify-center h-6 w-6 rounded transition-colors',
        enabled
          ? 'bg-primary/20 text-primary'
          : 'text-text-tertiary hover:text-text-primary hover:bg-surface-elevated',
        className
      )}
    >
      <Regex className="h-4 w-4" />
    </button>
  );
}
