import React from 'react';
import { cn } from '@/lib/utils';

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'destructive' | 'error' | 'success' | 'warning' | 'info';
  children: React.ReactNode;
}

export const Alert = React.forwardRef<HTMLDivElement, AlertProps>(
  ({ className, variant = 'default', children, ...props }, ref) => {
    const variantStyles = {
      default: 'bg-background text-foreground border-border',
      destructive:
        'border-destructive/50 text-destructive border-destructive [&>svg]:text-destructive',
      error: 'border-red-500/50 text-red-500 border-red-500 [&>svg]:text-red-500',
      success: 'border-green-500/50 text-green-600 border-green-500 [&>svg]:text-green-600',
      warning: 'border-yellow-500/50 text-yellow-600 border-yellow-500 [&>svg]:text-yellow-600',
      info: 'border-blue-500/50 text-blue-500 border-blue-500 [&>svg]:text-blue-500',
    };

    return (
      <div
        ref={ref}
        role="alert"
        className={cn(
          'relative w-full rounded-lg border p-4 [&>svg~*]:pl-7 [&>svg+div]:translate-y-[-3px] [&>svg]:absolute [&>svg]:left-4 [&>svg]:top-4 [&>svg]:text-foreground',
          variantStyles[variant] || variantStyles.default,
          className
        )}
        {...props}
      >
        {children}
      </div>
    );
  }
);
Alert.displayName = 'Alert';

export const AlertDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <div ref={ref} className={cn('text-sm [&_p]:leading-relaxed', className)} {...props} />
));
AlertDescription.displayName = 'AlertDescription';
