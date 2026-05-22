/**
 * PropertySelector Component
 * 
 * Searchable dropdown selector for user's existing properties to populate comparator fields.
 * Uses a search-enabled dropdown pattern consistent with AccountSelector and LiabilitySelector.
 */

import React, { useState, useMemo } from 'react';
import { Building2, Search, Loader2, MapPin } from 'lucide-react';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/Select';
import { useTranslation } from 'react-i18next';
import { useProperties } from '@/hooks/useRealEstate';
import { useAuthContext } from '@/context/AuthContext';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
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
  const { format: formatCurrency } = useFormatCurrency();
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  // Filter properties by search query
  const filteredProperties = useMemo(() => {
    if (!properties) return [];
    const normalizedQuery = searchQuery.trim().toLowerCase();
    if (!normalizedQuery) return properties;
    return properties.filter(
      (property) =>
        property.name.toLowerCase().includes(normalizedQuery) ||
        (property.address && property.address.toLowerCase().includes(normalizedQuery)) ||
        (property.propertyType && getPropertyTypeName(property.propertyType).toLowerCase().includes(normalizedQuery))
    );
  }, [properties, searchQuery]);

  const handlePropertySelect = (propertyId: string) => {
    if (propertyId === 'none') return;
    const property = (properties || []).find(p => String(p.id) === propertyId);
    if (!property) return;

    // Map property data to BuyRentInputs structure
    const propertyData: Partial<BuyRentInputs> = {
      purchase: {
        propertyPrice: Number(property.purchasePrice) || 0,
        renovationAmount: 0,
        notaryFeesPercent: 7,
        agencyFees: 0,
        isNewProperty: false,
        downPayment: 0,
        loanDuration: 25,
        interestRate: 4.2,
        totalInsurance: 0,
        applicationFees: 2000,
        guaranteeFees: 2750,
        accountFees: 720,
        propertyTax: 0,
        coOwnershipCharges: 0,
        maintenancePercent: 1,
        homeInsurance: 600,
        bankFees: 0,
        garbageTax: 150,
      },
      rental: {
        monthlyRent: property.rentalIncome ? Number(property.rentalIncome) : 1200,
        monthlyCharges: 100,
        securityDeposit: property.rentalIncome ? Number(property.rentalIncome) : 1200,
        rentalInsurance: 200,
        garbageTax: 150,
        initialSavings: 0,
        monthlySavings: 0,
      },
      market: {
        priceEvolution: 2,
        rentEvolution: 2,
        investmentReturn: 4,
        inflation: 2,
      },
      resale: {
        targetYear: 10,
        desiredProfit: 50000,
        resaleFeesPercent: 8,
      },
    };

    onPropertySelect(propertyData);
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
    <Select
      onValueChange={handlePropertySelect}
      onOpenChange={(open) => {
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
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={t('propertySelector.searchProperties')}
              className="w-full bg-transparent text-sm text-text-primary placeholder:text-text-tertiary outline-none"
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => e.stopPropagation()}
            />
          </div>
        )}

        {filteredProperties.length === 0 ? (
          <div className="py-4 text-center text-sm text-text-tertiary">
            {t('propertySelector.noMatch')}
          </div>
        ) : (
          filteredProperties.map((property) => (
            <SelectItem key={property.id} value={String(property.id)}>
              <div className="flex flex-col gap-0.5">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{property.name}</span>
                  <span className="text-xs px-1.5 py-0.5 rounded bg-primary/10 text-primary">
                    {getPropertyTypeName(property.propertyType)}
                  </span>
                </div>
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <span>{formatCurrency(Number(property.purchasePrice), property.currency || baseCurrency)}</span>
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
  );
};

export default PropertySelector;
