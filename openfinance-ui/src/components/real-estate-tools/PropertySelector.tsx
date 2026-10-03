/**
 * PropertySelector Component
 *
 * Searchable dropdown selector for user's existing properties to populate comparator fields.
 * Uses a search-enabled dropdown pattern consistent with AccountSelector and LiabilitySelector.
 */

import React, { useState, useMemo, useRef } from 'react';
import { Building2, Search, Loader2, MapPin } from 'lucide-react';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/Select';
import { useTranslation } from 'react-i18next';
import { useConvertCurrency } from '@/hooks/useCurrency';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';
import { multiply, roundToDecimals } from '@/utils/money';
import { useProperties } from '@/hooks/useRealEstate';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { getPropertyTypeName } from '@/types/realEstate';
import type { BuyRentInputs } from '@/types/realEstateTools';

export interface PropertySelectorProps {
  onPropertySelect: (propertyData: Partial<BuyRentInputs>) => void;
  placeholder?: string;
  className?: string;
}

export const PropertySelector: React.FC<PropertySelectorProps> = ({
  onPropertySelect,
  placeholder,
  className,
}) => {
  const { t } = useTranslation('realEstate');
  const { t: tc } = useTranslation('common');
  const resolvedPlaceholder = placeholder ?? t('propertySelector.selectProperty');
  const { data: properties, isLoading, isError } = useProperties();
  const { baseCurrency } = useAuthContext();
  const conversion = useConvertCurrency();
  const { buyVsRentInitialInputs } = useCountryToolConfig();
  const [conversionError, setConversionError] = useState<string | null>(null);
  const selection = useRef(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // Filter properties by search query
  const filteredProperties = useMemo(() => {
    if (!properties) return [];
    const normalizedQuery = searchQuery.trim().toLowerCase();
    if (!normalizedQuery) return properties;
    return properties.filter(
      property =>
        property.name.toLowerCase().includes(normalizedQuery) ||
        (property.address && property.address.toLowerCase().includes(normalizedQuery)) ||
        (property.propertyType &&
          getPropertyTypeName(property.propertyType).toLowerCase().includes(normalizedQuery))
    );
  }, [properties, searchQuery]);

  const handlePropertySelect = async (propertyId: string) => {
    const currentSelection = ++selection.current;
    const property = (properties || []).find(p => String(p.id) === propertyId);
    if (!property) return;
    setConversionError(null);
    try {
      const currency = property.currency || baseCurrency;
      const rate =
        currency === baseCurrency
          ? 1
          : (
              await conversion.mutateAsync({
                amount: 1,
                fromCurrency: currency,
                toCurrency: baseCurrency,
              })
            ).exchangeRate;
      if (!Number.isFinite(rate) || rate <= 0) throw new Error('Invalid exchange rate');
      if (selection.current !== currentSelection) return;
      const rent =
        property.rentalIncome == null
          ? buyVsRentInitialInputs.rental.monthlyRent
          : roundToDecimals(multiply(Number(property.rentalIncome), rate), 2);
      onPropertySelect({
        ...buyVsRentInitialInputs,
        purchase: {
          ...buyVsRentInitialInputs.purchase,
          propertyPrice: roundToDecimals(multiply(Number(property.purchasePrice), rate), 2),
        },
        rental: { ...buyVsRentInitialInputs.rental, monthlyRent: rent, securityDeposit: rent },
      });
    } catch {
      if (selection.current === currentSelection)
        setConversionError(t('validation.currencyConversion'));
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" />
        {tc('loading')}
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex items-center gap-2 text-sm text-error">
        <Building2 className="h-4 w-4" />
        {tc('loadError')}
      </div>
    );
  }

  if (!properties || properties.length === 0) {
    return null;
  }

  return (
    <div className="w-full">
      {conversionError && (
        <p role="alert" className="text-sm text-error">
          {conversionError}
        </p>
      )}
      <Select
        disabled={conversion.isPending}
        onValueChange={handlePropertySelect}
        onOpenChange={open => {
          setIsOpen(open);
          if (!open) setSearchQuery('');
        }}
      >
        <SelectTrigger className={className}>
          <div className="flex items-center gap-2">
            <Building2 className="h-4 w-4 text-primary shrink-0" />
            <SelectValue placeholder={resolvedPlaceholder} />
          </div>
        </SelectTrigger>
        <SelectContent>
          {/* Search Input */}
          {isOpen && (
            <div className="flex items-center gap-2 px-2 pb-2 border-b border-border">
              <Search className="h-4 w-4 text-text-tertiary shrink-0" />
              <input
                type="text"
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder={t('propertySelector.searchProperties')}
                className="w-full bg-transparent text-sm text-text-primary placeholder:text-text-tertiary outline-none"
                onClick={e => e.stopPropagation()}
                onKeyDown={e => e.stopPropagation()}
              />
            </div>
          )}

          {filteredProperties.length === 0 ? (
            <div className="py-4 text-center text-sm text-text-tertiary">
              {t('propertySelector.noMatch')}
            </div>
          ) : (
            filteredProperties.map(property => (
              <SelectItem key={property.id} value={String(property.id)}>
                <div className="flex flex-col gap-0.5">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{property.name}</span>
                    <span className="text-xs px-1.5 py-0.5 rounded bg-primary/10 text-primary">
                      {getPropertyTypeName(property.propertyType)}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-muted-foreground">
                    <span>
                      <ConvertedAmount
                        amount={Number(property.purchasePrice)}
                        currency={property.currency || baseCurrency}
                        inline
                      />
                    </span>
                    {property.address && (
                      <>
                        <span>•</span>
                        <MapPin className="h-3 w-3 shrink-0" />
                        <span className="truncate max-w-[200px]">{property.address}</span>
                      </>
                    )}
                  </div>
                </div>
              </SelectItem>
            ))
          )}
        </SelectContent>
      </Select>
    </div>
  );
};

export default PropertySelector;
