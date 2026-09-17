import { Users } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { EmptyState } from '@/components/layout/EmptyState';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export default function CommunityPage() {
  const { t } = useTranslation('tools');
  useDocumentTitle(t('community.title'));

  return (
    <div className="p-8">
      <PageHeader title={t('community.title')} description={t('community.description')} />
      <EmptyState
        icon={Users}
        title={t('community.comingSoon')}
        description={t('community.comingSoonDescription')}
      />
    </div>
  );
}
