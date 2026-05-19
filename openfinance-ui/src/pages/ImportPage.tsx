/**
 * ImportPage Component
 * Task 7.1.6: Create ImportPage with file upload interface
 * Task 7.4.9: Updated to use ImportWizard
 * 
 * Main page for importing transactions from external files
 */
import { useTranslation } from 'react-i18next';
import { FileText, AlertCircle } from 'lucide-react';
import { PageHeader } from '@/components/layout/PageHeader';
import { ImportWizard } from '@/components/import/ImportWizard';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';

export default function ImportPage() {
  const { t } = useTranslation('import');
  useDocumentTitle(t('title'));

  return (
    <div className="min-h-screen bg-app-bg">
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Page Header */}
        <PageHeader
          title={t('title')}
          description={t('description')}
        />

        {/* Instructions Section */}
        <div className="mt-6 bg-surface border border-border-subtle rounded-lg p-6">
          <div className="flex items-start space-x-3">
            <FileText className="h-5 w-5 text-primary mt-0.5 flex-shrink-0" />
            <div className="flex-1 space-y-2">
              <h3 className="text-lg font-semibold text-text-primary">
                {t('supportedFormats.title')}
              </h3>
              <ul className="text-sm text-text-secondary space-y-1">
                <li>
                  <strong>{t('supportedFormats.qif.name')}</strong> - {t('supportedFormats.qif.description')}
                </li>
                <li>
                  <strong>{t('supportedFormats.ofx.name')}</strong> - {t('supportedFormats.ofx.description')}
                </li>
                <li>
                  <strong>{t('supportedFormats.csv.name')}</strong> - {t('supportedFormats.csv.description')}
                </li>
                <li>
                  <strong>{t('supportedFormats.json.name')}</strong> - {t('supportedFormats.json.description')}
                </li>
              </ul>
            </div>
          </div>
        </div>

        {/* How to Download Section */}
        <div className="mt-4 bg-blue-500/5 border border-blue-500/20 rounded-lg p-6">
          <div className="flex items-start space-x-3">
            <AlertCircle className="h-5 w-5 text-blue-500 mt-0.5 flex-shrink-0" />
            <div className="flex-1 space-y-2">
              <h3 className="text-lg font-semibold text-text-primary">
                {t('howTo.title')}
              </h3>
              <div className="text-sm text-text-secondary space-y-2">
                <p>
                  {t('howTo.intro')}
                </p>
                <ol className="list-decimal list-inside space-y-1 ml-2">
                  {Array.isArray(t('howTo.steps', { returnObjects: true })) &&
                    (t('howTo.steps', { returnObjects: true }) as string[]).map((step, i) => (
                      <li key={i}>{step}</li>
                    ))}
                </ol>
              </div>
            </div>
          </div>
        </div>

        {/* Import Wizard */}
        <div className="mt-8">
          <ImportWizard />
        </div>

        {/* Data Privacy Notice */}
        <div className="mt-8 text-xs text-text-tertiary text-center">
          <p>
            {t('privacy')}
          </p>
        </div>
      </div>
    </div>
  );
}
