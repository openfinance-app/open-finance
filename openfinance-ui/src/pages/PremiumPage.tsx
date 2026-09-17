import { Gift } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export default function PremiumPage() {
  const { t } = useTranslation('tools');
  useDocumentTitle(t('premium.title'));

  return (
    <div className="p-8">
      <PageHeader title={t('premium.title')} description={t('premium.description')} />
      <EmptyState
        icon={Gift}
        title={t('premium.comingSoon')}
        description={t('premium.comingSoonDescription')}
      />
    </div>
  );
}
