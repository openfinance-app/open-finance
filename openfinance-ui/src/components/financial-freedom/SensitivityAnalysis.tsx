/**
 * Sensitivity Analysis Component
 *
 * Displays comparison of different return rate scenarios
 */

import { Card, CardContent, CardHeader, CardTitle } from '../ui/Card';
import type { SensitivityScenario } from '../../types/calculator';
import { formatPercentage, formatTimeToFreedom } from '../../utils/financialCalculations';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

interface SensitivityAnalysisProps {
  scenarios: SensitivityScenario[];
}

/**
 * Sensitivity Analysis Component
 * Shows optimistic, baseline, and pessimistic scenarios
 */
export function SensitivityAnalysis({ scenarios }: SensitivityAnalysisProps) {
  if (!scenarios || scenarios.length === 0) {
    return null;
  }

  const getScenarioIcon = (scenarioType: string) => {
    switch (scenarioType) {
      case 'optimistic':
        return <TrendingUp className="h-4 w-4 text-green-600" />;
      case 'pessimistic':
        return <TrendingDown className="h-4 w-4 text-red-600" />;
      default:
        return <Minus className="h-4 w-4 text-blue-600" />;
    }
  };

  const getScenarioColor = (scenarioType: string) => {
    switch (scenarioType) {
      case 'optimistic':
        return 'bg-green-50 border-green-200';
      case 'pessimistic':
        return 'bg-red-50 border-red-200';
      default:
        return 'bg-blue-50 border-blue-200';
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-lg">Sensitivity Analysis</CardTitle>
      </CardHeader>
      <CardContent>
        <p className="text-sm text-muted-foreground mb-4">
          See how different return rates affect your timeline to financial freedom
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {scenarios.map((scenario, index) => (
            <div
              key={index}
              className={`p-4 rounded-lg border ${getScenarioColor(scenario.scenarioType)}`}
            >
              <div className="flex items-center gap-2 mb-2">
                {getScenarioIcon(scenario.scenarioType)}
                <span className="font-medium">{scenario.label}</span>
              </div>

              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Return Rate:</span>
                  <span className="font-medium">{formatPercentage(scenario.returnRate)}</span>
                </div>

                <div className="flex justify-between text-sm">
                  <span className="text-muted-foreground">Years:</span>
                  <span className="font-medium">{scenario.yearsToFreedom}</span>
                </div>

                {scenario.monthsToFreedom > 0 && (
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Months:</span>
                    <span className="font-medium">{scenario.monthsToFreedom}</span>
                  </div>
                )}

                <div className="pt-2 border-t mt-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Timeline:</span>
                    <span className="font-medium">
                      {formatTimeToFreedom(scenario.yearsToFreedom * 12 + scenario.monthsToFreedom)}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

export default SensitivityAnalysis;
