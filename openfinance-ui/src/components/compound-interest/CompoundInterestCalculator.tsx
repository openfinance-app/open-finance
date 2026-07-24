import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
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
import { Calculator, RefreshCw, AlertCircle, TrendingUp, PiggyBank, Percent, DollarSign } from 'lucide-react';
import { subtract, roundToDecimals } from '@/utils/money';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Label } from '../ui/Label';
import { Button } from '../ui/Button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../ui/Select';
import { Switch } from '../ui/Switch';
import { useCompoundInterest } from '../../hooks/useCompoundInterest';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useAuthContext } from '@/context/AuthContext';
import type { CompoundingFrequency, CompoundInterestResult, CompoundInterestYearlyBreakdown } from '../../types/calculator';

// ---------------------------------------------------------------------------
// Compounding frequency options
// ---------------------------------------------------------------------------
const FREQUENCY_OPTIONS: { value: CompoundingFrequency; labelKey: string }[] = [
  { value: 1,   labelKey: 'annually' },
  { value: 2,   labelKey: 'semiAnnually' },
  { value: 4,   labelKey: 'quarterly' },
  { value: 12,  labelKey: 'monthly' },
  { value: 52,  labelKey: 'weekly' },
  { value: 365, labelKey: 'daily' },
];

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------
interface CompoundInterestCalculatorProps {
  className?: string;
}

