import React from 'react';
import { cn } from '@/lib/utils';

export interface LoadingSkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  width?: string | number;
  height?: string | number;
  variant?: 'text' | 'circular' | 'rectangular';
}

/**
 * LoadingSkeleton component with shimmer effect
 * Uses gold accent color for shimmer animation
 */
export const LoadingSkeleton = React.forwardRef<HTMLDivElement, LoadingSkeletonProps>(
  ({ className, width, height, variant = 'rectangular', ...props }, ref) => {
    const variantStyles = {
      text: 'h-4 rounded',
      circular: 'rounded-full',
      rectangular: 'rounded-lg',
    };

    const style = {
      ...(width && { width: typeof width === 'number' ? `${width}px` : width }),
      ...(height && { height: typeof height === 'number' ? `${height}px` : height }),
    };

    return (
      <div
        ref={ref}
        className={cn('bg-surface-elevated shimmer', variantStyles[variant], className)}
        style={style}
        {...props}
      />
    );
  }
);

LoadingSkeleton.displayName = 'LoadingSkeleton';

export interface LoadingSpinnerProps extends React.HTMLAttributes<HTMLDivElement> {
  size?: 'sm' | 'md' | 'lg' | 'xl';
}

/**
 * LoadingSpinner component with rotating gold ring animation
 */
export const LoadingSpinner = React.forwardRef<HTMLDivElement, LoadingSpinnerProps>(
  ({ className, size = 'md', ...props }, ref) => {
    const sizes = {
      sm: 'h-4 w-4',
      md: 'h-8 w-8',
      lg: 'h-12 w-12',
      xl: 'h-16 w-16',
    };

    return (
      <div
        ref={ref}
        className={cn('inline-block', className)}
        role="status"
        aria-label="Loading"
        {...props}
      >
        <svg
          className={cn('animate-spin text-primary', sizes[size])}
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
        <span className="sr-only">Loading...</span>
      </div>
    );
  }
);

export const FullPageSpinner = () => (
  <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center">
    <LoadingSpinner size="lg" />
  </div>
);
FullPageSpinner.displayName = 'FullPageSpinner';
