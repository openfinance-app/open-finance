import type { ComponentPropsWithoutRef, ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { FormActions } from '@/components/ui/FormActions';

interface FinanceFormProps extends ComponentPropsWithoutRef<'form'> {
  actions: ReactNode;
  error?: string | null;
}

export function FinanceForm({ children, actions, error, className, ...props }: FinanceFormProps) {
  return (
    <form {...props} className={cn('finance-form', className)}>
      <div className="finance-form-fields">{children}</div>
      <FormActions error={error}>{actions}</FormActions>
    </form>
  );
}
