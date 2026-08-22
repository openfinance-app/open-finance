import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
    Calculator,
    RefreshCw,
    DollarSign,
    Percent,
    Calendar,
    Plus,
    Trash2,
    TrendingDown,
    Clock,
    ChevronDown,
    ChevronUp,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Label } from '../ui/Label';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { useEarlyPayoffCalculator } from '../../hooks/useEarlyPayoffCalculator';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import {
    Tooltip as UITooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/Tooltip';
import { useCountryToolConfig } from '@/hooks/useCountryToolConfig';
import type { EarlyPayoffScenario } from '@/types/calculator';
import {
    AreaChart,
    Area,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
    ResponsiveContainer,
} from 'recharts';

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatTerm(months: number, t: (k: string, o?: Record<string, unknown>) => string): string {
    const y = Math.floor(months / 12);
    const m = months % 12;
    if (y === 0) return t('earlyPayoff.results.months', { count: m });
    if (m === 0) return t('earlyPayoff.results.months', { count: y * 12 });
    return t('earlyPayoff.results.years', { years: y, months: m });
}

// ── Sub-components ───────────────────────────────────────────────────────────

interface ScenarioCardProps {
    title: string;
    scenario: EarlyPayoffScenario;
    isBase?: boolean;
    highlight?: boolean;
    currency: string;
    isIRA: boolean;
    t: (k: string, o?: Record<string, unknown>) => string;
}

function ScenarioCard({ title, scenario, isBase, highlight, currency, isIRA, t }: ScenarioCardProps) {
    const termLabel = formatTerm(scenario.totalMonths, t);
    const cardClass = highlight
        ? 'border-primary/40 bg-primary/5'
        : 'border-border';
    const greenClass = 'text-green-600 dark:text-green-400 font-semibold block truncate min-w-0';
    const netSavingsClass = scenario.netSavings >= 0
        ? 'text-green-600 dark:text-green-400'
        : 'text-red-600 dark:text-red-400';

    return (
        <Card className={cardClass}>
            <CardHeader className="pb-3">
                <CardTitle className="text-base flex items-center gap-2">
                    {title}
                    {highlight && (
                        <Badge variant="default" className="text-xs">
                            {t('earlyPayoff.scenarios.reduceDuration')}
                        </Badge>
                    )}
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                <Row label={t('earlyPayoff.results.monthlyPayment')}>
                    <StatValue
                        amount={scenario.monthlyPayment}
                        currency={currency}
                        className="font-semibold block truncate min-w-0"
                    />
                </Row>

                {!isBase && scenario.finalMonthlyPayment !== scenario.monthlyPayment && (
                    <Row label={t('earlyPayoff.results.newMonthlyPayment')}>
                        <StatValue
                            amount={scenario.finalMonthlyPayment}
                            currency={currency}
                            className={greenClass}
                        />
                    </Row>
                )}

                <Row label={t('earlyPayoff.results.remainingTerm')}>
                    <span className={!isBase ? 'font-semibold text-green-600 dark:text-green-400' : ''}>
                        {termLabel}
                    </span>
                </Row>

                <Row label={t('earlyPayoff.results.totalInterest')}>
                    <StatValue
                        amount={scenario.totalInterest}
                        currency={currency}
                        className="text-red-600 dark:text-red-400 block truncate min-w-0"
                    />
                </Row>

                {!isBase && (
                    <>
                        <div className="border-t border-border pt-3 space-y-3">
                            <Row label={t('earlyPayoff.results.totalLumpSum')}>
                                <StatValue
                                    amount={scenario.totalLumpSum}
                                    currency={currency}
                                    className="block truncate min-w-0"
                                />
                            </Row>

                            {isIRA && scenario.totalIRA > 0 && (
                                <Row label={t('earlyPayoff.results.iraAmount')}>
                                    <StatValue
                                        amount={scenario.totalIRA}
                                        currency={currency}
                                        className="text-amber-600 dark:text-amber-400 block truncate min-w-0"
                                    />
                                </Row>
                            )}

                            <Row label={t('earlyPayoff.results.interestSaved')}>
                                <StatValue
                                    amount={scenario.interestSaved}
                                    currency={currency}
                                    className={greenClass}
                                />
                            </Row>

                            {isIRA ? (
                                <Row label={t('earlyPayoff.results.netSavings')}>
                                    <StatValue
                                        amount={scenario.netSavings}
                                        currency={currency}
                                        className={`${netSavingsClass} font-bold block truncate min-w-0`}
                                    />
                                </Row>
                            ) : null}

                            <Row label={t('earlyPayoff.results.timeSaved')}>
                                <span className="text-green-600 dark:text-green-400 font-semibold">
                                    {formatTerm(scenario.timeSavedMonths, t)}
                                </span>
                            </Row>
                        </div>
                    </>
                )}
            </CardContent>
        </Card>
    );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div className="flex items-center justify-between gap-4 text-sm">
            <span className="text-muted-foreground shrink-0">{label}</span>
            <span className="text-right">{children}</span>
        </div>
    );
}

