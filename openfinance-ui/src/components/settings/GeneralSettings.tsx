/**
 * GeneralSettings - General user settings component
 * 
 * Implements TASK-6.3:
 * - Base currency selection and update
 * - Secondary currency selection (comparison currency shown in tooltips)
 * - Shows current base currency from user profile
 * - Success/error notifications for updates
 * 
 * Requirements: REQ-6.3 (User Settings & Preferences), REQ-15.1–REQ-15.4
 */
import { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuthContext } from '@/context/AuthContext';
import { useUserSettings, useUpdateBaseCurrency, useUpdateUserSettings } from '@/hooks/useUserSettings';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { useCurrencies } from '@/hooks/useCurrency';
import { Loader2, Check, Search, Globe } from 'lucide-react';
import type { Currency } from '@/types/currency';
import { CountrySelector } from '@/components/common/CountrySelector';

// ---------------------------------------------------------------------------
// SecondaryCurrencySelector
// A Select-based wrapper that prepends a "None" option before the currency list.
// This lets the user clear the secondary currency (REQ-15.3) while reusing the
// same CurrencySelector-style UI (search, symbol + code + name).
// ---------------------------------------------------------------------------

interface SecondaryCurrencySelectorProps {
  value: string;
  onValueChange: (value: string) => void;
  disabled?: boolean;
  className?: string;
}

function SecondaryCurrencySelector({
  value,
  onValueChange,
  disabled = false,
  className,
}: SecondaryCurrencySelectorProps) {
  const { t } = useTranslation('settings');
  const { t: tCurrency } = useTranslation('currencies');
  const { data: currencies, isLoading, isError } = useCurrencies();
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  if (isLoading) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-border bg-surface px-3 py-2">
        <Loader2 className="h-4 w-4 animate-spin text-text-muted" />
        <span className="ml-2 text-sm text-text-muted">{t('general.currencies.loadingCurrencies')}</span>
      </div>
    );
  }

  if (isError || !currencies) {
    return (
      <div className="flex h-10 w-full items-center justify-center rounded-lg border border-error bg-surface px-3 py-2">
        <span className="text-sm text-error">{t('general.currencies.loadError')}</span>
      </div>
    );
  }

  const normalizedQuery = searchQuery.trim().toLowerCase();
  const activeCurrencies = currencies.filter((c: Currency) => c.isActive);
  const visibleCurrencies = normalizedQuery
    ? activeCurrencies.filter((c: Currency) => {
        const translatedName = tCurrency(`currency.${c.code}`, { defaultValue: c.name });
        return [c.code, translatedName, c.name, c.symbol]
          .filter(Boolean)
          .some((v) => v.toLowerCase().includes(normalizedQuery));
      })
    : activeCurrencies;

  const selectedCurrency = value && value !== '__none__'
    ? currencies.find((c: Currency) => c.code === value)
    : undefined;

  return (
    <Select
      value={value || '__none__'}
      onValueChange={onValueChange}
      disabled={disabled}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (!open) setSearchQuery('');
      }}
    >
      <SelectTrigger className={className} aria-label={t('general.currencies.secondaryCurrency')}>
        <SelectValue placeholder={t('general.currencies.none')}>
          {selectedCurrency ? (
            <span className="flex items-center gap-2">
              <span className="font-semibold text-primary">{selectedCurrency.symbol}</span>
              <span>{value}</span>
              <span className="text-text-muted">- {selectedCurrency.name}</span>
            </span>
          ) : (
            t('general.currencies.none')
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
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.stopPropagation()}
                placeholder={t('general.currencies.searchPlaceholder')}
                className="h-9 w-full rounded-md border border-border bg-background pl-8 pr-2 text-sm text-text-primary placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-primary"
                autoFocus={isOpen}
              />
            </div>
          </div>
        }
      >
        {/* "None" option — always shown, clears secondary currency (REQ-15.3) */}
        <SelectItem value="__none__">
          <span className="text-text-muted italic">{t('general.currencies.none')}</span>
        </SelectItem>
        {visibleCurrencies.length === 0 ? (
          <div className="p-2 text-center text-sm text-text-muted">
            {t('general.currencies.noMatch')}
          </div>
        ) : (
          visibleCurrencies.map((currency: Currency) => {
            const translatedName = tCurrency(`currency.${currency.code}`, { defaultValue: currency.name });
            return (
              <SelectItem key={currency.code} value={currency.code}>
                <span className="flex items-center gap-2">
                  <span className="w-8 font-semibold text-primary">{currency.symbol}</span>
                  <span className="w-12 font-mono">{currency.code}</span>
                  <span className="text-text-muted">{translatedName}</span>
                </span>
              </SelectItem>
            );
          })
        )}
      </SelectContent>
    </Select>
  );
}


