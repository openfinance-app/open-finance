/**
 * CurrencySelector component
 * Sprint 6 - Task 6.2.11: Multi-currency support
 *
 * A dropdown component for selecting currencies with symbol and name display.
 * Built on Radix Popover (not Select) so the search <input> never loses focus
 * while the user types — Radix Select's focus trap steals focus on every
 * re-render inside its Viewport.
 */

import { useEffect, useRef, useState } from 'react';
import * as PopoverPrimitive from '@radix-ui/react-popover';
import { useTranslation } from 'react-i18next';
import { useCurrencies } from '@/hooks/useCurrency';
import { ChevronDown, Loader2, Search, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Currency } from '@/types/currency';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function CurrencyTriggerLabel({
  currency,
  code,
  placeholder,
  translatedName,
}: {
  currency?: Currency;
  code?: string;
  placeholder: string;
  translatedName?: string;
}) {
  if (code && currency) {
    // Only show symbol separately if it's distinct from the code
    const showSymbol = currency.symbol && currency.symbol !== currency.code;
    const displayName = translatedName || currency.name;
    return (
      <span className="flex items-center gap-2 overflow-hidden">
        {showSymbol && (
          <span className="font-semibold text-primary shrink-0">{currency.symbol}</span>
        )}
        <span className="font-mono font-semibold shrink-0">{code}</span>
        <span className="text-text-muted truncate">— {displayName}</span>
      </span>
    );
  }
  return <span className="text-text-muted">{placeholder}</span>;
}

// ---------------------------------------------------------------------------
// CurrencySelector (full — shows symbol, code and name)
// ---------------------------------------------------------------------------

interface CurrencySelectorProps {
  value?: string;
  onValueChange: (value: any) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /** If true, shows all currencies including inactive ones. Default: false */
  showInactive?: boolean;
  /** Show "None / All currencies" option at the top */
  allowNone?: boolean;
}

