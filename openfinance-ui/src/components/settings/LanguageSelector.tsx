/**
 * LanguageSelector component
 * Task 1.5.1 (i18n): Dropdown for switching between supported UI languages.
 *
 * Calls `setLocale()` from LocaleContext which:
 *  1. Changes the active i18next language (async - waits for translations to load)
 *  2. Updates document.documentElement.lang
 *  3. Persists the preference to the backend UserSettings API
 *
 * Requirements: REQ-3.1.1
 */
import { Globe, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/context/LocaleContext';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { countryFlagClass } from '@/utils/countryUtils';

interface Language {
  code: string;
  /** Translation key in the settings namespace, e.g. 'display.language.english' */
  labelKey: string;
  /** ISO 3166-1 alpha-2 country code for the flag icon */
  flagCode: string;
}

const SUPPORTED_LANGUAGES: Language[] = [
  { code: 'en', labelKey: 'display.language.english', flagCode: 'gb' },
  { code: 'fr', labelKey: 'display.language.french', flagCode: 'fr' },
];

export function LanguageSelector() {
  const { t } = useTranslation('settings');
  const { locale, setLocale, isChangingLocale } = useLocale();

  // Find the current language object to display its label
  const currentLanguage = SUPPORTED_LANGUAGES.find(lang => lang.code === locale);

  const handleLanguageChange = async (newLocale: string) => {
    await setLocale(newLocale);
  };

  return (
    <div className="flex items-center gap-3">
      {isChangingLocale ? (
        <Loader2 size={16} className="text-primary animate-spin flex-shrink-0" />
      ) : (
        <Globe size={16} className="text-text-secondary flex-shrink-0" />
      )}
      <Select value={locale} onValueChange={handleLanguageChange} disabled={isChangingLocale}>
        <SelectTrigger className="w-48" aria-label={t('display.language.label')}>
          <SelectValue>
            {currentLanguage && (
              <span className="flex items-center gap-2">
                <span
                  className={`${countryFlagClass(currentLanguage.flagCode)} text-base`}
                  style={{ width: '1.33em', lineHeight: 1 }}
                />
                {t(currentLanguage.labelKey)}
              </span>
            )}
          </SelectValue>
        </SelectTrigger>
        <SelectContent>
          {SUPPORTED_LANGUAGES.map(lang => (
            <SelectItem key={lang.code} value={lang.code}>
              <span className="flex items-center gap-2">
                <span
                  className={`${countryFlagClass(lang.flagCode)} text-base`}
                  style={{ width: '1.33em', lineHeight: 1 }}
                />
                {t(lang.labelKey)}
              </span>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
