/**
 * PurchaseSection Component
 * 
 * Purchase parameters form section
 * Requirements: REQ-1.1.1, REQ-1.1.1, REQ-1.1.2, REQ-1.1.3, REQ-1.1.4
 */

import React from 'react';
import { Home, Calculator, FileText, Shield, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { NumberInput } from '@/components/ui/NumberInput';
import { Label } from '@/components/ui/Label';
import { Switch } from '@/components/ui/Switch';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from '@/components/ui/Accordion';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { useTranslation } from 'react-i18next';
import type { PurchaseInputs, ValidationError } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

export interface PurchaseSectionProps {
  inputs: PurchaseInputs;
  derivedValues: {
    totalPrice: number;
    borrowedAmount: number;
    monthlyPayment: number;
    minimumDownPayment: number;
  };
  errors: ValidationError[];
  onUpdate: (field: keyof PurchaseInputs, value: number | boolean) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const PurchaseSection: React.FC<PurchaseSectionProps> = ({
  inputs,
  derivedValues,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `purchase.${field}`)?.message;

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-primary/10 cursor-pointer select-none"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Home className="h-5 w-5" />
            {t('purchaseSection.title')}
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
          <Accordion defaultValue="price" className="w-full">
            {/* Price Section */}
            <AccordionItem value="price">
              <AccordionTrigger className="text-sm font-medium">
                <div className="flex items-center gap-2">
                  <Home className="h-4 w-4" />
                  {t('purchaseSection.priceAndFees')}
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-3 pt-2">
                <div className="space-y-2">
                  <Label htmlFor="propertyPrice">{t('purchaseSection.propertyPrice')}</Label>
                  <NumberInput
                    id="propertyPrice"
                    value={String(inputs.propertyPrice)}
                    onChange={(value) => onUpdate('propertyPrice', parseFloat(value) || 0)}
                    min={0}
                  />
                  {getFieldError('propertyPrice') && (
                    <Alert variant="error" className="py-2">
                      <AlertDescription className="text-xs">{getFieldError('propertyPrice')}</AlertDescription>
                    </Alert>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="renovationAmount">{t('purchaseSection.renovationAmount')}</Label>
                  <NumberInput
                    id="renovationAmount"
                    value={String(inputs.renovationAmount)}
                    onChange={(value) => onUpdate('renovationAmount', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="flex items-center justify-between space-y-0 py-2">
                  <Label htmlFor="isNewProperty">{t('purchaseSection.newPropertyExemption')}</Label>
                  <Switch
                    id="isNewProperty"
                    checked={inputs.isNewProperty}
                    onCheckedChange={(checked) => onUpdate('isNewProperty', checked)}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="notaryFeesPercent">{t('purchaseSection.notaryFees')}</Label>
                  <NumberInput
                    id="notaryFeesPercent"
                    value={String(inputs.notaryFeesPercent)}
                    onChange={(value) => onUpdate('notaryFeesPercent', parseFloat(value) || 0)}
                    min={0}
                    max={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="agencyFees">{t('purchaseSection.agencyFees')}</Label>
                  <NumberInput
                    id="agencyFees"
                    value={String(inputs.agencyFees)}
                    onChange={(value) => onUpdate('agencyFees', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="pt-2 border-t">
                  <p className="text-sm text-muted-foreground">{t('purchaseSection.totalPrice')}</p>
                  <p className="text-lg font-semibold text-primary">
                    <ConvertedAmount amount={derivedValues.totalPrice} currency={baseCurrency} inline />
                  </p>
                </div>
              </AccordionContent>
            </AccordionItem>

            {/* Financing Section */}
            <AccordionItem value="financing">
              <AccordionTrigger className="text-sm font-medium">
                <div className="flex items-center gap-2">
                  <Calculator className="h-4 w-4" />
                  {t('purchaseSection.financing')}
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-3 pt-2">
                <div className="space-y-2">
                  <Label htmlFor="downPayment">{t('purchaseSection.downPayment')}</Label>
                  <NumberInput
                    id="downPayment"
                    value={String(inputs.downPayment)}
                    onChange={(value) => onUpdate('downPayment', parseFloat(value) || 0)}
                    min={0}
                  />
                  <p className="text-xs text-muted-foreground">
                    {t('purchaseSection.minimumSuggested')} :{' '}
                    <ConvertedAmount
                      amount={derivedValues.minimumDownPayment}
                      currency={baseCurrency}
                      inline
                    />
                  </p>
                  {getFieldError('downPayment') && (
                    <Alert variant="error" className="py-2">
                      <AlertDescription className="text-xs">{getFieldError('downPayment')}</AlertDescription>
                    </Alert>
                  )}
                </div>

                <div className="pt-2 border-t">
                  <p className="text-sm text-muted-foreground">{t('purchaseSection.borrowedAmount')}</p>
                  <p className="text-lg font-semibold">
                    <ConvertedAmount
                      amount={derivedValues.borrowedAmount}
                      currency={baseCurrency}
                      inline
                    />
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="loanDuration">{t('purchaseSection.loanDuration')}</Label>
                  <NumberInput
                    id="loanDuration"
                    value={String(inputs.loanDuration)}
                    onChange={(value) => onUpdate('loanDuration', parseInt(value, 10) || 1)}
                    min={1}
                    max={40}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="interestRate">{t('purchaseSection.annualRate')}</Label>
                  <NumberInput
                    id="interestRate"
                    value={String(inputs.interestRate)}
                    onChange={(value) => onUpdate('interestRate', parseFloat(value) || 0)}
                    min={0}
                    max={100}
                  />
                </div>

                <div className="pt-2 border-t bg-muted/50 p-2 rounded">
                  <p className="text-sm text-muted-foreground">{t('purchaseSection.monthlyPaymentExclInsurance')}</p>
                  <p className="text-xl font-bold text-primary">
                    <ConvertedAmount
                      amount={derivedValues.monthlyPayment}
                      currency={baseCurrency}
                      inline
                    />
                    {t('purchaseSection.perMonth')}
                  </p>
                </div>
              </AccordionContent>
            </AccordionItem>

            {/* Insurance and Fees Section */}
            <AccordionItem value="insurance">
              <AccordionTrigger className="text-sm font-medium">
                <div className="flex items-center gap-2">
                  <Shield className="h-4 w-4" />
                  {t('purchaseSection.insuranceAndFees')}
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-3 pt-2">
                <div className="space-y-2">
                  <Label htmlFor="totalInsurance">{t('purchaseSection.totalInsurance')}</Label>
                  <NumberInput
                    id="totalInsurance"
                    value={String(inputs.totalInsurance)}
                    onChange={(value) => onUpdate('totalInsurance', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="applicationFees">{t('purchaseSection.applicationFees')}</Label>
                  <NumberInput
                    id="applicationFees"
                    value={String(inputs.applicationFees)}
                    onChange={(value) => onUpdate('applicationFees', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="guaranteeFees">{t('purchaseSection.guaranteeFees')}</Label>
                  <NumberInput
                    id="guaranteeFees"
                    value={String(inputs.guaranteeFees)}
                    onChange={(value) => onUpdate('guaranteeFees', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="accountFees">{t('purchaseSection.accountFees')}</Label>
                  <NumberInput
                    id="accountFees"
                    value={String(inputs.accountFees)}
                    onChange={(value) => onUpdate('accountFees', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>
              </AccordionContent>
            </AccordionItem>

            {/* Recurring Charges Section */}
            <AccordionItem value="charges">
              <AccordionTrigger className="text-sm font-medium">
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4" />
                  {t('purchaseSection.recurringCharges')}
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-3 pt-2">
                <div className="space-y-2">
                  <Label htmlFor="propertyTax">{t('purchaseSection.annualPropertyTax')}</Label>
                  <NumberInput
                    id="propertyTax"
                    value={String(inputs.propertyTax)}
                    onChange={(value) => onUpdate('propertyTax', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="coOwnershipCharges">{t('purchaseSection.coOwnershipChargesPerYear')}</Label>
                  <NumberInput
                    id="coOwnershipCharges"
                    value={String(inputs.coOwnershipCharges)}
                    onChange={(value) => onUpdate('coOwnershipCharges', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="maintenancePercent">{t('purchaseSection.maintenancePercent')}</Label>
                  <NumberInput
                    id="maintenancePercent"
                    value={String(inputs.maintenancePercent)}
                    onChange={(value) => onUpdate('maintenancePercent', parseFloat(value) || 0)}
                    min={0}
                    max={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="homeInsurance">{t('purchaseSection.homeInsurance')}</Label>
                  <NumberInput
                    id="homeInsurance"
                    value={String(inputs.homeInsurance)}
                    onChange={(value) => onUpdate('homeInsurance', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="bankFees">{t('purchaseSection.bankFees')}</Label>
                  <NumberInput
                    id="bankFees"
                    value={String(inputs.bankFees)}
                    onChange={(value) => onUpdate('bankFees', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="garbageTax">{t('purchaseSection.garbageTax')}</Label>
                  <NumberInput
                    id="garbageTax"
                    value={String(inputs.garbageTax)}
                    onChange={(value) => onUpdate('garbageTax', parseFloat(value) || 0)}
                    min={0}
                  />
                </div>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </CardContent>
      </div>
    </Card>
  );
};

export default PurchaseSection;
