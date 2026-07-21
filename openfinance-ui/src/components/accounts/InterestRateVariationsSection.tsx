import { useMemo, useState } from 'react';
import { format, differenceInDays } from 'date-fns';
import { Plus, Trash2, TrendingUp } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';

import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
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
} from '@/hooks/useAccounts';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { DEFAULT_CURRENCY } from '@/utils/currency';
import type { InterestPeriod } from '@/types/account';

interface Props {
    accountId: number;
    accountBalance?: number;
    accountCurrency?: string;
    accountInterestPeriod?: InterestPeriod;
}

const periodCompoundsPerYear: Record<InterestPeriod, number> = {
    DAILY: 365,
    MONTHLY: 12,
    QUARTERLY: 4,
    HALF_YEARLY: 2,
    ANNUAL: 1,
};

/** Net compound interest for `days` elapsed with given rate, taxRate, balance, and compounding n */
function calcPeriodInterest(
    balance: number,
    ratePercent: number,
    taxPercent: number,
    n: number,
    days: number
): number {
    if (!balance || balance <= 0 || ratePercent <= 0 || days <= 0) return 0;
    const r = ratePercent / 100;
    const elapsed = days / 365;
    const gross = balance * (Math.pow(1 + r / n, n * elapsed) - 1);
    const net = gross * (1 - taxPercent / 100);
    return net;
}

