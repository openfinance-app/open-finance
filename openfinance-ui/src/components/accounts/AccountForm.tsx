/**
 * AccountForm Component
 * Task 2.2.10: Create AccountForm component with validation
 * Task 6.2.15: Updated to use CurrencySelector component
 * Task 6.3.13: Updated to use baseCurrency from AuthContext
 * Task 2.5.11: Updated to use InstitutionSelector component
 * UX Improvement: Better interest calculation section with live preview
 * i18n: All labels & messages translated via useTranslation
 *
 * Form for creating and editing accounts with Zod validation
 */
import { useMemo } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { TrendingUp, Shield, Percent, CalendarDays } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { CurrencySelector } from '@/components/ui/CurrencySelector';
import { InstitutionSelector } from '@/components/ui/InstitutionSelector';
import { Switch } from '@/components/ui/Switch';
import { ExchangeRateInline } from '@/components/ui/ExchangeRateDisplay';
import { useAuthContext } from '@/context/AuthContext';
import { useLatestExchangeRate } from '@/hooks/useCurrency';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import { multiply, percentage } from '@/utils/money';
import type { Account, AccountRequest, AccountType, InterestPeriod } from '@/types/account';

const ACCOUNT_TYPES: AccountType[] = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER'];
const INTEREST_PERIODS: { value: InterestPeriod; compoundsPerYear: number }[] = [
  { value: 'DAILY', compoundsPerYear: 365 },
  { value: 'MONTHLY', compoundsPerYear: 12 },
  { value: 'QUARTERLY', compoundsPerYear: 4 },
  { value: 'HALF_YEARLY', compoundsPerYear: 2 },
  { value: 'ANNUAL', compoundsPerYear: 1 },
];

type AccountFormData = {
  name: string;
  accountNumber?: string;
  type: AccountType;
  currency: string;
  initialBalance: number;
  description?: string;
  institutionId?: string;
  isInterestEnabled?: boolean;
  interestPeriod?: InterestPeriod;
  interestRate?: number;
  taxRate?: number;
};