export function CompoundInterestCalculator({ className }: CompoundInterestCalculatorProps) {
  const { t } = useTranslation('tools');
  const { baseCurrency } = useAuthContext();
  const { input, result, isLoading, error, updateInput, resetInputs, calculate } = useCompoundInterest();
  const { format: formatCurrency } = useFormatCurrency();

  const handleCalculate = useCallback(() => {
    calculate();
  }, [calculate]);

  return (
    <div className={className}>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calculator className="h-5 w-5" />
            {t('compoundInterest.calculator.title')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mt-4 space-y-6">
            {/* ---- Error ---- */}
            {error && (
              <div className="flex items-center gap-2 p-3 text-sm text-red-600 bg-red-50 rounded-md">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* ---- Input form ---- */}
            <form onSubmit={(e) => { e.preventDefault(); handleCalculate(); }} className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Principal */}
              <div className="space-y-1">
                <Label htmlFor="ci-principal">
                  {t('compoundInterest.calculator.fields.principal.label')}
                </Label>
                <Input
                  id="ci-principal"
                  type="number"
                  min={0}
                  step={100}
                  value={input.principal}
                  onChange={(e) => updateInput('principal', Number(e.target.value))}
                />
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.principal.description')}
                </p>
              </div>

              {/* Annual rate */}
              <div className="space-y-1">
                <Label htmlFor="ci-rate">
                  {t('compoundInterest.calculator.fields.annualRate.label')}
                </Label>
                <Input
                  id="ci-rate"
                  type="number"
                  min={0.01}
                  max={100}
                  step="any"
                  value={input.annualRate}
                  onChange={(e) => updateInput('annualRate', Number(e.target.value))}
                />
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.annualRate.description')}
                </p>
              </div>

              {/* Compounding frequency */}
              <div className="space-y-1">
                <Label htmlFor="ci-frequency">
                  {t('compoundInterest.calculator.fields.frequency.label')}
                </Label>
                <Select
                  value={String(input.compoundingFrequency)}
                  onValueChange={(v) => updateInput('compoundingFrequency', Number(v) as CompoundingFrequency)}
                >
                  <SelectTrigger id="ci-frequency">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {FREQUENCY_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={String(opt.value)}>
                        {t(`compoundInterest.calculator.fields.frequency.options.${opt.labelKey}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.frequency.description')}
                </p>
              </div>

              {/* Duration */}
              <div className="space-y-1">
                <Label htmlFor="ci-years">
                  {t('compoundInterest.calculator.fields.years.label')}
                </Label>
                <Input
                  id="ci-years"
                  type="number"
                  min={1}
                  max={100}
                  step={1}
                  value={input.years}
                  onChange={(e) => updateInput('years', Number(e.target.value))}
                />
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.years.description')}
                </p>
              </div>

              {/* Regular contribution */}
              <div className="space-y-1">
                <Label htmlFor="ci-contribution">
                  {t('compoundInterest.calculator.fields.contribution.label')}
                </Label>
                <Input
                  id="ci-contribution"
                  type="number"
                  min={0}
                  step={10}
                  value={input.regularContribution}
                  onChange={(e) => updateInput('regularContribution', Number(e.target.value))}
                />
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.contribution.description')}
                </p>
              </div>

              {/* Contribution timing */}
              <div className="flex flex-col justify-center space-y-3 pt-4">
                <div className="flex items-center gap-3">
                  <Switch
                    id="ci-timing"
                    checked={input.contributionAtBeginning}
                    onCheckedChange={(v) => updateInput('contributionAtBeginning', v)}
                  />
                  <Label htmlFor="ci-timing" className="cursor-pointer">
                    {t('compoundInterest.calculator.fields.contributionTiming')}
                  </Label>
                </div>
                <p className="text-xs text-muted-foreground">
                  {t('compoundInterest.calculator.fields.contributionTimingDescription')}
                </p>
              </div>
              </div>

              {/* ---- Action buttons ---- */}
              <div className="flex items-center gap-3">
                <Button type="submit" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                    {t('compoundInterest.calculator.calculating')}
                  </>
                ) : (
                  t('compoundInterest.calculator.calculate')
                )}
                </Button>
                <Button type="button" variant="outline" onClick={resetInputs} disabled={isLoading}>
                  <RefreshCw className="mr-2 h-4 w-4" />
                  {t('compoundInterest.calculator.reset')}
                </Button>
              </div>
            </form>

            {/* ---- Results ---- */}
            {result && (
              <ResultsSection
                result={result}
                currency={baseCurrency}
                formatCurrency={(val) => formatCurrency(val, baseCurrency)}
                t={t}
              />
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Results section
// ---------------------------------------------------------------------------
interface ResultsSectionProps {
  result: CompoundInterestResult;
  currency: string;
  formatCurrency: (val: number) => string;
  t: (key: string) => string;
}

function ResultsSection({ result, formatCurrency, t }: ResultsSectionProps) {
  return (
    <div className="space-y-6">
      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <SummaryCard
          icon={<TrendingUp className="h-4 w-4 text-green-500" />}
          label={t('compoundInterest.results.finalBalance')}
          value={formatCurrency(result.finalBalance)}
          highlight
        />
        <SummaryCard
          icon={<DollarSign className="h-4 w-4 text-blue-500" />}
          label={t('compoundInterest.results.totalInterest')}
          value={formatCurrency(result.totalInterest)}
        />
        <SummaryCard
          icon={<PiggyBank className="h-4 w-4 text-yellow-500" />}
          label={t('compoundInterest.results.totalInvested')}
          value={formatCurrency(result.totalInvested)}
        />
        <SummaryCard
          icon={<Percent className="h-4 w-4 text-purple-500" />}
          label={t('compoundInterest.results.effectiveAnnualRate')}
          value={`${result.effectiveAnnualRate.toFixed(2)}%`}
        />
      </div>

      {/* Chart */}
      <GrowthChart breakdown={result.yearlyBreakdown} formatCurrency={formatCurrency} t={t} />

      {/* Breakdown table */}
      <BreakdownTable breakdown={result.yearlyBreakdown} formatCurrency={formatCurrency} t={t} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Summary card
// ---------------------------------------------------------------------------
function SummaryCard({
  icon,
  label,
  value,
  highlight = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <Card className={highlight ? 'border-green-200 bg-green-50' : undefined}>
      <CardContent className="pt-4">
        <div className="flex items-center gap-2 mb-1">
          {icon}
          <p className="text-xs text-text-muted font-medium">{label}</p>
        </div>
        <p 
          className={`text-lg font-bold truncate ${highlight ? 'text-green-700' : 'text-text-primary'}`}
          title={value}
        >
          {value}
        </p>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Growth chart (stacked area: principal / contributions / interest)
// ---------------------------------------------------------------------------
function GrowthChart({
  breakdown,
  formatCurrency,
  t,
}: {
  breakdown: CompoundInterestYearlyBreakdown[];
  formatCurrency: (val: number) => string;
  t: (key: string) => string;
}) {
  // Build chart data suitable for a stacked area
  const chartData = breakdown.map((row) => {
    const initPrincipal = breakdown[0].startingBalance;
    const totalContribs = subtract(row.cumulativePrincipal, initPrincipal);
    return {
      year: `Y${row.year}`,
      [t('compoundInterest.chart.principal')]: roundToDecimals(initPrincipal),
      [t('compoundInterest.chart.contributions')]: roundToDecimals(totalContribs),
      [t('compoundInterest.chart.interest')]: roundToDecimals(row.cumulativeInterest),
    };
  });

  const principalKey = t('compoundInterest.chart.principal');
  const contributionsKey = t('compoundInterest.chart.contributions');
  const interestKey = t('compoundInterest.chart.interest');

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('compoundInterest.results.growthChart')}</CardTitle>
      </CardHeader>
      <CardContent>
        <ResponsiveContainer width="100%" height={300} minWidth={0}>
          <AreaChart data={chartData} margin={{ top: 4, right: 16, left: 0, bottom: 0 }}>
            <defs>
              <linearGradient id="colorPrincipal" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#3b82f6" stopOpacity={0.2} />
              </linearGradient>
              <linearGradient id="colorContributions" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f59e0b" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#f59e0b" stopOpacity={0.2} />
              </linearGradient>
              <linearGradient id="colorInterest" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#22c55e" stopOpacity={0.8} />
                <stop offset="95%" stopColor="#22c55e" stopOpacity={0.2} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="year" tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={(v: number) => formatCurrency(v)} tick={{ fontSize: 11 }} width={90} />
            <Tooltip formatter={(value: number | undefined) => (value != null ? formatCurrency(value) : '')} />
            <Legend />
            <Area
              type="monotone"
              dataKey={principalKey}
              stackId="1"
              stroke="#3b82f6"
              fill="url(#colorPrincipal)"
            />
            <Area
              type="monotone"
              dataKey={contributionsKey}
              stackId="1"
              stroke="#f59e0b"
              fill="url(#colorContributions)"
            />
            <Area
              type="monotone"
              dataKey={interestKey}
              stackId="1"
              stroke="#22c55e"
              fill="url(#colorInterest)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </CardContent>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// Year-by-year breakdown table
// ---------------------------------------------------------------------------
function BreakdownTable({
  breakdown,
  formatCurrency,
  t,
}: {
  breakdown: CompoundInterestYearlyBreakdown[];
  formatCurrency: (val: number) => string;
  t: (key: string) => string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('compoundInterest.results.yearlyBreakdown')}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-text-muted text-left">
                <th className="pb-2 pr-4 font-medium">{t('compoundInterest.results.table.year')}</th>
                <th className="pb-2 pr-4 font-medium">{t('compoundInterest.results.table.startBalance')}</th>
                <th className="pb-2 pr-4 font-medium">{t('compoundInterest.results.table.contributions')}</th>
                <th className="pb-2 pr-4 font-medium">{t('compoundInterest.results.table.interest')}</th>
                <th className="pb-2 pr-4 font-medium">{t('compoundInterest.results.table.endBalance')}</th>
                <th className="pb-2 font-medium">{t('compoundInterest.results.table.totalInterest')}</th>
              </tr>
            </thead>
            <tbody>
              {breakdown.map((row) => (
                <tr key={row.year} className="border-b border-border/50 hover:bg-surface/50">
                  <td className="py-2 pr-4 font-medium">{row.year}</td>
                  <td className="py-2 pr-4">{formatCurrency(row.startingBalance)}</td>
                  <td className="py-2 pr-4">{formatCurrency(row.contributions)}</td>
                  <td className="py-2 pr-4 text-green-600">{formatCurrency(row.interestEarned)}</td>
                  <td className="py-2 pr-4 font-medium">{formatCurrency(row.endingBalance)}</td>
                  <td className="py-2 text-green-600">{formatCurrency(row.cumulativeInterest)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}
