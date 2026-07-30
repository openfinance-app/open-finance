/**
 * RevenueSection Component
 * 
 * Rental revenue parameters
 * Requirements: REQ-2.2.x
 */

import React from 'react';
import { Euro, Users, AlertTriangle, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Progress } from '@/components/ui/Progress';
import { useTranslation } from 'react-i18next';
import type { RentalRevenueInputs, ValidationError } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';

export interface RevenueSectionProps {
  inputs: RentalRevenueInputs;
  errors: ValidationError[];
  onUpdate: (field: keyof RentalRevenueInputs, value: number) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const RevenueSection: React.FC<RevenueSectionProps> = ({
  inputs,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `revenue.${field}`)?.message;

  // Calculate gross annual revenue
  const annualRent = (inputs.monthlyRent + inputs.recoverableCharges) * 12;
  const effectiveRevenue = annualRent * (inputs.occupancyRate / 100) * (1 - inputs.badDebtRate / 100);

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-success/10 cursor-pointer select-none pb-4"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Euro className="h-5 w-5" />
            {t('revenueSection.title')}
          </span>
          <ChevronDown
            className={`h-4 w-4 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
          />
        </CardTitle>
      </CardHeader>
      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${isOpen ? 'max-h-[2000px] opacity-100' : 'max-h-0 opacity-0'
          }`}
      >
        <CardContent className="p-4 space-y-4">
          {/* Monthly Rent */}
          <div className="space-y-2">
            <Label htmlFor="monthlyRent">{t('revenueSection.monthlyRent')}</Label>
            <Input
              id="monthlyRent"
              type="number"
              value={inputs.monthlyRent}
              onChange={(e) => onUpdate('monthlyRent', parseFloat(e.target.value) || 0)}
              min={0}
              step={50}
            />
            {getFieldError('monthlyRent') && (
              <Alert variant="error" className="py-2">
                <AlertDescription className="text-xs">{getFieldError('monthlyRent')}</AlertDescription>
              </Alert>
            )}
          </div>

          {/* Recoverable Charges */}
          <div className="space-y-2">
            <Label htmlFor="recoverableCharges">{t('revenueSection.recoverableCharges')}</Label>
            <Input
              id="recoverableCharges"
              type="number"
              value={inputs.recoverableCharges}
              onChange={(e) => onUpdate('recoverableCharges', parseFloat(e.target.value) || 0)}
              min={0}
              step={10}
            />
          </div>

          {/* Occupancy Rate */}
          <div className="space-y-2">
            <Label htmlFor="occupancyRate" className="flex items-center gap-2">
              <Users className="h-4 w-4" />
              {t('revenueSection.occupancyRate')}
            </Label>
            <Input
              id="occupancyRate"
              type="number"
              value={inputs.occupancyRate}
              onChange={(e) => onUpdate('occupancyRate', parseFloat(e.target.value) || 0)}
              min={0}
              max={100}
              step={1}
            />
            <Progress value={inputs.occupancyRate} className="h-2" />
            {getFieldError('occupancyRate') && (
              <Alert variant="error" className="py-2">
                <AlertDescription className="text-xs">{getFieldError('occupancyRate')}</AlertDescription>
              </Alert>
            )}
          </div>

          {/* Bad Debt Rate */}
          <div className="space-y-2">
            <Label htmlFor="badDebtRate" className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4" />
              {t('revenueSection.badDebtRate')}
            </Label>
            <Input
              id="badDebtRate"
              type="number"
              value={inputs.badDebtRate}
              onChange={(e) => onUpdate('badDebtRate', parseFloat(e.target.value) || 0)}
              min={0}
              max={100}
              step={0.5}
            />
            <Progress value={inputs.badDebtRate} className="h-2" />
            {getFieldError('badDebtRate') && (
              <Alert variant="error" className="py-2">
                <AlertDescription className="text-xs">{getFieldError('badDebtRate')}</AlertDescription>
              </Alert>
            )}
          </div>

          {/* Revenue Summary */}
          <div className="pt-2 border-t space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-muted-foreground">{t('revenueSection.annualGrossRent')}</span>
              <span>{formatCurrency(annualRent, baseCurrency)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('revenueSection.effectiveRevenue')}</span>
              <span className="font-semibold text-success">{formatCurrency(effectiveRevenue, baseCurrency)}</span>
            </div>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default RevenueSection;
