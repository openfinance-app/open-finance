/**
 * PropertyCard Component
 * Task 9.1.10: Create PropertyCard component
 * 
 * Displays property information in a card format with action buttons
 */
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/Dialog';
import { Edit2, Trash2, Eye, Home, Building2, Mountain, Building, Factory, MapPin } from 'lucide-react';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { multiply } from '@/utils/money';
import { useSecondaryConversion } from '@/hooks/useSecondaryConversion';
import { useDeleteProperty } from '@/hooks/useRealEstate';
import { PropertyType, getPropertyTypeName, getPropertyTypeBadgeColor } from '@/types/realEstate';
import { cn } from '@/lib/utils';
import type { RealEstateProperty } from '@/types/realEstate';

interface PropertyCardProps {
  property: RealEstateProperty;
  onEdit: (property: RealEstateProperty) => void;
  onView: (propertyId: number) => void;
  isHighlighted?: boolean;
}

// Icon mapping for property types
function getPropertyTypeIcon(type: string): React.ReactNode {
  const icons: Record<string, React.ReactNode> = {
    [PropertyType.RESIDENTIAL]: <Home className="h-5 w-5" />,
    [PropertyType.COMMERCIAL]: <Building2 className="h-5 w-5" />,
    [PropertyType.LAND]: <Mountain className="h-5 w-5" />,
    [PropertyType.MIXED_USE]: <Building className="h-5 w-5" />,
    [PropertyType.INDUSTRIAL]: <Factory className="h-5 w-5" />,
    [PropertyType.OTHER]: <MapPin className="h-5 w-5" />,
  };
  return icons[type] || <MapPin className="h-5 w-5" />;
}

