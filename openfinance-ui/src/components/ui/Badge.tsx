import React from 'react';
import { cn } from '@/lib/utils';

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?:
    | 'default'
    | 'success'
    | 'error'
    | 'warning'
    | 'info'
    | 'secondary'
    | 'destructive'
    | 'outline';
  size?: 'sm' | 'md' | 'lg';
  children: React.ReactNode;
}

/**
 * Badge component for status indicators and labels
 * Supports multiple variants with appropriate color coding
 */
export const Badge = React.forwardRef<HTMLDivElement, BadgeProps>(
  ({ className, variant = 'default', size = 'md', children, ...props }, ref) => {
    const baseStyles =
      'inline-flex items-center justify-center font-medium rounded-md whitespace-nowrap transition-colors tracking-wide';

    const variants = {
      default: 'bg-surface-elevated text-text-primary border border-border-strong',
      success: 'bg-success/10 text-success border border-success/30',
      error: 'bg-error/10 text-error border border-error/30',
      warning: 'bg-warning/10 text-warning border border-warning/30',
      info: 'bg-accent-blue/10 text-accent-blue border border-accent-blue/30',
      secondary: 'bg-surface text-text-secondary border border-border',
      destructive: 'bg-destructive/10 text-destructive border border-destructive/30',
      outline: 'border border-border-strong bg-transparent text-text-primary',
    };

    const sizes = {
      sm: 'px-2 py-0.5 text-xs',
      md: 'px-2.5 py-1 text-sm',
      lg: 'px-3 py-1.5 text-base',
    };

    return (
      <div
        ref={ref}
        className={cn(baseStyles, variants[variant], sizes[size], className)}
        {...props}
      >
        {children}
      </div>
    );
  }
);

Badge.displayName = 'Badge';
