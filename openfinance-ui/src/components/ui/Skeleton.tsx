import React from 'react';
import { cn } from '@/lib/utils';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {}

/**
 * Skeleton component for loading states
 */
export const Skeleton = React.forwardRef<HTMLDivElement, SkeletonProps>(
  ({ className, ...props }, ref) => {
    return (
      <div ref={ref} className={cn('animate-pulse rounded-md bg-muted', className)} {...props} />
    );
  }
);

Skeleton.displayName = 'Skeleton';
