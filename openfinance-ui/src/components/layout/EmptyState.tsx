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
        'py-12 px-4 text-center',
        className
      )}
    >
      {Icon && (
        <div className="mb-4 p-4 rounded-full bg-surface">
          <Icon size={32} className="text-text-secondary" />
        </div>
      )}

      <h3 className="text-lg font-semibold text-text-primary mb-2">{title}</h3>

      {description && <p className="text-sm text-text-secondary mb-6 max-w-md">{description}</p>}

      {action && (
        <Button variant="primary" onClick={action.onClick}>
          {action.label}
        </Button>
      )}

      {children}
    </div>
  );
}
