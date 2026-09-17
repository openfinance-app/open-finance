import React from 'react';
import { cn } from '@/lib/utils';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'outline' | 'default' | 'destructive';
  size?: 'sm' | 'md' | 'lg' | 'icon';
  children: React.ReactNode;
  isLoading?: boolean;
}

/**
 * Button component with multiple variants and sizes
 * Follows Finary design style with gold primary color
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    { className, variant = 'primary', size = 'md', children, isLoading, disabled, ...props },
    ref
  ) => {
    const baseStyles =
      'inline-flex items-center justify-center rounded-lg font-medium transition-all duration-150 ease-in-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50';

    const variants = {
      primary: 'bg-primary text-background hover:bg-primary/90 active:bg-primary/80',
      secondary: 'bg-surface hover:bg-surface-elevated text-text-primary border border-border',
      ghost: 'bg-transparent hover:bg-surface text-text-primary',
      danger: 'bg-error text-white hover:bg-error/90 active:bg-error/80',
      destructive: 'bg-destructive text-white hover:bg-destructive/90 active:bg-destructive/80',
      outline:
        'border border-border bg-transparent hover:bg-surface hover:text-text-primary text-text-primary',
      default: 'bg-primary text-background hover:bg-primary/90 active:bg-primary/80',
    };

    const sizes = {
      sm: 'min-h-[44px] h-10 px-3 text-sm md:h-8 md:min-h-0', // 44px on mobile/tablet, 32px on desktop
      md: 'min-h-[44px] h-11 px-4 text-base md:h-10 md:min-h-0', // 44px on mobile/tablet, 40px on desktop
      lg: 'h-12 px-6 text-lg', // 48px meets minimum on all devices
      icon: 'min-h-[44px] min-w-[44px] h-11 w-11 p-0 md:h-10 md:w-10 md:min-h-0 md:min-w-0', // 44x44 on mobile/tablet, 40x40 on desktop
    };

    return (
      <button
        ref={ref}
        className={cn(
          baseStyles,
          variants[variant],
          sizes[size],
          isLoading && 'opacity-70 cursor-wait',
          className
        )}
        disabled={disabled || isLoading}
        {...props}
      >
        {isLoading && (
          <svg
            className="mr-2 h-4 w-4 animate-spin"
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
        )}
        {children}
      </button>
    );
  }
);

Button.displayName = 'Button';
