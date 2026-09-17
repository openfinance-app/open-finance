import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { LoanCalculator } from '@/components/loan-calculator/LoanCalculator';

export default function LoanCalculatorPage() {
  const { t } = useTranslation('tools');

  useDocumentTitle(t('loanCalculator.title', 'Loan Calculator'));

  return (
    <div className="p-8">
      <PageHeader
        title={t('loanCalculator.title', 'Loan Calculator')}
        description={t(
          'loanCalculator.description',
          'Calculate your monthly payments, total interest, and view the amortization schedule for your loan.'
        )}
      />
      <div className="mt-8">
        <LoanCalculator />
      </div>
    </div>
  );
}
