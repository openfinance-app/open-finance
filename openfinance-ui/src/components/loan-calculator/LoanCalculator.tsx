import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Calculator, RefreshCw, DollarSign, Percent, Calendar } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/Card';
import { Input } from '../ui/Input';
import { Label } from '../ui/Label';
import { Button } from '../ui/Button';
import { useLoanCalculator } from '../../hooks/useLoanCalculator';
import { useFormatCurrency } from '@/hooks/useFormatCurrency';
import { useAuthContext } from '@/context/AuthContext';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

interface LoanCalculatorProps {
  className?: string;
}

export function LoanCalculator({ className }: LoanCalculatorProps) {
  const { t, i18n } = useTranslation('tools');
  const { baseCurrency } = useAuthContext();
  const { input, result, updateInput, resetInputs, calculate } = useLoanCalculator();
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
            {t('loanCalculator.title', 'Loan Calculator')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <form className="mt-4 space-y-6" onSubmit={(e) => { e.preventDefault(); handleCalculate(); }}>
            {/* Input form */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Principal */}
              <div className="space-y-1">
                <Label htmlFor="lc-principal">{t('loanCalculator.fields.principal', 'Loan Amount')}</Label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <DollarSign className="h-4 w-4 text-muted-foreground" />
                  </div>
                  <Input
                    id="lc-principal"
                    type="number"
                    min={0}
                    step={100}
                    className="pl-10"
                    value={input.principal || ''}
                    onChange={(e) => updateInput('principal', Number(e.target.value))}
                  />
                </div>
              </div>

              {/* Annual Rate */}
              <div className="space-y-1">
                <Label htmlFor="lc-rate">{t('loanCalculator.fields.rate', 'Annual Interest Rate (%)')}</Label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Percent className="h-4 w-4 text-muted-foreground" />
                  </div>
                  <Input
                    id="lc-rate"
                    type="number"
                    min={0}
                    step={0.1}
                    className="pl-10"
                    value={input.annualRate || ''}
                    onChange={(e) => updateInput('annualRate', Number(e.target.value))}
                  />
                </div>
              </div>

              {/* Years */}
              <div className="space-y-1">
                <Label htmlFor="lc-years">{t('loanCalculator.fields.years', 'Loan Term (Years)')}</Label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Calendar className="h-4 w-4 text-muted-foreground" />
                  </div>
                  <Input
                    id="lc-years"
                    type="number"
                    min={1}
                    step={1}
                    className="pl-10"
                    value={input.years || ''}
                    onChange={(e) => updateInput('years', Number(e.target.value))}
                  />
                </div>
              </div>
            </div>

            {/* Actions */}
            <div className="flex items-center gap-4 pt-4 border-t">
              <Button type="submit" className="flex-1 md:flex-none">
                <Calculator className="w-4 h-4 mr-2" />
                {t('common.calculate', 'Calculate')}
              </Button>
              <Button type="button" variant="outline" onClick={resetInputs} className="flex-1 md:flex-none">
                <RefreshCw className="w-4 h-4 mr-2" />
                {t('common.reset', 'Reset')}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Results */}
      {result && (
        <div className="mt-8 space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card className="bg-primary/5 border-primary/20">
              <CardContent className="pt-6">
                <div className="text-sm font-medium text-muted-foreground mb-1">
                  {t('loanCalculator.results.monthlyPayment', 'Monthly Payment')}
                </div>
                <div 
                  className="text-3xl font-bold text-foreground truncate"
                  title={formatCurrency(result.monthlyPayment, baseCurrency)}
                >
                  {formatCurrency(result.monthlyPayment, baseCurrency)}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="pt-6">
                <div className="text-sm font-medium text-muted-foreground mb-1">
                  {t('loanCalculator.results.totalInterest', 'Total Interest')}
                </div>
                <div 
                  className="text-2xl font-semibold text-foreground truncate"
                  title={formatCurrency(result.totalInterest, baseCurrency)}
                >
                  {formatCurrency(result.totalInterest, baseCurrency)}
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="pt-6">
                <div className="text-sm font-medium text-muted-foreground mb-1">
                  {t('loanCalculator.results.totalPayment', 'Total Payment')}
                </div>
                <div 
                  className="text-2xl font-semibold text-foreground truncate"
                  title={formatCurrency(result.totalPayment, baseCurrency)}
                >
                  {formatCurrency(result.totalPayment, baseCurrency)}
                </div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t('loanCalculator.results.chartTitle', 'Balance Over Time')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="h-[400px] w-full mt-4">
                <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                  <AreaChart
                    data={result.amortizationSchedule.filter((_, i) => i % 12 === 0 || i === result.amortizationSchedule.length - 1)}
                    margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
                  >
                    <defs>
                      <linearGradient id="colorBalance" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="paymentNumber"
                      tickFormatter={(val) => `Year ${Math.round(val / 12)}`}
                      stroke="#888888"
                      fontSize={12}
                    />
                    <YAxis
                      tickFormatter={(val) =>
                        new Intl.NumberFormat(i18n.language, {
                          notation: 'compact',
                          compactDisplay: 'short',
                        }).format(val)
                      }
                      stroke="#888888"
                      fontSize={12}
                    />
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                    <Tooltip
                      formatter={(value) => [formatCurrency(value as number, baseCurrency), 'Balance']}
                      labelFormatter={(label) => `Payment ${label} (Year ${(Number(label)/12).toFixed(1)})`}
                    />
                    <Area
                      type="monotone"
                      dataKey="remainingBalance"
                      stroke="#3b82f6"
                      strokeWidth={2}
                      fillOpacity={1}
                      fill="url(#colorBalance)"
                      name="Remaining Balance"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{t('loanCalculator.results.schedule', 'Amortization Schedule (Yearly)')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                  <thead className="bg-muted text-muted-foreground">
                    <tr>
                      <th className="px-4 py-3 font-medium rounded-tl-lg">{t('loanCalculator.results.table.year', 'Year')}</th>
                      <th className="px-4 py-3 font-medium text-right">{t('loanCalculator.results.table.payment', 'Total Payment')}</th>
                      <th className="px-4 py-3 font-medium text-right">{t('loanCalculator.results.table.principal', 'Principal Paid')}</th>
                      <th className="px-4 py-3 font-medium text-right">{t('loanCalculator.results.table.interest', 'Interest Paid')}</th>
                      <th className="px-4 py-3 font-medium text-right rounded-tr-lg">{t('loanCalculator.results.table.balance', 'Remaining Balance')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {(() => {
                      const yearlyStats: { year: number; payment: number; principal: number; interest: number; balance: number }[] = [];
                      let yearPrincipal = 0;
                      let yearInterest = 0;
                      let yearPayment = 0;
                      
                      result.amortizationSchedule.forEach((entry) => {
                        yearPrincipal += entry.principalPortion;
                        yearInterest += entry.interestPortion;
                        yearPayment += entry.paymentAmount;
                        
                        if (entry.paymentNumber % 12 === 0 || entry.paymentNumber === result.amortizationSchedule.length) {
                          yearlyStats.push({
                            year: Math.ceil(entry.paymentNumber / 12),
                            payment: yearPayment,
                            principal: yearPrincipal,
                            interest: yearInterest,
                            balance: entry.remainingBalance,
                          });
                          yearPrincipal = 0;
                          yearInterest = 0;
                          yearPayment = 0;
                        }
                      });

                      return yearlyStats.map((stat) => (
                        <tr key={stat.year} className="hover:bg-muted/50 transition-colors">
                          <td className="px-4 py-3 font-medium text-foreground">{stat.year}</td>
                          <td className="px-4 py-3 text-right">{formatCurrency(stat.payment, baseCurrency)}</td>
                          <td className="px-4 py-3 text-right text-muted-foreground">{formatCurrency(stat.principal, baseCurrency)}</td>
                          <td className="px-4 py-3 text-right text-red-600 dark:text-red-400">{formatCurrency(stat.interest, baseCurrency)}</td>
                          <td className="px-4 py-3 text-right font-medium text-foreground">{formatCurrency(stat.balance, baseCurrency)}</td>
                        </tr>
                      ));
                    })()}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
