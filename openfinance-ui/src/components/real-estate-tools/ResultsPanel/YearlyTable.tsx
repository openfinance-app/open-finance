import React, { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { useTranslation } from 'react-i18next';
import type { BuyRentResults } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

export interface YearlyTableProps {
  results: BuyRentResults;
}

export const YearlyTable: React.FC<YearlyTableProps> = ({ results }) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');
  const [expandedYears, setExpandedYears] = useState<Set<number>>(new Set());

  const toggleYear = (year: number) => {
    const newExpanded = new Set(expandedYears);
    if (newExpanded.has(year)) {
      newExpanded.delete(year);
    } else {
      newExpanded.add(year);
    }
    setExpandedYears(newExpanded);
  };

  const isPriceAboveMinimum = (propertyValue: number, minimumPrice: number) => {
    return minimumPrice > propertyValue;
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>{t('yearlyTable.title')}</CardTitle>
        <div className="text-sm text-muted-foreground">
          {t('yearlyTable.yearsCount', { count: results.years.length })}
        </div>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">{t('yearlyTable.year')}</TableHead>
                <TableHead>{t('yearlyTable.buyCost')}</TableHead>
                <TableHead>{t('yearlyTable.buyCumulative')}</TableHead>
                <TableHead>{t('yearlyTable.propertyValue')}</TableHead>
                <TableHead>{t('yearlyTable.remainingCapital')}</TableHead>
                <TableHead>{t('yearlyTable.minResalePrice')}</TableHead>
                <TableHead>{t('yearlyTable.rentCost')}</TableHead>
                <TableHead>{t('yearlyTable.rentCumulative')}</TableHead>
                <TableHead>{t('yearlyTable.savings')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {results.years.map((year) => {
                const isExpanded = expandedYears.has(year.year);
                const showWarning = isPriceAboveMinimum(
                  year.buy.propertyValue,
                  year.buy.minimumResalePrice
                );

                return (
                  <React.Fragment key={year.year}>
                    <TableRow
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => toggleYear(year.year)}
                    >
                      <TableCell className="font-medium">
                        <div className="flex items-center gap-1">
                          {isExpanded ? (
                            <ChevronUp className="h-4 w-4" />
                          ) : (
                            <ChevronDown className="h-4 w-4" />
                          )}
                          {year.year}
                        </div>
                      </TableCell>
                      <TableCell>
                        <ConvertedAmount amount={year.buy.annualCost} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell>
                        <ConvertedAmount amount={year.buy.cumulativeCost} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell className="text-green-600">
                        <ConvertedAmount amount={year.buy.propertyValue} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell className="text-red-600">
                        <ConvertedAmount amount={year.buy.remainingCapital} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell className={showWarning ? 'text-red-600 font-semibold' : ''}>
                        <ConvertedAmount amount={year.buy.minimumResalePrice} currency={baseCurrency} inline />
                        {showWarning && ' ⚠️'}
                      </TableCell>
                      <TableCell>
                        <ConvertedAmount amount={year.rent.annualCost} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell>
                        <ConvertedAmount amount={year.rent.cumulativeCost} currency={baseCurrency} inline />
                      </TableCell>
                      <TableCell className="text-green-600">
                        <ConvertedAmount amount={year.rent.savings} currency={baseCurrency} inline />
                      </TableCell>
                    </TableRow>

                    {isExpanded && (
                      <TableRow>
                        <TableCell colSpan={9} className="bg-muted/30 p-4">
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                            <div>
                              <p className="font-medium text-muted-foreground">{t('yearlyTable.buyDetails')}</p>
                              <ul className="mt-2 space-y-1">
                                <li>
                                  {t('yearlyTable.mortgage')}:{' '}
                                  <ConvertedAmount amount={year.buy.details.mortgage} currency={baseCurrency} inline />
                                </li>
                                <li>
                                  {t('yearlyTable.insurance')}:{' '}
                                  <ConvertedAmount amount={year.buy.details.insurance} currency={baseCurrency} inline />
                                </li>
                                <li>
                                  {t('yearlyTable.propertyTax')}:{' '}
                                  <ConvertedAmount
                                    amount={year.buy.details.propertyTax}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                                <li>
                                  {t('yearlyTable.coOwnershipCharges')}:{' '}
                                  <ConvertedAmount
                                    amount={year.buy.details.coOwnershipCharges}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                                <li>
                                  {t('yearlyTable.maintenance')}:{' '}
                                  <ConvertedAmount
                                    amount={year.buy.details.maintenance}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                              </ul>
                            </div>
                            <div>
                              <p className="font-medium text-muted-foreground">{t('yearlyTable.netWorth')}</p>
                              <ul className="mt-2 space-y-1">
                                <li>
                                  {t('yearlyTable.value')}:{' '}
                                  <ConvertedAmount amount={year.buy.propertyValue} currency={baseCurrency} inline />
                                </li>
                                <li>
                                  {t('yearlyTable.capitalDue')}:{' '}
                                  <ConvertedAmount amount={year.buy.remainingCapital} currency={baseCurrency} inline />
                                </li>
                                <li className="font-semibold">
                                  {t('yearlyTable.net')}:{' '}
                                  <ConvertedAmount
                                    amount={year.buy.propertyValue - year.buy.remainingCapital}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                              </ul>
                            </div>
                            <div>
                              <p className="font-medium text-muted-foreground">{t('yearlyTable.rentDetails')}</p>
                              <ul className="mt-2 space-y-1">
                                <li>
                                  {t('yearlyTable.rent')}:{' '}
                                  <ConvertedAmount
                                    amount={year.rent.annualCost * 0.8}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                                <li>
                                  {t('yearlyTable.charges')}:{' '}
                                  <ConvertedAmount
                                    amount={year.rent.annualCost * 0.2}
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                                <li>
                                  {t('yearlyTable.total')}:{' '}
                                  <ConvertedAmount amount={year.rent.annualCost} currency={baseCurrency} inline />
                                </li>
                              </ul>
                            </div>
                            <div>
                              <p className="font-medium text-muted-foreground">{t('yearlyTable.comparison')}</p>
                              <ul className="mt-2 space-y-1">
                                <li>
                                  {t('yearlyTable.difference')}:{' '}
                                  <ConvertedAmount
                                    amount={
                                      year.buy.propertyValue - year.buy.remainingCapital - year.rent.savings
                                    }
                                    currency={baseCurrency}
                                    inline
                                  />
                                </li>
                                <li>
                                  {t('yearlyTable.advantage')}: {(year.buy.propertyValue - year.buy.remainingCapital) > year.rent.savings
                                    ? t('results.buyGenitive')
                                    : t('results.rentGenitive')}
                                </li>
                              </ul>
                            </div>
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
                  </React.Fragment>
                );
              })}
            </TableBody>
          </Table>
        </div>

        <div className="mt-4 flex justify-center">
          <Button
            variant="outline"
            onClick={() => setExpandedYears(new Set(results.years.map(y => y.year)))}
            className="mr-2"
          >
            {t('yearlyTable.expandAll')}
          </Button>
          <Button
            variant="outline"
            onClick={() => setExpandedYears(new Set())}
          >
            {t('yearlyTable.collapseAll')}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};

export default YearlyTable;