export function PropertyCard({ property, onEdit, onView, isHighlighted }: PropertyCardProps) {
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const deleteMutation = useDeleteProperty();
  const { convert, secondaryCurrency: secCurrency, secondaryExchangeRate } = useSecondaryConversion(property.currency);
  const { t: tc } = useTranslation('common');
  const { t } = useTranslation('realEstate');

  useEffect(() => {
    if (isHighlighted) {
      const element = document.getElementById(`property-${property.id}`);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    }
  }, [isHighlighted, property.id]);

  const handleDelete = async () => {
    try {
      await deleteMutation.mutateAsync(property.id);
      setShowDeleteDialog(false);
    } catch (error) {
      console.error('Failed to delete property:', error);
    }
  };

  const appreciationColor = (property.appreciation ?? 0) >= 0 ? 'text-green-400' : 'text-red-400';

  return (
    <>
      <Card
        id={`property-${property.id}`}
        className={cn(
          "p-6 hover:bg-surface-elevated transition-all duration-300 group cursor-pointer relative",
          isHighlighted && "ring-2 ring-primary ring-offset-2 ring-offset-background bg-primary/5 shadow-lg scale-[1.02] z-30"
        )}
        onClick={() => onView(property.id)}
      >
        {/* Header */}
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-start gap-3 flex-1 min-w-0">
            {/* Icon */}
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10 text-primary shrink-0">
              {getPropertyTypeIcon(property.propertyType)}
            </div>

            {/* Name and Type */}
            <div className="flex-1 min-w-0">
              <h3 className="text-lg font-semibold text-text-primary truncate">
                {property.name}
              </h3>
              <div className={cn(
                'inline-flex items-center px-2 py-0.5 rounded-md text-xs font-medium border mt-1',
                getPropertyTypeBadgeColor(property.propertyType)
              )}>
                {getPropertyTypeName(property.propertyType)}
              </div>
            </div>
          </div>

          {/* Actions */}
          <div
            className="flex items-center gap-1 ml-2 opacity-0 group-hover:opacity-100 transition-opacity duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onView(property.id);
              }}
              className="h-8 w-8 p-0"
              aria-label={tc('aria.viewPropertyDetails')}
            >
              <Eye className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                onEdit(property);
              }}
              className="h-8 w-8 p-0"
              aria-label={tc('aria.editProperty')}
            >
              <Edit2 className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={(e) => {
                e.stopPropagation();
                setShowDeleteDialog(true);
              }}
              className="h-8 w-8 p-0 text-error hover:text-error hover:bg-error/10"
              aria-label={tc('aria.deleteProperty')}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {/* Image Placeholder */}
        <div className="mb-4 aspect-video rounded-lg bg-gradient-to-br from-primary/20 to-primary/5 flex items-center justify-center">
          <div className="text-primary/30 scale-150">
            {getPropertyTypeIcon(property.propertyType)}
          </div>
        </div>

        {/* Address */}
        <p className="text-sm text-text-secondary mb-3 line-clamp-2" title={property.address}>
          {property.address}
        </p>

        {/* Divider */}
        <div className="border-t border-border my-3" />

        {/* Property Details Grid */}
        <div className="space-y-2 text-sm">
          {/* Purchase Date */}
          <div className="flex justify-between">
            <span className="text-text-secondary">{t('card.purchased')}</span>
            <span className="text-text-primary font-medium">
              {new Date(property.purchaseDate).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })}
            </span>
          </div>

          {/* Purchase Price */}
          <div className="flex justify-between">
            <span className="text-text-secondary">{t('form.purchasePrice')}</span>
            <span className="text-text-primary font-medium">
              <ConvertedAmount
                amount={property.purchasePrice}
                currency={property.currency}
                convertedAmount={property.isConverted && property.exchangeRate
                  ? multiply(property.purchasePrice, property.exchangeRate)
                  : undefined}
                baseCurrency={property.baseCurrency}
                exchangeRate={property.exchangeRate}
                isConverted={property.isConverted}
              />
            </span>
          </div>

          {/* Current Value */}
          <div className="flex justify-between">
            <span className="text-text-secondary">{t('form.currentValue')}</span>
            <span className="text-text-primary font-bold text-base">
              {/* REQ-10.3: Display property current value with base-currency conversion hint */}
              <ConvertedAmount
                amount={property.currentValue}
                currency={property.currency}
                convertedAmount={property.valueInBaseCurrency}
                baseCurrency={property.baseCurrency}
                exchangeRate={property.exchangeRate}
                isConverted={property.isConverted}
                secondaryAmount={property.valueInSecondaryCurrency}
                secondaryCurrency={property.secondaryCurrency}
              />
            </span>
          </div>

          {/* Appreciation */}
          {property.appreciation !== undefined && property.appreciationPercentage !== undefined && (
            <div className="flex justify-between">
              <span className="text-text-secondary">{t('card.appreciation')}</span>
              <span className={cn('font-medium flex items-center gap-1', appreciationColor)}>
                <span>{property.appreciationPercentage >= 0 ? '+' : ''}</span>
                <ConvertedAmount
                  amount={property.appreciation}
                  currency={property.currency}
                  convertedAmount={property.isConverted && property.exchangeRate
                    ? multiply(property.appreciation, property.exchangeRate)
                    : undefined}
                  baseCurrency={property.baseCurrency}
                  exchangeRate={property.exchangeRate}
                  isConverted={property.isConverted}
                  inline
                />
                <span>({property.appreciationPercentage >= 0 ? '+' : ''}{property.appreciationPercentage.toFixed(2)}%)</span>
              </span>
            </div>
          )}

          {/* Equity (if has mortgage) */}
          {property.mortgageId && property.equity !== undefined && (
            <div className="flex justify-between">
              <span className="text-text-secondary">{t('card.equity')}</span>
              <span className="text-green-400 font-medium">
                <ConvertedAmount
                  amount={property.equity}
                  currency={property.currency}
                  convertedAmount={property.isConverted && property.exchangeRate ? multiply(property.equity!, property.exchangeRate) : undefined}
                  baseCurrency={property.baseCurrency}
                  exchangeRate={property.exchangeRate}
                  isConverted={property.isConverted}
                />
              </span>
            </div>
          )}

          {/* Rental Income */}
          {property.rentalIncome && property.rentalIncome > 0 && (
            <div className="flex justify-between">
              <span className="text-text-secondary">{t('card.monthlyRent')}</span>
              <span className="text-primary font-medium">
                <ConvertedAmount
                  amount={property.rentalIncome}
                  currency={property.currency}
                  isConverted={false}
                  secondaryAmount={convert(property.rentalIncome)}
                  secondaryCurrency={secCurrency}
                  secondaryExchangeRate={secondaryExchangeRate}
                  inline
                />/mo
              </span>
            </div>
          )}
        </div>

        {/* Inactive Badge */}
        {!property.isActive && (
          <div className="mt-3 inline-flex items-center px-2 py-1 rounded-md bg-surface-elevated text-text-muted text-xs font-medium">
            {t('card.inactive')}
          </div>
        )}
      </Card>

      {/* Delete Confirmation Dialog */}
      <Dialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('card.deleteTitle')}</DialogTitle>
            <DialogDescription>
              {t('card.deleteConfirm', { name: property.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setShowDeleteDialog(false)}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handleDelete}
              isLoading={deleteMutation.isPending}
            >
              Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
