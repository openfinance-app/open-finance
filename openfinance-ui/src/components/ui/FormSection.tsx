import { useId, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

/** Group related fields without repeating their labels in a visual heading. */
export function FormSection({
  title,
  children,
  className,
}: {
  title: string;
  children: ReactNode;
  className?: string;
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={cn('form-section', className)}>
      <h3 id={id} className="sr-only">
        {title}
      </h3>
      <div className="space-y-3">{children}</div>
    </section>
  );
}
