import React from 'react';
import { Calendar, TrendingUp, Home, Key, ArrowRight, AlertCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/Table';
import { useTranslation } from 'react-i18next';
import type { YearNAnalysis } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

export interface YearNAnalysisCardProps {
  analysis: YearNAnalysis;
  targetYear: number;
}

export const YearNAnalysisCard: React.FC<YearNAnalysisCardProps> = ({
  analysis,
  targetYear,
}) => {
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('realEstate');
  const buyAdvantage = analysis.netWorth > analysis.rentSavings;
  const minimumPriceAchievable = analysis.propertyValue >= analysis.minimumResalePrice;

  return (
    <div className="space-y-6">
      {/* Header */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calendar className="h-5 w-5" />
            {t('results.analysisYearN', { year: targetYear })}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center gap-4 py-4">
            {buyAdvantage ? (
              <Badge variant="success" className="text-lg px-4 py-2">
                <Home className="mr-2 h-5 w-5" />
                {t('results.buyRecommended')}
              </Badge>
            ) : (
              <Badge variant="warning" className="text-lg px-4 py-2">
                <Key className="mr-2 h-5 w-5" />
                {t('results.rentRecommended')}
              </Badge>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Key Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Buy Scenario */}
        <Card className="border-l-4 border-l-primary">
          <CardHeader className="bg-primary/10">
            <CardTitle className="flex items-center gap-2">
              <Home className="h-5 w-5" />
              {t('results.buyScenario')}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4">
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium">{t('results.propertyValue')}</TableCell>
                  <TableCell className="text-right text-green-600">
                    {formatCurrency(analysis.propertyValue, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.remainingCapital')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    {formatCurrency(analysis.remainingCapital, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netWorth')}</TableCell>
                  <TableCell className="text-right font-bold text-primary">
                    {formatCurrency(analysis.netWorth, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.costsTotal')}</TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(analysis.totalCostsBuy, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netExpense')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    {formatCurrency(analysis.netExpenseBuy, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            {/* Resale Price Alert */}
            {!minimumPriceAchievable && (
              <Alert variant="warning" className="mt-4">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  {t('results.resalePriceAlert', {
                    minPrice: formatCurrency(analysis.minimumResalePrice, baseCurrency),
                    propertyValue: formatCurrency(analysis.propertyValue, baseCurrency),
                  })}
                </AlertDescription>
              </Alert>
            )}

            {minimumPriceAchievable && (
              <Alert variant="success" className="mt-4">
                <TrendingUp className="h-4 w-4" />
                <AlertDescription>
                  {t('results.resalePriceSuccess', {
                    profit: formatCurrency(analysis.propertyValue - analysis.minimumResalePrice, baseCurrency),
                  })}
                </AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>

        {/* Rent Scenario */}
        <Card className="border-l-4 border-l-warning">
          <CardHeader className="bg-warning/10">
            <CardTitle className="flex items-center gap-2">
              <Key className="h-5 w-5" />
              {t('results.rentScenario')}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4">
            <Table>
              <TableBody>
                <TableRow>
                  <TableCell className="font-medium">{t('results.accumulatedSavings')}</TableCell>
                  <TableCell className="text-right font-bold text-green-600">
                    {formatCurrency(analysis.rentSavings, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netWorth')}</TableCell>
                  <TableCell className="text-right font-bold text-warning">
                    {formatCurrency(analysis.rentSavings, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.costsTotal')}</TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(analysis.totalCostsRent, baseCurrency)}
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netExpense')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    {formatCurrency(analysis.netExpenseRent, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            {/* Rent Advantage */}
            {!buyAdvantage && (
              <Alert className="mt-4">
                <TrendingUp className="h-4 w-4" />
                <AlertDescription>
                  {t('results.rentAdvantage', {
                    savings: formatCurrency(analysis.netExpenseRent - analysis.netExpenseBuy, baseCurrency),
                  })}
                </AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Comparison */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <ArrowRight className="h-5 w-5" />
            {t('results.directComparison')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-center">
            <div>
              <p className="text-sm text-muted-foreground mb-2">{t('results.netWorthDifference')}</p>
              <p className={`text-2xl font-bold ${buyAdvantage ? 'text-primary' : 'text-warning'}`}>
                {buyAdvantage ? '+' : ''}{formatCurrency(analysis.netWorth - analysis.rentSavings, baseCurrency)}
              </p>
              <p className="text-sm text-muted-foreground mt-1">
                {t('results.inFavorOf', { scenario: buyAdvantage ? t('results.buyGenitive') : t('results.rentGenitive') })}
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground mb-2">{t('results.expenseDifference')}</p>
              <p className={`text-2xl font-bold ${analysis.netExpenseBuy < analysis.netExpenseRent ? 'text-primary' : 'text-warning'}`}>
                {formatCurrency(Math.abs(analysis.netExpenseBuy - analysis.netExpenseRent), baseCurrency)}
              </p>
              <p className="text-sm text-muted-foreground mt-1">
                {t('results.savedWith', { scenario: analysis.netExpenseBuy < analysis.netExpenseRent ? t('results.buyGenitive') : t('results.rentGenitive') })}
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground mb-2">{t('results.annualProfitability')}</p>
              <p className="text-2xl font-bold">
                {analysis.annualProfitability.toFixed(2)}%
              </p>
              <p className="text-sm text-muted-foreground mt-1">
                {t('results.forBuyScenario')}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Conclusion */}
      <Alert className={buyAdvantage ? 'border-primary' : 'border-warning'}>
        <AlertDescription className="text-center text-lg">
          {t('results.conclusionYearN', {
            year: targetYear,
            winner: buyAdvantage ? t('results.buyGenitive') : t('results.rentGenitive'),
            winnerAmount: formatCurrency(buyAdvantage ? analysis.netWorth : analysis.rentSavings, baseCurrency),
            loserAmount: formatCurrency(buyAdvantage ? analysis.rentSavings : analysis.netWorth, baseCurrency),
          })}
        </AlertDescription>
      </Alert>
    </div>
  );
};

export default YearNAnalysisCard;
