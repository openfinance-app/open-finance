import { ExactDecimal, decimalField } from '@/utils/exact-decimal';
import { isValidDecimalString } from '@/utils/money';
import { formatDecimal } from '@/utils/format';
import { useMemo, useState } from 'react';
import { formatDate, toLocalISODate } from '@/utils/date';
import { Plus, Trash2, TrendingUp, Pencil } from 'lucide-react';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { DateInput } from '@/components/ui/DateInput';
import { NumberInput } from '@/components/ui/NumberInput';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { LoadingSkeleton } from '@/components/LoadingComponents';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/Dialog';
import {
  useInterestRateVariations,
  useCreateVariation,
  useDeleteVariation,
  useInterestEstimate,
  useUpdateVariation,
} from '@/hooks/useAccounts';
import { DEFAULT_CURRENCY } from '@/utils/currency';

import {
  INTEREST_VALUE_DATE_MODES,
  INTEREST_DAY_COUNT_BASES,
  type InterestRateVariation,
} from '@/types/account';

interface Props {
  accountId: number;
  accountCurrency?: string;
}

export function InterestRateVariationsSection({
  accountId,
  accountCurrency = DEFAULT_CURRENCY,
}: Props) {
  const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
  const [editingRate, setEditingRate] = useState<InterestRateVariation | null>(null);
  const { t } = useTranslation('accounts');

  const variationSchema = useMemo(
    () =>
      z.object({
        rate: z
          .string()
          .refine(
            v => isValidDecimalString(v) && new ExactDecimal(v).gte('0'),
            t('validation.interestRatePositive')
          ),
        taxRate: z
          .string()
          .refine(
            v => isValidDecimalString(v) && new ExactDecimal(v).gte(0),
            t('validation.taxRateMustBePositive')
          )
          .refine(v => new ExactDecimal(v).lte(100), t('validation.taxRateMax'))
          .optional(),
        validFrom: z.string().min(1, t('validation.effectiveDateRequired')),
        creditValueDateMode: z.enum(INTEREST_VALUE_DATE_MODES),
        debitValueDateMode: z.enum(INTEREST_VALUE_DATE_MODES),
        dayCountBasis: z.enum(INTEREST_DAY_COUNT_BASES),
      }),
    [t]
  );

  type VariationFormData = z.infer<typeof variationSchema>;
  const { data: variations, isLoading } = useInterestRateVariations(accountId);
  const {
    data: interestEstimate,
    isLoading: estimateLoading,
    isError: estimateError,
  } = useInterestEstimate(accountId);
  const createVariation = useCreateVariation();
  const updateVariation = useUpdateVariation();
  const deleteVariation = useDeleteVariation();

  const {
    handleSubmit,
    control,
    register,
    reset,
    formState: { errors },
  } = useForm<VariationFormData>({
    resolver: zodResolver(variationSchema),
    defaultValues: {
      rate: '0',
      taxRate: '0',
      validFrom: toLocalISODate(new Date()),
      creditValueDateMode: 'TRANSACTION_DATE',
      debitValueDateMode: 'TRANSACTION_DATE',
      dayCountBasis: 'ACTUAL_365',
    },
  });

  const onSubmit = (data: VariationFormData) => {
    const callbacks = {
      onSuccess: () => {
        setIsAddDialogOpen(false);
        setEditingRate(null);
        reset();
      },
    };
    if (editingRate) {
      updateVariation.mutate({ accountId, variationId: editingRate.id, data }, callbacks);
    } else {
      createVariation.mutate({ accountId, data }, callbacks);
    }
  };

  const openEditor = (variation: InterestRateVariation | null) => {
    setEditingRate(variation);
    reset({
      rate: variation ? decimalField(variation, 'rate') : '0',
      taxRate: variation ? decimalField(variation, 'taxRate') || '0' : '0',
      validFrom: variation?.validFrom ?? toLocalISODate(new Date()),
      creditValueDateMode: variation?.creditValueDateMode ?? 'TRANSACTION_DATE',
      debitValueDateMode: variation?.debitValueDateMode ?? 'TRANSACTION_DATE',
      dayCountBasis: variation?.dayCountBasis ?? 'ACTUAL_365',
    });
    setIsAddDialogOpen(true);
  };

  const handleDelete = (variationId: number) => {
    if (confirm(t('interest.deleteConfirm'))) {
      deleteVariation.mutate({ accountId, variationId });
    }
  };

  const orderedVariations = useMemo(
    () => [...(variations ?? [])].sort((a, b) => a.validFrom.localeCompare(b.validFrom)),
    [variations]
  );
  const today = toLocalISODate(new Date());
  const earned = interestEstimate?.yearToDate ?? 0;
  const projected = interestEstimate?.estimate ?? 0;
  const totalEarned = interestEstimate?.historicalAccumulated ?? 0;
  const estimateUnavailable = estimateLoading || estimateError;

  if (isLoading) {
    return (
      <Card className="p-6">
        <LoadingSkeleton className="h-8 w-48 mb-4" />
        <LoadingSkeleton className="h-40 w-full" />
      </Card>
    );
  }

  return (
    <Card className="min-w-0 p-4 sm:p-6">
      {/* Header */}
      <div className="mb-6 flex flex-col items-start justify-between gap-4 sm:flex-row">
        <div className="min-w-0 flex-1">
          <h2 className="plate-label ">{t('interest.historyTitle')}</h2>
          <p className="text-sm text-text-secondary mb-3">{t('interest.historyDescription')}</p>
          {/* Summary pills */}
          <div className="flex items-center gap-3 flex-wrap">
            <div className="flex items-center gap-1.5 bg-surface border border-border rounded-lg px-3 py-1.5">
              <span className="text-xs text-text-muted">{t('interest.earnedSoFar')}</span>
              <span className="text-sm font-mono font-semibold text-text-primary">
                {estimateUnavailable ? (
                  '—'
                ) : (
                  <ConvertedAmount amount={earned} currency={accountCurrency} inline />
                )}
              </span>
            </div>
            <div className="flex items-center gap-1.5 bg-success/5 border border-success/20 rounded-lg px-3 py-1.5">
              <TrendingUp className="h-3.5 w-3.5 text-success" />
              <span className="text-xs text-text-muted">{t('interest.projectedNet')}</span>
              <span className="text-sm font-mono font-semibold text-success">
                {estimateUnavailable ? (
                  '—'
                ) : (
                  <ConvertedAmount amount={projected} currency={accountCurrency} inline />
                )}
              </span>
            </div>
            <div className="flex items-center gap-1.5 bg-primary/5 border border-primary/20 rounded-lg px-3 py-1.5">
              <span className="text-xs text-text-muted">{t('interest.totalEarned')}</span>
              <span className="text-sm font-mono font-semibold text-primary">
                {estimateUnavailable ? (
                  '—'
                ) : (
                  <ConvertedAmount amount={totalEarned} currency={accountCurrency} inline />
                )}
              </span>
            </div>
          </div>
        </div>
        <Button onClick={() => openEditor(null)} size="sm" className="shrink-0">
          <Plus className="h-4 w-4 mr-2" />
          {t('interest.addRate')}
        </Button>
      </div>

      <div className="mb-4 space-y-2">
        <p className="text-xs text-text-secondary">{t('interest.estimateExplanation')}</p>
        {estimateError && (
          <p role="alert" className="text-sm text-error">
            {t('interest.loadError')}
          </p>
        )}
      </div>

      {orderedVariations.length > 0 ? (
        <div className="border border-border rounded-lg overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-elevated text-text-secondary">
              <tr>
                <th className="px-4 py-3 font-medium">{t('interest.cols.effectiveDate')}</th>
                <th className="px-4 py-3 font-medium">{t('interest.cols.interestRate')}</th>
                <th className="px-4 py-3 font-medium">{t('interest.cols.taxRate')}</th>
                <th className="px-4 py-3 font-medium">{t('interest.cols.periodActive')}</th>
                <th className="px-4 py-3 font-medium text-right">
                  {t('interest.cols.interestProduced')}
                </th>
                <th className="px-4 py-3 font-medium text-right w-12"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {orderedVariations.map(variation => (
                <tr key={variation.id} className="hover:bg-surface-elevated/50 transition-colors">
                  <td className="px-4 py-3 text-text-primary">
                    {formatDate(variation.validFrom)}
                    <div className="mt-1 text-xs text-text-secondary">
                      {t(
                        `interest.valueModes.${variation.creditValueDateMode ?? 'TRANSACTION_DATE'}`
                      )}{' '}
                      /{' '}
                      {t(
                        `interest.valueModes.${variation.debitValueDateMode ?? 'TRANSACTION_DATE'}`
                      )}
                      {' · '}
                      {t(`interest.bases.${variation.dayCountBasis ?? 'ACTUAL_365'}`)}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-text-primary font-mono bg-success/5">
                    {formatDecimal(variation.rate, 2)}%
                  </td>
                  <td className="px-4 py-3 text-text-secondary font-mono">
                    {variation.taxRate
                      ? `${formatDecimal(variation.taxRate, 2)}%`
                      : `${formatDecimal(0, 2)}%`}
                  </td>
                  <td className="px-4 py-3 text-text-secondary">
                    {variation.validFrom > today ? (
                      <span className="text-xs text-text-muted italic">
                        {t('interest.scheduled')}
                      </span>
                    ) : (
                      <span>{variation.activeDays ?? 0}d</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {(variation.interestProduced ?? 0) > 0 ? (
                      <span className="font-mono font-semibold text-success">
                        +
                        <ConvertedAmount
                          amount={variation.interestProduced ?? 0}
                          currency={accountCurrency}
                          inline
                        />
                      </span>
                    ) : (
                      <span className="text-xs text-text-muted italic">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => openEditor(variation)}
                      aria-label={t('interest.editRate')}
                      className="h-8 w-8 p-0"
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('interest.deleteRate')}
                      onClick={() => handleDelete(variation.id)}
                      className="text-error hover:text-error hover:bg-error/10 h-8 w-8 p-0"
                      disabled={deleteVariation.isPending}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="text-center py-8 text-text-secondary border border-dashed border-border rounded-lg bg-surface/50">
          {t('interest.noVariations')}
        </div>
      )}

      <Dialog open={isAddDialogOpen} onOpenChange={setIsAddDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {t(editingRate ? 'interest.editRate' : 'interest.addDialog.title')}
            </DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-4">
            <div>
              <label
                htmlFor="validFrom"
                className="block text-sm font-medium text-text-primary mb-1.5"
              >
                {t('interest.addDialog.effectiveDate')} *
              </label>
              <Controller
                name="validFrom"
                control={control}
                render={({ field }) => (
                  <DateInput
                    id="validFrom"
                    value={field.value}
                    onChange={field.onChange}
                    onBlur={field.onBlur}
                    error={errors.validFrom?.message}
                  />
                )}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label
                  htmlFor="rate"
                  className="block text-sm font-medium text-text-primary mb-1.5"
                >
                  {t('interest.addDialog.interestRate')} *
                </label>
                <Controller
                  name="rate"
                  control={control}
                  render={({ field }) => (
                    <NumberInput
                      id="rate"
                      value={
                        field.value !== undefined && !Number.isNaN(field.value)
                          ? String(field.value)
                          : ''
                      }
                      onChange={val => field.onChange(val || '0')}
                      onBlur={field.onBlur}
                      placeholder="0.00"
                      error={errors.rate?.message}
                      min="0"
                    />
                  )}
                />
              </div>
              <div>
                <label
                  htmlFor="taxRate"
                  className="block text-sm font-medium text-text-primary mb-1.5"
                >
                  {t('interest.addDialog.taxRate')}
                </label>
                <Controller
                  name="taxRate"
                  control={control}
                  render={({ field }) => (
                    <NumberInput
                      id="taxRate"
                      value={
                        field.value !== undefined && !Number.isNaN(field.value)
                          ? String(field.value)
                          : ''
                      }
                      onChange={val => field.onChange(val || '0')}
                      onBlur={field.onBlur}
                      placeholder="0.00"
                      error={errors.taxRate?.message}
                      min="0"
                      max="100"
                    />
                  )}
                />
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              {(['creditValueDateMode', 'debitValueDateMode'] as const).map(field => (
                <label key={field} className="text-sm">
                  {t(`interest.${field}`)}
                  <select
                    {...register(field)}
                    className="mt-1 w-full rounded-md border border-border bg-surface px-3 py-2"
                  >
                    {INTEREST_VALUE_DATE_MODES.map(mode => (
                      <option key={mode} value={mode}>
                        {t(`interest.valueModes.${mode}`)}
                      </option>
                    ))}
                  </select>
                </label>
              ))}
              <label className="text-sm sm:col-span-2">
                {t('interest.dayCountBasis')}
                <select
                  {...register('dayCountBasis')}
                  className="mt-1 w-full rounded-md border border-border bg-surface px-3 py-2"
                >
                  {INTEREST_DAY_COUNT_BASES.map(basis => (
                    <option key={basis} value={basis}>
                      {t(`interest.bases.${basis}`)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            <p className="text-xs text-text-secondary">{t('interest.valueDateHelp')}</p>
            {(createVariation.error || updateVariation.error) && (
              <p role="alert" className="text-sm text-error">
                {(editingRate ? updateVariation.error : createVariation.error)?.message}
              </p>
            )}
            <DialogFooter className="pt-4">
              <Button type="button" variant="ghost" onClick={() => setIsAddDialogOpen(false)}>
                {t('common:cancel')}
              </Button>
              <Button
                type="submit"
                isLoading={createVariation.isPending || updateVariation.isPending}
              >
                {t('interest.addDialog.saveRate')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </Card>
  );
}
