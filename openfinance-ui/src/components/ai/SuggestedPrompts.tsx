/**
 * Suggested Prompts Component
 * Task 11.3.4: Create suggested prompts
 *
 * Displays quick action buttons for common financial questions
 *
 * @since Sprint 11 - AI Assistant Integration
 */
import React from 'react';
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
import type { SuggestedPrompt } from '@/types/ai';

interface SuggestedPromptsProps {
  /** Handler for when a prompt is selected */
  onSelectPrompt: (question: string) => void;

  /** Whether prompts should be disabled */
  disabled?: boolean;
}

/**
 * Predefined suggested prompts for common financial queries
 */
const SUGGESTED_PROMPTS: SuggestedPrompt[] = [
  {
    label: 'Analyze My Spending',
    question: 'Can you analyze my spending patterns and tell me where most of my money is going?',
    icon: 'TrendingDown',
    category: 'spending',
  },
  {
    label: 'Budget Recommendations',
    question: 'Based on my income and expenses, what budget recommendations do you have for me?',
    icon: 'Target',
    category: 'budgeting',
  },
  {
    label: 'Investment Performance',
    question: 'How is my investment portfolio performing? What are my best and worst performers?',
    icon: 'TrendingUp',
    category: 'investing',
  },
  {
    label: 'Debt Strategy',
    question: 'What strategy should I follow to pay off my debts most efficiently?',
    icon: 'CreditCard',
    category: 'debt',
  },
  {
    label: 'Savings Goals',
    question: 'How much should I be saving each month to reach my financial goals?',
    icon: 'PiggyBank',
    category: 'general',
  },
  {
    label: 'Cash Flow Analysis',
    question: 'What does my cash flow look like? Am I spending more than I earn?',
    icon: 'DollarSign',
    category: 'general',
  },
  {
    label: 'Financial Summary',
    question: 'Can you give me an overall summary of my current financial situation?',
    icon: 'BarChart3',
    category: 'general',
  },
  {
    label: 'Money-Saving Tips',
    question: 'What are some practical tips to reduce my expenses and save more money?',
    icon: 'Lightbulb',
    category: 'general',
  },
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
  return (
    <div className="space-y-4">
      <h3 className="text-sm font-medium text-text-secondary">Suggested Questions</h3>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        {SUGGESTED_PROMPTS.map((prompt, index) => {
          const Icon = ICON_MAP[prompt.icon || 'Lightbulb'];

          return (
            <button
              key={index}
              onClick={() => onSelectPrompt(prompt.question)}
              disabled={disabled}
              className="flex items-center gap-3 p-4 bg-surface border border-border rounded-lg hover:border-blue-500 hover:shadow-md transition-all disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:border-border text-left"
            >
              <div className="flex-shrink-0 w-10 h-10 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
                <Icon className="w-5 h-5 text-blue-600" />
              </div>

              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-text-primary truncate">{prompt.label}</p>
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
};

export default SuggestedPrompts;