/**
 * General settings component with base currency and secondary currency selection
 */
export function GeneralSettings({ onHasChanges }: { onHasChanges?: (dirty: boolean) => void }) {
  const { t } = useTranslation('settings');
  const { user } = useAuthContext();
  const [selectedCurrency, setSelectedCurrency] = useState(user?.baseCurrency || DEFAULT_CURRENCY);
  const updateBaseCurrency = useUpdateBaseCurrency();
  const updateSettings = useUpdateUserSettings();
  const [showSuccess, setShowSuccess] = useState(false);

  // Secondary currency state from context
  const { secondaryCurrency, setSecondaryCurrency } = useCurrencyDisplay();

  // Backend settings (for initialising + reverting secondary currency)
  const { data: settings } = useUserSettings();

  // Country setting
  const [selectedCountry, setSelectedCountry] = useState<string>('FR');
  const [countrySaveSuccess, setCountrySaveSuccess] = useState(false);
  const [secondarySaveSuccess, setSecondarySaveSuccess] = useState(false);

  // Update selected currency when user changes (e.g., after successful update)
  useEffect(() => {
    if (user?.baseCurrency) {
      setSelectedCurrency(user.baseCurrency);
    }
  }, [user?.baseCurrency]);

  /**
   * Sync secondary currency from backend profile on first load.
   * Requirement REQ-2.7, REQ-6.3
   */
  useEffect(() => {
    if (settings?.secondaryCurrency && !secondaryCurrency) {
      setSecondaryCurrency(settings.secondaryCurrency);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settings]);

  // Sync country from backend settings on load
  useEffect(() => {
    if (settings?.country) {
      setSelectedCountry(settings.country);
    }
  }, [settings?.country]);

  const handleSave = async () => {
    try {
      await updateBaseCurrency.mutateAsync(selectedCurrency);
      setShowSuccess(true);
      setTimeout(() => setShowSuccess(false), 3000);
    } catch (error) {
      // Error is handled by the mutation hook and can be accessed via updateBaseCurrency.error
      console.error('Failed to update base currency:', error);
    }
  };

  /**
   * Handle secondary currency change.
   * Updates the local context (localStorage) and persists to backend.
   * Requirement REQ-15.1–REQ-15.4, REQ-2.2
   */
  const handleSecondaryCurrencyChange = (value: string) => {
    const code = value === '__none__' ? null : (value || null);
    setSecondaryCurrency(code);
    updateSettings.mutate(
      { secondaryCurrency: code ?? '' },
      {
        onSuccess: () => {
          setSecondarySaveSuccess(true);
          setTimeout(() => setSecondarySaveSuccess(false), 3000);
        },
        onError: () => {
          // Revert to previous value on error
          if (settings) {
            setSecondaryCurrency(settings.secondaryCurrency ?? null);
          }
        },
      }
    );
  };

  /** Handle country change — persists to backend immediately */
  const handleCountryChange = (value: string) => {
    setSelectedCountry(value);
    updateSettings.mutate(
      { country: value },
      {
        onSuccess: () => {
          setCountrySaveSuccess(true);
          setTimeout(() => setCountrySaveSuccess(false), 3000);
        },
        onError: () => {
          if (settings?.country) {
            setSelectedCountry(settings.country);
          }
        },
      }
    );
  };

  const hasChanges = selectedCurrency !== user?.baseCurrency;

  // Notify parent whenever the unsaved-changes flag flips
  const onHasChangesRef = useRef(onHasChanges);
  onHasChangesRef.current = onHasChanges;
  useEffect(() => {
    onHasChangesRef.current?.(hasChanges);
  }, [hasChanges]);

  return (
    <div className="space-y-6">
      {/* Section Header */}
      <div>
        <h2 className="text-xl font-semibold text-text-primary mb-2">{t('general.title')}</h2>
        <p className="text-text-secondary text-sm">{t('general.description')}</p>
      </div>

      {/* Country Section */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="flex items-center gap-2 mb-1">
          <Globe className="h-5 w-5 text-text-secondary" />
          <h3 className="text-base font-semibold text-text-primary">{t('general.country.sectionTitle')}</h3>
        </div>
        <p className="text-xs text-text-secondary mb-4">{t('general.country.description')}</p>

        <CountrySelector
          value={selectedCountry}
          onValueChange={handleCountryChange}
          disabled={updateSettings.isPending}
          placeholder={t('general.country.placeholder')}
          searchPlaceholder={t('general.country.searchPlaceholder')}
          noMatch={t('general.country.noMatch')}
        />

        {countrySaveSuccess && (
          <div className="mt-3 flex items-center gap-2 text-sm text-green-400">
            <Check className="h-4 w-4" />
            {t('general.country.updateSuccess')}
          </div>
        )}

        <p className="mt-3 text-xs text-text-muted">
          {t('general.country.toolNote')}
        </p>
      </div>

      {/* Currencies Section — Base Currency + Secondary Currency */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        {/* Section title */}
        <h3 className="text-base font-semibold text-text-primary mb-5">{t('general.currencies.sectionTitle')}</h3>

        {/* Base Currency */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-text-secondary mb-2">
            {t('general.currencies.baseCurrency')}
          </label>
          <p className="text-xs text-text-secondary mb-4">
            {t('general.currencies.baseCurrencyDescription')}
          </p>

          <CurrencySelector
            value={selectedCurrency}
            onValueChange={setSelectedCurrency}
            placeholder={t('general.currencies.baseCurrencyPlaceholder')}
            className="w-full max-w-md mb-4"
          />

          {/* Current vs. New Currency Info */}
          {user && (
            <div className="text-xs text-text-secondary mb-4">
              <p>
                {t('general.currencies.current')} <span className="text-text-primary font-medium">{user.baseCurrency}</span>
              </p>
              {hasChanges && (
                <p className="text-primary mt-1">
                  {t('general.currencies.new')} <span className="font-medium">{selectedCurrency}</span>
                </p>
              )}
            </div>
          )}
        </div>

        {/* Divider */}
        <div className="border-t border-border mb-6" />

        {/* Secondary Currency Selector — REQ-15.1–REQ-15.4 */}
        <div className="mb-6">
          <label className="block text-sm font-medium text-text-secondary mb-2">
            {t('general.currencies.secondaryCurrency')}
          </label>
          <p className="text-xs text-text-secondary mb-4">
            {t('general.currencies.secondaryCurrencyDescription')}
          </p>

          {/* CurrencySelector with prepended "None" option — REQ-15.1, REQ-15.3 */}
          <SecondaryCurrencySelector
            value={secondaryCurrency ?? ''}
            onValueChange={handleSecondaryCurrencyChange}
            disabled={updateSettings.isPending}
            className="w-full max-w-md"
          />

          {secondaryCurrency && (
            <p className="mt-2 text-xs text-text-muted">
              {t('general.currencies.secondaryAmountsNote', { currency: secondaryCurrency })}
            </p>
          )}

          {secondarySaveSuccess && (
            <div className="mt-3 flex items-center gap-2 text-sm text-green-400">
              <Check className="h-4 w-4" />
              {secondaryCurrency
                ? t('general.currencies.secondaryUpdateSuccess', { currency: secondaryCurrency })
                : t('general.currencies.secondaryUpdateSuccessNone')}
            </div>
          )}
        </div>

        {/* Divider */}
        <div className="border-t border-border mb-6" />

        {/* Error Message */}
        {updateBaseCurrency.isError && (
          <div className="mb-4 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
            <p className="text-sm text-red-400">
              {t('general.currencies.updateError')}
            </p>
          </div>
        )}

        {/* Success Message */}
        {showSuccess && (
          <div className="mb-4 p-3 bg-green-500/10 border border-green-500/20 rounded-lg flex items-center gap-2">
            <Check className="h-4 w-4 text-green-400" />
            <p className="text-sm text-green-400">
              {t('general.currencies.updateSuccess', { currency: selectedCurrency })}
            </p>
          </div>
        )}

        {/* Save Button */}
        <button
          onClick={handleSave}
          disabled={updateBaseCurrency.isPending || !hasChanges}
          className="px-6 py-2.5 bg-primary text-black font-semibold rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
        >
          {updateBaseCurrency.isPending ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              {t('general.currencies.saving')}
            </>
          ) : (
            t('general.currencies.saveChanges')
          )}
        </button>

        {!hasChanges && (
          <p className="text-xs text-text-muted mt-2">
            {t('general.currencies.noChanges')}
          </p>
        )}
      </div>

    </div>
  );
}
