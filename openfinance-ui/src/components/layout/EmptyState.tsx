import { type ReactNode } from 'react';
import { type LucideIcon } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils';

export interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  description?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
  className?: string;
  children?: ReactNode;
}

/**
 * EmptyState component for displaying when no data is available
 * Shows icon, title, description, and optional CTA button
 */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
  children,
}: EmptyStateProps) {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center',
        'py-16 px-4 text-center',
        className
      )}
    >
      {Icon && (
        <div
          className="mb-5 flex h-16 w-16 items-center justify-center rounded-xl bg-surface-elevated border border-border-strong shadow-slot"
          aria-hidden="true"
        >
          <Icon size={28} strokeWidth={1.75} className="text-primary/80" />
        </div>
      )}

      <h3 className="font-display text-lg uppercase tracking-[0.06em] text-text-primary mb-2">
        {title}
      </h3>

      {description && (
        <p className="text-sm text-text-secondary mb-6 max-w-sm leading-relaxed">{description}</p>
      )}

      {action && (
        <Button variant="primary" onClick={action.onClick}>
          {action.label}
        </Button>
      )}

      {children}
    </div>
  );
}
