/**
 * LiabilityForm Component
 * Task 6.2.2: Create LiabilityForm component with validation
 * Task 6.2.15: Updated to use CurrencySelector component
 * Task 6.3.13: Updated to use baseCurrency from AuthContext
 * 
 * Form for creating and editing liabilities with Zod validation
 */
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { InstitutionSelector } from '@/components/ui/InstitutionSelector';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { useAuthContext } from '@/context/AuthContext';
import { getLiabilityTypeName } from '@/hooks/useLiabilities';
import { useProperties } from '@/hooks/useRealEstate';
import type { Liability, LiabilityRequest, LiabilityType } from '@/types/liability';

const liabilityTypes: LiabilityType[] = [
  'MORTGAGE',
  'LOAN',
  'CREDIT_CARD',
  'STUDENT_LOAN',
  'AUTO_LOAN',
  'PERSONAL_LOAN',
  'OTHER',
];

const liabilitySchema = (tv: (key: string) => string) => z.object({
  name: z.string().min(1, tv('form.validation.nameRequired')).max(100, tv('form.validation.nameTooLong')),
  type: z.enum([
    'MORTGAGE',
    'LOAN',
    'CREDIT_CARD',
    'STUDENT_LOAN',
    'AUTO_LOAN',
    'PERSONAL_LOAN',
    'OTHER',
  ]),
  principal: z.number().positive(tv('form.validation.principalPositive')).min(0.01, tv('form.validation.principalTooSmall')),
  currentBalance: z.number().min(0, tv('form.validation.balanceNonNegative')),
  interestRate: z.number().min(0, tv('form.validation.interestRateNonNegative')).max(100, tv('form.validation.interestRateMax')).optional().or(z.literal(0)),
  startDate: z.string().min(1, tv('form.validation.startDateRequired')).regex(/^\d{4}-\d{2}-\d{2}$/, tv('form.validation.invalidDateFormat')),
  endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, tv('form.validation.invalidDateFormat')).optional().or(z.literal('')),
  minimumPayment: z.number().min(0, tv('form.validation.paymentNonNegative')).optional().or(z.literal(0)),
  currency: z.string().length(3, tv('form.validation.currencyCode')),
  notes: z.string().max(500, tv('form.validation.notesTooLong')).optional().or(z.literal('')),
  institutionId: z.string().optional(),
  // Requirement 1.1: Insurance percentage (annual, 0–100%) and one-time/periodic additional fees
  insurancePercentage: z.number().min(0, tv('form.validation.insuranceRateNonNegative')).max(100, tv('form.validation.insuranceRateMax')).optional().or(z.literal(0)),
  additionalFees: z.number().min(0, tv('form.validation.feesNonNegative')).optional().or(z.literal(0)),
  realEstateId: z.number().optional(),
}).refine(
  (data) => {
    if (!data.endDate || data.endDate === '') return true;
    return data.endDate > data.startDate;
  },
  {
    message: tv('form.validation.endDateAfterStart'),
    path: ['endDate'],
  }
);

type LiabilityFormData = z.infer<ReturnType<typeof liabilitySchema>>;

interface LiabilityFormProps {
  liability?: Liability;
  onSubmit: (data: LiabilityRequest) => void;
  onCancel: () => void;
  isLoading: boolean;
}

