/**
 * ResaleSection Component
 * 
 * Resale target parameters form section
 * Requirements: REQ-1.4.1
 */

import React from 'react';
import { Target, Calendar, DollarSign, Percent, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Badge } from '@/components/ui/Badge';
import { useTranslation } from 'react-i18next';
import type { ResaleInputs, ValidationError } from '@/types/realEstateTools';

export interface ResaleSectionProps {
  inputs: ResaleInputs;
  loanDuration: number;
  errors: ValidationError[];
  onUpdate: (field: keyof ResaleInputs, value: number) => void;
  isValid: boolean;
  isOpen: boolean;
  onToggle: () => void;
}

export const ResaleSection: React.FC<ResaleSectionProps> = ({
  inputs,
  loanDuration,
  errors,
  onUpdate,
  isValid,
  isOpen,
  onToggle,
}) => {
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `resale.${field}`)?.message;

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-success/10 cursor-pointer select-none"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Target className="h-5 w-5" />
            {t('resaleSection.title')}
          </span>
          <ChevronDown
            className={`h-4 w-4 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
          />
        </CardTitle>
      </CardHeader>
      <div
        className={`overflow-hidden transition-all duration-300 ease-in-out ${isOpen ? 'max-h-[3000px] opacity-100' : 'max-h-0 opacity-0'
          }`}
      >
        <CardContent className="p-4 space-y-4">
          {/* Validation Badge */}
          {!isValid && (
            <Alert variant="warning" className="py-2">
              <AlertDescription className="text-xs">
                {t('resaleSection.warningYearExceedsLoan', { loanDuration })}
              </AlertDescription>
            </Alert>
          )}

          <div className="space-y-4">
            {/* Target Year */}
            <div className="space-y-2">
              <Label htmlFor="targetYear" className="flex items-center gap-2">
                <Calendar className="h-4 w-4" />
                {t('resaleSection.targetYear')}
                {isValid && <Badge variant="success" className="text-xs">{t('resaleSection.valid')}</Badge>}
              </Label>
              <Input
                id="targetYear"
                type="number"
                value={inputs.targetYear}
                onChange={(e) => onUpdate('targetYear', parseInt(e.target.value) || 1)}
                min={1}
                max={loanDuration}
                className={!isValid ? 'border-warning' : ''}
              />
              <p className="text-xs text-muted-foreground">
                {t('resaleSection.targetYearHelp', { loanDuration })}
              </p>
              {getFieldError('targetYear') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('targetYear')}</AlertDescription>
                </Alert>
              )}
            </div>

            {/* Desired Profit */}
            <div className="space-y-2">
              <Label htmlFor="desiredProfit" className="flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                {t('resaleSection.desiredProfit')}
              </Label>
              <Input
                id="desiredProfit"
                type="number"
                value={inputs.desiredProfit}
                onChange={(e) => onUpdate('desiredProfit', parseFloat(e.target.value) || 0)}
                min={0}
                step={1000}
              />
              <p className="text-xs text-muted-foreground">
                {t('resaleSection.desiredProfitHelp')}
              </p>
            </div>

            {/* Resale Fees */}
            <div className="space-y-2">
              <Label htmlFor="resaleFeesPercent" className="flex items-center gap-2">
                <Percent className="h-4 w-4" />
                {t('resaleSection.resaleFees')}
              </Label>
              <Input
                id="resaleFeesPercent"
                type="number"
                value={inputs.resaleFeesPercent}
                onChange={(e) => onUpdate('resaleFeesPercent', parseFloat(e.target.value) || 0)}
                min={0}
                max={100}
                step={0.1}
              />
              <p className="text-xs text-muted-foreground">
                {t('resaleSection.resaleFeesHelp')}
              </p>
              {getFieldError('resaleFeesPercent') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('resaleFeesPercent')}</AlertDescription>
                </Alert>
              )}
            </div>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default ResaleSection;
