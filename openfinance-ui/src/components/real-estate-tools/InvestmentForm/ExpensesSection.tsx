/**
 * ExpensesSection Component
 * 
 * Owner expenses parameters
 * Requirements: REQ-2.3.x
 */

import React from 'react';
import { Receipt, FileText, Shield, Percent, Building2, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { NumberInput } from '@/components/ui/NumberInput';
import { Label } from '@/components/ui/Label';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { Separator } from '@/components/ui/Separator';
import { useTranslation } from 'react-i18next';
import type { OwnerExpensesInputs, ValidationError } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

export interface ExpensesSectionProps {
  inputs: OwnerExpensesInputs;
  errors: ValidationError[];
  onUpdate: (field: keyof OwnerExpensesInputs, value: number) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const ExpensesSection: React.FC<ExpensesSectionProps> = ({
  inputs,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `expenses.${field}`)?.message;

  const totalDeductibleExpenses =
    inputs.propertyTax +
    inputs.nonRecoverableCharges +
    inputs.annualMaintenance +
    inputs.cfe +
    inputs.cvae +
    inputs.managementFees +
    inputs.pnoInsurance +
    inputs.accountingFees;

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-info/10 cursor-pointer select-none pb-4"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Receipt className="h-5 w-5" />
            {t('expensesSection.title')}
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
          {/* Tax Section */}
          <div className="space-y-3">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <FileText className="h-4 w-4" />
              {t('expensesSection.taxesTitle')}
            </h4>

            <div className="space-y-2">
              <Label htmlFor="propertyTax">{t('expensesSection.propertyTax')}</Label>
              <NumberInput
                id="propertyTax"
                value={String(inputs.propertyTax)}
                onChange={(value) => onUpdate('propertyTax', parseFloat(value) || 0)}
                min={0}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="cfe">{t('expensesSection.cfe')}</Label>
              <NumberInput
                id="cfe"
                value={String(inputs.cfe)}
                onChange={(value) => onUpdate('cfe', parseFloat(value) || 0)}
                min={0}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="cvae">{t('expensesSection.cvae')}</Label>
              <NumberInput
                id="cvae"
                value={String(inputs.cvae)}
                onChange={(value) => onUpdate('cvae', parseFloat(value) || 0)}
                min={0}
              />
            </div>
          </div>

          <Separator />

          {/* Building Charges */}
          <div className="space-y-3">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <Building2 className="h-4 w-4" />
              {t('expensesSection.coOwnershipTitle')}
            </h4>

            <div className="space-y-2">
              <Label htmlFor="nonRecoverableCharges">{t('expensesSection.nonRecoverableCharges')}</Label>
              <NumberInput
                id="nonRecoverableCharges"
                value={String(inputs.nonRecoverableCharges)}
                onChange={(value) => onUpdate('nonRecoverableCharges', parseFloat(value) || 0)}
                min={0}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="annualMaintenance">{t('expensesSection.annualMaintenance')}</Label>
              <NumberInput
                id="annualMaintenance"
                value={String(inputs.annualMaintenance)}
                onChange={(value) => onUpdate('annualMaintenance', parseFloat(value) || 0)}
                min={0}
              />
            </div>
          </div>

          <Separator />

          {/* Management & Insurance */}
          <div className="space-y-3">
            <h4 className="text-sm font-medium flex items-center gap-2">
              <Shield className="h-4 w-4" />
              {t('expensesSection.managementTitle')}
            </h4>

            <div className="space-y-2">
              <Label htmlFor="managementFees">{t('expensesSection.managementFees')}</Label>
              <NumberInput
                id="managementFees"
                value={String(inputs.managementFees)}
                onChange={(value) => onUpdate('managementFees', parseFloat(value) || 0)}
                min={0}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="pnoInsurance">{t('expensesSection.pnoInsurance')}</Label>
              <NumberInput
                id="pnoInsurance"
                value={String(inputs.pnoInsurance)}
                onChange={(value) => onUpdate('pnoInsurance', parseFloat(value) || 0)}
                min={0}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="accountingFees">{t('expensesSection.accountingFees')}</Label>
              <NumberInput
                id="accountingFees"
                value={String(inputs.accountingFees)}
                onChange={(value) => onUpdate('accountingFees', parseFloat(value) || 0)}
                min={0}
              />
            </div>
          </div>

          <Separator />

          {/* Tax Rate */}
          <div className="space-y-2">
            <Label htmlFor="marginalTaxRate" className="flex items-center gap-2">
              <Percent className="h-4 w-4" />
              {t('expensesSection.marginalTaxRate')}
            </Label>
            <NumberInput
              id="marginalTaxRate"
              value={String(inputs.marginalTaxRate)}
              onChange={(value) => onUpdate('marginalTaxRate', parseFloat(value) || 0)}
              min={0}
              max={60}
            />
            {getFieldError('marginalTaxRate') && (
              <Alert variant="error" className="py-2">
                <AlertDescription className="text-xs">{getFieldError('marginalTaxRate')}</AlertDescription>
              </Alert>
            )}
          </div>

          {/* Expenses Summary */}
          <div className="pt-2 border-t">
            <div className="flex justify-between">
              <span className="text-muted-foreground">{t('expensesSection.totalDeductibleExpenses')}</span>
              <span className="font-semibold text-error">
                <ConvertedAmount amount={totalDeductibleExpenses} currency={baseCurrency} inline />
              </span>
            </div>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default ExpensesSection;
