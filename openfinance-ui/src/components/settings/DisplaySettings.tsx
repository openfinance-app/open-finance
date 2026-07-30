/**
 * DisplaySettings - Display preferences component
 *
 * Implements TASK-6.3.15 and TASK-6.3.7:
 * - Theme selection (light/dark mode)
 * - Date format preferences
 * - Number format preferences (1,234.56 / 1.234,56 / 1 234,56)
 * - Currency display mode (base/native/both)
 * - Persists settings to backend via UserSettings API
 *
 * Note: Base currency and secondary currency selectors live in GeneralSettings.
 *
 * Requirements: REQ-6.3 (User Settings & Preferences), REQ-15.1–REQ-15.4
 */
import { useState, useEffect } from 'react';
import { Moon, Sun, Calendar, Check, DollarSign, Hash, Percent } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useUserSettings, useUpdateUserSettings } from '@/hooks/useUserSettings';
import { useCurrencyDisplay } from '@/context/CurrencyDisplayContext';
import { useTheme } from '@/context/ThemeContext';
import { useNumberFormat, type NumberFormat } from '@/context/NumberFormatContext';
import { useDecimalPlaces } from '@/context/DecimalPlacesContext';
import { LanguageSelector } from '@/components/settings/LanguageSelector';
import type { AmountDisplayMode } from '@/context/CurrencyDisplayContext';
import {
  SETTINGS_SUCCESS_MESSAGE_DURATION_MS,
  EXTENDED_MESSAGE_DURATION_MS,
} from '@/constants/timing';

type DateFormat = 'MM/DD/YYYY' | 'DD/MM/YYYY' | 'YYYY-MM-DD';

/**
 * Display settings component with theme, date format, number format, and
 * currency display mode.
 */
