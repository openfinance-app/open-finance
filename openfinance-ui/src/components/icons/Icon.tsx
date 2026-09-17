import * as LucideIcons from 'lucide-react';
import { type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface IconProps {
  icon?: LucideIcon;
  name?: string;
  size?: 16 | 20 | 24 | 32;
  className?: string;
}

/**
 * Icon wrapper component for lucide-react icons
 * Provides consistent sizing: 16px, 20px, 24px, 32px
 */
export function Icon({ icon, name, size = 20, className }: IconProps) {
  let IconComponent: LucideIcon | undefined = icon;

  if (!IconComponent && name) {
    const icons = LucideIcons.icons as Partial<Record<string, LucideIcon>>;
    IconComponent =
      icons[name] || icons[name.charAt(0).toUpperCase() + name.slice(1)] || LucideIcons.HelpCircle;
  }

  if (!IconComponent) {
    IconComponent = LucideIcons.HelpCircle;
  }

  return <IconComponent size={size} className={cn('inline-block', className)} aria-hidden="true" />;
}