const STAT_TOOLTIP_CLASS = 'bg-surface-elevated text-xs whitespace-nowrap border-border shadow-md';

function StatValue({
    amount,
    currency,
    className,
}: {
    amount: number;
    currency: string;
    className?: string;
}) {
    return (
        <TooltipProvider delayDuration={150}>
            <UITooltip>
                <TooltipTrigger asChild>
                    <span className={className}>
                        <ConvertedAmount amount={amount} currency={currency} inline />
                    </span>
                </TooltipTrigger>
                <TooltipContent className={STAT_TOOLTIP_CLASS} sideOffset={4}>
                    <ConvertedAmount amount={amount} currency={currency} inline />
                </TooltipContent>
            </UITooltip>
        </TooltipProvider>
    );
}

// ── Main Component ───────────────────────────────────────────────────────────

export function EarlyPayoffCalculator({ className }: { className?: string }) {
    const { t } = useTranslation('tools');
    const { baseCurrency } = useAuthContext();
    const { earlyPayoffConfig: cfg } = useCountryToolConfig();

    const {
        input,
        result,
        updateInput,
        addLumpSum,
        updateLumpSum,
        removeLumpSum,
        resetInputs,
        calculate,
    } = useEarlyPayoffCalculator(cfg);

    const { format: formatCurrency } = useFormatCurrency();

    const [showSchedule, setShowSchedule] = useState<'base' | 'reduceDuration' | 'reducePayment' | null>(null);

    const hasIRA = cfg.hasIRA;

    const handleCalculate = useCallback(() => { calculate(); }, [calculate]);

    // Build chart data from result (show up to the longest scenario)
    const chartData = result
        ? (() => {
            const maxYears = Math.max(
                result.base.yearlySchedule.length,
                result.reduceDuration.yearlySchedule.length,
                result.reducePayment.yearlySchedule.length,
            );
            return Array.from({ length: maxYears }, (_, i) => {
                const y = i + 1;
                const getBalance = (s: EarlyPayoffScenario) =>
                    s.yearlySchedule.find(r => r.year === y)?.endBalance ?? 0;
                return {
                    year: y,
                    base: getBalance(result.base),
                    reduceDuration: getBalance(result.reduceDuration),
                    reducePayment: getBalance(result.reducePayment),
                };
            });
        })()
        : [];

    return (
        <div className={className}>
            {/* ── Input card ── */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Calculator className="h-5 w-5" />
                        {t('earlyPayoff.title')}
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <form className="space-y-6" onSubmit={(e) => { e.preventDefault(); handleCalculate(); }}>

                    {/* Loan details */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        {/* Balance */}
                        <div className="space-y-1">
                            <Label htmlFor="ep-balance">{t('earlyPayoff.fields.loanBalance')}</Label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <DollarSign className="h-4 w-4 text-muted-foreground" />
                                </div>
                                <Input
                                    id="ep-balance"
                                    type="number"
                                    min={0}
                                    step={1000}
                                    className="pl-10"
                                    value={input.loanBalance || ''}
                                    onChange={e => updateInput('loanBalance', Number(e.target.value))}
                                />
                            </div>
                        </div>

                        {/* Annual rate */}
                        <div className="space-y-1">
                            <Label htmlFor="ep-rate">{t('earlyPayoff.fields.annualRate')}</Label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Percent className="h-4 w-4 text-muted-foreground" />
                                </div>
                                <Input
                                    id="ep-rate"
                                    type="number"
                                    min={0}
                                    step="any"
                                    className="pl-10"
                                    value={input.annualRate || ''}
                                    onChange={e => updateInput('annualRate', Number(e.target.value))}
                                />
                            </div>
                        </div>

                        {/* Remaining years */}
                        <div className="space-y-1">
                            <Label htmlFor="ep-years">{t('earlyPayoff.fields.remainingYears')}</Label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Calendar className="h-4 w-4 text-muted-foreground" />
                                </div>
                                <Input
                                    id="ep-years"
                                    type="number"
                                    min={0}
                                    step={1}
                                    className="pl-10"
                                    value={input.remainingYears || ''}
                                    onChange={e => updateInput('remainingYears', Number(e.target.value))}
                                />
                            </div>
                        </div>

                        {/* Extra months */}
                        <div className="space-y-1">
                            <Label htmlFor="ep-months">{t('earlyPayoff.fields.remainingMonthsExtra')}</Label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                    <Calendar className="h-4 w-4 text-muted-foreground" />
                                </div>
                                <Input
                                    id="ep-months"
                                    type="number"
                                    min={0}
                                    max={11}
                                    step={1}
                                    className="pl-10"
                                    value={input.remainingMonthsExtra || ''}
                                    onChange={e => updateInput('remainingMonthsExtra', Math.min(11, Math.max(0, Number(e.target.value))))}
                                />
                            </div>
                        </div>
                    </div>

                    {/* IRA notice — shown automatically when country config includes penalty rules */}
                    {hasIRA && (
                        <div className="rounded-md border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/40 p-4 space-y-1 text-sm">
                            <p className="font-semibold text-amber-900 dark:text-amber-200">{t('earlyPayoff.iraInfo.title')}</p>
                            <p className="text-amber-800 dark:text-amber-300">{t('earlyPayoff.iraInfo.body')}</p>
                            <p className="text-amber-700 dark:text-amber-400 italic">{t('earlyPayoff.iraInfo.waiver')}</p>
                        </div>
                    )}

                    {/* Lump-sum payments */}
                    <div className="space-y-3">
                        <div className="flex items-center justify-between">
                            <Label className="text-base font-semibold">{t('earlyPayoff.fields.lumpSumPayments')}</Label>
                            <Button variant="outline" size="sm" onClick={addLumpSum} className="gap-1">
                                <Plus className="h-3.5 w-3.5" />
                                {t('earlyPayoff.fields.addPayment')}
                            </Button>
                        </div>

                        {input.lumpSumPayments.length === 0 ? (
                            <p className="text-sm text-muted-foreground italic">{t('earlyPayoff.noPayments')}</p>
                        ) : (
                            <div className="space-y-2">
                                {input.lumpSumPayments.map((ls, idx) => (
                                    <div key={ls.id} className="flex items-end gap-3 p-3 rounded-lg border border-border bg-muted/30">
                                        <span className="text-xs font-medium text-muted-foreground w-4 shrink-0">#{idx + 1}</span>

                                        <div className="space-y-1 flex-1">
                                            <Label htmlFor={`ep-ls-month-${ls.id}`} className="text-xs">
                                                {t('earlyPayoff.fields.paymentMonth')}
                                            </Label>
                                            <div className="relative">
                                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                                    <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                                                </div>
                                                <Input
                                                    id={`ep-ls-month-${ls.id}`}
                                                    type="number"
                                                    min={1}
                                                    step={1}
                                                    className="pl-9 h-9 text-sm"
                                                    value={ls.month || ''}
                                                    onChange={e => updateLumpSum(ls.id, 'month', Number(e.target.value))}
                                                />
                                            </div>
                                        </div>

                                        <div className="space-y-1 flex-1">
                                            <Label htmlFor={`ep-ls-amount-${ls.id}`} className="text-xs">
                                                {t('earlyPayoff.fields.paymentAmount')}
                                            </Label>
                                            <div className="relative">
                                                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                                    <DollarSign className="h-3.5 w-3.5 text-muted-foreground" />
                                                </div>
                                                <Input
                                                    id={`ep-ls-amount-${ls.id}`}
                                                    type="number"
                                                    min={0}
                                                    step={1000}
                                                    className="pl-9 h-9 text-sm"
                                                    value={ls.amount || ''}
                                                    onChange={e => updateLumpSum(ls.id, 'amount', Number(e.target.value))}
                                                />
                                            </div>
                                        </div>

                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            className="text-destructive hover:text-destructive h-9 w-9 p-0 shrink-0"
                                            onClick={() => removeLumpSum(ls.id)}
                                            aria-label={t('earlyPayoff.fields.removePayment')}
                                        >
                                            <Trash2 className="h-4 w-4" />
                                        </Button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    {/* Extra monthly payment */}
                    <div className="space-y-1">
                        <Label htmlFor="ep-monthly-extra">{t('earlyPayoff.fields.monthlyExtraPayment')}</Label>
                        <div className="relative">
                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                <DollarSign className="h-4 w-4 text-muted-foreground" />
                            </div>
                            <Input
                                id="ep-monthly-extra"
                                type="number"
                                min={0}
                                step={100}
                                className="pl-10"
                                value={input.monthlyExtraPayment || ''}
                                onChange={e => updateInput('monthlyExtraPayment', Math.max(0, Number(e.target.value)))}
                            />
                        </div>
                        <p className="text-xs text-muted-foreground">
                            {t('earlyPayoff.fields.monthlyExtraPaymentDescription')}
                        </p>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-3 pt-4 border-t">
                        <Button type="submit" className="flex-1 md:flex-none">
                            <Calculator className="w-4 h-4 mr-2" />
                            {t('common.calculate')}
                        </Button>
                        <Button type="button" variant="outline" onClick={resetInputs} className="flex-1 md:flex-none">
                            <RefreshCw className="w-4 h-4 mr-2" />
                            {t('common.reset')}
                        </Button>
                    </div>
                    </form>
                </CardContent>
            </Card>

            {/* ── Results ── */}
            {result && (
                <div className="mt-8 space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">

                    {/* Scenario cards */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <ScenarioCard
                            title={t('earlyPayoff.scenarios.base')}
                            scenario={result.base}
                            isBase
                            currency={baseCurrency}
                            isIRA={hasIRA}
                            t={t}
                        />
                        <ScenarioCard
                            title={t('earlyPayoff.scenarios.reduceDuration')}
                            scenario={result.reduceDuration}
                            highlight
                            currency={baseCurrency}
                            isIRA={hasIRA}
                            t={t}
                        />
                        <ScenarioCard
                            title={t('earlyPayoff.scenarios.reducePayment')}
                            scenario={result.reducePayment}
                            currency={baseCurrency}
                            isIRA={hasIRA}
                            t={t}
                        />
                    </div>

                    {/* Comparison table */}
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <TrendingDown className="h-5 w-5" />
                                {t('earlyPayoff.scenarios.comparison')}
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead className="bg-muted text-muted-foreground">
                                        <tr>
                                            <th className="px-4 py-3 font-medium text-left rounded-tl-lg">&nbsp;</th>
                                            <th className="px-4 py-3 font-medium text-right">{t('earlyPayoff.scenarios.base')}</th>
                                            <th className="px-4 py-3 font-medium text-right text-primary">
                                                {t('earlyPayoff.scenarios.reduceDuration')}
                                            </th>
                                            <th className="px-4 py-3 font-medium text-right rounded-tr-lg">
                                                {t('earlyPayoff.scenarios.reducePayment')}
                                            </th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-border">
                                        <ComparisonRow
                                            label={t('earlyPayoff.results.monthlyPayment')}
                                            base={<ConvertedAmount amount={result.base.monthlyPayment} currency={baseCurrency} inline />}
                                            rd={<ConvertedAmount amount={result.reduceDuration.monthlyPayment} currency={baseCurrency} inline />}
                                            rp={<ConvertedAmount amount={result.reducePayment.monthlyPayment} currency={baseCurrency} inline />}
                                        />
                                        {(result.reducePayment.finalMonthlyPayment !== result.reducePayment.monthlyPayment) && (
                                            <ComparisonRow
                                                label={t('earlyPayoff.results.newMonthlyPayment')}
                                                base="—"
                                                rd="—"
                                                rp={
                                                    <span className="text-green-600 dark:text-green-400 font-semibold">
                                                        <ConvertedAmount
                                                            amount={result.reducePayment.finalMonthlyPayment}
                                                            currency={baseCurrency}
                                                            inline
                                                        />
                                                    </span>
                                                }
                                            />
                                        )}
                                        <ComparisonRow
                                            label={t('earlyPayoff.results.remainingTerm')}
                                            base={formatTerm(result.base.totalMonths, t)}
                                            rd={
                                                <span className="text-green-600 dark:text-green-400 font-semibold">
                                                    {formatTerm(result.reduceDuration.totalMonths, t)}
                                                </span>
                                            }
                                            rp={formatTerm(result.reducePayment.totalMonths, t)}
                                        />
                                        <ComparisonRow
                                            label={t('earlyPayoff.results.totalInterest')}
                                            base={<span className="text-red-600 dark:text-red-400"><ConvertedAmount amount={result.base.totalInterest} currency={baseCurrency} inline /></span>}
                                            rd={<span className="text-red-600 dark:text-red-400"><ConvertedAmount amount={result.reduceDuration.totalInterest} currency={baseCurrency} inline /></span>}
                                            rp={<span className="text-red-600 dark:text-red-400"><ConvertedAmount amount={result.reducePayment.totalInterest} currency={baseCurrency} inline /></span>}
                                        />
                                        <ComparisonRow
                                            label={t('earlyPayoff.results.interestSaved')}
                                            base="—"
                                            rd={
                                                <span className="text-green-600 dark:text-green-400 font-semibold">
                                                    <ConvertedAmount
                                                        amount={result.reduceDuration.interestSaved}
                                                        currency={baseCurrency}
                                                        inline
                                                    />
                                                </span>
                                            }
                                            rp={
                                                <span className="text-green-600 dark:text-green-400 font-semibold">
                                                    <ConvertedAmount
                                                        amount={result.reducePayment.interestSaved}
                                                        currency={baseCurrency}
                                                        inline
                                                    />
                                                </span>
                                            }
                                        />
                                        <ComparisonRow
                                            label={t('earlyPayoff.results.timeSaved')}
                                            base="—"
                                            rd={
                                                <span className="text-green-600 dark:text-green-400 font-semibold flex items-center justify-end gap-1">
                                                    <Clock className="h-3.5 w-3.5" />
                                                    {formatTerm(result.reduceDuration.timeSavedMonths, t)}
                                                </span>
                                            }
                                            rp={
                                                <span className="text-green-600 dark:text-green-400">
                                                    {result.reducePayment.timeSavedMonths > 0
                                                        ? formatTerm(result.reducePayment.timeSavedMonths, t)
                                                        : '—'}
                                                </span>
                                            }
                                        />
                                        {hasIRA && (
                                            <ComparisonRow
                                                label={t('earlyPayoff.results.iraAmount')}
                                                base="—"
                                                rd={
                                                    <span className="text-amber-600 dark:text-amber-400">
                                                        <ConvertedAmount
                                                            amount={result.reduceDuration.totalIRA}
                                                            currency={baseCurrency}
                                                            inline
                                                        />
                                                    </span>
                                                }
                                                rp={
                                                    <span className="text-amber-600 dark:text-amber-400">
                                                        <ConvertedAmount
                                                            amount={result.reducePayment.totalIRA}
                                                            currency={baseCurrency}
                                                            inline
                                                        />
                                                    </span>
                                                }
                                            />
                                        )}
                                        <ComparisonRow
                                            label={hasIRA ? t('earlyPayoff.results.netSavings') : t('earlyPayoff.results.interestSaved')}
                                            base="—"
                                            rd={
                                                <span className={`font-bold ${result.reduceDuration.netSavings >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                                                    <ConvertedAmount
                                                        amount={result.reduceDuration.netSavings}
                                                        currency={baseCurrency}
                                                        inline
                                                    />
                                                </span>
                                            }
                                            rp={
                                                <span className={`font-bold ${result.reducePayment.netSavings >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                                                    <ConvertedAmount
                                                        amount={result.reducePayment.netSavings}
                                                        currency={baseCurrency}
                                                        inline
                                                    />
                                                </span>
                                            }
                                        />
                                    </tbody>
                                </table>
                            </div>
                        </CardContent>
                    </Card>

                    {/* Balance evolution chart */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t('loanCalculator.results.chartTitle')}</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="h-[350px] w-full mt-2">
                                <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                                    <AreaChart data={chartData} margin={{ top: 10, right: 30, left: 0, bottom: 0 }}>
                                        <defs>
                                            <linearGradient id="epBase" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor="#94a3b8" stopOpacity={0.3} />
                                                <stop offset="95%" stopColor="#94a3b8" stopOpacity={0} />
                                            </linearGradient>
                                            <linearGradient id="epRD" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                                                <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                                            </linearGradient>
                                            <linearGradient id="epRP" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="5%" stopColor="#10b981" stopOpacity={0.3} />
                                                <stop offset="95%" stopColor="#10b981" stopOpacity={0} />
                                            </linearGradient>
                                        </defs>
                                        <XAxis dataKey="year" stroke="#888888" fontSize={12} tickFormatter={v => `Y${v}`} />
                                        <YAxis
                                            stroke="#888888"
                                            fontSize={12}
                                            tickFormatter={v => new Intl.NumberFormat('en', { notation: 'compact', compactDisplay: 'short' }).format(v)}
                                        />
                                        <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                                        <Tooltip
                                            formatter={(value: number | undefined, name: string | undefined) => [
                                                value !== undefined ? formatCurrency(value, baseCurrency) : '—',
                                                name === 'base'
                                                    ? t('earlyPayoff.scenarios.base')
                                                    : name === 'reduceDuration'
                                                    ? t('earlyPayoff.scenarios.reduceDuration')
                                                    : t('earlyPayoff.scenarios.reducePayment'),
                                            ]}
                                            labelFormatter={(v) => `Year ${v}`}
                                        />
                                        <Legend
                                            formatter={(value: string) =>
                                                value === 'base'
                                                    ? t('earlyPayoff.scenarios.base')
                                                    : value === 'reduceDuration'
                                                    ? t('earlyPayoff.scenarios.reduceDuration')
                                                    : t('earlyPayoff.scenarios.reducePayment')
                                            }
                                        />
                                        <Area type="monotone" dataKey="base" stroke="#94a3b8" strokeWidth={2} fillOpacity={1} fill="url(#epBase)" />
                                        <Area type="monotone" dataKey="reduceDuration" stroke="#3b82f6" strokeWidth={2} fillOpacity={1} fill="url(#epRD)" />
                                        <Area type="monotone" dataKey="reducePayment" stroke="#10b981" strokeWidth={2} fillOpacity={1} fill="url(#epRP)" />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </div>
                        </CardContent>
                    </Card>

                    {/* Amortisation schedules */}
                    <Card>
                        <CardHeader>
                            <CardTitle>{t('earlyPayoff.results.schedule')}</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {/* Toggle buttons */}
                            <div className="flex flex-wrap gap-2">
                                {(['base', 'reduceDuration', 'reducePayment'] as const).map(key => (
                                    <Button
                                        key={key}
                                        variant={showSchedule === key ? 'default' : 'outline'}
                                        size="sm"
                                        onClick={() => setShowSchedule(prev => prev === key ? null : key)}
                                        className="gap-1"
                                    >
                                        {showSchedule === key ? <ChevronUp className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                                        {t(`earlyPayoff.scenarios.${key}`)}
                                    </Button>
                                ))}
                            </div>

                            {showSchedule && (
                                <div className="overflow-x-auto animate-in fade-in duration-200">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-muted text-muted-foreground">
                                            <tr>
                                                <th className="px-3 py-3 font-medium rounded-tl-lg">{t('earlyPayoff.results.table.year')}</th>
                                                <th className="px-3 py-3 font-medium text-right">{t('earlyPayoff.results.table.payment')}</th>
                                                <th className="px-3 py-3 font-medium text-right">{t('earlyPayoff.results.table.principal')}</th>
                                                <th className="px-3 py-3 font-medium text-right">{t('earlyPayoff.results.table.interest')}</th>
                                                {showSchedule !== 'base' && (
                                                    <th className="px-3 py-3 font-medium text-right">{t('earlyPayoff.results.table.lumpSum')}</th>
                                                )}
                                                {showSchedule !== 'base' && hasIRA && (
                                                    <th className="px-3 py-3 font-medium text-right">{t('earlyPayoff.results.table.ira')}</th>
                                                )}
                                                <th className="px-3 py-3 font-medium text-right rounded-tr-lg">{t('earlyPayoff.results.table.balance')}</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-border">
                                            {result[showSchedule].yearlySchedule.map(row => (
                                                <tr key={row.year} className="hover:bg-muted/50 transition-colors">
                                                    <td className="px-3 py-3 font-medium">{row.year}</td>
                                                    <td className="px-3 py-3 text-right">
                                                        <ConvertedAmount amount={row.totalPayment} currency={baseCurrency} inline />
                                                    </td>
                                                    <td className="px-3 py-3 text-right text-muted-foreground">
                                                        <ConvertedAmount amount={row.principalPaid} currency={baseCurrency} inline />
                                                    </td>
                                                    <td className="px-3 py-3 text-right text-red-600 dark:text-red-400">
                                                        <ConvertedAmount amount={row.interestPaid} currency={baseCurrency} inline />
                                                    </td>
                                                    {showSchedule !== 'base' && (
                                                        <td className="px-3 py-3 text-right text-blue-600 dark:text-blue-400">
                                                            {row.lumpSum > 0 ? (
                                                                <ConvertedAmount amount={row.lumpSum} currency={baseCurrency} inline />
                                                            ) : (
                                                                '—'
                                                            )}
                                                        </td>
                                                    )}
                                                    {showSchedule !== 'base' && hasIRA && (
                                                        <td className="px-3 py-3 text-right text-amber-600 dark:text-amber-400">
                                                            {row.ira > 0 ? (
                                                                <ConvertedAmount amount={row.ira} currency={baseCurrency} inline />
                                                            ) : (
                                                                '—'
                                                            )}
                                                        </td>
                                                    )}
                                                    <td className="px-3 py-3 text-right font-medium">
                                                        <ConvertedAmount amount={row.endBalance} currency={baseCurrency} inline />
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </div>
            )}

            {/* Empty state before first calculation */}
            {!result && (
                <div className="mt-6 text-center text-sm text-muted-foreground py-4">
                    {t('earlyPayoff.noPayments')}
                </div>
            )}
        </div>
    );
}

// ── Comparison row helper ────────────────────────────────────────────────────

function ComparisonRow({
    label,
    base,
    rd,
    rp,
}: {
    label: string;
    base: React.ReactNode;
    rd: React.ReactNode;
    rp: React.ReactNode;
}) {
    return (
        <tr className="hover:bg-muted/50 transition-colors">
            <td className="px-4 py-3 text-muted-foreground">{label}</td>
            <td className="px-4 py-3 text-right">{base}</td>
            <td className="px-4 py-3 text-right">{rd}</td>
            <td className="px-4 py-3 text-right">{rp}</td>
        </tr>
    );
}
