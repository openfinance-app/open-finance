import React from 'react';
import { Calendar, TrendingUp, Home, Key, ArrowRight, AlertCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/Table';
import { Trans, useTranslation } from 'react-i18next';
import type { YearNAnalysis } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

export interface YearNAnalysisCardProps {
  analysis: YearNAnalysis;
  targetYear: number;
}

export const YearNAnalysisCard: React.FC<YearNAnalysisCardProps> = ({
  analysis,
  targetYear,
}) => {
  const { baseCurrency } = useAuthContext();
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
                    <ConvertedAmount amount={analysis.propertyValue} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.remainingCapital')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    <ConvertedAmount amount={analysis.remainingCapital} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netWorth')}</TableCell>
                  <TableCell className="text-right font-bold text-primary">
                    <ConvertedAmount amount={analysis.netWorth} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.costsTotal')}</TableCell>
                  <TableCell className="text-right">
                    <ConvertedAmount amount={analysis.totalCostsBuy} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netExpense')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    <ConvertedAmount amount={analysis.netExpenseBuy} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            {/* Resale Price Alert */}
            {!minimumPriceAchievable && (
              <Alert variant="warning" className="mt-4">
                <AlertCircle className="h-4 w-4" />
                <AlertDescription>
                  <Trans
                    t={t}
                    i18nKey="results.resalePriceAlert"
                    components={{
                      minPrice: (
                        <ConvertedAmount
                          amount={analysis.minimumResalePrice}
                          currency={baseCurrency}
                          inline
                        />
                      ),
                      propertyValue: (
                        <ConvertedAmount
                          amount={analysis.propertyValue}
                          currency={baseCurrency}
                          inline
                        />
                      ),
                    }}
                  />
                </AlertDescription>
              </Alert>
            )}

            {minimumPriceAchievable && (
              <Alert variant="success" className="mt-4">
                <TrendingUp className="h-4 w-4" />
                <AlertDescription>
                  <Trans
                    t={t}
                    i18nKey="results.resalePriceSuccess"
                    components={{
                      profit: (
                        <ConvertedAmount
                          amount={analysis.propertyValue - analysis.minimumResalePrice}
                          currency={baseCurrency}
                          inline
                        />
                      ),
                    }}
                  />
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
                    <ConvertedAmount amount={analysis.rentSavings} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netWorth')}</TableCell>
                  <TableCell className="text-right font-bold text-warning">
                    <ConvertedAmount amount={analysis.rentSavings} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.costsTotal')}</TableCell>
                  <TableCell className="text-right">
                    <ConvertedAmount amount={analysis.totalCostsRent} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
                <TableRow>
                  <TableCell className="font-medium">{t('results.netExpense')}</TableCell>
                  <TableCell className="text-right text-red-600">
                    <ConvertedAmount amount={analysis.netExpenseRent} currency={baseCurrency} inline />
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>

            {/* Rent Advantage */}
            {!buyAdvantage && (
              <Alert className="mt-4">
                <TrendingUp className="h-4 w-4" />
                <AlertDescription>
                  <Trans
                    t={t}
                    i18nKey="results.rentAdvantage"
                    components={{
                      savings: (
                        <ConvertedAmount
                          amount={analysis.netExpenseRent - analysis.netExpenseBuy}
                          currency={baseCurrency}
                          inline
                        />
                      ),
                    }}
                  />
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
                {buyAdvantage ? '+' : ''}
                <ConvertedAmount
                  amount={analysis.netWorth - analysis.rentSavings}
                  currency={baseCurrency}
                  inline
                />
              </p>
              <p className="text-sm text-muted-foreground mt-1">
                {t('results.inFavorOf', { scenario: buyAdvantage ? t('results.buyGenitive') : t('results.rentGenitive') })}
              </p>
            </div>
            <div>
              <p className="text-sm text-muted-foreground mb-2">{t('results.expenseDifference')}</p>
              <p className={`text-2xl font-bold ${analysis.netExpenseBuy < analysis.netExpenseRent ? 'text-primary' : 'text-warning'}`}>
                <ConvertedAmount
                  amount={Math.abs(analysis.netExpenseBuy - analysis.netExpenseRent)}
                  currency={baseCurrency}
                  inline
                />
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
          <Trans
            t={t}
            i18nKey="results.conclusionYearN"
            values={{
              year: targetYear,
              winner: buyAdvantage ? t('results.buyGenitive') : t('results.rentGenitive'),
            }}
            components={{
              winnerAmount: (
                <ConvertedAmount
                  amount={buyAdvantage ? analysis.netWorth : analysis.rentSavings}
                  currency={baseCurrency}
                  inline
                />
              ),
              loserAmount: (
                <ConvertedAmount
                  amount={buyAdvantage ? analysis.rentSavings : analysis.netWorth}
                  currency={baseCurrency}
                  inline
                />
              ),
            }}
          />
        </AlertDescription>
      </Alert>
    </div>
  );
};

export default YearNAnalysisCard;
