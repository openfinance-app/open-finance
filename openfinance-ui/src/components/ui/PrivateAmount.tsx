/**
 * PrivateAmount - Component that wraps financial amounts with privacy blur
 *
 * Automatically blurs content when amounts visibility is toggled off,
 * with smooth animation transitions.
 */
import { useVisibility } from '@/context/VisibilityContext';
import { cn } from '@/lib/utils';
import type { ReactNode } from 'react';

interface PrivateAmountProps {
  children: ReactNode;
  /** Additional CSS classes */
  className?: string;
  /** Whether to use inline display (default: false) */
  inline?: boolean;
}

/**
 * PrivateAmount component
 * Wraps financial amounts and applies blur effect when visibility is toggled off
 */
export function PrivateAmount({ children, className, inline = false }: PrivateAmountProps) {
  const { isAmountsVisible } = useVisibility();

  return (
    <span
      className={cn(
        'transition-all duration-300 ease-in-out',
        !isAmountsVisible && 'blur-md select-none',
        inline ? 'inline-block' : 'block',
        className
      )}
      aria-hidden={!isAmountsVisible}
    >
      {children}
    </span>
  );
}
