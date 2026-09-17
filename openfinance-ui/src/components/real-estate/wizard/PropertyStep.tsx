/**
 * Wizard step 1 (Task 9): property identity, type, valuation and currency.
 */
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/Input';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { PROPERTY_TYPE_OPTIONS, type PropertyStepState } from './types';

interface PropertyStepProps {
  property: PropertyStepState;
  onChange: (patch: Partial<PropertyStepState>) => void;
  today: string;
}

export function PropertyStep({ property, onChange, today }: PropertyStepProps) {
  const { t } = useTranslation('realEstate');

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div className="md:col-span-2">
        <label htmlFor="wizard-name" className="block text-sm font-medium mb-1.5">
          {t('form.propertyName')} *
        </label>
        <Input
          id="wizard-name"
          value={property.name}
          onChange={e => onChange({ name: e.target.value })}
          placeholder={t('form.propertyNamePlaceholder')}
        />
      </div>
      <div className="md:col-span-2">
        <label htmlFor="wizard-address" className="block text-sm font-medium mb-1.5">
          {t('form.address')} *
        </label>
        <Input
          id="wizard-address"
          value={property.address}
          onChange={e => onChange({ address: e.target.value })}
          placeholder={t('form.addressPlaceholder')}
        />
      </div>
      <div>
        <label htmlFor="wizard-type" className="block text-sm font-medium mb-1.5">
          {t('form.propertyType')} *
        </label>
        <select
          id="wizard-type"
          value={property.propertyType}
          onChange={e => onChange({ propertyType: e.target.value })}
          className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
        >
          {PROPERTY_TYPE_OPTIONS.map(type => (
            <option key={type} value={type}>
              {t(`filters.${type.toLowerCase()}`) !== `filters.${type.toLowerCase()}`
                ? t(`filters.${type.toLowerCase()}`)
                : type}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label htmlFor="wizard-currency" className="block text-sm font-medium mb-1.5">
          {t('form.currency')} *
        </label>
        <CurrencySelector
          value={property.currency}
          onValueChange={v => onChange({ currency: v })}
          className="w-full"
        />
      </div>
      <div>
        <label htmlFor="wizard-price" className="block text-sm font-medium mb-1.5">
          {t('form.purchasePrice')} *
        </label>
        <NumberInput
          id="wizard-price"
          value={property.purchasePrice}
          onChange={v => onChange({ purchasePrice: v })}
          placeholder="0.00"
          min="0.01"
        />
      </div>
      <div>
        <label htmlFor="wizard-date" className="block text-sm font-medium mb-1.5">
          {t('form.purchaseDate')} *
        </label>
        <DateInput
          id="wizard-date"
          value={property.purchaseDate}
          onChange={v => onChange({ purchaseDate: v ?? today })}
          max={today}
        />
      </div>
      <div>
        <label htmlFor="wizard-value" className="block text-sm font-medium mb-1.5">
          {t('form.currentValue')} *
        </label>
        <NumberInput
          id="wizard-value"
          value={property.currentValue}
          onChange={v => onChange({ currentValue: v })}
          placeholder="0.00"
          min="0"
        />
      </div>
    </div>
  );
}
