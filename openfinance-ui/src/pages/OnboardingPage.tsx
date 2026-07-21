/**
 * OnboardingPage — shown once after a user's first login.
 * Collects: country, base currency, secondary currency, language,
 * date format, number format, and currency display preference.
 * On submit the user's `onboardingComplete` flag is set to true
 * and they are redirected to the dashboard.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Globe, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { Button } from '@/components/ui/Button';
import { useCompleteOnboarding } from '@/hooks/useAuth';
import { LanguageSelector } from '@/components/settings/LanguageSelector';
import { CountrySelector } from '@/components/common/CountrySelector';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import type { OnboardingRequest } from '@/types/user';

// ---------------------------------------------------------------------------
// Locale-based defaults
// ---------------------------------------------------------------------------

/** Map browser country code → default currency code */
const COUNTRY_CURRENCY_MAP: Record<string, string> = {
  US: 'USD', CA: 'CAD', GB: 'GBP', AU: 'AUD', NZ: 'NZD', JP: 'JPY',
  CN: 'CNY', IN: 'INR', BR: 'BRL', MX: 'MXN', KR: 'KRW', SG: 'SGD',
  HK: 'HKD', CH: 'CHF', NO: 'NOK', SE: 'SEK', DK: 'DKK',
  // Euro-zone
  FR: 'EUR', DE: 'EUR', IT: 'EUR', ES: 'EUR', PT: 'EUR', NL: 'EUR',
  BE: 'EUR', AT: 'EUR', FI: 'EUR', IE: 'EUR', LU: 'EUR', GR: 'EUR',
  SK: 'EUR', SI: 'EUR', EE: 'EUR', LV: 'EUR', LT: 'EUR', CY: 'EUR',
  MT: 'EUR',
};

function detectDefaults(): {
  country: string;
  baseCurrency: string;
  language: string;
  dateFormat: OnboardingRequest['dateFormat'];
  numberFormat: OnboardingRequest['numberFormat'];
} {
  const lang = navigator.language || 'en-US';
  const parts = lang.split('-');
  const langCode = parts[0].toLowerCase();
  // Country from second part of locale tag, else fall back to language heuristic
  let countryCode = parts[1]?.toUpperCase() ?? '';
  if (!countryCode) {
    countryCode = langCode === 'fr' ? 'FR' : 'US';
  }

  const baseCurrency = COUNTRY_CURRENCY_MAP[countryCode] ?? DEFAULT_CURRENCY;

  // Language — only en/fr are supported
  const language: string = langCode === 'fr' ? 'fr' : 'en';

  // Date format
  const dateFormat: OnboardingRequest['dateFormat'] =
    langCode === 'fr' || (countryCode && !['US', 'CA'].includes(countryCode) && langCode !== 'en')
      ? 'DD/MM/YYYY'
      : countryCode === 'US'
      ? 'MM/DD/YYYY'
      : 'DD/MM/YYYY';

  // Number format
  const numberFormat: OnboardingRequest['numberFormat'] =
    langCode === 'fr'
      ? '1 234,56'
      : countryCode === 'US' || countryCode === 'CA' || countryCode === 'GB'
      ? '1,234.56'
      : '1.234,56';

  return { country: countryCode, baseCurrency, language, dateFormat, numberFormat };
}

// ---------------------------------------------------------------------------
// Section wrapper
// ---------------------------------------------------------------------------

