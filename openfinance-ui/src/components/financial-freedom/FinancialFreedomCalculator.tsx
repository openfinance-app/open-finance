import { useCallback } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { useFinancialFreedom } from '../../hooks/useFinancialFreedom';
import { useUserFinancialData } from '../../hooks/useUserFinancialData';
import { useAuthContext } from '@/context/AuthContext';
import { ConvertedAmount } from '@/components/ui/ConvertedAmount';
import { Card, CardContent, CardHeader, CardTitle } from '../ui/Card';

import { Input } from '../ui/Input';
import { Label } from '../ui/Label';
import { Button } from '../ui/Button';
import { Slider } from '../ui/Slider';
import { Switch } from '../ui/Switch';
import { Progress } from '../ui/Progress';
import { ProgressChart } from './ProgressChart';
import { TimelineProjection } from './TimelineProjection';
import { SensitivityAnalysis } from './SensitivityAnalysis';
import {
  Calculator,
  AlertCircle,
  CheckCircle,
  RefreshCw,
  Database,
} from 'lucide-react';

interface FinancialFreedomCalculatorProps {
  className?: string;
}

/**
 * Financial Freedom Calculator main component
 */
export function FinancialFreedomCalculator({ className }: FinancialFreedomCalculatorProps) {
  const { baseCurrency } = useAuthContext();
  const { t } = useTranslation('tools');
  const {
    input,
    result,
    longevityResult,
    isLoading,
    error,
    updateInput,
    resetInputs,
    calculateLocal,
  } = useFinancialFreedom();

  // Fetch user's actual financial data
  const {
    data: userData,
    isLoading: isLoadingUserData,
    error: userDataError,
    refetch: _refetchUserData,
  } = useUserFinancialData();

  // Handler to populate form with user's actual data
  const handleUseActualData = useCallback(() => {
    if (userData) {
      updateInput('currentSavings', userData.totalSavings);
      updateInput('monthlyExpenses', userData.averageMonthlyExpenses);
    }
  }, [userData, updateInput]);

  const handleCalculate = useCallback(() => {
    calculateLocal();
  }, [calculateLocal]);

  return (
    <div className={className}>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Calculator className="h-5 w-5" />
            {t('financialFreedom.calculator.title')}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="mt-6 space-y-6 pl-1 md:pl-0">
            <CalculatorInputForm
              input={input}
              onChange={updateInput}
              onCalculate={handleCalculate}
              isLoading={isLoading}
              error={error}
              userData={userData}
              isLoadingUserData={isLoadingUserData}
              userDataError={userDataError}
              onUseActualData={handleUseActualData}
              currency={userData?.currency || baseCurrency}
            />

            {(result || longevityResult) && (
              <FreedomResults
                result={result}
                longevityResult={longevityResult}
                currency={userData?.currency || baseCurrency}
              />
            )}
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button variant="outline" onClick={resetInputs}>
              <RefreshCw className="mr-2 h-4 w-4" />
              {t('financialFreedom.calculator.reset')}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * Calculator input form
 */
interface CalculatorInputFormProps {
  input: {
    currentSavings: number;
    monthlyExpenses: number;
    expectedAnnualReturn: number;
    monthlyContribution?: number;
    withdrawalRate?: number;
    inflationRate?: number;
    adjustForInflation?: boolean;
  };
  onChange: (key: any, value: any) => void;
  onCalculate: () => void;
  isLoading: boolean;
  error: string | null;
  currency: string;
  userData?: {
    totalSavings: number;
    averageMonthlyExpenses: number;
    currency: string;
  } | null;
  isLoadingUserData?: boolean;
  userDataError?: string | null;
  onUseActualData?: () => void;
}

function CalculatorInputForm({
  input,
  onChange,
  onCalculate,
  isLoading,
  error,
  currency,
  userData,
  isLoadingUserData,
  userDataError,
  onUseActualData,
}: CalculatorInputFormProps) {
  const { t } = useTranslation('tools');
  return (
    <div className="space-y-6">
      {error && (
        <div className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-md">
          <AlertCircle className="h-4 w-4" />
          {error}
        </div>
      )}

      {/* Use Actual Data Button */}
      {userData && onUseActualData && (
        <Card className="bg-primary/5 border-primary/20">
          <CardContent className="pt-6">
            <div className="flex items-center justify-between gap-4">
              <div className="flex-1">
                <p className="text-sm font-medium text-primary mb-1">
                  {t('financialFreedom.calculator.useActualData.title')}
                </p>
                <p className="text-xs text-muted-foreground">
                  <Trans
                    t={t}
                    i18nKey="financialFreedom.calculator.useActualData.description"
                    components={{
                      savings: (
                        <ConvertedAmount
                          amount={userData.totalSavings}
                          currency={userData.currency || currency}
                          inline
                        />
                      ),
                      expenses: (
                        <ConvertedAmount
                          amount={userData.averageMonthlyExpenses}
                          currency={userData.currency || currency}
                          inline
                        />
                      ),
                    }}
                  />
                </p>
              </div>
              <Button
                variant="default"
                size="sm"
                onClick={onUseActualData}
                disabled={isLoadingUserData}
                className="shrink-0 shadow-sm"
              >
                <Database className="mr-2 h-4 w-4" />
                {t('financialFreedom.calculator.useActualData.button')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {isLoadingUserData && (
        <div className="flex items-center gap-2 p-3 text-sm text-primary bg-primary/10 rounded-md">
          <RefreshCw className="h-4 w-4 animate-spin" />
          {t('financialFreedom.calculator.loadingData')}
        </div>
      )}

      {userDataError && (
        <div className="flex items-center gap-2 p-3 text-sm text-destructive bg-destructive/10 rounded-md">
          <AlertCircle className="h-4 w-4" />
          {t('financialFreedom.calculator.dataLoadError')}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Current Savings */}
        <div className="space-y-2">
          <Label htmlFor="currentSavings">{t('financialFreedom.calculator.fields.currentSavings.label')}</Label>
          <Input
            id="currentSavings"
            type="number"
            value={input.currentSavings}
            onChange={(e) => onChange('currentSavings', parseFloat(e.target.value) || 0)}
            onFocus={(e) => e.target.select()}
            placeholder="50000"
          />
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.currentSavings.description')}
          </p>
        </div>

        {/* Monthly Expenses */}
        <div className="space-y-2">
          <Label htmlFor="monthlyExpenses">{t('financialFreedom.calculator.fields.monthlyExpenses.label')}</Label>
          <Input
            id="monthlyExpenses"
            type="number"
            value={input.monthlyExpenses}
            onChange={(e) => onChange('monthlyExpenses', parseFloat(e.target.value) || 0)}
            onFocus={(e) => e.target.select()}
            placeholder="2500"
          />
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.monthlyExpenses.description')}
          </p>
        </div>

        {/* Expected Return */}
        <div className="space-y-2">
          <Label htmlFor="expectedReturn">{t('financialFreedom.calculator.fields.expectedReturn.label')}</Label>
          <Input
            id="expectedReturn"
            type="number"
            step="0.1"
            value={input.expectedAnnualReturn}
            onChange={(e) => onChange('expectedAnnualReturn', parseFloat(e.target.value) || 0)}
            onFocus={(e) => e.target.select()}
            placeholder="7"
          />
          <Slider
            value={[input.expectedAnnualReturn]}
            onValueChange={([value]) => onChange('expectedAnnualReturn', value)}
            min={-10}
            max={30}
            step={0.5}
            className="mt-2"
          />
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.expectedReturn.description')}{' '}
            <a
              href="https://en.wikipedia.org/wiki/Compound_interest"
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary hover:underline"
            >
              {t('financialFreedom.calculator.fields.expectedReturn.learnMore')}
            </a>
          </p>
        </div>

        {/* Monthly Contribution */}
        <div className="space-y-2">
          <Label htmlFor="monthlyContribution">{t('financialFreedom.calculator.fields.monthlyContribution.label')}</Label>
          <Input
            id="monthlyContribution"
            type="number"
            value={input.monthlyContribution ?? 0}
            onChange={(e) => onChange('monthlyContribution', parseFloat(e.target.value) || 0)}
            onFocus={(e) => e.target.select()}
            placeholder="500"
          />
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.monthlyContribution.description')}
          </p>
        </div>

        {/* Withdrawal Rate */}
        <div className="space-y-2">
          <Label htmlFor="withdrawalRate">{t('financialFreedom.calculator.fields.withdrawalRate.label')}</Label>
          <Input
            id="withdrawalRate"
            type="number"
            step="0.1"
            value={input.withdrawalRate ?? 4}
            onChange={(e) => onChange('withdrawalRate', parseFloat(e.target.value) || 4)}
            onFocus={(e) => e.target.select()}
            placeholder="4"
          />
          <Slider
            value={[input.withdrawalRate ?? 4]}
            onValueChange={([value]) => onChange('withdrawalRate', value)}
            min={2}
            max={10}
            step={0.5}
            className="mt-2"
          />
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.withdrawalRate.description')}{' '}
            <a
              href="https://en.wikipedia.org/wiki/Trinity_study"
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary hover:underline"
            >
              {t('financialFreedom.calculator.fields.withdrawalRate.learnMore')}
            </a>
          </p>
        </div>

        {/* Inflation Adjustment */}
        <div className="space-y-2">
          <Label htmlFor="inflationRate">{t('financialFreedom.calculator.fields.inflationRate.label')}</Label>
          <Input
            id="inflationRate"
            type="number"
            step="0.1"
            value={input.inflationRate ?? 2.5}
            onChange={(e) => onChange('inflationRate', parseFloat(e.target.value) || 2.5)}
            onFocus={(e) => e.target.select()}
            placeholder="2.5"
          />
          <div className="flex items-center space-x-2 mt-2">
            <Switch
              id="adjustForInflation"
              checked={input.adjustForInflation ?? false}
              onCheckedChange={(checked) => onChange('adjustForInflation', checked)}
            />
            <Label htmlFor="adjustForInflation">{t('financialFreedom.calculator.fields.adjustForInflation')}</Label>
          </div>
          <p className="text-xs text-muted-foreground">
            {t('financialFreedom.calculator.fields.inflationRate.description')}{' '}
            <a
              href="https://en.wikipedia.org/wiki/Real_versus_nominal_value_(economics)"
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary hover:underline"
            >
              {t('financialFreedom.calculator.fields.inflationRate.learnMore')}
            </a>
          </p>
        </div>
      </div>

      <Button
        onClick={onCalculate}
        disabled={isLoading}
        className="w-full"
        size="lg"
      >
        {isLoading ? t('financialFreedom.calculator.calculating') : t('financialFreedom.calculator.calculate')}
      </Button>
    </div>
  );
}

/**
 * Freedom results display
 */
function FreedomResults({ result, longevityResult, currency }: { result: any; longevityResult?: any; currency: string }) {
  const { t } = useTranslation('tools');
  const yearsToFreedom = result.yearsToFreedom;

  const yearsText = t('financialFreedom.results.timeToFreedom_years', { count: result.yearsToFreedom });
  const monthsText = t('financialFreedom.results.timeToFreedom_months', { count: result.monthsToFreedom });
  const timeToFreedomDisplay = result.yearsToFreedom === 0 
    ? monthsText 
    : result.monthsToFreedom === 0 
      ? yearsText 
      : t('financialFreedom.results.timeToFreedom_combined', { 
          defaultValue: '{{years}}, {{months}}', 
          years: yearsText, 
          months: monthsText 
        });

  return (
    <div className="mt-6 space-y-6">
      {/* Main Results Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Card>
          <CardContent className="pt-6 h-full flex flex-col justify-center">
            <div className="text-center">
              <p className="text-sm text-muted-foreground mb-2">
                {t('financialFreedom.results.timeToFreedom')}
              </p>
              <p className="text-5xl font-bold text-primary">
                {timeToFreedomDisplay}
              </p>
              {result.isAchievable ? (
                <div className="flex items-center justify-center gap-2 mt-2 text-green-600">
                  <CheckCircle className="h-4 w-4" />
                  <span className="text-sm">{t('financialFreedom.results.achievable')}</span>
                </div>
              ) : (
                <div className="flex items-center justify-center gap-2 mt-2 text-amber-600">
                  <AlertCircle className="h-4 w-4" />
                  <span className="text-sm">{t('financialFreedom.results.notAchievable')}</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        {longevityResult && (
          <Card>
            <CardContent className="pt-6 h-full flex flex-col justify-center">
              <div className="text-center">
                <p className="text-sm text-muted-foreground mb-2">
                  {t('financialFreedom.results.savingsWillLast')}
                </p>
                {longevityResult.isInfinite ? (
                  <>
                    <p className="text-5xl font-bold text-green-600">{t('financialFreedom.results.forever')}</p>
                    <p className="text-sm text-muted-foreground mt-2">
                      {t('financialFreedom.results.returnsExceedExpenses')}
                    </p>
                  </>
                ) : (
                  <>
                    <p className="text-5xl font-bold text-primary">
                      {t('financialFreedom.results.yearsPlural', { count: longevityResult.yearsUntilDepletion })}
                    </p>
                    <p className="text-sm text-muted-foreground mt-2">
                      {t('financialFreedom.results.until', { year: longevityResult.depletionYear })}
                    </p>
                  </>
                )}
              </div>
            </CardContent>
          </Card>
        )}
      </div>

      {/* Progress Chart */}
      {result.projections && result.projections.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.pathToFreedom')}</CardTitle>
          </CardHeader>
          <CardContent>
            <ProgressChart
              data={result.projections}
              currentSavings={result.currentProgress}
              targetAmount={result.targetSavingsAmount}
              currency={currency}
              yearsToFreedom={result.isAchievable ? yearsToFreedom : undefined}
            />
          </CardContent>
        </Card>
      )}

      {/* Timeline Projection Table */}
      {result.projections && result.projections.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.yearByYear')}</CardTitle>
          </CardHeader>
          <CardContent>
            <TimelineProjection
              projections={result.projections}
              targetAmount={result.targetSavingsAmount}
              currency={currency}
              yearsToFreedom={result.isAchievable ? yearsToFreedom : undefined}
            />
          </CardContent>
        </Card>
      )}

      {/* Sensitivity Analysis */}
      {result.sensitivityScenarios && result.sensitivityScenarios.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.whatIf')}</CardTitle>
          </CardHeader>
          <CardContent>
            <SensitivityAnalysis
              scenarios={result.sensitivityScenarios}
            />
          </CardContent>
        </Card>
      )}

      {/* Progress */}
      <Card>
        <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.progressToGoal')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="flex justify-between text-sm">
              <span>
                <ConvertedAmount amount={result.currentProgress} currency={currency} inline />
              </span>
              <span>
                <ConvertedAmount amount={result.targetSavingsAmount} currency={currency} inline />
              </span>
            </div>
            <Progress value={result.progressPercentage} className="h-3" />
            <p className="text-center text-sm text-muted-foreground">
            {t('financialFreedom.results.percentOfGoal', { pct: result.progressPercentage.toFixed(1) })}
            </p>
          </div>
        </CardContent>
      </Card>

      {/* Key Metrics */}
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.targetAmount.title')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold">
              <ConvertedAmount amount={result.targetSavingsAmount} currency={currency} inline />
            </p>
            <p className="text-xs text-muted-foreground">
              <Trans
                t={t}
                i18nKey="financialFreedom.results.targetAmount.description"
                components={{
                  income: (
                    <ConvertedAmount
                      amount={result.annualPassiveIncome}
                      currency={currency}
                      inline
                    />
                  ),
                }}
              />
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-lg">{t('financialFreedom.results.passiveIncome.title')}</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold">
              <ConvertedAmount amount={result.annualPassiveIncome} currency={currency} inline />
              /yr
            </p>
            <p className="text-xs text-muted-foreground">
              {t('financialFreedom.results.passiveIncome.description', { rate: result.withdrawalRate ?? 4 })}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Message */}
      <Card>
        <CardContent className="pt-6">
          <p className="text-sm text-muted-foreground text-center">
            {result.isAchievable 
              ? t('financialFreedom.results.achievableMessage', {
                  defaultValue: `Based on your inputs, you could achieve financial freedom in ${timeToFreedomDisplay}.`,
                  time: timeToFreedomDisplay
                })
              : t('financialFreedom.results.notAchievableMessage', {
                  defaultValue: 'Financial freedom is not achievable within 50 years with current inputs.'
                })
            }
          </p>
        </CardContent>
      </Card>
    </div>
  );
}


export default FinancialFreedomCalculator;
