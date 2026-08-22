/**
 * PurchaseSection Component
 * 
 * Purchase parameters form section
 * Requirements: REQ-1.1.1, REQ-1.1.1, REQ-1.1.2, REQ-1.1.3, REQ-1.1.4
 */

import React from 'react';
import { Home, Calculator, FileText, Shield, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
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
                  <Input
                    id="propertyPrice"
                    type="number"
                    value={inputs.propertyPrice}
                    onChange={(e) => onUpdate('propertyPrice', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={1000}
                  />
                  {getFieldError('propertyPrice') && (
                    <Alert variant="error" className="py-2">
                      <AlertDescription className="text-xs">{getFieldError('propertyPrice')}</AlertDescription>
                    </Alert>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="renovationAmount">{t('purchaseSection.renovationAmount')}</Label>
                  <Input
                    id="renovationAmount"
                    type="number"
                    value={inputs.renovationAmount}
                    onChange={(e) => onUpdate('renovationAmount', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
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
                  <Input
                    id="notaryFeesPercent"
                    type="number"
                    value={inputs.notaryFeesPercent}
                    onChange={(e) => onUpdate('notaryFeesPercent', parseFloat(e.target.value) || 0)}
                    min={0}
                    max={100}
                    step={0.1}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="agencyFees">{t('purchaseSection.agencyFees')}</Label>
                  <Input
                    id="agencyFees"
                    type="number"
                    value={inputs.agencyFees}
                    onChange={(e) => onUpdate('agencyFees', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
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
                  <Input
                    id="downPayment"
                    type="number"
                    value={inputs.downPayment}
                    onChange={(e) => onUpdate('downPayment', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={1000}
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
                  <Input
                    id="loanDuration"
                    type="number"
                    value={inputs.loanDuration}
                    onChange={(e) => onUpdate('loanDuration', parseInt(e.target.value) || 1)}
                    min={1}
                    max={40}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="interestRate">{t('purchaseSection.annualRate')}</Label>
                  <Input
                    id="interestRate"
                    type="number"
                    value={inputs.interestRate}
                    onChange={(e) => onUpdate('interestRate', parseFloat(e.target.value) || 0)}
                    min={0}
                    max={100}
                    step={0.01}
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
                  <Input
                    id="totalInsurance"
                    type="number"
                    value={inputs.totalInsurance}
                    onChange={(e) => onUpdate('totalInsurance', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="applicationFees">{t('purchaseSection.applicationFees')}</Label>
                  <Input
                    id="applicationFees"
                    type="number"
                    value={inputs.applicationFees}
                    onChange={(e) => onUpdate('applicationFees', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="guaranteeFees">{t('purchaseSection.guaranteeFees')}</Label>
                  <Input
                    id="guaranteeFees"
                    type="number"
                    value={inputs.guaranteeFees}
                    onChange={(e) => onUpdate('guaranteeFees', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="accountFees">{t('purchaseSection.accountFees')}</Label>
                  <Input
                    id="accountFees"
                    type="number"
                    value={inputs.accountFees}
                    onChange={(e) => onUpdate('accountFees', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
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
                  <Label htmlFor="coOwnershipCharges">{t('purchaseSection.coOwnershipChargesPerYear')}</Label>
                  <Input
                    id="coOwnershipCharges"
                    type="number"
                    value={inputs.coOwnershipCharges}
                    onChange={(e) => onUpdate('coOwnershipCharges', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="maintenancePercent">{t('purchaseSection.maintenancePercent')}</Label>
                  <Input
                    id="maintenancePercent"
                    type="number"
                    value={inputs.maintenancePercent}
                    onChange={(e) => onUpdate('maintenancePercent', parseFloat(e.target.value) || 0)}
                    min={0}
                    max={100}
                    step={0.1}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="homeInsurance">{t('purchaseSection.homeInsurance')}</Label>
                  <Input
                    id="homeInsurance"
                    type="number"
                    value={inputs.homeInsurance}
                    onChange={(e) => onUpdate('homeInsurance', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={50}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="bankFees">{t('purchaseSection.bankFees')}</Label>
                  <Input
                    id="bankFees"
                    type="number"
                    value={inputs.bankFees}
                    onChange={(e) => onUpdate('bankFees', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={50}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="garbageTax">{t('purchaseSection.garbageTax')}</Label>
                  <Input
                    id="garbageTax"
                    type="number"
                    value={inputs.garbageTax}
                    onChange={(e) => onUpdate('garbageTax', parseFloat(e.target.value) || 0)}
                    min={0}
                    step={50}
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
