/**
 * RealEstateForm Component
 * Task 9.1.9: Create RealEstateForm component with validation
 *
 * Form for creating and editing real estate properties with Zod validation
 */
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/Input';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { Button } from '@/components/ui/Button';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { LiabilitySelector } from '@/components/ui/LiabilitySelector';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { useAuthContext } from '@/context/AuthContext';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { isValidDecimalString } from '@/utils/money';
import type { RealEstateProperty, RealEstatePropertyRequest } from '@/types/realEstate';
import { PropertyType, getPropertyTypeName } from '@/types/realEstate';

const propertyTypes: Array<(typeof PropertyType)[keyof typeof PropertyType]> = [
  PropertyType.RESIDENTIAL,
  PropertyType.COMMERCIAL,
  PropertyType.LAND,
  PropertyType.MIXED_USE,
  PropertyType.INDUSTRIAL,
  PropertyType.OTHER,
];

const propertySchema = (tv: (key: string) => string) =>
  z
    .object({
      name: z
        .string()
        .min(1, tv('form.validation.nameRequired'))
        .max(500, tv('form.validation.nameTooLong')),
      address: z
        .string()
        .min(1, tv('form.validation.addressRequired'))
        .max(1000, tv('form.validation.addressTooLong')),
      propertyType: z.enum([
        PropertyType.RESIDENTIAL,
        PropertyType.COMMERCIAL,
        PropertyType.LAND,
        PropertyType.MIXED_USE,
        PropertyType.INDUSTRIAL,
        PropertyType.OTHER,
      ] as const),
      acquisitionType: z.enum(['PURCHASE', 'GIFT', 'PLANNED']),
      purchasePrice: z
        .string()
        .min(1, tv('form.validation.priceInvalid'))
        .refine(isValidDecimalString, tv('form.validation.priceInvalid'))
        .refine(v => Number(v) >= 0, tv('form.validation.priceTooSmall')),
      purchaseDate: z
        .string()
        .min(1, tv('form.validation.purchaseDateRequired'))
        .regex(/^\d{4}-\d{2}-\d{2}$/, tv('form.validation.invalidDateFormat')),
      currentValue: z
        .string()
        .min(1, tv('form.validation.priceInvalid'))
        .refine(isValidDecimalString, tv('form.validation.priceInvalid'))
        .refine(v => Number(v) >= 0, tv('form.validation.valueTooSmall')),
      currency: z.string().length(3, tv('form.validation.currencyCode')),
      mortgageId: z.number().optional(),
      rentalIncome: z
        .string()
        .refine(v => v === '' || isValidDecimalString(v), tv('form.validation.rentalIncomeInvalid'))
        .refine(v => v === '' || Number(v) >= 0, tv('form.validation.rentalIncomeNonNegative'))
        .optional()
        .or(z.literal('')),
      notes: z.string().max(2048, tv('form.validation.notesTooLong')).optional().or(z.literal('')),
      latitude: z
        .number()
        .min(-90, tv('form.validation.latitudeRange'))
        .max(90, tv('form.validation.latitudeRange'))
        .optional(),
      longitude: z
        .number()
        .min(-180, tv('form.validation.longitudeRange'))
        .max(180, tv('form.validation.longitudeRange'))
        .optional(),
      isActive: z.boolean().optional(),
    })
    .refine(data => data.acquisitionType !== 'PURCHASE' || Number(data.purchasePrice) > 0, {
      message: tv('form.validation.priceTooSmall'),
      path: ['purchasePrice'],
    })
    .refine(data => data.acquisitionType !== 'PLANNED' || Number(data.currentValue) === 0, {
      message: tv('form.plannedValue'),
      path: ['currentValue'],
    })
    .refine(
      data => {
        const today = new Date().toISOString().split('T')[0];
        return data.acquisitionType === 'PLANNED' || data.purchaseDate <= today;
      },
      {
        message: tv('form.validation.purchaseDateFuture'),
        path: ['purchaseDate'],
      }
    );

type PropertyFormData = z.infer<ReturnType<typeof propertySchema>>;

interface RealEstateFormProps {
  property?: RealEstateProperty;
  onSubmit: (data: RealEstatePropertyRequest) => Promise<void>;
  onCancel: () => void;
  isLoading: boolean;
}

