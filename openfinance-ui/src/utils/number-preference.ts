import i18n from '@/i18n';
import type { NumberFormat } from '@/context/NumberFormatContext';

export function preferredNumberFormat(): NumberFormat {
  try {
    const stored = localStorage.getItem('open_finance_number_format');
    if (stored === '1,234.56' || stored === '1.234,56' || stored === '1 234,56') return stored;
  } catch {
    /* storage unavailable */
  }
  return i18n.language?.startsWith('fr') ? '1 234,56' : '1,234.56';
}

export function numberLocale(): string {
  return { '1,234.56': 'en-US', '1.234,56': 'de-DE', '1 234,56': 'fr-FR' }[preferredNumberFormat()];
}
