/**
 * Insight types and interfaces
 * TASK-11.4.5: Display AI-Powered Insights in Dashboard
 *
 * Matches backend DTOs from org.openfinance.dto.InsightDto
 */

export interface Insight {
  id: number;
  type: InsightType;
  title: string;
  description: string;
  priority: InsightPriority;
  dismissed: boolean;
  createdAt: string; // ISO 8601 format
}

export const InsightType = {
  SPENDING_ANOMALY: 'SPENDING_ANOMALY',
  BUDGET_WARNING: 'BUDGET_WARNING',
  BUDGET_RECOMMENDATION: 'BUDGET_RECOMMENDATION',
  SAVINGS_OPPORTUNITY: 'SAVINGS_OPPORTUNITY',
  INVESTMENT_SUGGESTION: 'INVESTMENT_SUGGESTION',
  DEBT_ALERT: 'DEBT_ALERT',
  CASH_FLOW_WARNING: 'CASH_FLOW_WARNING',
  TAX_OPTIMIZATION: 'TAX_OPTIMIZATION',
  GOAL_PROGRESS: 'GOAL_PROGRESS',
  GENERAL_TIP: 'GENERAL_TIP',
  UNUSUAL_TRANSACTION: 'UNUSUAL_TRANSACTION',
  REGION_COMPARISON: 'REGION_COMPARISON',
  TAX_OBLIGATION: 'TAX_OBLIGATION',
  RECURRING_BILLING: 'RECURRING_BILLING',
} as const;

export type InsightType = (typeof InsightType)[keyof typeof InsightType];

export const InsightPriority = {
  HIGH: 'HIGH',
  MEDIUM: 'MEDIUM',
  LOW: 'LOW',
} as const;

export type InsightPriority = (typeof InsightPriority)[keyof typeof InsightPriority];