export function RealEstateForm({ property, onSubmit, onCancel, isLoading }: RealEstateFormProps) {
  const isEditing = !!property;
  const today = new Date().toISOString().split('T')[0];
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('realEstate');

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<PropertyFormData>({
    resolver: zodResolver(propertySchema(t)),
    defaultValues: property
      ? {
          name: property.name,
          address: property.address,
          propertyType: property.propertyType,
          purchasePrice: String(property.purchasePrice),
          acquisitionType: property.acquisitionType ?? 'PURCHASE',
          purchaseDate: property.purchaseDate,
          currentValue: String(property.currentValue),
          currency: property.currency,
          mortgageId: property.mortgageId || undefined,
          rentalIncome:
            property.rentalIncome !== undefined && property.rentalIncome !== null
              ? String(property.rentalIncome)
              : '',
          notes: property.notes || '',
          latitude: property.latitude || undefined,
          longitude: property.longitude || undefined,
          isActive: property.isActive,
        }
      : {
          name: '',
          address: '',
          propertyType: PropertyType.RESIDENTIAL,
          purchasePrice: '0',
          acquisitionType: 'PURCHASE',
          purchaseDate: today,
          currentValue: '0',
          currency: baseCurrency || DEFAULT_CURRENCY,
          mortgageId: undefined,
          rentalIncome: '',
          notes: '',
          latitude: undefined,
          longitude: undefined,
          isActive: true,
        },
  });

  const selectedCurrency = watch('currency');
  const acquisitionType = watch('acquisitionType');

  const handleFormSubmit = handleSubmit(async (data: PropertyFormData) => {
    const request: RealEstatePropertyRequest = {
      name: data.name,
      address: data.address,
      propertyType: data.propertyType,
      purchasePrice: data.purchasePrice.trim(),
      acquisitionType: data.acquisitionType,
      purchaseDate: data.purchaseDate,
      currentValue: data.currentValue.trim(),
      currency: data.currency,
      mortgageId: data.mortgageId || null,
      rentalIncome:
        data.rentalIncome && data.rentalIncome !== '' && Number(data.rentalIncome) > 0
          ? data.rentalIncome.trim()
          : null,
      notes: data.notes && data.notes !== '' ? data.notes : null,
      documents: null,
      latitude: data.latitude || null,
      longitude: data.longitude || null,
      isActive: data.isActive !== undefined ? data.isActive : true,
    };

    try {
      await onSubmit(request);
    } catch (error) {
      console.error('Failed to save property:', error);
    }
  });

  return (
    <form onSubmit={handleFormSubmit} className="space-y-4">
      <div>
        <label htmlFor="acquisitionType">{t('form.acquisitionType')}</label>
        <select
          id="acquisitionType"
          {...register('acquisitionType')}
          className="w-full rounded border border-border bg-surface p-2"
        >
          {(['PURCHASE', 'GIFT', 'PLANNED'] as const).map(type => (
            <option key={type} value={type}>
              {t(`form.acquisitionTypes.${type}`)}
            </option>
          ))}
        </select>
        <p className="text-xs text-text-secondary">{t('form.acquisitionHint')}</p>
      </div>
      {/* Top Row: Property Name & Type */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Property Name */}
        <div className="md:col-span-2">
          <label htmlFor="name" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.propertyName')} *
          </label>
          <Input
            id="name"
            {...register('name')}
            placeholder={t('form.propertyNamePlaceholder')}
            error={errors.name?.message}
          />
        </div>

        {/* Property Type */}
        <div>
          <label
            htmlFor="propertyType"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.propertyType')} *
          </label>
          <select
            id="propertyType"
            {...register('propertyType')}
            className="w-full h-10 px-3 pr-8 rounded-lg bg-surface border border-border text-text-primary text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150"
          >
            {propertyTypes.map(type => (
              <option key={type} value={type}>
                {getPropertyTypeName(type)}
              </option>
            ))}
          </select>
          {errors.propertyType && (
            <p className="mt-1 text-sm text-error">{errors.propertyType.message}</p>
          )}
        </div>
      </div>

      {/* Address */}
      <div>
        <label htmlFor="address" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.address')} *
        </label>
        <Input
          id="address"
          {...register('address')}
          placeholder={t('form.addressPlaceholder')}
          error={errors.address?.message}
        />
        {errors.address && <p className="mt-1 text-sm text-error">{errors.address.message}</p>}
      </div>

      {/* Purchase Price, Purchase Date, Current Value Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Purchase Price */}
        <div>
          <label
            htmlFor="purchasePrice"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.purchasePrice')} *
          </label>
          <Controller
            name="purchasePrice"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="purchasePrice"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="0.00"
                error={errors.purchasePrice?.message}
                min="0"
              />
            )}
          />
        </div>

        {/* Purchase Date */}
        <div>
          <label
            htmlFor="purchaseDate"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.purchaseDate')} *
          </label>
          <Controller
            name="purchaseDate"
            control={control}
            render={({ field }) => (
              <DateInput
                id="purchaseDate"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                max={acquisitionType === 'PLANNED' ? undefined : today}
                error={errors.purchaseDate?.message}
              />
            )}
          />
        </div>

        {/* Current Value */}
        <div>
          <label
            htmlFor="currentValue"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.currentValue')} *
          </label>
          <Controller
            name="currentValue"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="currentValue"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="0.00"
                error={errors.currentValue?.message}
                min="0"
              />
            )}
          />
        </div>
      </div>

      {/* Currency, Mortgage, and Rental Income Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Currency */}
        <div>
          <label htmlFor="currency" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.currency')} *
          </label>
          <Controller
            name="currency"
            control={control}
            render={({ field }) => (
              <CurrencySelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.selectCurrency')}
                className="w-full"
              />
            )}
          />
          {errors.currency && <p className="mt-1 text-sm text-error">{errors.currency.message}</p>}
          {selectedCurrency && selectedCurrency !== baseCurrency && (
            <div className="mt-2">
              <ExchangeRateInline from={selectedCurrency} to={baseCurrency || DEFAULT_CURRENCY} />
            </div>
          )}
        </div>

        {/* Mortgage */}
        <div>
          <label
            htmlFor="mortgageId"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.linkedMortgage')}
          </label>
          <Controller
            name="mortgageId"
            control={control}
            render={({ field }) => (
              <LiabilitySelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.selectMortgage')}
                liabilityFilter={l => l.type === 'MORTGAGE'}
              />
            )}
          />
          {errors.mortgageId && (
            <p className="mt-1 text-sm text-error">{errors.mortgageId.message}</p>
          )}
        </div>

        {/* Monthly Rental Income */}
        <div>
          <label
            htmlFor="rentalIncome"
            className="block text-sm font-medium text-text-primary mb-1.5"
          >
            {t('form.monthlyRentalIncome')}
          </label>
          <Controller
            name="rentalIncome"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="rentalIncome"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                placeholder="0.00"
                error={errors.rentalIncome?.message}
                min="0"
              />
            )}
          />
        </div>
      </div>

      {/* Location Coordinates */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Latitude */}
        <div>
          <label htmlFor="latitude" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.latitude')}
          </label>
          <Controller
            name="latitude"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="latitude"
                value={
                  field.value !== undefined && !Number.isNaN(field.value) ? String(field.value) : ''
                }
                onChange={val => field.onChange(val === '' ? undefined : Number(val))}
                onBlur={field.onBlur}
                placeholder={t('form.latitudePlaceholder')}
                error={errors.latitude?.message}
                min="-90"
                max="90"
              />
            )}
          />
        </div>

        {/* Longitude */}
        <div>
          <label htmlFor="longitude" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.longitude')}
          </label>
          <Controller
            name="longitude"
            control={control}
            render={({ field }) => (
              <NumberInput
                id="longitude"
                value={
                  field.value !== undefined && !Number.isNaN(field.value) ? String(field.value) : ''
                }
                onChange={val => field.onChange(val === '' ? undefined : Number(val))}
                onBlur={field.onBlur}
                placeholder={t('form.longitudePlaceholder')}
                error={errors.longitude?.message}
                min="-180"
                max="180"
              />
            )}
          />
        </div>
      </div>

      {/* Notes */}
      <div>
        <label htmlFor="notes" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.notes')}
        </label>
        <textarea
          id="notes"
          {...register('notes')}
          rows={3}
          placeholder={t('form.notesPlaceholder')}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-muted text-sm placeholder:text-text-muted hover:border-border/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:cursor-not-allowed disabled:opacity-50 transition-colors duration-150 resize-none"
        />
        {errors.notes && <p className="mt-1 text-sm text-error">{errors.notes.message}</p>}
      </div>

      {/* Is Active Checkbox */}
      {isEditing && (
        <div className="flex items-center gap-2">
          <input
            id="isActive"
            type="checkbox"
            {...register('isActive')}
            className="h-4 w-4 rounded border-border bg-surface text-primary focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background"
          />
          <label htmlFor="isActive" className="text-sm text-text-primary">
            {t('form.propertyIsActive')}
          </label>
        </div>
      )}

      {/* Actions */}
      <div className="sticky bottom-[-25px] -mx-6 mt-2 flex justify-end gap-3 border-t border-border bg-surface px-6 py-4">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button variant="primary" type="submit" isLoading={isLoading}>
          {isEditing ? t('form.updateProperty') : t('form.createProperty')}
        </Button>
      </div>
    </form>
  );
}
