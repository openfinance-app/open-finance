import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { EarlyPayoffCalculator } from '@/components/early-payoff/EarlyPayoffCalculator';

export default function EarlyPayoffPage() {
  const { t } = useTranslation('tools');

  useDocumentTitle(t('earlyPayoff.title'));

  return (
    <div className="p-8">
      <PageHeader title={t('earlyPayoff.title')} description={t('earlyPayoff.description')} />
      <div className="mt-8">
        <EarlyPayoffCalculator />
      </div>
    </div>
  );
}
