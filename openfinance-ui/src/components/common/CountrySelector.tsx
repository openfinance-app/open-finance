/**
 * CountrySelector — shared searchable dropdown for all ISO 3166-1 alpha-2 countries.
 *
 * Extracted from GeneralSettings.tsx and OnboardingPage.tsx to avoid duplication.
 * Uses the full ALL_COUNTRIES list from countryUtils so every country (including
 * Switzerland, CH) is available.
 */
import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, Search } from 'lucide-react';
import * as PopoverPrimitive from '@radix-ui/react-popover';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { ALL_COUNTRIES, countryFlagClass } from '@/utils/countryUtils';

export { ALL_COUNTRIES } from '@/utils/countryUtils';

export interface CountrySelectorProps {
  value: string;
  onValueChange: (value: string) => void;
  disabled?: boolean;
  placeholder?: string;
  searchPlaceholder?: string;
  noMatch?: string;
  className?: string;
}

export function CountrySelector({
  value,
  onValueChange,
  disabled = false,
  placeholder,
  searchPlaceholder,
  noMatch,
  className,
}: CountrySelectorProps) {
  const { t } = useTranslation('common');
  const resolvedPlaceholder = placeholder ?? t('country.selectCountry');
  const resolvedSearchPlaceholder = searchPlaceholder ?? t('country.searchCountry');
  const resolvedNoMatch = noMatch ?? t('country.noMatch');
  const [searchQuery, setSearchQuery] = useState('');
  const [open, setOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);

  // Focus the search input as soon as the popover opens
  useEffect(() => {
    if (open) {
      const id = requestAnimationFrame(() => searchRef.current?.focus());
      return () => cancelAnimationFrame(id);
    } else {
      setSearchQuery('');
    }
  }, [open]);

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const visibleCountries = normalizedQuery
    ? ALL_COUNTRIES.filter(
        (c) =>
          c.name.toLowerCase().includes(normalizedQuery) ||
          c.code.toLowerCase().includes(normalizedQuery)
      )
    : ALL_COUNTRIES;

  const selectedCountry = ALL_COUNTRIES.find((c) => c.code === value);

  const handleSelect = (code: string) => {
    onValueChange(code);
    setOpen(false);
  };

  return (
    <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Trigger asChild>
        <button
          type="button"
          disabled={disabled}
          aria-label={resolvedPlaceholder}
          className={cn(
            'flex h-10 w-full items-center justify-between rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary',
            'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background',
            'disabled:cursor-not-allowed disabled:opacity-50 transition-all duration-150',
            className
          )}
        >
          {selectedCountry ? (
            <span className="flex items-center gap-2">
              <span
                className={`${countryFlagClass(selectedCountry.code)} text-lg`}
                style={{ width: '1.33em', lineHeight: 1 }}
              />
              <span>{selectedCountry.name}</span>
            </span>
          ) : (
            <span className="text-text-muted">{resolvedPlaceholder}</span>
          )}
          <ChevronDown className="h-4 w-4 opacity-50 shrink-0 ml-2" />
        </button>
      </PopoverPrimitive.Trigger>

      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={4}
          style={{
            minWidth: 'var(--radix-popover-trigger-width)',
            width: 'max-content',
            maxWidth: '420px',
            maxHeight: 'var(--radix-popover-content-available-height)',
          }}
          className={cn(
            'z-[100] flex flex-col overflow-hidden rounded-lg border border-border bg-surface text-text-primary shadow-md',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
            'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95'
          )}
          onOpenAutoFocus={(e) => e.preventDefault()}
        >
          {/* Search */}
          <div className="shrink-0 border-b border-border p-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                ref={searchRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') setOpen(false);
                }}
                placeholder={resolvedSearchPlaceholder}
                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                autoComplete="off"
              />
            </div>
          </div>

          {/* Options list */}
          <div
            className="flex-1 overflow-y-auto p-1 shrink min-h-0 max-h-72"
            onWheel={(e) => e.stopPropagation()}
            onTouchMove={(e) => e.stopPropagation()}
          >
            {visibleCountries.length === 0 ? (
              <div className="p-3 text-center text-sm text-text-muted">{resolvedNoMatch}</div>
            ) : (
              visibleCountries.map((country) => (
                <button
                  key={country.code}
                  type="button"
                  onClick={() => handleSelect(country.code)}
                  className={cn(
                    'flex w-full cursor-default items-center gap-2 rounded-md px-3 py-2 text-sm outline-none',
                    'hover:bg-surface-elevated focus:bg-surface-elevated',
                    value === country.code && 'bg-surface-elevated'
                  )}
                >
                  <span className="w-4 shrink-0 flex items-center justify-center">
                    {value === country.code && <Check className="h-4 w-4 text-primary" />}
                  </span>
                  <span
                    className={`${countryFlagClass(country.code)} text-base`}
                    style={{ width: '1.33em', lineHeight: 1 }}
                  />
                  <span>{country.name}</span>
                </button>
              ))
            )}
          </div>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
