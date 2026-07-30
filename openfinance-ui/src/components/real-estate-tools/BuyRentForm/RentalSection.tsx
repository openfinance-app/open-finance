/**
 * RentalSection Component
 * 
 * Rental parameters form section
 * Requirements: REQ-1.2.1
 */

import React from 'react';
import { Key, DollarSign, Shield, Trash2, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { useTranslation } from 'react-i18next';
import type { RentalInputs, ValidationError } from '@/types/realEstateTools';

export interface RentalSectionProps {
  inputs: RentalInputs;
  errors: ValidationError[];
  onUpdate: (field: keyof RentalInputs, value: number) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const RentalSection: React.FC<RentalSectionProps> = ({
  inputs,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `rental.${field}`)?.message;

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-warning/10 cursor-pointer select-none"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Key className="h-5 w-5" />
            {t('rentalSection.title')}
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
          <div className="space-y-4">
            {/* Rent */}
            <div className="space-y-2">
              <Label htmlFor="monthlyRent">{t('rentalSection.monthlyRent')}</Label>
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

            {/* Charges */}
            <div className="space-y-2">
              <Label htmlFor="monthlyCharges">{t('rentalSection.monthlyCharges')}</Label>
              <Input
                id="monthlyCharges"
                type="number"
                value={inputs.monthlyCharges}
                onChange={(e) => onUpdate('monthlyCharges', parseFloat(e.target.value) || 0)}
                min={0}
                step={10}
              />
            </div>

            {/* Security Deposit */}
            <div className="space-y-2">
              <Label htmlFor="securityDeposit" className="flex items-center gap-2">
                <Key className="h-4 w-4" />
                {t('rentalSection.securityDeposit')}
              </Label>
              <Input
                id="securityDeposit"
                type="number"
                value={inputs.securityDeposit}
                onChange={(e) => onUpdate('securityDeposit', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
              />
            </div>

            {/* Insurance */}
            <div className="space-y-2">
              <Label htmlFor="rentalInsurance" className="flex items-center gap-2">
                <Shield className="h-4 w-4" />
                {t('rentalSection.rentalInsurance')}
              </Label>
              <Input
                id="rentalInsurance"
                type="number"
                value={inputs.rentalInsurance}
                onChange={(e) => onUpdate('rentalInsurance', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
              />
            </div>

            {/* Garbage Tax */}
            <div className="space-y-2">
              <Label htmlFor="rentalGarbageTax" className="flex items-center gap-2">
                <Trash2 className="h-4 w-4" />
                {t('rentalSection.garbageTax')}
              </Label>
              <Input
                id="rentalGarbageTax"
                type="number"
                value={inputs.garbageTax}
                onChange={(e) => onUpdate('garbageTax', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
              />
            </div>

            <hr className="border-border" />

            {/* Initial Savings */}
            <div className="space-y-2">
              <Label htmlFor="initialSavings" className="flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                {t('rentalSection.initialSavings')}
              </Label>
              <Input
                id="initialSavings"
                type="number"
                value={inputs.initialSavings}
                onChange={(e) => onUpdate('initialSavings', parseFloat(e.target.value) || 0)}
                min={0}
                step={1000}
                readOnly
                className="bg-muted"
              />
              <p className="text-xs text-muted-foreground">
                {t('rentalSection.autoAdjusted')}
              </p>
            </div>

            {/* Monthly Savings */}
            <div className="space-y-2">
              <Label htmlFor="monthlySavings" className="flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                {t('rentalSection.monthlySavings')}
              </Label>
              <Input
                id="monthlySavings"
                type="number"
                value={inputs.monthlySavings}
                onChange={(e) => onUpdate('monthlySavings', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
                readOnly
                className="bg-muted"
              />
              <p className="text-xs text-muted-foreground">
                {t('rentalSection.calculatedFromGap')}
              </p>
            </div>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default RentalSection;
