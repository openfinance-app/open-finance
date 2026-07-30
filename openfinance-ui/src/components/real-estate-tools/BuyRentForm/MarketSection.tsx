/**
 * MarketSection Component
 * 
 * Market evolution parameters form section
 * Requirements: REQ-1.3.1
 */

import React from 'react';
import { TrendingUp, Percent, Euro, ChevronDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Alert, AlertDescription } from '@/components/ui/Alert';
import { useTranslation } from 'react-i18next';
import type { MarketInputs, ValidationError } from '@/types/realEstateTools';

export interface MarketSectionProps {
  inputs: MarketInputs;
  errors: ValidationError[];
  onUpdate: (field: keyof MarketInputs, value: number) => void;
  isOpen: boolean;
  onToggle: () => void;
}

export const MarketSection: React.FC<MarketSectionProps> = ({
  inputs,
  errors,
  onUpdate,
  isOpen,
  onToggle,
}) => {
  const { t } = useTranslation('realEstate');

  const getFieldError = (field: string) => errors.find(e => e.field === `market.${field}`)?.message;

  return (
    <Card className="h-full">
      <CardHeader
        className="bg-info/10 cursor-pointer select-none"
        onClick={onToggle}
      >
        <CardTitle className="flex items-center justify-between text-lg">
          <span className="flex items-center gap-2">
            <TrendingUp className="h-5 w-5" />
            {t('marketSection.title')}
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
            {/* Price Evolution */}
            <div className="space-y-2">
              <Label htmlFor="priceEvolution" className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4" />
                {t('marketSection.priceEvolution')}
              </Label>
              <Input
                id="priceEvolution"
                type="number"
                value={inputs.priceEvolution}
                onChange={(e) => onUpdate('priceEvolution', parseFloat(e.target.value) || 0)}
                min={-50}
                max={50}
                step={0.1}
              />
              <p className="text-xs text-muted-foreground">
                {t('marketSection.priceEvolutionHelp')}
              </p>
              {getFieldError('priceEvolution') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('priceEvolution')}</AlertDescription>
                </Alert>
              )}
            </div>

            {/* Rent Evolution */}
            <div className="space-y-2">
              <Label htmlFor="rentEvolution" className="flex items-center gap-2">
                <TrendingUp className="h-4 w-4" />
                {t('marketSection.rentEvolution')}
              </Label>
              <Input
                id="rentEvolution"
                type="number"
                value={inputs.rentEvolution}
                onChange={(e) => onUpdate('rentEvolution', parseFloat(e.target.value) || 0)}
                min={-50}
                max={50}
                step={0.1}
              />
              <p className="text-xs text-muted-foreground">
                {t('marketSection.rentEvolutionHelp')}
              </p>
              {getFieldError('rentEvolution') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('rentEvolution')}</AlertDescription>
                </Alert>
              )}
            </div>

            {/* Investment Return */}
            <div className="space-y-2">
              <Label htmlFor="investmentReturn" className="flex items-center gap-2">
                <Percent className="h-4 w-4" />
                {t('marketSection.investmentReturn')}
              </Label>
              <Input
                id="investmentReturn"
                type="number"
                value={inputs.investmentReturn}
                onChange={(e) => onUpdate('investmentReturn', parseFloat(e.target.value) || 0)}
                min={-20}
                max={50}
                step={0.1}
              />
              <p className="text-xs text-muted-foreground">
                {t('marketSection.investmentReturnHelp')}
              </p>
              {getFieldError('investmentReturn') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('investmentReturn')}</AlertDescription>
                </Alert>
              )}
            </div>

            {/* Inflation */}
            <div className="space-y-2">
              <Label htmlFor="inflation" className="flex items-center gap-2">
                <Euro className="h-4 w-4" />
                {t('marketSection.inflation')}
              </Label>
              <Input
                id="inflation"
                type="number"
                value={inputs.inflation}
                onChange={(e) => onUpdate('inflation', parseFloat(e.target.value) || 0)}
                min={-10}
                max={50}
                step={0.1}
              />
              <p className="text-xs text-muted-foreground">
                {t('marketSection.inflationHelp')}
              </p>
              {getFieldError('inflation') && (
                <Alert variant="error" className="py-2">
                  <AlertDescription className="text-xs">{getFieldError('inflation')}</AlertDescription>
                </Alert>
              )}
            </div>
          </div>
        </CardContent>
      </div>
    </Card>
  );
};

export default MarketSection;
