/**
 * Types for budget alerts
 * 
 * Backend mapping:
 * - BudgetAlertRequest.java
 * - BudgetAlertResponse.java
 */
import { ALERT_SEVERITY_COLORS } from '@/constants/colors';

export interface BudgetAlert {
  id: string;
  budgetId: number;
  budgetName: string;
  categoryName: string;
  threshold: number;
  isEnabled: boolean;
  lastTriggered: string | null;
  isRead: boolean;
  currentSpentPercentage: number | null;
  message: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAlertRequest {
  threshold: number;
  isEnabled?: boolean;
}

export interface UpdateAlertRequest {
  threshold?: number;
  isEnabled?: boolean;
}

export type AlertSeverity = 'warning' | 'critical' | 'exceeded';

export function getAlertSeverity(threshold: number, spentPercentage: number | null): AlertSeverity {
  if (spentPercentage === null) return 'warning';
  
  if (threshold >= 100 && spentPercentage >= 100) {
    return 'exceeded';
  } else if (threshold >= 90) {
    return 'critical';
  }
  return 'warning';
}

export function getAlertColor(severity: AlertSeverity): string {
  return ALERT_SEVERITY_COLORS[severity];
}
