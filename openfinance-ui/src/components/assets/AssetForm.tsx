/**
 * AssetForm Component
 * Task 5.2.8: Create AssetForm component with validation
 * Task 6.2.15: Updated to use CurrencySelector component
 * Task 6.3.13: Updated to use baseCurrency from AuthContext
 * Task 9.2.5: Added conditional fields for physical assets
 * Task 12.1.12: Integrate attachments into forms
 * 
 * Form for creating and editing assets with Zod validation and file attachments
 */
import { useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { AccountSelector } from '@/components/ui/AccountSelector';
import { useAuthContext } from '@/context/AuthContext';
import { getAssetTypeName } from '@/hooks/useAssets';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import type { Asset, AssetRequest, AssetType, AssetCondition } from '@/types/asset';

const assetTypes: AssetType[] = [
  'STOCK',
  'ETF',
  'CRYPTO',
  'BOND',
  'MUTUAL_FUND',
  //'REAL_ESTATE', // NOTE: Real estate has its own module
  'COMMODITY',
  'VEHICLE',
  'JEWELRY',
  'COLLECTIBLE',
  'ELECTRONICS',
  'FURNITURE',
  'OTHER',
];

const assetConditions: AssetCondition[] = [
  'NEW',
  'EXCELLENT',
  'GOOD',
  'FAIR',
  'POOR',
];


// Helper to check if asset type is physical
const isPhysicalAssetType = (type: AssetType): boolean => {
  return ['VEHICLE', 'JEWELRY', 'COLLECTIBLE', 'ELECTRONICS', 'FURNITURE'].includes(type);
};

interface AssetFormProps {
  asset?: Asset;
  onSubmit: (data: AssetRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

export function AssetForm({ asset, onSubmit, onCancel, isLoading }: AssetFormProps) {
  const isEditing = !!asset;
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('assets');

  const assetSchema = useMemo(() => z.object({
    accountId: z.number().optional(),
    name: z.string().min(1, t('validation.nameRequired')).max(100, t('validation.nameTooLong')),
    type: z.enum([
      'STOCK',
      'ETF',
      'CRYPTO',
      'BOND',
      'MUTUAL_FUND',
      'COMMODITY',
      'VEHICLE',
      'JEWELRY',
      'COLLECTIBLE',
      'ELECTRONICS',
      'FURNITURE',
      'OTHER',
    ]),
    symbol: z.string().max(20, t('validation.symbolTooLong')).optional().or(z.literal('')),
    quantity: z.coerce.number().min(0.000001, t('validation.quantityRequired')),
    purchasePrice: z.coerce.number().min(0.000001, t('validation.purchasePriceRequired')),
    currentPrice: z.coerce.number().min(0.000001, t('validation.currentPriceRequired')),
    currency: z.string().length(3, t('validation.currencyInvalid')),
    purchaseDate: z.string().min(1, t('validation.purchaseDateRequired')),
    notes: z.string().max(500, t('validation.notesTooLong')).optional().or(z.literal('')),
    serialNumber: z.string().max(100).optional().or(z.literal('')),
    brand: z.string().max(100).optional().or(z.literal('')),
    model: z.string().max(100).optional().or(z.literal('')),
    condition: z.enum(['NEW', 'EXCELLENT', 'GOOD', 'FAIR', 'POOR']).optional().nullable(),
    warrantyExpiration: z.string().optional().or(z.literal('')),
    usefulLifeYears: z.number().int().min(1).max(50).optional().or(z.nan()),
  }), [t]);

  type AssetFormData = z.infer<typeof assetSchema>;

  // Get today's date in YYYY-MM-DD format for max date validation
  const today = new Date().toISOString().split('T')[0];

  // Get tomorrow's date for warranty min date validation
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const tomorrowStr = tomorrow.toISOString().split('T')[0];

  const defaultValues = useMemo<AssetFormData>(
    () =>
      asset
        ? {
          accountId: asset.accountId ?? undefined,
          name: asset.name,
          type: asset.type as any, // Cast to avoid type mismatch if asset is REAL_ESTATE
          symbol: asset.symbol || '',
          quantity: asset.quantity,
          purchasePrice: asset.purchasePrice,
          currentPrice: asset.currentPrice,
          currency: asset.currency,
          purchaseDate: asset.purchaseDate,
          notes: asset.notes || '',
          serialNumber: asset.serialNumber || '',
          brand: asset.brand || '',
          model: asset.model || '',
          condition: asset.condition ?? undefined,
          warrantyExpiration: asset.warrantyExpiration || '',
          usefulLifeYears: asset.usefulLifeYears || undefined,
        }
        : {
          accountId: undefined,
          name: '',
          type: 'STOCK',
          symbol: '',
          quantity: 0,
          purchasePrice: 0,
          currentPrice: 0,
          currency: baseCurrency || DEFAULT_CURRENCY,
          purchaseDate: today,
          notes: '',
          serialNumber: '',
          brand: '',
          model: '',
          condition: undefined,
          warrantyExpiration: '',
          usefulLifeYears: undefined,
        },
    [asset, today]
  );

  const {
    register,
    handleSubmit,
    control,
    watch,
    reset,
    formState: { errors },
  } = useForm<AssetFormData>({
    resolver: zodResolver(assetSchema) as any,
    defaultValues,
  });

  useEffect(() => {
    reset(defaultValues);
  }, [defaultValues, reset]);

  const selectedCurrency = watch('currency');
  const selectedType = watch('type');
  const isPhysicalAsset = isPhysicalAssetType(selectedType);

  const handleFormSubmit = handleSubmit(
    (data) => {
      const requestData: AssetRequest = {
        accountId: data.accountId || undefined,
        name: data.name,
        type: data.type,
        symbol: data.symbol || undefined,
        quantity: Number(data.quantity),
        purchasePrice: Number(data.purchasePrice),
        currentPrice: Number(data.currentPrice),
        currency: data.currency,
        purchaseDate: data.purchaseDate,
        notes: data.notes || undefined,
      };
      // Add physical asset fields if applicable
      if (isPhysicalAsset) {
        requestData.serialNumber = data.serialNumber || undefined;
        requestData.brand = data.brand || undefined;
        requestData.model = data.model || undefined;
        requestData.condition = data.condition ?? undefined;
        requestData.warrantyExpiration = data.warrantyExpiration || undefined;
        requestData.usefulLifeYears = data.usefulLifeYears || undefined;
      }

      onSubmit(requestData);
    }
  );

  return (
    <>
      <form onSubmit={handleFormSubmit} noValidate className="space-y-4">
        {/* Top Row: Type and Account */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Asset Type */}
          <div>
            <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.assetType')} *
            </label>
            <select
              id="type"
              {...register('type')}
              className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            >
              {assetTypes.map((type) => (
                <option key={type} value={type}>
                  {getAssetTypeName(type)}
                </option>
              ))}
            </select>
            {errors.type && (
              <p className="mt-1 text-sm text-error">{errors.type.message}</p>
            )}
          </div>

          {/* Account (Optional) */}
          <div>
            <label htmlFor="accountId" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.account')}
            </label>
            <Controller
              name="accountId"
              control={control}
              render={({ field }) => (
                <AccountSelector
                  value={field.value}
                  onValueChange={field.onChange}
                  className="w-full"
                />
              )}
            />
            {errors.accountId && (
              <p className="mt-1 text-sm text-error">{errors.accountId.message}</p>
            )}
          </div>
        </div>

        {/* Second Row: Name and Symbol */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Asset Name */}
          <div className={isPhysicalAsset ? "md:col-span-2" : ""}>
            <label htmlFor="name" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.assetName')} *
            </label>
            <Input
              id="name"
              {...register('name')}
              placeholder={t('form.assetNamePlaceholder')}
              error={errors.name?.message}
            />
          </div>

          {/* Symbol (Optional) - Hide for physical assets */}
          {!isPhysicalAsset && (
            <div>
              <label htmlFor="symbol" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.symbol')}
              </label>
              <Input
                id="symbol"
                {...register('symbol')}
                placeholder={t('form.symbolPlaceholder')}
                error={errors.symbol?.message}
              />
            </div>
          )}
        </div>
        {/* Physical Asset Fields (Task 9.2.5) */}
        {isPhysicalAsset && (
          <div className="border border-border rounded-lg p-4 space-y-4 bg-surface-elevated">
            <h3 className="text-sm font-semibold text-text-primary">{t('form.physicalDetails')}</h3>
            {/* Brand and Model Row */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="brand" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('form.brand')}
                </label>
                <Input
                  id="brand"
                  {...register('brand')}
                  placeholder={t('form.brandPlaceholder')}
                  error={errors.brand?.message}
                />
              </div>
              <div>
                <label htmlFor="model" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('form.model')}
                </label>
                <Input
                  id="model"
                  {...register('model')}
                  placeholder={t('form.modelPlaceholder')}
                  error={errors.model?.message}
                />
              </div>
            </div>
            {/* Serial Number */}
            <div>
              <label htmlFor="serialNumber" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.serialNumber')}
              </label>
              <Input
                id="serialNumber"
                {...register('serialNumber')}
                placeholder={t('form.serialNumberPlaceholder')}
                error={errors.serialNumber?.message}
              />
            </div>


            {/* Condition */}
            <div>
              <label htmlFor="condition" className="block text-sm font-medium text-text-primary mb-1.5">
                {t('form.condition')} *
              </label>
              <select
                id="condition"
                {...register('condition')}
                className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="">{t('form.selectCondition')}</option>
                {assetConditions.map((condition) => (
                  <option key={condition} value={condition}>
                    {t(`form.conditions.${condition}`)}
                  </option>
                ))}
              </select>
              {errors.condition && (
                <p className="mt-1 text-sm text-error">{errors.condition.message}</p>
              )}
            </div>
            {/* Warranty Expiration and Useful Life */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label htmlFor="warrantyExpiration" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('form.warrantyExpiration')}
                </label>
                <Input
                  id="warrantyExpiration"
                  type="date"
                  min={tomorrowStr}
                  {...register('warrantyExpiration')}
                  error={errors.warrantyExpiration?.message}
                />
              </div>
              <div>
                <label htmlFor="usefulLifeYears" className="block text-sm font-medium text-text-primary mb-1.5">
                  {t('form.usefulLifeYears')}
                </label>
                <Input
                  id="usefulLifeYears"
                  type="number"
                  min="1"
                  max="50"
                  step="1"
                  {...register('usefulLifeYears', {
                    setValueAs: (v) => (v === '' || isNaN(Number(v)) ? undefined : Number(v))
                  })}
                  placeholder={t('form.usefulLifePlaceholder')}
                  error={errors.usefulLifeYears?.message}
                />
                <p className="mt-1 text-xs text-text-tertiary">
                  {t('form.usefulLifeHint')}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Quantity and Prices Row */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Quantity */}
          <div>
            <label htmlFor="quantity" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.quantity')} *
            </label>
            <Input
              id="quantity"
              type="number"
              step="0.000001"
              min="0.000001"
              {...register('quantity', { valueAsNumber: true })}
              placeholder="0.00"
              error={errors.quantity?.message}
            />
          </div>

          {/* Purchase Price */}
          <div>
            <label htmlFor="purchasePrice" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.purchasePrice')} *
            </label>
            <Input
              id="purchasePrice"
              type="number"
              step="0.01"
              min="0.01"
              {...register('purchasePrice', { valueAsNumber: true })}
              placeholder="0.00"
              error={errors.purchasePrice?.message}
            />
          </div>

          {/* Current Price */}
          <div>
            <label htmlFor="currentPrice" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.currentPrice')} *
            </label>
            <Input
              id="currentPrice"
              type="number"
              step="0.01"
              min="0.01"
              {...register('currentPrice', { valueAsNumber: true })}
              placeholder="0.00"
              error={errors.currentPrice?.message}
            />
          </div>
        </div>

        {/* Currency and Purchase Date Row */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                  placeholder={t('form.selectCurrencyPlaceholder')}
                  className="w-full"
                />
              )}
            />
            {errors.currency && (
              <p className="mt-1 text-sm text-error">{errors.currency.message}</p>
            )}
            {/* Show exchange rate if different from base currency */}
            {selectedCurrency && selectedCurrency !== baseCurrency && (
              <div className="mt-2">
                <ExchangeRateInline from={selectedCurrency} to={baseCurrency} />
              </div>
            )}
          </div>


          {/* Purchase Date */}
          <div>
            <label htmlFor="purchaseDate" className="block text-sm font-medium text-text-primary mb-1.5">
              {t('form.purchaseDate')} *
            </label>
            <Input
              id="purchaseDate"
              type="date"
              max={today}
              {...register('purchaseDate')}
              error={errors.purchaseDate?.message}
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
            className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
          />
          {errors.notes && (
            <p className="mt-1 text-sm text-error">{errors.notes.message}</p>
          )}
        </div>

        {/* Actions */}
        <div className="flex justify-end gap-3 pt-4">
          <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
            {t('form.cancel')}
          </Button>
          <Button variant="primary" type="submit" isLoading={isLoading}>
            {isEditing ? t('form.updateAsset') : t('form.createAsset')}
          </Button>
        </div>
      </form>
    </>
  );
}
