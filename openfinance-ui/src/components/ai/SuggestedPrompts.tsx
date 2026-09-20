/**
 * Suggested Prompts Component
 * Task 11.3.4: Create suggested prompts
 *
 * Displays quick action buttons for common financial questions
 *
 * @since Sprint 11 - AI Assistant Integration
 */
import React from 'react';
import { useTranslation } from 'react-i18next';
import {
  TrendingUp,
  TrendingDown,
  PiggyBank,
  DollarSign,
  CreditCard,
  BarChart3,
  Target,
  Lightbulb,
} from 'lucide-react';

interface SuggestedPromptsProps {
  /** Handler for when a prompt is selected */
  onSelectPrompt: (question: string) => void;

  /** Whether prompts should be disabled */
  disabled?: boolean;
}

/**
 * Predefined suggested prompts for common financial queries
 */
const SUGGESTED_PROMPTS = [
  { key: 'analyzeSpending', icon: 'TrendingDown' },
  { key: 'budgetRecommendations', icon: 'Target' },
  { key: 'investmentPerformance', icon: 'TrendingUp' },
  { key: 'debtStrategy', icon: 'CreditCard' },
  { key: 'savingsGoals', icon: 'PiggyBank' },
  { key: 'cashFlowAnalysis', icon: 'DollarSign' },
  { key: 'financialSummary', icon: 'BarChart3' },
  { key: 'moneySavingTips', icon: 'Lightbulb' },
];

/**
 * Icon map for rendering icons
 */
const ICON_MAP: Record<string, React.FC<{ className?: string }>> = {
  TrendingUp,
  TrendingDown,
  PiggyBank,
  DollarSign,
  CreditCard,
  BarChart3,
  Target,
  Lightbulb,
};

/**
 * SuggestedPrompts displays quick action buttons for common questions
 */
export const SuggestedPrompts: React.FC<SuggestedPromptsProps> = ({
  onSelectPrompt,
  disabled = false,
}) => {
  const { t } = useTranslation('ai');
  return (
    <div className="space-y-4">
      <h3 className="text-sm font-medium text-text-secondary">{t('suggestedQuestions')}</h3>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {SUGGESTED_PROMPTS.map(prompt => {
          const Icon = ICON_MAP[prompt.icon || 'Lightbulb'];

          return (
            <button
              key={prompt.key}
              onClick={() => onSelectPrompt(t(`prompts.${prompt.key}.question`))}
              disabled={disabled}
              className="flex items-center gap-3 p-4 bg-surface border border-border rounded-lg hover:border-blue-500 hover:shadow-md transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-border text-left"
            >
              <div className="flex-shrink-0 w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                <Icon className="w-5 h-5 text-blue-600" />
              </div>

              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-text-primary truncate">
                  {t(`prompts.${prompt.key}.label`)}
                </p>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default SuggestedPrompts;
