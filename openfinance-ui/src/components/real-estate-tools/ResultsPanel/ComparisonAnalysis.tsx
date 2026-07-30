import React from 'react';
import { TrendingUp, Wallet, Calendar, CheckCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Progress } from '@/components/ui/Progress';
import { Badge } from '@/components/ui/Badge';
import { useTranslation } from 'react-i18next';
import type { BuyRentResults } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

export interface ComparisonAnalysisProps {
  results: BuyRentResults;
}

export const ComparisonAnalysis: React.FC<ComparisonAnalysisProps> = ({ results }) => {
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('realEstate');
  const { buy, rent, comparison } = results.summary;

  const buyAdvantage = comparison.netWorthDifference > 0;
  const worthDifference = Math.abs(comparison.netWorthDifference);
  const worthDifferencePercent = (worthDifference / Math.max(buy.netWorth, rent.netWorth)) * 100;

  return (
    <div className="space-y-6">
      {/* Winner Banner */}
      <Card className={buyAdvantage ? 'border-green-500 border-2' : 'border-yellow-500 border-2'}>
        <CardContent className="p-6">
          <div className="flex items-center justify-center gap-4">
            {buyAdvantage ? (
              <>
                <CheckCircle className="h-12 w-12 text-green-500" />
                <div className="text-center">
                  <p className="text-2xl font-bold text-green-600">
                    {t('comparison.winnerBuy')}
                  </p>
                  <p className="text-muted-foreground">
                    {t('comparison.winnerBuyDetail', {
                      amount: formatCurrency(worthDifference, baseCurrency),
                      years: results.years.length,
                    })}
                  </p>
                </div>
              </>
            ) : (
              <>
                <CheckCircle className="h-12 w-12 text-yellow-500" />
                <div className="text-center">
                  <p className="text-2xl font-bold text-yellow-600">
                    {t('comparison.winnerRent')}
                  </p>
                  <p className="text-muted-foreground">
                    {t('comparison.winnerRentDetail', {
                      amount: formatCurrency(worthDifference, baseCurrency),
                      years: results.years.length,
                    })}
                  </p>
                </div>
              </>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Key Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {/* Net Worth Comparison */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Wallet className="h-4 w-4" />
              {t('comparison.netWorthComparison')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div>
                <div className="flex justify-between mb-1">
                  <span className="text-sm">{t('comparison.buy')}</span>
                  <span className="text-sm font-semibold">{formatCurrency(buy.netWorth, baseCurrency)}</span>
                </div>
                <Progress value={(buy.netWorth / Math.max(buy.netWorth, rent.netWorth)) * 100} />
              </div>
              <div>
                <div className="flex justify-between mb-1">
                  <span className="text-sm">{t('comparison.rent')}</span>
                  <span className="text-sm font-semibold">{formatCurrency(rent.netWorth, baseCurrency)}</span>
                </div>
                <Progress value={(rent.netWorth / Math.max(buy.netWorth, rent.netWorth)) * 100} />
              </div>
              <div className="pt-2 border-t">
                <p className="text-sm text-muted-foreground">{t('comparison.difference')}</p>
                <p className={`text-xl font-bold ${comparison.netWorthDifference >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {comparison.netWorthDifference >= 0 ? '+' : ''}{formatCurrency(comparison.netWorthDifference, baseCurrency)}
                </p>
                <p className="text-xs text-muted-foreground">
                  ({worthDifferencePercent.toFixed(1)}%)
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Monthly Cost Comparison */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Calendar className="h-4 w-4" />
              {t('comparison.averageMonthlyCost')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex justify-between items-center">
                <span className="text-sm">{t('comparison.buy')}</span>
                <Badge variant={buy.averageMonthlyCost < rent.averageMonthlyCost ? 'default' : 'secondary'}>
                  {formatCurrency(buy.averageMonthlyCost, baseCurrency)}/{t('comparison.perMonth')}
                </Badge>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-sm">{t('comparison.rent')}</span>
                <Badge variant={rent.averageMonthlyCost < buy.averageMonthlyCost ? 'default' : 'secondary'}>
                  {formatCurrency(rent.averageMonthlyCost, baseCurrency)}/{t('comparison.perMonth')}
                </Badge>
              </div>
              <div className="pt-2 border-t">
                <p className="text-sm text-muted-foreground">{t('comparison.monthlyGap')}</p>
                <p className={`text-xl font-bold ${comparison.monthlyGap >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                  {comparison.monthlyGap >= 0 ? '+' : ''}{formatCurrency(comparison.monthlyGap, baseCurrency)}/{t('comparison.perMonth')}
                </p>
                <p className="text-xs text-muted-foreground">
                  {comparison.monthlyGap >= 0
                    ? t('comparison.buyCheaper')
                    : t('comparison.rentCheaper')}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Total Cost Analysis */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <TrendingUp className="h-4 w-4" />
              {t('comparison.costAnalysis')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              <div>
                <p className="text-sm text-muted-foreground">{t('comparison.totalBuyCost')}</p>
                <p className="text-lg font-semibold">{formatCurrency(buy.totalCost, baseCurrency)}</p>
              </div>
              <div>
                <p className="text-sm text-muted-foreground">{t('comparison.totalRentCost')}</p>
                <p className="text-lg font-semibold">{formatCurrency(rent.totalCost, baseCurrency)}</p>
              </div>
              <div className="pt-2 border-t">
                <p className="text-sm text-muted-foreground">{t('comparison.netBuyExpense')}</p>
                <p className="text-lg font-semibold text-red-600">{formatCurrency(buy.netExpense, baseCurrency)}</p>
              </div>
              <div>
                <p className="text-sm text-muted-foreground">{t('comparison.netRentExpense')}</p>
                <p className="text-lg font-semibold text-red-600">{formatCurrency(rent.netExpense, baseCurrency)}</p>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Pros and Cons */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-green-600">
              <CheckCircle className="h-5 w-5" />
              {t('comparison.buyAdvantagesTitle')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2">
              <li className="flex items-start gap-2">
                <TrendingUp className="h-4 w-4 mt-1 text-green-600" />
                <span>{t('comparison.buyAdvantage1')}</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle className="h-4 w-4 mt-1 text-green-600" />
                <span>{t('comparison.buyAdvantage2')}</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle className="h-4 w-4 mt-1 text-green-600" />
                <span>{t('comparison.buyAdvantage3')}</span>
              </li>
              {buy.netWorth > rent.netWorth && (
                <li className="flex items-start gap-2">
                  <TrendingUp className="h-4 w-4 mt-1 text-green-600" />
                  <span>{t('comparison.betterPerformance', { years: results.years.length })}</span>
                </li>
              )}
            </ul>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-blue-600">
              <CheckCircle className="h-5 w-5" />
              {t('comparison.rentAdvantagesTitle')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-2">
              <li className="flex items-start gap-2">
                <Wallet className="h-4 w-4 mt-1 text-blue-600" />
                <span>{t('comparison.rentAdvantage1')}</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle className="h-4 w-4 mt-1 text-blue-600" />
                <span>{t('comparison.rentAdvantage2')}</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle className="h-4 w-4 mt-1 text-blue-600" />
                <span>{t('comparison.rentAdvantage3')}</span>
              </li>
              {rent.netWorth > buy.netWorth && (
                <li className="flex items-start gap-2">
                  <TrendingUp className="h-4 w-4 mt-1 text-blue-600" />
                  <span>{t('comparison.betterPerformance', { years: results.years.length })}</span>
                </li>
              )}
            </ul>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default ComparisonAnalysis;
