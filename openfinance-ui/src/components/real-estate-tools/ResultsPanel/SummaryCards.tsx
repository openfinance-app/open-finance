/**
 * SummaryCards Component
 * 
 * Summary cards for Buy and Rent scenarios
 * Requirements: REQ-1.6.1, REQ-1.6.2
 */

import React from 'react';
import { Home, Key, TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { useTranslation } from 'react-i18next';
import type { BuyRentResults } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

export interface SummaryCardsProps {
  results: BuyRentResults;
}

export const SummaryCards: React.FC<SummaryCardsProps> = ({ results }) => {
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('realEstate');
  const { buy, rent, comparison } = results.summary;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      {/* Buy Scenario Card */}
      <Card className="border-l-4 border-l-primary">
        <CardHeader className="bg-primary/10">
          <CardTitle className="flex items-center gap-2">
            <Home className="h-5 w-5" />
            {t('results.buyScenario')}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-sm text-muted-foreground">{t('results.averageMonthlyCost')}</p>
              <p className="text-lg font-semibold">{formatCurrency(buy.averageMonthlyCost, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.totalCost')}</p>
              <p className="text-lg font-semibold">{formatCurrency(buy.totalCost, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.propertyValue')}</p>
              <p className="text-lg font-semibold text-green-600">{formatCurrency(buy.finalPropertyValue, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.remainingCapital')}</p>
              <p className="text-lg font-semibold text-red-600">{formatCurrency(buy.remainingCapital, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.netWorth')}</p>
              <p className="text-xl font-bold text-primary">{formatCurrency(buy.netWorth, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.creditCost')}</p>
              <p className="text-lg font-semibold">{formatCurrency(buy.totalCreditCost, baseCurrency)}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Rent Scenario Card */}
      <Card className="border-l-4 border-l-warning">
        <CardHeader className="bg-warning/10">
          <CardTitle className="flex items-center gap-2">
            <Key className="h-5 w-5" />
            {t('results.rentScenario')}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <p className="text-sm text-muted-foreground">{t('results.averageMonthlyCost')}</p>
              <p className="text-lg font-semibold">{formatCurrency(rent.averageMonthlyCost, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.totalCost')}</p>
              <p className="text-lg font-semibold">{formatCurrency(rent.totalCost, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.accumulatedSavings')}</p>
              <p className="text-lg font-semibold text-green-600">{formatCurrency(rent.accumulatedSavings, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.netExpense')}</p>
              <p className="text-lg font-semibold text-red-600">{formatCurrency(rent.netExpense, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.netWorth')}</p>
              <p className="text-xl font-bold text-warning">{formatCurrency(rent.netWorth, baseCurrency)}</p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground">{t('results.monthlySavings')}</p>
              <p className="text-lg font-semibold">{formatCurrency(results.years[0]?.rent.savings || 0, baseCurrency)}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Comparison Card */}
      <Card className="md:col-span-2 border-l-4 border-l-info">
        <CardHeader className="bg-info/10">
          <CardTitle className="flex items-center gap-2">
            <TrendingUp className="h-5 w-5" />
            {t('results.comparison')}
          </CardTitle>
        </CardHeader>
        <CardContent className="p-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="text-center">
              <p className="text-sm text-muted-foreground mb-2">{t('results.netWorthDifference')}</p>
              <p className={`text-2xl font-bold ${comparison.netWorthDifference >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                 {comparison.netWorthDifference >= 0 ? '+' : ''}{formatCurrency(comparison.netWorthDifference, baseCurrency)}
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {t('results.inFavorOf', { winner: comparison.winner === 'buy' ? t('results.buyGenitive') : t('results.rentGenitive') })}
              </p>
            </div>
            <div className="text-center">
              <p className="text-sm text-muted-foreground mb-2">{t('results.expenseDifference')}</p>
              <p className={`text-2xl font-bold ${comparison.netExpenseDifference >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                 {comparison.netExpenseDifference >= 0 ? '+' : ''}{formatCurrency(comparison.netExpenseDifference, baseCurrency)}
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {t('results.netSavings')}
              </p>
            </div>
            <div className="text-center">
              <p className="text-sm text-muted-foreground mb-2">{t('results.monthlyGap')}</p>
              <p className={`text-2xl font-bold ${comparison.monthlyGap >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                 {comparison.monthlyGap >= 0 ? '+' : ''}{formatCurrency(comparison.monthlyGap, baseCurrency)}
              </p>
              <p className="text-xs text-muted-foreground mt-1">
                {t('results.perMonth')}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export default SummaryCards;
