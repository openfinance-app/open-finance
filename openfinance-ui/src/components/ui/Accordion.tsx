import * as React from 'react';
import { cn } from '@/lib/utils';
import { ChevronDown } from 'lucide-react';

/**
 * Accordion components for collapsible content
 */

type AccordionContextType = {
  value?: string;
  onValueChange?: (value: string) => void;
};

const AccordionContext = React.createContext<AccordionContextType>({});

const Accordion = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement> & {
    value?: string;
    onValueChange?: (value: string) => void;
    defaultValue?: string;
  }
>(({ className, defaultValue, value, onValueChange, ...props }, ref) => {
  const [internalValue, setInternalValue] = React.useState(defaultValue);
  const currentValue = value !== undefined ? value : internalValue;

  const handleValueChange = React.useCallback(
    (newValue: string) => {
      if (value === undefined) {
        setInternalValue(newValue);
      }
      onValueChange?.(newValue);
    },
    [value, onValueChange]
  );

  return (
    <AccordionContext.Provider value={{ value: currentValue, onValueChange: handleValueChange }}>
      <div ref={ref} className={cn('', className)} {...props} />
    </AccordionContext.Provider>
  );
});
Accordion.displayName = 'Accordion';

const AccordionItem = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement> & { value: string }
>(({ className, value, children, ...props }, ref) => {
  const context = React.useContext(AccordionContext);
  const isOpen = context.value === value;

  return (
    <div ref={ref} className={cn('border-b border-border', className)} {...props}>
      {React.Children.map(children, child => {
        if (React.isValidElement(child)) {
          return React.cloneElement(child, {
            isOpen,
            onToggle: () => context.onValueChange?.(context.value === value ? '' : value),
          } as any);
        }
        return child;
      })}
    </div>
  );
});
AccordionItem.displayName = 'AccordionItem';

const AccordionTrigger = React.forwardRef<
  HTMLButtonElement,
  React.ButtonHTMLAttributes<HTMLButtonElement> & {
    isOpen?: boolean;
    onToggle?: () => void;
  }
>(({ className, children, isOpen, onToggle, ...props }, ref) => (
  <div className="flex">
    <button
      ref={ref}
      type="button"
      className={cn(
        'flex flex-1 items-center justify-between py-4 font-medium transition-all hover:underline',
        className
      )}
      onClick={onToggle}
      {...props}
    >
      {children}
      <ChevronDown
        className={cn(
          'h-4 w-4 shrink-0 transition-transform duration-200',
          isOpen ? 'rotate-180' : ''
        )}
      />
    </button>
  </div>
));
AccordionTrigger.displayName = 'AccordionTrigger';

const AccordionContent = React.forwardRef<
  HTMLDivElement,
  React.HTMLAttributes<HTMLDivElement> & { isOpen?: boolean }
>(({ className, children, isOpen, ...props }, ref) => (
  <div
    ref={ref}
    className={cn(
      'overflow-hidden text-sm transition-all duration-300 ease-in-out',
      isOpen ? 'max-h-[2000px] opacity-100' : 'max-h-0 opacity-0',
      className
    )}
    {...props}
  >
    <div className="pb-4 pt-0">{children}</div>
  </div>
));
AccordionContent.displayName = 'AccordionContent';

export { Accordion, AccordionItem, AccordionTrigger, AccordionContent };
