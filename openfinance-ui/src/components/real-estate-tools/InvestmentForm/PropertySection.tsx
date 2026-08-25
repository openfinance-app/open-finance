/**
 * PropertySection Component
 * 
 * Investment property parameters
 * Requirements: REQ-2.1.x
 */

import React from 'react';
import { Building2, Sofa, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { NumberInput } from '@/components/ui/NumberInput';
import { Label } from '@/components/ui/Label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { useTranslation } from 'react-i18next';
import type { InvestmentPropertyInputs, ValidationError, FurnishingType } from '@/types/realEstateTools';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';

export interface PropertySectionProps {
  inputs: InvestmentPropertyInputs;
  errors: ValidationError[];
  onUpdate: (field: keyof InvestmentPropertyInputs, value: string | number) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const PropertySection: React.FC<PropertySectionProps> = ({
  inputs,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `property.${field}`)?.message;

  const furnishingOptions: { value: FurnishingType; label: string; price: number }[] = [
    { value: 'unfurnished', label: t('propertySection.unfurnished'), price: 0 },
    { value: 'basic', label: t('propertySection.basic'), price: 5000 },
    { value: 'standard', label: t('propertySection.standard'), price: 10000 },
    { value: 'luxury', label: t('propertySection.luxury'), price: 20000 },
  ];

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-primary/10 cursor-pointer select-none pb-4"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <Building2 className="h-5 w-5" />
            {t('propertySection.title')}
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
          {/* Total Price */}
          <div className="space-y-2">
            <Label htmlFor="totalPrice">{t('propertySection.totalPrice')}</Label>
            <NumberInput
              id="totalPrice"
              value={String(inputs.totalPrice)}
              onChange={(value) => onUpdate('totalPrice', parseFloat(value) || 0)}
              min={0}
            />
            {getFieldError('totalPrice') && (
              <Alert variant="error" className="py-2">
                <AlertDescription className="text-xs">{getFieldError('totalPrice')}</AlertDescription>
              </Alert>
            )}
          </div>

          {/* Furnishing Type */}
          <div className="space-y-2">
            <Label htmlFor="furnishingType" className="flex items-center gap-2">
              <Sofa className="h-4 w-4" />
              {t('propertySection.furnishingType')}
            </Label>
            <Select
              value={inputs.furnishingType}
              onValueChange={(value) => onUpdate('furnishingType', value as FurnishingType)}
            >
              <SelectTrigger id="furnishingType">
                <SelectValue placeholder={t('propertySection.selectFurnishingType')} />
              </SelectTrigger>
              <SelectContent>
                {furnishingOptions.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label} (+<ConvertedAmount
                      amount={option.price}
                      currency={baseCurrency}
                      inline
                    />)
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* Furniture Value */}
          <div className="space-y-2">
            <Label htmlFor="furnitureValue">{t('propertySection.furnitureValue')}</Label>
            <NumberInput
              id="furnitureValue"
              value={String(inputs.furnitureValue)}
              onChange={(value) => onUpdate('furnitureValue', parseFloat(value) || 0)}
              min={0}
              readOnly={inputs.furnishingType !== 'unfurnished'}
              className={inputs.furnishingType !== 'unfurnished' ? 'bg-muted' : ''}
            />
            <p className="text-xs text-muted-foreground">
              {inputs.furnishingType === 'unfurnished'
                ? t('propertySection.freeInputUnfurnished')
                : t('propertySection.autoCalculated')}
            </p>
          </div>

          {/* Investment Total */}
          <div className="pt-2 border-t">
            <p className="text-sm text-muted-foreground">{t('propertySection.totalInvestment')}</p>
            <p className="text-lg font-semibold text-primary">
              <ConvertedAmount
                amount={inputs.totalPrice + inputs.furnitureValue}
                currency={baseCurrency}
                inline
              />
            </p>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default PropertySection;
