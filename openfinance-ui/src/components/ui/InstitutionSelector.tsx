/**
 * InstitutionSelector component
 * 
 * A dropdown component for selecting financial institutions with search functionality.
 * Supports grouping by country and displays institution logo.
 */

import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { useInstitutions } from '@/hooks/useInstitutions';
import { Loader2, Search, Building2 } from 'lucide-react';
import type { Institution } from '@/types/institution';
import { cn } from '@/lib/utils';

interface InstitutionSelectorProps {
  value?: string;
  onValueChange: (value: string | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /**
   * Show "None" option at the top
   */
  allowNone?: boolean;
}

/**
 * Country code to display name mapping
 */
const COUNTRY_NAMES: Record<string, string> = {
  FR: 'France',
  DE: 'Germany',
  ES: 'Spain',
  IT: 'Italy',
  NL: 'Netherlands',
  BE: 'Belgium',
  AT: 'Austria',
  US: 'United States',
  UK: 'United Kingdom',
  MT: 'Malta',
  PL: 'Poland',
};

export function InstitutionSelector({
  value,
  onValueChange,
  placeholder = 'Select institution',
  disabled = false,
  className,
  allowNone = true,
}: InstitutionSelectorProps) {
  const { t } = useTranslation(['institutions', 'common']);
  const { data: institutions, isLoading, isError } = useInstitutions();
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // Filter and group institutions by country
  const groupedInstitutions = useMemo(() => {
    if (!institutions) return {};

    const normalizedQuery = searchQuery.trim().toLowerCase();
    const filtered = normalizedQuery
      ? institutions.filter(
        (inst) =>
          inst.name.toLowerCase().includes(normalizedQuery) ||
          inst.bic?.toLowerCase().includes(normalizedQuery)
      )
      : institutions;

    // Group by country
    const groups: Record<string, Institution[]> = {};
    filtered.forEach((inst) => {
      const country = inst.country || 'OTHER';
      if (!groups[country]) {
        groups[country] = [];
      }
      groups[country].push(inst);
    });

    // Sort each group by name
    Object.keys(groups).forEach((country) => {
      groups[country].sort((a, b) => a.name.localeCompare(b.name));
    });

    return groups;
  }, [institutions, searchQuery]);

  // Get sorted country codes
  const sortedCountries = useMemo(() => {
    return Object.keys(groupedInstitutions).sort((a, b) => {
      // Sort by country name using COUNTRY_NAMES
      const nameA = COUNTRY_NAMES[a] || a;
      const nameB = COUNTRY_NAMES[b] || b;
      return nameA.localeCompare(nameB);
    });
  }, [groupedInstitutions]);

  const selectedInstitution = value
    ? institutions?.find((inst) => inst.id.toString() === value)
    : undefined;

  // Render logo
  const renderLogo = (logo?: string, size: 'sm' | 'md' = 'md') => {
    const sizeClasses = size === 'sm' ? 'h-6 w-6' : 'h-8 w-8';
    if (logo) {
      return (
        <img
          src={logo}
          alt=""
          className={cn('rounded object-contain bg-white', sizeClasses)}
        />
      );
    }
    return (
      <div
        className={cn(
          'flex items-center justify-center rounded bg-primary/10',
          sizeClasses
        )}
      >
        <Building2 className="h-4 w-4 text-primary" />
      </div>
    );
  };

  // Handle selection - use a hidden input to help track value
  const handleValueChange = (val: string) => {
    if (val === '__none__') {
      onValueChange(undefined);
    } else {
      onValueChange(val);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-border bg-surface px-3 py-2">
        <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
        <span className="ml-2 text-sm text-text-muted">Loading institutions...</span>
      </div>
    );
  }

  if (isError || !institutions) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-error bg-surface px-3 py-2">
        <span className="text-sm text-error">Failed to load institutions</span>
      </div>
    );
  }

  return (
    <Select
      value={value || '__none__'}
      onValueChange={handleValueChange}
      disabled={disabled}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (!open) {
          setSearchQuery('');
        }
      }}
    >
      <SelectTrigger className={className}>
        <SelectValue placeholder={placeholder}>
          {value && selectedInstitution ? (
            <span className="flex items-center gap-2">
              {renderLogo(selectedInstitution.logo, 'sm')}
              <span>{selectedInstitution.name}</span>
              {selectedInstitution.country && (
                <span className="text-text-muted">
                  ({COUNTRY_NAMES[selectedInstitution.country] || selectedInstitution.country})
                </span>
              )}
            </span>
          ) : value === undefined && allowNone ? (
            <span className="text-text-muted">{t('common:none')}</span>
          ) : (
            placeholder
          )}
        </SelectValue>
      </SelectTrigger>
      <SelectContent 
        className="p-0 flex flex-col" 
        viewportClassName="p-1"
        headerSlot={
          <div className="shrink-0 border-b border-border bg-surface p-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                type="text"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                onKeyDown={(event) => event.stopPropagation()}
                placeholder={t('institutions:searchInstitution')}
                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                autoFocus={isOpen}
              />
            </div>
          </div>
        }
      >
        {/* "None" option - use SelectItem with special value */}
        {allowNone && (
          <SelectItem value="__none__">
            <div className="flex items-center gap-2">
              {/* Logo in place of checkmark */}
              <span className="flex h-4 w-4 items-center justify-center shrink-0">
                <Building2 className="h-4 w-4 text-text-muted" />
              </span>
              <span className="text-text-muted">{t('common:none')}</span>
            </div>
          </SelectItem>
        )}

        {/* Grouped institutions using SelectItem */}
        {sortedCountries.map((country) => (
          <div key={country} className="mt-2">
            {/* Country header */}
            <div className="px-2 py-1 text-xs font-semibold text-text-muted">
              {COUNTRY_NAMES[country] || country}
            </div>

            {/* Institutions in this country */}
            {groupedInstitutions[country].map((inst) => (
              <SelectItem
                key={inst.id}
                value={inst.id.toString()}
              >
                <div className="flex items-center gap-2">
                  {/* Logo in place of checkmark */}
                  <span className="flex h-4 w-4 items-center justify-center shrink-0">
                    {inst.logo ? (
                      <img
                        src={inst.logo}
                        alt=""
                        className="h-5 w-5 rounded object-contain bg-white"
                      />
                    ) : (
                      <Building2 className="h-4 w-4 text-primary" />
                    )}
                  </span>
                  <div className="flex flex-col">
                    <span className="text-sm">{inst.name}</span>
                    {inst.bic && (
                      <span className="text-xs text-text-muted">{inst.bic}</span>
                    )}
                  </div>
                </div>
              </SelectItem>
            ))}
          </div>
        ))}

        {/* Empty state */}
        {sortedCountries.length === 0 && (
          <div className="p-2 text-center text-sm text-text-muted">
            No institutions match your search
          </div>
        )}
      </SelectContent>
    </Select>
  );
}
