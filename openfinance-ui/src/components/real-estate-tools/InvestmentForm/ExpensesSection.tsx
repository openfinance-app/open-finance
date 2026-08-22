/**
 * ExpensesSection Component
 * 
 * Owner expenses parameters
 * Requirements: REQ-2.3.x
 */

import React from 'react';
import { Receipt, FileText, Shield, Percent, Building2, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
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
              <Input
                id="propertyTax"
                type="number"
                value={inputs.propertyTax}
                onChange={(e) => onUpdate('propertyTax', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="cfe">{t('expensesSection.cfe')}</Label>
              <Input
                id="cfe"
                type="number"
                value={inputs.cfe}
                onChange={(e) => onUpdate('cfe', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="cvae">{t('expensesSection.cvae')}</Label>
              <Input
                id="cvae"
                type="number"
                value={inputs.cvae}
                onChange={(e) => onUpdate('cvae', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
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
              <Input
                id="nonRecoverableCharges"
                type="number"
                value={inputs.nonRecoverableCharges}
                onChange={(e) => onUpdate('nonRecoverableCharges', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="annualMaintenance">{t('expensesSection.annualMaintenance')}</Label>
              <Input
                id="annualMaintenance"
                type="number"
                value={inputs.annualMaintenance}
                onChange={(e) => onUpdate('annualMaintenance', parseFloat(e.target.value) || 0)}
                min={0}
                step={100}
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
              <Input
                id="managementFees"
                type="number"
                value={inputs.managementFees}
                onChange={(e) => onUpdate('managementFees', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="pnoInsurance">{t('expensesSection.pnoInsurance')}</Label>
              <Input
                id="pnoInsurance"
                type="number"
                value={inputs.pnoInsurance}
                onChange={(e) => onUpdate('pnoInsurance', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="accountingFees">{t('expensesSection.accountingFees')}</Label>
              <Input
                id="accountingFees"
                type="number"
                value={inputs.accountingFees}
                onChange={(e) => onUpdate('accountingFees', parseFloat(e.target.value) || 0)}
                min={0}
                step={50}
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
            <Input
              id="marginalTaxRate"
              type="number"
              value={inputs.marginalTaxRate}
              onChange={(e) => onUpdate('marginalTaxRate', parseFloat(e.target.value) || 0)}
              min={0}
              max={60}
              step={0.5}
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