export function InterestRateVariationsSection({ accountId, accountBalance = 0, accountCurrency = DEFAULT_CURRENCY, accountInterestPeriod = 'ANNUAL' }: Props) {
    const [isAddDialogOpen, setIsAddDialogOpen] = useState(false);
    const { format: formatCurrency } = useFormatCurrency();
    const { t } = useTranslation('accounts');

    const variationSchema = useMemo(() => z.object({
        rate: z.number().min(0.01, t('validation.interestRatePositive')),
        taxRate: z.number().min(0, t('validation.taxRateMustBePositive')).max(100, t('validation.taxRateMax')).optional(),
        validFrom: z.string().min(1, t('validation.effectiveDateRequired')),
    }), [t]);

    type VariationFormData = z.infer<typeof variationSchema>;
    const { data: variations, isLoading } = useInterestRateVariations(accountId);
    const { data: interestEstimate } = useInterestEstimate(accountId, '1Y');
    const createVariation = useCreateVariation();
    const deleteVariation = useDeleteVariation();

    const {
        register,
        handleSubmit,
        reset,
        formState: { errors },
    } = useForm<VariationFormData>({
        resolver: zodResolver(variationSchema),
        defaultValues: {
            rate: 0,
            taxRate: 0,
            validFrom: new Date().toISOString().split('T')[0],
        },
    });

    const onSubmit = (data: VariationFormData) => {
        createVariation.mutate({
            accountId,
            data: {
                rate: data.rate,
                taxRate: data.taxRate,
                validFrom: data.validFrom,
            },
        }, {
            onSuccess: () => {
                setIsAddDialogOpen(false);
                reset();
            }
        });
    };

    const handleDelete = (variationId: number) => {
        if (confirm(t('interest.deleteConfirm'))) {
            deleteVariation.mutate({ accountId, variationId });
        }
    };

    // Compute per-variation interest produced (days active × daily accrual)
    const variationsWithInterest = useMemo(() => {
        if (!variations || variations.length === 0) return [];
        const n = periodCompoundsPerYear[accountInterestPeriod];
        const today = new Date();

        // Sorted ascending by date for period calculation
        const sorted = [...variations].sort(
            (a, b) => new Date(a.validFrom).getTime() - new Date(b.validFrom).getTime()
        );

        return sorted.map((v, idx) => {
            const from = new Date(v.validFrom);
            const to = idx < sorted.length - 1 ? new Date(sorted[idx + 1].validFrom) : today;
            const days = Math.max(0, differenceInDays(to, from));
            const netInterest = calcPeriodInterest(accountBalance, v.rate, v.taxRate ?? 0, n, days);
            return { ...v, days, netInterest };
        });
    }, [variations, accountBalance, accountInterestPeriod]);

    const earned = interestEstimate?.historicalAccumulated ?? 0;
    const projected = interestEstimate?.estimate ?? 0;
    const projectedPct = accountBalance > 0 ? ((projected / accountBalance) * 100).toFixed(2) : '0.00';

    if (isLoading) {
        return (
            <Card className="p-6">
                <LoadingSkeleton className="h-8 w-48 mb-4" />
                <LoadingSkeleton className="h-40 w-full" />
            </Card>
        );
    }

    return (
        <Card className="p-6">
            {/* Header */}
            <div className="flex items-start justify-between mb-6">
                <div className="flex-1">
                    <h2 className="text-lg font-semibold text-text-primary">{t('interest.historyTitle')}</h2>
                    <p className="text-sm text-text-secondary mb-3">{t('interest.historyDescription')}</p>
                    {/* Summary pills */}
                    <div className="flex items-center gap-3 flex-wrap">
                        <div className="flex items-center gap-1.5 bg-surface border border-border rounded-lg px-3 py-1.5">
                            <span className="text-xs text-text-muted">{t('interest.earnedSoFar')}</span>
                            <span className="text-sm font-mono font-semibold text-text-primary">
                                {formatCurrency(earned, accountCurrency)}
                            </span>
                        </div>
                        <div className="text-text-muted text-xs">|</div>
                        <div className="flex items-center gap-1.5 bg-success/5 border border-success/20 rounded-lg px-3 py-1.5">
                            <TrendingUp className="h-3.5 w-3.5 text-success" />
                            <span className="text-xs text-text-muted">{t('interest.projectedNet')}</span>
                            <span className="text-sm font-mono font-semibold text-success">
                                {formatCurrency(projected, accountCurrency)}
                            </span>
                            {projected > 0 && (
                                <span className="text-xs text-success/70">({projectedPct}%)</span>
                            )}
                        </div>
                        {variationsWithInterest.length > 0 && (() => {
                            const totalProduced = variationsWithInterest.reduce((s, v) => s + v.netInterest, 0);
                            return totalProduced > 0 ? (
                                <>
                                    <div className="text-text-muted text-xs">|</div>
                                    <div className="flex items-center gap-1.5 bg-primary/5 border border-primary/20 rounded-lg px-3 py-1.5">
                                        <span className="text-xs text-text-muted">{t('interest.totalProduced')}</span>
                                        <span className="text-sm font-mono font-semibold text-primary">
                                            +{formatCurrency(totalProduced, accountCurrency)}
                                        </span>
                                    </div>
                                </>
                            ) : null;
                        })()}
                    </div>
                </div>
                <Button onClick={() => setIsAddDialogOpen(true)} size="sm" className="ml-4 shrink-0">
                    <Plus className="h-4 w-4 mr-2" />
                    {t('interest.addRate')}
                </Button>
            </div>

            {variationsWithInterest.length > 0 ? (
                <div className="border border-border rounded-lg overflow-hidden">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-surface-elevated text-text-secondary">
                            <tr>
                                <th className="px-4 py-3 font-medium">{t('interest.cols.effectiveDate')}</th>
                                <th className="px-4 py-3 font-medium">{t('interest.cols.interestRate')}</th>
                                <th className="px-4 py-3 font-medium">{t('interest.cols.taxRate')}</th>
                                <th className="px-4 py-3 font-medium">{t('interest.cols.periodActive')}</th>
                                <th className="px-4 py-3 font-medium text-right">{t('interest.cols.interestProduced')}</th>
                                <th className="px-4 py-3 font-medium text-right w-12"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-border">
                            {variationsWithInterest.map((variation) => (
                                <tr key={variation.id} className="hover:bg-surface-elevated/50 transition-colors">
                                    <td className="px-4 py-3 text-text-primary">
                                        {format(new Date(variation.validFrom), 'MMM d, yyyy')}
                                    </td>
                                    <td className="px-4 py-3 text-text-primary font-mono bg-success/5">
                                        {variation.rate.toFixed(2)}%
                                    </td>
                                    <td className="px-4 py-3 text-text-secondary font-mono">
                                        {variation.taxRate ? `${variation.taxRate.toFixed(2)}%` : '0.00%'}
                                    </td>
                                    <td className="px-4 py-3 text-text-secondary">
                                        {variation.days === 0
                                            ? <span className="text-xs text-text-muted italic">{t('interest.today')}</span>
                                            : <span>{variation.days}d</span>
                                        }
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        {variation.netInterest > 0 ? (
                                            <span className="font-mono font-semibold text-success">
                                                +{formatCurrency(variation.netInterest, accountCurrency)}
                                            </span>
                                        ) : (
                                            <span className="text-xs text-text-muted italic">—</span>
                                        )}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        <Button
                                            variant="ghost"
                                            size="sm"
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
                        <DialogTitle>{t('interest.addDialog.title')}</DialogTitle>
                    </DialogHeader>
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 py-4">
                        <div>
                            <label htmlFor="validFrom" className="block text-sm font-medium text-text-primary mb-1.5">
                                {t('interest.addDialog.effectiveDate')} *
                            </label>
                            <Input
                                id="validFrom"
                                type="date"
                                {...register('validFrom')}
                                error={errors.validFrom?.message}
                            />
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                            <div>
                                <label htmlFor="rate" className="block text-sm font-medium text-text-primary mb-1.5">
                                    {t('interest.addDialog.interestRate')} *
                                </label>
                                <Input
                                    id="rate"
                                    type="number"
                                    step="0.01"
                                    {...register('rate', { valueAsNumber: true })}
                                    placeholder="0.00"
                                    error={errors.rate?.message}
                                />
                            </div>
                            <div>
                                <label htmlFor="taxRate" className="block text-sm font-medium text-text-primary mb-1.5">
                                    {t('interest.addDialog.taxRate')}
                                </label>
                                <Input
                                    id="taxRate"
                                    type="number"
                                    step="0.01"
                                    {...register('taxRate', { valueAsNumber: true })}
                                    placeholder="0.00"
                                    error={errors.taxRate?.message}
                                />
                            </div>
                        </div>

                        <DialogFooter className="pt-4">
                            <Button type="button" variant="ghost" onClick={() => setIsAddDialogOpen(false)}>
                                {t('common:cancel')}
                            </Button>
                            <Button type="submit" isLoading={createVariation.isPending}>
                                {t('interest.addDialog.saveRate')}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>
        </Card>
    );
}