export function DisplaySettings() {
  const { t } = useTranslation('settings');
  const { data: settings, isLoading, error } = useUserSettings();
  const updateSettings = useUpdateUserSettings();

  // Theme — delegated to ThemeContext which owns the DOM class and persistence.
  const { theme, setTheme } = useTheme();

  // Currency display mode preference from context — REQ-11.1, REQ-11.4
  const { displayMode, setDisplayMode } = useCurrencyDisplay();

  // Number format preference from context — REQ-6.3.15
  const { numberFormat, setNumberFormat } = useNumberFormat();

  // Preferred decimal-places override — display-only global precision preference.
  const { overrideEnabled, decimalPlaces, setOverrideEnabled, setDecimalPlaces } =
    useDecimalPlaces();

  const handleToggleDecimalOverride = (enabled: boolean) => {
    setOverrideEnabled(enabled);
    setSuccessMessage(t('display.decimalPlaces.updateSuccess'));
    setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
  };

  const handleDecimalPlacesChange = (places: number) => {
    setDecimalPlaces(places);
    setSuccessMessage(t('display.decimalPlaces.updateSuccess'));
    setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
  };

  // Sample amount used for the decimal-places live preview.
  const decimalPreview = (1234.56789).toFixed(overrideEnabled ? decimalPlaces : 2);

  // Local state for UI updates before API response
  const [dateFormat, setDateFormat] = useState<DateFormat>('MM/DD/YYYY');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Initialize date format from backend settings (theme is managed by ThemeContext)
  useEffect(() => {
    if (settings) {
      setDateFormat(settings.dateFormat);
    }
  }, [settings]);

  const handleThemeChange = (newTheme: 'dark' | 'light') => {
    setTheme(newTheme);
    setSuccessMessage(t('display.theme.updateSuccess'));
    setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
  };

  const handleDateFormatChange = (format: DateFormat) => {
    setDateFormat(format);

    updateSettings.mutate(
      { dateFormat: format },
      {
        onSuccess: () => {
          setSuccessMessage(t('display.dateFormat.updateSuccess'));
          setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
        },
        onError: () => {
          setErrorMessage(t('display.dateFormat.updateError'));
          setTimeout(() => setErrorMessage(null), EXTENDED_MESSAGE_DURATION_MS);
          // Revert to previous format on error
          if (settings) {
            setDateFormat(settings.dateFormat);
          }
        },
      }
    );
  };

  const handleNumberFormatChange = (format: NumberFormat) => {
    setNumberFormat(format);
    setSuccessMessage(t('display.numberFormat.updateSuccess'));
    setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
  };

  const handleDisplayModeChange = (mode: AmountDisplayMode) => {
    setDisplayMode(mode);
    setSuccessMessage(t('display.currencyDisplay.updateSuccess'));
    setTimeout(() => setSuccessMessage(null), SETTINGS_SUCCESS_MESSAGE_DURATION_MS);
  };

  const formatExample = (format: DateFormat): string => {
    switch (format) {
      case 'MM/DD/YYYY':
        return '12/31/2025';
      case 'DD/MM/YYYY':
        return '31/12/2025';
      case 'YYYY-MM-DD':
        return '2025-12-31';
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="bg-surface rounded-lg p-6 border border-border">
          <div className="animate-pulse space-y-4">
            <div className="h-4 bg-surface-elevated rounded w-1/4"></div>
            <div className="h-24 bg-surface-elevated rounded"></div>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-error/10 border border-error/20 rounded-lg p-4">
        <p className="text-error text-sm">{t('display.loadError')}</p>
      </div>
    );
  }

  // Number format options with live previews
  const numberFormatOptions: {
    value: NumberFormat;
    label: string;
    description: string;
    example: string;
  }[] = [
    {
      value: '1,234.56',
      label: t('display.numberFormat.usUkStyle'),
      description: t('display.numberFormat.usUkDescription'),
      example: '1,234,567.89',
    },
    {
      value: '1.234,56',
      label: t('display.numberFormat.europeanStyle'),
      description: t('display.numberFormat.europeanDescription'),
      example: '1.234.567,89',
    },
    {
      value: '1 234,56',
      label: t('display.numberFormat.frenchSwissStyle'),
      description: t('display.numberFormat.frenchSwissDescription'),
      example: '1\u202F234\u202F567,89',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Section Header */}
      <div>
        <h2 className="text-xl font-semibold text-text-primary mb-2">{t('display.title')}</h2>
        <p className="text-text-secondary text-sm">{t('display.description')}</p>
      </div>

      {/* Success Message */}
      {successMessage && (
        <div className="bg-success/10 border border-success/20 rounded-lg p-4 flex items-center gap-2">
          <Check className="h-4 w-4 text-success" />
          <p className="text-success text-sm">{successMessage}</p>
        </div>
      )}

      {/* Error Message */}
      {errorMessage && (
        <div className="bg-error/10 border border-error/20 rounded-lg p-4">
          <p className="text-error text-sm">{errorMessage}</p>
        </div>
      )}

      {/* Language Selection */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2">
            {t('display.language.label')}
          </label>
        </div>
        <LanguageSelector />
      </div>

      {/* Theme Selection */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2 flex items-center gap-2">
            <Sun className="h-4 w-4" />
            {t('display.theme.label')}
          </label>
          <p className="text-xs text-text-muted mb-4">{t('display.theme.description')}</p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {/* Dark Theme Option */}
          <button
            aria-label="Dark theme"
            onClick={() => handleThemeChange('dark')}
            disabled={updateSettings.isPending}
            className={`relative p-4 rounded-lg border-2 transition-all ${
              theme === 'dark'
                ? 'border-primary bg-primary/10'
                : 'border-border bg-surface-elevated hover:border-text-muted'
            } ${updateSettings.isPending ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            <div className="flex items-center gap-3 mb-2">
              <Moon className="h-5 w-5 text-primary" />
              <span className="text-text-primary font-medium">{t('display.theme.dark')}</span>
            </div>
            <p className="text-xs text-text-muted text-left">
              {t('display.theme.darkDescription')}
            </p>
            {theme === 'dark' && (
              <div className="absolute top-2 right-2">
                <div className="h-2 w-2 rounded-full bg-primary" />
              </div>
            )}
          </button>

          {/* Light Theme Option */}
          <button
            aria-label="Light theme"
            onClick={() => handleThemeChange('light')}
            disabled={updateSettings.isPending}
            className={`relative p-4 rounded-lg border-2 transition-all ${
              theme === 'light'
                ? 'border-primary bg-primary/10'
                : 'border-border bg-surface-elevated hover:border-text-muted'
            } ${updateSettings.isPending ? 'opacity-50 cursor-not-allowed' : ''}`}
          >
            <div className="flex items-center gap-3 mb-2">
              <Sun className="h-5 w-5 text-primary" />
              <span className="text-text-primary font-medium">{t('display.theme.light')}</span>
            </div>
            <p className="text-xs text-text-muted text-left">
              {t('display.theme.lightDescription')}
            </p>
            {theme === 'light' && (
              <div className="absolute top-2 right-2">
                <div className="h-2 w-2 rounded-full bg-primary" />
              </div>
            )}
          </button>
        </div>

        {/* Theme preview swatch */}
        <div
          className="mt-4 grid grid-cols-2 gap-4 pointer-events-none select-none"
          aria-hidden="true"
        >
          {/* Dark swatch */}
          <div className="rounded-md overflow-hidden border border-border/50 h-14 flex">
            <div className="w-6 bg-[#0a0a0a] shrink-0" />
            <div className="flex-1 bg-[#1a1a1a] flex flex-col justify-center px-2 gap-1">
              <div className="h-1.5 w-3/4 bg-white/20 rounded-full" />
              <div className="h-1 w-1/2 bg-white/10 rounded-full" />
            </div>
          </div>
          {/* Light swatch */}
          <div className="rounded-md overflow-hidden border border-border/50 h-14 flex">
            <div className="w-6 bg-[#f0f0f0] shrink-0" />
            <div className="flex-1 bg-[#ffffff] flex flex-col justify-center px-2 gap-1">
              <div className="h-1.5 w-3/4 bg-black/15 rounded-full" />
              <div className="h-1 w-1/2 bg-black/08 rounded-full" />
            </div>
          </div>
        </div>
      </div>

      {/* Date Format Selection */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2 flex items-center gap-2">
            <Calendar className="h-4 w-4" />
            {t('display.dateFormat.label')}
          </label>
          <p className="text-xs text-text-muted mb-4">{t('display.dateFormat.description')}</p>
        </div>

        <div className="space-y-3">
          {(['MM/DD/YYYY', 'DD/MM/YYYY', 'YYYY-MM-DD'] as DateFormat[]).map(format => (
            <button
              key={format}
              onClick={() => handleDateFormatChange(format)}
              disabled={updateSettings.isPending}
              className={`w-full p-4 rounded-lg border-2 transition-all text-left ${
                dateFormat === format
                  ? 'border-primary bg-primary/10'
                  : 'border-border bg-surface-elevated hover:border-text-muted'
              } ${updateSettings.isPending ? 'opacity-50 cursor-not-allowed' : ''}`}
            >
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-text-primary font-medium mb-1">{format}</div>
                  <div className="text-sm text-text-secondary">
                    {t('display.dateFormat.example')} {formatExample(format)}
                  </div>
                </div>
                {dateFormat === format && (
                  <div className="h-2 w-2 rounded-full bg-primary shrink-0 ml-2" />
                )}
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Number Format Selection — REQ-6.3.15 */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2 flex items-center gap-2">
            <Hash className="h-4 w-4" />
            {t('display.numberFormat.label')}
          </label>
          <p className="text-xs text-text-muted mb-4">{t('display.numberFormat.description')}</p>
        </div>

        <div className="space-y-3">
          {numberFormatOptions.map(({ value, label, description, example }) => (
            <button
              key={value}
              type="button"
              onClick={() => handleNumberFormatChange(value)}
              className={`w-full p-4 rounded-lg border-2 transition-all text-left ${
                numberFormat === value
                  ? 'border-primary bg-primary/10'
                  : 'border-border bg-surface-elevated hover:border-text-muted'
              }`}
              aria-pressed={numberFormat === value}
            >
              <div className="flex items-center justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-3 mb-1">
                    <span className="text-text-primary font-medium">{label}</span>
                    {/* Live preview badge */}
                    <span
                      className={`font-mono text-sm px-2 py-0.5 rounded ${
                        numberFormat === value
                          ? 'bg-primary/20 text-primary'
                          : 'bg-surface text-text-secondary'
                      }`}
                      aria-label={`${t('display.dateFormat.example')} ${example}`}
                    >
                      {example}
                    </span>
                  </div>
                  <div className="text-sm text-text-secondary">{description}</div>
                </div>
                {numberFormat === value && (
                  <div className="h-2 w-2 rounded-full bg-primary shrink-0 ml-3" />
                )}
              </div>
            </button>
          ))}
        </div>

        {/* Live preview strip */}
        <div className="mt-5 pt-4 border-t border-border">
          <p className="text-xs text-text-muted mb-2">{t('display.numberFormat.livePreview')}</p>
          <div className="grid grid-cols-3 gap-3 text-center">
            <div className="bg-surface-elevated rounded-md p-3">
              <div className="text-xs text-text-muted mb-1">{t('display.numberFormat.small')}</div>
              <div className="font-mono text-sm text-text-primary">
                {numberFormat === '1,234.56' && '9.99'}
                {numberFormat === '1.234,56' && '9,99'}
                {numberFormat === '1 234,56' && '9,99'}
              </div>
            </div>
            <div className="bg-surface-elevated rounded-md p-3">
              <div className="text-xs text-text-muted mb-1">{t('display.numberFormat.medium')}</div>
              <div className="font-mono text-sm text-text-primary">
                {numberFormat === '1,234.56' && '1,234.56'}
                {numberFormat === '1.234,56' && '1.234,56'}
                {numberFormat === '1 234,56' && '1\u202F234,56'}
              </div>
            </div>
            <div className="bg-surface-elevated rounded-md p-3">
              <div className="text-xs text-text-muted mb-1">{t('display.numberFormat.large')}</div>
              <div className="font-mono text-sm text-text-primary">
                {numberFormat === '1,234.56' && '1,234,567.89'}
                {numberFormat === '1.234,56' && '1.234.567,89'}
                {numberFormat === '1 234,56' && '1\u202F234\u202F567,89'}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Decimal Places Override */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2 flex items-center gap-2">
            <Percent className="h-4 w-4" />
            {t('display.decimalPlaces.label')}
          </label>
          <p className="text-xs text-text-muted mb-4">{t('display.decimalPlaces.description')}</p>
        </div>

        {/* Enable/disable toggle */}
        <button
          type="button"
          role="switch"
          aria-checked={overrideEnabled}
          onClick={() => handleToggleDecimalOverride(!overrideEnabled)}
          className={`w-full p-4 rounded-lg border-2 transition-all text-left flex items-center justify-between ${
            overrideEnabled
              ? 'border-primary bg-primary/10'
              : 'border-border bg-surface-elevated hover:border-text-muted'
          }`}
        >
          <div>
            <div className="text-text-primary font-medium mb-1">
              {t('display.decimalPlaces.enableLabel')}
            </div>
            <div className="text-sm text-text-secondary">
              {t('display.decimalPlaces.enableHint')}
            </div>
          </div>
          <span
            aria-hidden="true"
            className={`ml-3 inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
              overrideEnabled ? 'bg-primary' : 'bg-surface'
            }`}
          >
            <span
              className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                overrideEnabled ? 'translate-x-6' : 'translate-x-1'
              }`}
            />
          </span>
        </button>

        {/* Decimal-place selector (1-8) */}
        <div className={`mt-4 ${overrideEnabled ? '' : 'opacity-50 pointer-events-none'}`}>
          <label className="block text-xs text-text-muted mb-2">
            {t('display.decimalPlaces.placesLabel')}
          </label>
          <div
            className="grid grid-cols-8 gap-2"
            role="group"
            aria-label={t('display.decimalPlaces.placesLabel')}
          >
            {[1, 2, 3, 4, 5, 6, 7, 8].map(n => (
              <button
                key={n}
                type="button"
                disabled={!overrideEnabled}
                onClick={() => handleDecimalPlacesChange(n)}
                aria-pressed={overrideEnabled && decimalPlaces === n}
                className={`p-2 rounded-lg border-2 font-mono text-sm transition-all ${
                  overrideEnabled && decimalPlaces === n
                    ? 'border-primary bg-primary/10 text-primary'
                    : 'border-border bg-surface-elevated text-text-secondary hover:border-text-muted'
                }`}
              >
                {n}
              </button>
            ))}
          </div>

          {/* Live preview strip */}
          <div className="mt-5 pt-4 border-t border-border">
            <p className="text-xs text-text-muted mb-2">{t('display.decimalPlaces.livePreview')}</p>
            <div className="bg-surface-elevated rounded-md p-3 text-center">
              <div className="font-mono text-sm text-text-primary">{decimalPreview}</div>
            </div>
          </div>
        </div>
      </div>

      {/* Currency Display Mode — REQ-11.1, REQ-11.4 */}
      <div className="bg-surface rounded-lg p-6 border border-border">
        <div className="mb-4">
          <label className="block text-sm font-medium text-text-secondary mb-2 flex items-center gap-2">
            <DollarSign className="h-4 w-4" />
            {t('display.currencyDisplay.label')}
          </label>
          <p className="text-xs text-text-muted mb-4">{t('display.currencyDisplay.description')}</p>
        </div>

        <div className="space-y-3">
          {(
            [
              {
                value: 'base' as AmountDisplayMode,
                label: t('display.currencyDisplay.base'),
                description: t('display.currencyDisplay.baseDescription'),
              },
              {
                value: 'native' as AmountDisplayMode,
                label: t('display.currencyDisplay.native'),
                description: t('display.currencyDisplay.nativeDescription'),
              },
              {
                value: 'both' as AmountDisplayMode,
                label: t('display.currencyDisplay.both'),
                description: t('display.currencyDisplay.bothDescription'),
              },
            ] satisfies { value: AmountDisplayMode; label: string; description: string }[]
          ).map(({ value, label, description }) => (
            <button
              key={value}
              type="button"
              onClick={() => handleDisplayModeChange(value)}
              className={`w-full p-4 rounded-lg border-2 transition-all text-left ${
                displayMode === value
                  ? 'border-primary bg-primary/10'
                  : 'border-border bg-surface-elevated hover:border-text-muted'
              }`}
              aria-pressed={displayMode === value}
            >
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-text-primary font-medium mb-1">{label}</div>
                  <div className="text-sm text-text-secondary">{description}</div>
                </div>
                {displayMode === value && (
                  <div className="h-2 w-2 rounded-full bg-primary shrink-0 ml-2" />
                )}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
