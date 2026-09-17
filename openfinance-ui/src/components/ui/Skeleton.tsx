import React from 'react';
import { cn } from '@/lib/utils';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {}

/**
 * Skeleton component for loading states.
 * Neutral fill with a slow amber shimmer sweep; motion removed
 * under prefers-reduced-motion (fill remains visible).
 */
export const Skeleton = React.forwardRef<HTMLDivElement, SkeletonProps>(
  ({ className, ...props }, ref) => {
    return (
      <div
        ref={ref}
        aria-hidden="true"
        className={cn('relative overflow-hidden rounded-md bg-muted skeleton-sweep', className)}
        {...props}
      />
    );
  }
);

Skeleton.displayName = 'Skeleton';