export function LiabilityForm({ liability, onSubmit, onCancel, isLoading }: LiabilityFormProps) {
  const isEditing = !!liability;
  const today = new Date().toISOString().split('T')[0];
  const { t } = useTranslation('liabilities');
  // Get base currency from user settings (REQ-6.3)
  const { baseCurrency } = useAuthContext();
  // Real estate properties for mortgage linking (ISSUE-005)
  const { data: properties } = useProperties();

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<LiabilityFormData>({
    resolver: zodResolver(liabilitySchema(t)),
    defaultValues: liability
      ? {
        name: liability.name,
        type: liability.type,
        principal: liability.principal,
        currentBalance: liability.currentBalance,
        interestRate: liability.interestRate || 0,
        startDate: liability.startDate,
        endDate: liability.endDate || '',
        minimumPayment: liability.minimumPayment || 0,
        currency: liability.currency,
        notes: liability.notes || '',
        institutionId: liability.institution?.id?.toString() || '',
        insurancePercentage: liability.insurancePercentage || 0,
        additionalFees: liability.additionalFees || 0,
        realEstateId: undefined,
      }
      : {
        name: '',
        type: 'OTHER',
        principal: 0,
        currentBalance: 0,
        interestRate: 0,
        startDate: today,
        endDate: '',
        minimumPayment: 0,
        currency: baseCurrency || 'USD',
        notes: '',
        institutionId: '',
        insurancePercentage: 0,
        additionalFees: 0,
        realEstateId: undefined,
      },
  });

  const selectedCurrency = watch('currency');
  const selectedType = watch('type');

  const handleFormSubmit = handleSubmit((data: LiabilityFormData) => {
    onSubmit({
      name: data.name,
      type: data.type,
      principal: Number(data.principal),
      currentBalance: Number(data.currentBalance),
      interestRate: data.interestRate && data.interestRate > 0 ? Number(data.interestRate) : undefined,
      startDate: data.startDate,
      endDate: data.endDate && data.endDate !== '' ? data.endDate : undefined,
      minimumPayment: data.minimumPayment && data.minimumPayment > 0 ? Number(data.minimumPayment) : undefined,
      currency: data.currency,
      notes: data.notes && data.notes !== '' ? data.notes : undefined,
      institutionId: data.institutionId && data.institutionId !== '__none__' && data.institutionId !== '' ? Number(data.institutionId) : undefined,
      // Requirement 1.1: Pass insurance percentage and fees (omit if zero/unset)
      insurancePercentage: data.insurancePercentage && data.insurancePercentage > 0 ? Number(data.insurancePercentage) : undefined,
      additionalFees: data.additionalFees && data.additionalFees > 0 ? Number(data.additionalFees) : undefined,
      realEstateId: data.realEstateId,
    });
  });

  return (
    <form onSubmit={handleFormSubmit} className="space-y-4">
      {/* Top Row: Name and Type */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Liability Name */}
        <div className="md:col-span-2">
          <label htmlFor="name" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.name')} *
          </label>
          <Input
            id="name"
            {...register('name')}
            placeholder={t('form.namePlaceholder')}
            error={errors.name?.message}
          />
        </div>

        {/* Liability Type */}
        <div>
          <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.type')} *
          </label>
          <select
            id="type"
            {...register('type')}
            className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
          >
            {liabilityTypes.map((type) => (
              <option key={type} value={type}>
                {getLiabilityTypeName(type)}
              </option>
            ))}
          </select>
          {errors.type && (
            <p className="mt-1 text-sm text-error">{errors.type.message}</p>
          )}
        </div>
      </div>

      {/* Principal, Balance, and Currency Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Principal Amount */}
        <div>
          <label htmlFor="principal" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.principal')} *
          </label>
          <Input
            id="principal"
            type="number"
            step="0.01"
            min="0.01"
            {...register('principal', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.principal?.message}
          />
        </div>

        {/* Current Balance */}
        <div>
          <label htmlFor="currentBalance" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.currentBalance')} *
          </label>
          <Input
            id="currentBalance"
            type="number"
            step="0.01"
            min="0"
            {...register('currentBalance', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.currentBalance?.message}
          />
        </div>

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
                placeholder={t('form.currencyPlaceholder')}
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
      </div>

      {/* Rates Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Interest Rate */}
        <div>
          <label htmlFor="interestRate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.interestRate')}
          </label>
          <Input
            id="interestRate"
            type="number"
            step="0.01"
            min="0"
            max="100"
            {...register('interestRate', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.interestRate?.message}
          />
        </div>

        {/* Insurance Percentage */}
        <div>
          <label htmlFor="insurancePercentage" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.insurancePercentage')}
          </label>
          <Input
            id="insurancePercentage"
            type="number"
            step="0.01"
            min="0"
            max="100"
            {...register('insurancePercentage', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.insurancePercentage?.message}
          />
        </div>

        {/* Minimum Payment */}
        <div>
          <label htmlFor="minimumPayment" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.minimumPayment')}
          </label>
          <Input
            id="minimumPayment"
            type="number"
            step="0.01"
            min="0"
            {...register('minimumPayment', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.minimumPayment?.message}
          />
        </div>
      </div>

      {/* Dates and Fees Row */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* One-time Fee */}
        <div>
          <label htmlFor="additionalFees" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.additionalFees')}
          </label>
          <Input
            id="additionalFees"
            type="number"
            step="0.01"
            min="0"
            {...register('additionalFees', { valueAsNumber: true })}
            placeholder="0.00"
            error={errors.additionalFees?.message}
          />
        </div>

        {/* Start Date */}
        <div>
          <label htmlFor="startDate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.startDate')} *
          </label>
          <Input
            id="startDate"
            type="date"
            max={today}
            {...register('startDate')}
            error={errors.startDate?.message}
          />
        </div>

        {/* End Date */}
        <div>
          <label htmlFor="endDate" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.endDate')}
          </label>
          <Input
            id="endDate"
            type="date"
            {...register('endDate')}
            error={errors.endDate?.message}
          />
        </div>
      </div>

      {/* Notes and Institution */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 border-t border-border pt-4">
        <div>
          <label htmlFor="institutionId" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.institution')}
          </label>
          <Controller
            name="institutionId"
            control={control}
            render={({ field }) => (
              <InstitutionSelector
                value={field.value}
                onValueChange={field.onChange}
                placeholder={t('form.institutionPlaceholder')}
                className="w-full"
              />
            )}
          />
        </div>

        <div>
          <label htmlFor="notes" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.notes')}
          </label>
          <textarea
            id="notes"
            {...register('notes')}
            rows={2}
            placeholder={t('form.notesPlaceholder')}
            className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
          />
          {errors.notes && (
            <p className="mt-1 text-sm text-error">{errors.notes.message}</p>
          )}
        </div>
      </div>

      {/* Link to Real Estate property (only shown for MORTGAGE type, creation only) */}
      {selectedType === 'MORTGAGE' && !isEditing && (
        <div className="border-t border-border pt-4">
          <label htmlFor="realEstateId" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.linkToProperty')} <span className="text-text-muted font-normal">({t('form.optional')})</span>
          </label>
          <Controller
            name="realEstateId"
            control={control}
            render={({ field }) => (
              <select
                id="realEstateId"
                value={field.value ?? ''}
                onChange={(e) => field.onChange(e.target.value ? Number(e.target.value) : undefined)}
                className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
              >
                <option value="">— None —</option>
                {(properties ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            )}
          />
          <p className="mt-1 text-xs text-text-muted">
            Selecting a property will automatically link this mortgage to it.
          </p>
        </div>
      )}

      {/* Actions */}
      <div className="flex justify-end gap-3 pt-4">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button variant="primary" type="submit" isLoading={isLoading}>
          {isEditing ? t('form.update') : t('form.create')}
        </Button>
      </div>
    </form>
  );
}
