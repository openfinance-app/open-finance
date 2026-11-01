import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/Button';

export function OwnershipIntentChoice({
  onSelect,
  onBack,
}: {
  onSelect: (intent: 'PURCHASE' | 'BUY' | 'GIFT' | 'PLANNED') => void;
  onBack: () => void;
}) {
  const { t } = useTranslation('assets');
  return (
    <div className="space-y-4">
      <p className="text-sm text-text-secondary">{t('ownership.intentPrompt')}</p>
      <div className="divide-y divide-border">
        {(['PURCHASE', 'BUY', 'GIFT', 'PLANNED'] as const).map(intent => (
          <button
            key={intent}
            type="button"
            onClick={() => onSelect(intent)}
            className="block w-full rounded-lg px-3 py-4 text-left hover:bg-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <span className="block font-medium">{t(`ownership.intents.${intent}`)}</span>
            <span className="mt-1 block text-sm text-text-secondary">
              {t(`ownership.intentHints.${intent}`)}
            </span>
          </button>
        ))}
      </div>
      <Button variant="secondary" onClick={onBack}>
        {t('ownership.previous')}
      </Button>
    </div>
  );
}