function Section({ title, hint, children }: { title: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <div>
        <label className="block text-sm font-medium text-text-primary">{title}</label>
        {hint && <p className="text-xs text-text-muted mt-0.5">{hint}</p>}
      </div>
      {children}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Radio group helper
// ---------------------------------------------------------------------------

interface RadioOption<T extends string> {
  value: T;
  label: string;
  description?: string;
}

function RadioGroup<T extends string>({
  name,
  value,
  onChange,
  options,
}: {
  name: string;
  value: T;
  onChange: (v: T) => void;
  options: RadioOption<T>[];
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
      {options.map((opt) => (
        <label
          key={opt.value}
          className={cn(
            'flex items-start gap-2.5 rounded-lg border p-2.5 cursor-pointer transition-colors',
            value === opt.value
              ? 'border-primary bg-primary/10'
              : 'border-border bg-surface hover:border-border-hover'
          )}
        >
          <input
            type="radio"
            name={name}
            value={opt.value}
            checked={value === opt.value}
            onChange={() => onChange(opt.value)}
            className="mt-0.5 accent-yellow-500 shrink-0"
          />
          <div>
            <span className="text-sm font-medium text-text-primary">{opt.label}</span>
            {opt.description && (
              <p className="text-xs text-text-muted mt-0.5">{opt.description}</p>
            )}
          </div>
        </label>
      ))}
    </div>
  );
}

// ---------------------------------------------------------------------------
// OnboardingPage
// ---------------------------------------------------------------------------

export default function OnboardingPage() {
  const { t, i18n } = useTranslation('onboarding');
  const defaults = detectDefaults();

  const [country, setCountry] = useState(defaults.country);
  const [baseCurrency, setBaseCurrency] = useState(defaults.baseCurrency);
  const [secondaryCurrency, setSecondaryCurrency] = useState<string | undefined>(undefined);
  const [language, setLanguage] = useState<string>(defaults.language);
  const [dateFormat, setDateFormat] = useState<OnboardingRequest['dateFormat']>(defaults.dateFormat);
  const [numberFormat, setNumberFormat] = useState<OnboardingRequest['numberFormat']>(defaults.numberFormat);
  const [amountDisplayMode, setAmountDisplayMode] = useState<OnboardingRequest['amountDisplayMode']>('base');

  // Sync i18n when language changes
  useEffect(() => {
    if (i18n.language !== language) {
      i18n.changeLanguage(language);
    }
  }, [language, i18n]);

  const completeOnboarding = useCompleteOnboarding();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    completeOnboarding.mutate({
      country,
      baseCurrency,
      secondaryCurrency: secondaryCurrency ?? '',
      language,
      dateFormat,
      numberFormat,
      amountDisplayMode,
    });
  };

  const dateFormatOptions: RadioOption<OnboardingRequest['dateFormat']>[] = [
    { value: 'MM/DD/YYYY', label: 'MM/DD/YYYY', description: 'e.g. 12/31/2025' },
    { value: 'DD/MM/YYYY', label: 'DD/MM/YYYY', description: 'e.g. 31/12/2025' },
    { value: 'YYYY-MM-DD', label: 'YYYY-MM-DD', description: 'e.g. 2025-12-31' },
  ];

  const numberFormatOptions: RadioOption<OnboardingRequest['numberFormat']>[] = [
    {
      value: '1,234.56',
      label: t('numberFormat.usUkStyle'),
      description: t('numberFormat.usUkDescription'),
    },
    {
      value: '1.234,56',
      label: t('numberFormat.europeanStyle'),
      description: t('numberFormat.europeanDescription'),
    },
    {
      value: '1 234,56',
      label: t('numberFormat.frenchSwissStyle'),
      description: t('numberFormat.frenchSwissDescription'),
    },
  ];

  const amountDisplayModeOptions: RadioOption<OnboardingRequest['amountDisplayMode']>[] = [
    {
      value: 'base',
      label: t('amountDisplayMode.base'),
      description: t('amountDisplayMode.base_description'),
    },
    {
      value: 'native',
      label: t('amountDisplayMode.native'),
      description: t('amountDisplayMode.native_description'),
    },
    {
      value: 'both',
      label: t('amountDisplayMode.both'),
      description: t('amountDisplayMode.both_description'),
    },
  ];

  return (
    <div className="min-h-screen bg-background flex items-start justify-center px-4 py-8 relative">
      {/* Language switcher */}
      <div className="absolute top-4 right-4 z-10" role="region" aria-label="Language selection">
        <LanguageSelector />
      </div>

      <div className="w-full max-w-xl">
        {/* Header */}
        <div className="text-center mb-5">
          <div className="inline-flex items-center justify-center w-11 h-11 rounded-full bg-primary/15 mb-3">
            <Globe className="w-5 h-5 text-primary" />
          </div>
          <h1 className="text-2xl font-bold text-text-primary mb-1">{t('title')}</h1>
          <p className="text-text-secondary text-sm">{t('subtitle')}</p>
        </div>

        {/* Form card */}
        <div className="bg-surface rounded-xl border border-border p-5">
          <form onSubmit={handleSubmit} className="space-y-4">

            {/* Error banner */}
            {completeOnboarding.isError && (
              <div
                className="bg-error/10 border border-error rounded-lg p-3 text-error text-sm"
                role="alert"
              >
                {t('error')}
              </div>
            )}

            {/* Country + Language — side by side */}
            <div className="grid grid-cols-2 gap-4">
              <Section title={t('country.label')}>
                <CountrySelector
                  value={country}
                  onValueChange={setCountry}
                  placeholder={t('country.placeholder')}
                  searchPlaceholder={t('country.searchPlaceholder')}
                  noMatch={t('country.noMatch')}
                />
              </Section>

              <Section title={t('language.label')}>
                <div className="flex rounded-lg border border-border overflow-hidden h-10">
                  {(['en', 'fr'] as const).map((lang) => (
                    <button
                      key={lang}
                      type="button"
                      onClick={() => setLanguage(lang)}
                      className={cn(
                        'flex-1 text-sm font-medium transition-colors',
                        language === lang
                          ? 'bg-primary text-background'
                          : 'text-text-secondary hover:bg-surface-elevated'
                      )}
                    >
                      {lang === 'en' ? t('language.english') : t('language.french')}
                    </button>
                  ))}
                </div>
              </Section>
            </div>

            {/* Base Currency + Secondary Currency — side by side */}
            <div className="grid grid-cols-2 gap-4">
              <Section title={t('baseCurrency.label')} hint={t('baseCurrency.hint')}>
                <CurrencySelector
                  value={baseCurrency}
                  onValueChange={setBaseCurrency}
                  placeholder={t('baseCurrency.placeholder')}
                />
              </Section>

              <Section title={t('secondaryCurrency.label')} hint={t('secondaryCurrency.hint')}>
                <CurrencySelector
                  value={secondaryCurrency}
                  onValueChange={(v: string) => setSecondaryCurrency(v || undefined)}
                  placeholder={t('secondaryCurrency.placeholder')}
                  allowNone
                />
              </Section>
            </div>

            {/* Date Format — compact 3-button toggle strip */}
            <Section title={t('dateFormat.label')}>
              <div className="flex rounded-lg border border-border overflow-hidden h-9">
                {dateFormatOptions.map((opt) => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => setDateFormat(opt.value)}
                    className={cn(
                      'flex-1 text-xs font-mono font-medium transition-colors border-r last:border-r-0 border-border',
                      dateFormat === opt.value
                        ? 'bg-primary text-background'
                        : 'text-text-secondary hover:bg-surface-elevated'
                    )}
                  >
                    {opt.value}
                  </button>
                ))}
              </div>
            </Section>

            {/* Number Format */}
            <Section title={t('numberFormat.label')}>
              <RadioGroup
                name="numberFormat"
                value={numberFormat}
                onChange={setNumberFormat}
                options={numberFormatOptions}
              />
            </Section>

            {/* Amount Display Mode */}
            <Section title={t('amountDisplayMode.label')} hint={t('amountDisplayMode.hint')}>
              <RadioGroup
                name="amountDisplayMode"
                value={amountDisplayMode}
                onChange={setAmountDisplayMode}
                options={amountDisplayModeOptions}
              />
            </Section>

            {/* Submit */}
            <Button
              type="submit"
              disabled={completeOnboarding.isPending || !country || !baseCurrency}
              className="w-full"
            >
              {completeOnboarding.isPending ? (
                <span className="flex items-center justify-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t('submitting')}
                </span>
              ) : (
                t('submit')
              )}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
