import { useTranslation } from 'react-i18next';
import { PageHeader } from '@/components/layout/PageHeader';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import { FinancialFreedomCalculator } from '@/components/financial-freedom/FinancialFreedomCalculator';

/**
 * Financial Freedom Calculator Page
 *
 * This page provides a comprehensive financial planning tool that helps users
 * determine when they can achieve financial independence based on their current
 * savings, expenses, and investment returns.
 *
 * Features:
 * - Time to Financial Freedom calculation (4% rule)
 * - Savings Longevity analysis
 * - Sensitivity analysis with optimistic/pessimistic scenarios
 * - Inflation adjustment
 * - Progress tracking
 */
export default function FinancialFreedomPage() {
  const { t } = useTranslation('tools');

  useDocumentTitle(t('financialFreedom.title'));

  return (
    <div className="p-8">
      <PageHeader
        title={t('financialFreedom.title')}
        description={t('financialFreedom.description')}
      />
      <div className="mt-8">
        <FinancialFreedomCalculator />
      </div>
    </div>
  );
}
