import type { ReactNode } from 'react';
import { Button } from '@/components/ui/Button';

export function FilterChoice({
  children,
  selected,
  onClick,
}: {
  children: ReactNode;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <Button
      type="button"
      size="sm"
      variant="ghost"
      aria-pressed={selected}
      onClick={onClick}
      className="min-w-0 max-w-full whitespace-normal break-words rounded-md border border-transparent aria-pressed:border-primary/50 aria-pressed:bg-primary/10 aria-pressed:text-primary"
    >
      {children}
    </Button>
  );
}