interface AccountFormProps {
  account?: Account;
  onSubmit: (data: AccountRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
  existingAccountNames?: string[];
}

/** Calculate projected annual net interest earnings using compound interest formula */
function calcInterestPreview(
  balance: number,
  annualRatePercent: number,
  taxRatePercent: number,
  compoundsPerYear: number
): { grossInterest: number; taxAmount: number; netInterest: number } {
  if (!balance || balance <= 0 || !annualRatePercent || annualRatePercent <= 0) {
    return { grossInterest: 0, taxAmount: 0, netInterest: 0 };
  }
  const r = annualRatePercent / 100;
  const n = compoundsPerYear;
  const grossInterest = balance * (Math.pow(1 + r / n, n) - 1);
  const taxAmount = grossInterest * (taxRatePercent / 100);
  const netInterest = grossInterest - taxAmount;
  return { grossInterest, taxAmount, netInterest };
}

export function AccountForm({ account, onSubmit, onCancel, isLoading, existingAccountNames = [] }: AccountFormProps) {
  const isEditing = !!account;
  const { baseCurrency } = useAuthContext();
  const { format: formatCurrency } = useFormatCurrency();
  const { t } = useTranslation('accounts');

  const accountSchema = useMemo(() => z.object({
    name: z.string().min(1, t('validation.nameRequired')).max(100, t('validation.nameTooLong')),
    accountNumber: z.string().max(50, t('validation.accountNumberTooLong')).optional(),
    type: z.enum(['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'INVESTMENT', 'CASH', 'OTHER']),
    currency: z.string().length(3),
    initialBalance: z.coerce.number(),
    description: z.string().max(500, t('validation.descriptionTooLong')).optional(),
    institutionId: z.string().optional(),
    isInterestEnabled: z.boolean().optional(),
    interestPeriod: z.enum(['ANNUAL', 'HALF_YEARLY', 'QUARTERLY', 'MONTHLY', 'DAILY']).optional(),
    interestRate: z.coerce.number().min(0, t('validation.interestRateMustBePositive')).optional(),
    taxRate: z.coerce.number().min(0, t('validation.taxRateMustBePositive')).max(100, t('validation.taxRateMax')).optional(),
  }).superRefine((data, ctx) => {
    if (data.type !== 'CREDIT_CARD' && data.initialBalance < 0) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: t('validation.balanceNegative'),
        path: ['initialBalance'],
      });
    }
    const nameLower = data.name.trim().toLowerCase();
    if (existingAccountNames.some((n) => n.trim().toLowerCase() === nameLower)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: t('validation.duplicateName'),
        path: ['name'],
      });
    }
  }), [t, existingAccountNames]);

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<AccountFormData>({
    resolver: zodResolver(accountSchema) as any,
    mode: 'onChange',
    defaultValues: account
      ? {
        name: account.name,
        accountNumber: account.accountNumber || '',
        type: account.type,
        currency: account.currency,
        initialBalance: account.balance,
        description: account.description || '',
        institutionId: account.institution?.id?.toString() || '',
        isInterestEnabled: account.isInterestEnabled || false,
        interestPeriod: account.interestPeriod || 'ANNUAL',
        interestRate: 0,
        taxRate: 0,
      }
      : {
        name: '',
        accountNumber: '',
        type: 'CHECKING',
        currency: baseCurrency || DEFAULT_CURRENCY,
        initialBalance: 0,
        description: '',
        institutionId: '',
        isInterestEnabled: false,
        interestPeriod: 'ANNUAL',
        interestRate: 0,
        taxRate: 0,
      },
  });

  const selectedCurrency = watch('currency');
  const initialBalance = watch('initialBalance');
  const isInterestEnabled = watch('isInterestEnabled');
  const interestRate = watch('interestRate') ?? 0;
  const taxRate = watch('taxRate') ?? 0;
  const interestPeriod = watch('interestPeriod') ?? 'ANNUAL';

  const { data: exchangeRate } = useLatestExchangeRate(
    selectedCurrency,
    baseCurrency,
    selectedCurrency !== baseCurrency ? 1 : 0
  );

  const convertedAmount =
    selectedCurrency &&
      selectedCurrency !== baseCurrency &&
      exchangeRate &&
      initialBalance
      ? multiply(initialBalance, exchangeRate.rate)
      : undefined;

  // Live interest preview computation
  const interestPreview = useMemo(() => {
    const periodInfo = INTEREST_PERIODS.find(p => p.value === interestPeriod);
    const n = periodInfo?.compoundsPerYear ?? 1;
    return calcInterestPreview(initialBalance ?? 0, interestRate, taxRate, n);
  }, [initialBalance, interestRate, taxRate, interestPeriod]);

  const handleFormSubmit = handleSubmit((data: AccountFormData) => {
    onSubmit({
      name: data.name,
      accountNumber: data.accountNumber || undefined,
      type: data.type,
      currency: data.currency,
      initialBalance: Number(data.initialBalance),
      description: data.description || undefined,
      institutionId: data.institutionId ? parseInt(data.institutionId, 10) : undefined,
      isInterestEnabled: data.isInterestEnabled,
      interestPeriod: data.interestPeriod,
      interestRate: data.interestRate,
      taxRate: data.taxRate,
    });
  });

  return (
    <form onSubmit={handleFormSubmit} className="space-y-4">
      {/* Institution */}
      <div>
        <label htmlFor="institution" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.institution')}
        </label>
        <Controller
          name="institutionId"
          control={control}
          render={({ field }) => (
            <InstitutionSelector
              value={field.value}
              onValueChange={field.onChange}
              placeholder={t('form.selectBank')}
              className="w-full"
            />
          )}
        />
      </div>

      {/* Account Name + Account Number in a row */}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="name" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.accountName')} *
          </label>
          <Input
            id="name"
            {...register('name')}
            placeholder={t('form.accountNamePlaceholder')}
            error={errors.name?.message}
          />
        </div>
        <div>
          <label htmlFor="accountNumber" className="block text-sm font-medium text-text-primary mb-1.5">
            {t('form.accountNumber')}
          </label>
          <Input
            id="accountNumber"
            {...register('accountNumber')}
            placeholder={t('form.accountNumberPlaceholder')}
            error={errors.accountNumber?.message}
          />
          <p className="text-xs text-text-secondary mt-1">
            {t('form.accountNumberHint')}
          </p>
        </div>
      </div>

      {/* Account Type */}
      <div>
        <label htmlFor="type" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.accountType')} *
        </label>
        <select
          id="type"
          {...register('type')}
          className="w-full h-10 px-3 rounded-lg bg-surface border border-border text-text-primary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
        >
          {ACCOUNT_TYPES.map((type) => (
            <option key={type} value={type}>
              {t(`form.types.${type}`)}
            </option>
          ))}
        </select>
        {errors.type && (
          <p className="mt-1 text-sm text-error">{errors.type.message}</p>
        )}
      </div>

      {/* Currency + Balance in a row */}
      <div className="grid grid-cols-2 gap-4">
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
                placeholder={t('form.currency')}
                className="w-full"
              />
            )}
          />
          {errors.currency && (
            <p className="mt-1 text-sm text-error">{errors.currency.message}</p>
          )}
          {selectedCurrency && selectedCurrency !== baseCurrency && (
            <div className="mt-1.5">
              <ExchangeRateInline from={selectedCurrency} to={baseCurrency} />
            </div>
          )}
        </div>
        <div>
          <label htmlFor="initialBalance" className="block text-sm font-medium text-text-primary mb-1.5">
            {isEditing ? t('form.currentBalance') : t('form.initialBalance')} *
          </label>
          <Input
            id="initialBalance"
            type="number"
            step="0.01"
            {...register('initialBalance', { valueAsNumber: true })}
            onFocus={(e) => e.target.select()}
            placeholder="0.00"
            error={errors.initialBalance?.message}
          />
          {convertedAmount !== undefined && (
            <p className="text-xs text-text-secondary mt-1">
              ≈ {formatCurrency(convertedAmount, baseCurrency)}
            </p>
          )}
        </div>
      </div>

      {/* Description */}
      <div>
        <label htmlFor="description" className="block text-sm font-medium text-text-primary mb-1.5">
          {t('form.description')}
        </label>
        <textarea
          id="description"
          {...register('description')}
          rows={2}
          placeholder={t('form.descriptionPlaceholder')}
          className="w-full px-3 py-2 rounded-lg bg-surface border border-border text-text-primary placeholder:text-text-tertiary focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent resize-none"
        />
        {errors.description && (
          <p className="mt-1 text-sm text-error">{errors.description.message}</p>
        )}
      </div>

      {/* Interest Settings */}
      <div className="rounded-xl border border-border overflow-hidden">
        {/* Toggle header */}
        <div className="flex items-center justify-between px-4 py-3 bg-surface-elevated">
          <div className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4 text-primary" />
            <div>
              <p className="text-sm font-medium text-text-primary">{t('form.interestCalculation')}</p>
              <p className="text-xs text-text-secondary">{t('form.interestCalculationHint')}</p>
            </div>
          </div>
          <Controller
            name="isInterestEnabled"
            control={control}
            render={({ field }) => (
              <Switch
                id="isInterestEnabled"
                checked={field.value}
                onCheckedChange={field.onChange}
              />
            )}
          />
        </div>

        {isInterestEnabled && (
          <div className="px-4 py-4 space-y-4 border-t border-border bg-surface/50">
            {isEditing ? (
              <p className="text-sm text-text-secondary italic">
                {t('form.interestEditHint')}
              </p>
            ) : (
              <>
                {/* Period + Rates in one compact row */}
                <div className="grid grid-cols-3 gap-3">
                  <div>
                    <label htmlFor="interestPeriod" className="block text-xs font-medium text-text-secondary mb-1">
                      <span className="flex items-center gap-1"><CalendarDays className="h-3 w-3" /> {t('form.compounding')}</span>
                    </label>
                    <select
                      id="interestPeriod"
                      {...register('interestPeriod')}
                      className="w-full h-9 px-2 rounded-lg bg-surface border border-border text-text-primary text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
                    >
                      {INTEREST_PERIODS.map((period) => (
                        <option key={period.value} value={period.value}>
                          {t(`form.periods.${period.value}`)}
                        </option>
                      ))}
                    </select>
                    {errors.interestPeriod && (
                      <p className="mt-1 text-xs text-error">{errors.interestPeriod.message}</p>
                    )}
                  </div>
                  <div>
                    <label htmlFor="interestRate" className="block text-xs font-medium text-text-secondary mb-1">
                      <span className="flex items-center gap-1"><Percent className="h-3 w-3" /> {t('form.interestRate')}</span>
                    </label>
                    <Input
                      id="interestRate"
                      type="number"
                      step="0.01"
                      min="0"
                      {...register('interestRate', { valueAsNumber: true })}
                      onFocus={(e) => e.target.select()}
                      placeholder="0.00"
                      error={errors.interestRate?.message}
                      className="h-9"
                    />
                  </div>
                  <div>
                    <label htmlFor="taxRate" className="block text-xs font-medium text-text-secondary mb-1">
                      <span className="flex items-center gap-1"><Shield className="h-3 w-3" /> {t('form.taxRate')}</span>
                    </label>
                    <Input
                      id="taxRate"
                      type="number"
                      step="0.01"
                      min="0"
                      max="100"
                      {...register('taxRate', { valueAsNumber: true })}
                      onFocus={(e) => e.target.select()}
                      placeholder="0.00"
                      error={errors.taxRate?.message}
                      className="h-9"
                    />
                  </div>
                </div>

                {/* Live Calculation Preview */}
                {interestRate > 0 && (initialBalance ?? 0) > 0 ? (
                  <div className="rounded-lg bg-primary/5 border border-primary/20 p-3">
                    <p className="text-xs font-semibold text-primary mb-2 flex items-center gap-1">
                      <TrendingUp className="h-3.5 w-3.5" />
                      {t('form.interestPreviewTitle')}
                    </p>
                    <div className="grid grid-cols-3 gap-2 text-center">
                      <div className="bg-surface rounded-lg p-2">
                        <p className="text-xs text-text-secondary mb-0.5">{t('form.grossInterest')}</p>
                        <p className="text-sm font-semibold font-mono text-success">
                          +{formatCurrency(interestPreview.grossInterest, selectedCurrency)}
                        </p>
                      </div>
                      <div className="bg-surface rounded-lg p-2">
                        <p className="text-xs text-text-secondary mb-0.5">{t('form.taxDeduction')}</p>
                        <p className="text-sm font-semibold font-mono text-error">
                          -{formatCurrency(interestPreview.taxAmount, selectedCurrency)}
                        </p>
                      </div>
                      <div className="bg-surface rounded-lg p-2 border border-primary/30">
                        <p className="text-xs text-text-secondary mb-0.5">{t('form.netEarnings')}</p>
                        <p className="text-sm font-bold font-mono text-primary">
                          +{formatCurrency(interestPreview.netInterest, selectedCurrency)}
                        </p>
                        {(initialBalance ?? 0) > 0 && (
                          <p className="text-xs font-medium text-primary/70 mt-0.5">
                            ({percentage(interestPreview.netInterest, initialBalance ?? 1).toFixed(2)}%)
                          </p>
                        )}
                      </div>
                    </div>
                    <p className="text-xs text-text-muted mt-2 text-center">
                      Based on {formatCurrency(initialBalance ?? 0, selectedCurrency)} balance · {interestRate}% {t(`form.periods.${interestPeriod}`)} compounding
                      {taxRate > 0 ? ` · ${taxRate}% tax` : ''}
                    </p>
                  </div>
                ) : (
                  <div className="rounded-lg bg-surface border border-border border-dashed p-3 text-center">
                    <p className="text-xs text-text-muted">
                      {t('form.interestPreviewHint')}
                    </p>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>


      {/* Actions */}
      <div className="flex justify-end gap-3 pt-2">
        <Button variant="ghost" type="button" onClick={onCancel} disabled={isLoading}>
          {t('form.cancel')}
        </Button>
        <Button variant="primary" type="submit" isLoading={isLoading}>
          {isEditing ? t('form.updateAccount') : t('form.createAccount')}
        </Button>
      </div>
    </form>
  );
}
