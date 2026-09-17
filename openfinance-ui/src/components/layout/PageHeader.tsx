import { type ReactNode } from 'react';
import { cn } from '@/lib/utils';

export interface PageHeaderProps {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  className?: string;
}

/**
 * PageHeader component for consistent page titles
 * Displays title, optional description, and optional action buttons
 */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <div className={cn('flex items-start justify-between gap-4 mb-6', className)}>
      <div className="flex-1 min-w-0">
        <h1 className="text-2xl font-bold text-text-primary mb-1">{title}</h1>
        {description && <p className="text-sm text-text-secondary">{description}</p>}
      </div>

      {actions && <div className="flex items-center gap-2 flex-shrink-0">{actions}</div>}
    </div>
  );
}
