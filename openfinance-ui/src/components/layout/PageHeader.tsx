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
 * The Vault: the page title is an engraved plate heading (Marcellus caps)
 */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <div
      className={cn(
        'flex flex-col sm:flex-row sm:items-start sm:justify-between gap-3 sm:gap-4 mb-6',
        className
      )}
    >
      <div className="flex-1 min-w-0">
        <h1 className="font-display text-[22px] leading-7 lg:text-[26px] lg:leading-8 uppercase tracking-[0.08em] text-text-primary mb-1 [text-wrap:balance]">
          {title}
        </h1>
        {description && <p className="text-sm text-text-secondary">{description}</p>}
      </div>

      {actions && <div className="flex flex-wrap items-center gap-2 sm:justify-end">{actions}</div>}
    </div>
  );
}
