/**
 * RegimeCard Component
 * 
 * Individual tax regime result card
 * Requirements: REQ-2.6.1, REQ-2.6.2, REQ-2.6.3
 */

import React from 'react';
import { AlertTriangle, TrendingUp, Wallet, Percent, Building, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Table, TableBody, TableCell, TableRow } from '@/components/ui/Table';
import { getRegimeDisplayName, getRegimeDescription } from '@/utils/taxRegimeCalculations';
import type { RegimeCalculationResult, TaxRegime } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useTranslation } from 'react-i18next';

export interface RegimeCardProps {
  regime: TaxRegime;
  result: RegimeCalculationResult;
  isRecommended?: boolean;
  isOpen: boolean;
  onToggle: () => void;
}

export const RegimeCard: React.FC<RegimeCardProps> = ({
  regime,
  result,
  isRecommended = false,
  isOpen,
  onToggle,
}) => {
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('realEstate');
  const isEligible = result.eligible;
  const hasWarnings = result.details.warnings.length > 0;

  return (
    <Card className={`h-full ${isRecommended ? 'border-success border-2' : ''} ${!isEligible ? 'opacity-75' : ''}`}>
      <CardHeader
        className={`${isRecommended ? 'bg-success/10' : 'bg-muted/50'} pb-4 cursor-pointer select-none`}
        onClick={onToggle}
      >
        <div className="flex items-center justify-between">
          <CardTitle className="text-lg flex items-center gap-2">
            <Building className="h-5 w-5" />
            {getRegimeDisplayName(regime)}
          </CardTitle>
          <div className="flex items-center gap-2">
            {isRecommended && (
              <Badge variant="success">{t('regimeCard.recommended')}</Badge>
            )}
            {isEligible ? (
              <Badge variant="default">{t('regimeCard.eligible')}</Badge>
            ) : (
              <Badge variant="destructive">{t('regimeCard.notEligible')}</Badge>
            )}
            <ChevronDown
              className={`h-4 w-4 text-muted-foreground transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
            />
          </div>
        </div>
        <p className="text-sm text-muted-foreground mt-2">
          {getRegimeDescription(regime)}
        </p>
      </CardHeader>

      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${isOpen ? 'max-h-[2000px] opacity-100' : 'max-h-0 opacity-0'
          }`}
      >

        <CardContent className="p-4 space-y-4">
          {/* Warnings */}
          {hasWarnings && (
            <Alert variant="warning" className="py-2">
              <AlertTriangle className="h-4 w-4" />
              <AlertDescription className="text-xs">
                {result.details.warnings.join(', ')}
              </AlertDescription>
            </Alert>
          )}

          {/* Performance Metrics */}
          <div className="grid grid-cols-3 gap-4 text-center">
            <div className="bg-muted/50 p-3 rounded">
              <p className="text-xs text-muted-foreground mb-1">{t('regimeCard.cashFlow')}</p>
              <p className={`text-lg font-bold ${result.performance.monthlyCashFlow >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                {formatCurrency(result.performance.monthlyCashFlow, baseCurrency)}
              </p>
              <p className="text-xs text-muted-foreground">{t('regimeCard.perMonth')}</p>
            </div>
            <div className="bg-muted/50 p-3 rounded">
              <p className="text-xs text-muted-foreground mb-1">{t('regimeCard.grossYield')}</p>
              <p className="text-lg font-bold">
                {result.performance.grossYield.toFixed(2)}%
              </p>
            </div>
            <div className="bg-muted/50 p-3 rounded">
              <p className="text-xs text-muted-foreground mb-1">{t('regimeCard.netYield')}</p>
              <p className={`text-lg font-bold ${isRecommended ? 'text-success' : ''}`}>
                {result.performance.netYield.toFixed(2)}%
              </p>
            </div>
          </div>

          {/* Investment Summary */}
          <div>
              <h4 className="text-sm font-medium mb-2 flex items-center gap-2">
                <Building className="h-4 w-4" />
                {t('regimeCard.investment')}
              </h4>
              <Table>
                <TableBody>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.totalPrice')}</TableCell>
                    <TableCell className="py-1 text-right font-medium">
                      {formatCurrency(result.investment.totalPrice, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.annualCreditCost')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.investment.annualCreditCost, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.monthlyPayment')}</TableCell>
                  <TableCell className="py-1 text-right">
                    {formatCurrency(result.investment.monthlyCreditPayment, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>

          {/* Revenue */}
          <div>
              <h4 className="text-sm font-medium mb-2 flex items-center gap-2">
                <TrendingUp className="h-4 w-4" />
                {t('regimeCard.revenue')}
              </h4>
              <Table>
                <TableBody>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.gross')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.revenue.gross, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.deduction')}</TableCell>
                    <TableCell className="py-1 text-right text-red-600">
                      -{formatCurrency(result.revenue.deduction, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm font-medium">{t('regimeCard.taxable')}</TableCell>
                  <TableCell className="py-1 text-right font-medium">
                    {formatCurrency(result.revenue.taxable, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>

          {/* Charges */}
          <div>
              <h4 className="text-sm font-medium mb-2 flex items-center gap-2">
                <Wallet className="h-4 w-4" />
                {t('regimeCard.charges')}
              </h4>
              <Table>
                <TableBody>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.credit')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.charges.credit, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.other')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.charges.other, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm font-medium">{t('regimeCard.total')}</TableCell>
                  <TableCell className="py-1 text-right font-medium">
                    {formatCurrency(result.charges.total, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>

          {/* Taxes */}
          <div>
              <h4 className="text-sm font-medium mb-2 flex items-center gap-2">
                <Percent className="h-4 w-4" />
                {t('regimeCard.taxation')}
              </h4>
              <Table>
                <TableBody>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.incomeTax')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.taxation.incomeTax, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="py-1 text-sm">{t('regimeCard.socialContributions')}</TableCell>
                    <TableCell className="py-1 text-right">
                      {formatCurrency(result.taxation.socialContributions, baseCurrency)}
                    </TableCell>
                  </TableRow>
                  <TableRow className="bg-muted/50">
                    <TableCell className="py-1 text-sm font-medium">{t('regimeCard.totalTaxes')}</TableCell>
                  <TableCell className="py-1 text-right font-medium text-red-600">
                    {formatCurrency(result.taxation.totalTaxes, baseCurrency)}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default RegimeCard;