export function CurrencySelector({
  value,
  onValueChange,
  placeholder = 'Select currency',
  disabled = false,
  className,
  showInactive = false,
  allowNone = false,
}: CurrencySelectorProps) {
  const { t } = useTranslation('currencies');
  const { t: tc } = useTranslation('common');
  const { data: currencies, isLoading, isError } = useCurrencies();
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const searchRef = useRef<HTMLInputElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  // Focus the search input as soon as the popover opens
  useEffect(() => {
    if (open) {
      // rAF ensures the popover DOM is mounted before we try to focus
      const id = requestAnimationFrame(() => searchRef.current?.focus());
      return () => cancelAnimationFrame(id);
    } else {
      setSearchQuery('');
    }
  }, [open]);

  if (isLoading) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-border bg-surface px-3 py-2">
        <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
        <span className="ml-2 text-sm text-text-muted">{tc('loading')}</span>
      </div>
    );
  }

  if (isError || !currencies) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-error bg-surface px-3 py-2">
        <span className="text-sm text-error">{tc('loadError')}</span>
      </div>
    );
  }

  const baseCurrencies = showInactive
    ? currencies
    : currencies.filter((c: Currency) => c.isActive);

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const visibleCurrencies = normalizedQuery
    ? baseCurrencies.filter((currency: Currency) => {
        const translatedName = t(`currency.${currency.code}`, { defaultValue: currency.name });
        return [currency.code, translatedName, currency.name, currency.symbol]
          .filter(Boolean)
          .some((v) => v.toLowerCase().includes(normalizedQuery));
      })
    : baseCurrencies;

  const selectedCurrency = value
    ? currencies.find((c: Currency) => c.code === value)
    : undefined;

  const selectedTranslatedName = selectedCurrency
    ? t(`currency.${selectedCurrency.code}`, { defaultValue: selectedCurrency.name })
    : undefined;

  const handleSelect = (code: string | undefined) => {
    onValueChange(code);
    setOpen(false);
  };

  return (
    <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Trigger asChild>
        <button
          ref={triggerRef}
          type="button"
          disabled={disabled}
          className={cn(
            'flex h-10 w-full items-center justify-between rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary',
            'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'transition-all duration-150',
            className
          )}
        >
          {allowNone && !value ? (
            <span className="text-text-muted">{t('allCurrencies')}</span>
          ) : (
            <CurrencyTriggerLabel
              currency={selectedCurrency}
              code={value}
              placeholder={placeholder}
              translatedName={selectedTranslatedName}
            />
          )}
          <ChevronDown className="h-4 w-4 opacity-50 shrink-0 ml-2" />
        </button>
      </PopoverPrimitive.Trigger>

      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={4}
          // At minimum match the trigger width, but allow growing wider for long names
          style={{ 
            minWidth: 'var(--radix-popover-trigger-width)', 
            width: 'max-content', 
            maxWidth: '420px',
            maxHeight: 'var(--radix-popover-content-available-height)'
          }}
          className={cn(
            'z-[100] flex flex-col overflow-hidden rounded-lg border border-border bg-surface text-text-primary shadow-md',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
            'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
          )}
          onOpenAutoFocus={(e) => e.preventDefault()}
          onEscapeKeyDown={(e) => {
            // Prevent the Escape event from bubbling to the parent Dialog
            e.stopPropagation();
          }}
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
                  if (e.key === 'Escape') {
                    e.stopPropagation();
                    setOpen(false);
                  }
                }}
                placeholder={tc('lastUpdated.searchCurrency')}
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
            {allowNone && (
              <button
                type="button"
                onClick={() => handleSelect(undefined)}
                className={cn(
                  'flex w-full cursor-default items-center gap-2 rounded-md px-3 py-2 text-sm outline-none',
                  'hover:bg-surface-elevated focus:bg-surface-elevated',
                  !value && 'bg-surface-elevated'
                )}
              >
                <span className="w-4 shrink-0">
                  {!value && <Check className="h-4 w-4 text-primary" />}
                </span>
                <span className="text-text-muted">{t('allCurrencies')}</span>
              </button>
            )}

            {visibleCurrencies.length === 0 ? (
              <div className="p-3 text-center text-sm text-text-muted">
                {t('noMatch')}
              </div>
            ) : (
              visibleCurrencies.map((currency: Currency) => {
                const translatedName = t(`currency.${currency.code}`, { defaultValue: currency.name });
                return (
                  <button
                    key={currency.code}
                    type="button"
                    onClick={() => handleSelect(currency.code)}
                    className={cn(
                      'flex w-full cursor-default items-center gap-3 rounded-md px-3 py-2 text-sm outline-none',
                      'hover:bg-surface-elevated focus:bg-surface-elevated',
                      value === currency.code && 'bg-surface-elevated'
                    )}
                  >
                    {/* Check indicator */}
                    <span className="w-4 shrink-0 flex items-center justify-center">
                      {value === currency.code && (
                        <Check className="h-4 w-4 text-primary" />
                      )}
                    </span>
                    {/* Code */}
                    <span className="w-12 shrink-0 font-mono font-semibold text-primary">
                      {currency.code}
                    </span>
                    {/* Name — takes remaining space, no wrap */}
                    <span className="text-text-muted whitespace-nowrap">
                      {translatedName}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}

// ---------------------------------------------------------------------------
// CurrencySelectorCompact — shows only symbol + code, useful for tight spaces
// ---------------------------------------------------------------------------

interface CurrencySelectorCompactProps {
  value?: string;
  onValueChange: (value: string) => void;
  disabled?: boolean;
  className?: string;
}

export function CurrencySelectorCompact({
  value,
  onValueChange,
  disabled = false,
  className,
}: CurrencySelectorCompactProps) {
  const { t } = useTranslation('currencies');
  const { t: tc } = useTranslation('common');
  const { data: currencies, isLoading } = useCurrencies();
  const [open, setOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const searchRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      const id = requestAnimationFrame(() => searchRef.current?.focus());
      return () => cancelAnimationFrame(id);
    } else {
      setSearchQuery('');
    }
  }, [open]);

  if (isLoading || !currencies) {
    return (
      <div className="flex h-10 w-24 items-center justify-center rounded-lg border border-border bg-surface">
        <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
      </div>
    );
  }

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const visibleCurrencies = normalizedQuery
    ? currencies.filter((currency: Currency) => {
        const translatedName = t(`currency.${currency.code}`, { defaultValue: currency.name });
        return [currency.code, translatedName, currency.name, currency.symbol]
          .filter(Boolean)
          .some((v) => v.toLowerCase().includes(normalizedQuery));
      })
    : currencies;

  const selectedCurrency = value
    ? currencies.find((c: Currency) => c.code === value)
    : undefined;

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
          className={cn(
            'flex h-10 items-center justify-between gap-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary',
            'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'transition-all duration-150',
            className
          )}
        >
          {value && selectedCurrency ? (
            <span className="flex items-center gap-1">
              <span className="font-semibold text-primary">
                {selectedCurrency.symbol}
              </span>
              <span className="font-mono">{value}</span>
            </span>
          ) : (
            <span className="text-text-muted">Select</span>
          )}
          <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
        </button>
      </PopoverPrimitive.Trigger>

      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={4}
          style={{ maxHeight: 'var(--radix-popover-content-available-height)' }}
          className={cn(
            'z-[100] w-56 flex flex-col overflow-hidden rounded-lg border border-border bg-surface text-text-primary shadow-md',
            'data-[state=open]:animate-in data-[state=closed]:animate-out',
            'data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0',
            'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95',
          )}
          onOpenAutoFocus={(e) => e.preventDefault()}
          onEscapeKeyDown={(e) => {
            // Prevent the Escape event from bubbling to the parent Dialog
            e.stopPropagation();
          }}
        >
          <div className="shrink-0 border-b border-border p-2">
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
              <input
                ref={searchRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Escape') {
                    e.stopPropagation();
                    setOpen(false);
                  }
                }}
                placeholder={tc('lastUpdated.searchCurrency')}
                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                autoComplete="off"
              />
            </div>
          </div>

          <div 
            className="flex-1 overflow-y-auto p-1 shrink min-h-0 max-h-72"
            onWheel={(e) => e.stopPropagation()}
            onTouchMove={(e) => e.stopPropagation()}
          >
            {visibleCurrencies.length === 0 ? (
              <div className="p-2 text-center text-sm text-text-muted">
                No currencies match your search
              </div>
            ) : (
              visibleCurrencies.map((currency: Currency) => (
                <button
                  key={currency.code}
                  type="button"
                  onClick={() => handleSelect(currency.code)}
                  className={cn(
                    'relative flex w-full cursor-default items-center rounded-md py-1.5 pl-8 pr-2 text-sm outline-none',
                    'hover:bg-surface-elevated focus:bg-surface-elevated',
                    value === currency.code && 'bg-surface-elevated'
                  )}
                >
                  <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                    {value === currency.code && (
                      <Check className="h-4 w-4 text-primary" />
                    )}
                  </span>
                  <span className="flex items-center gap-2">
                    <span className="w-6 font-semibold text-primary">
                      {currency.symbol}
                    </span>
                    <span className="font-mono">{currency.code}</span>
                  </span>
                </button>
              ))
            )}
          </div>
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
