import { Filter } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';

interface FilterToggleProps {
  label: string;
  panelId: string;
  expanded: boolean;
  active: boolean;
  onToggle: () => void;
  size?: 'sm' | 'md';
}

export function FilterToggle({
  label,
  panelId,
  expanded,
  active,
  onToggle,
  size,
}: FilterToggleProps) {
  const { t } = useTranslation('common');
  return (
    <Button
      type="button"
      variant={expanded || active ? 'primary' : 'outline'}
      size={size}
      aria-expanded={expanded}
      aria-controls={expanded ? panelId : undefined}
      aria-description={active ? t('formLayout.activeFilters') : undefined}
      onClick={onToggle}
    >
      <Filter aria-hidden="true" className="mr-2 h-4 w-4" />
      {label}
    </Button>
  );
}
