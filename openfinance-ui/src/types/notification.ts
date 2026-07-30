/**
 * Types for system notifications
 */
import { NOTIFICATION_SEVERITY_COLORS } from '@/constants/colors';

export type NotificationType = 'STALE_QUOTES' | 'STALE_EXCHANGE_RATES' | 'UNCATEGORIZED_TRANSACTIONS' | 'UNLINKED_PAYEE' | 'LOW_BALANCE' | 'BUDGET_ALERT';

export type NotificationSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

export interface INotification {
    type: NotificationType;
    title: string;
    message: string;
    count: number;
    actionUrl: string;
    actionLabel: string;
    severity: NotificationSeverity;
    metadata?: string;
}

export function getNotificationColor(severity: NotificationSeverity): string {
    return NOTIFICATION_SEVERITY_COLORS[severity];
}

export function getNotificationIcon(type: NotificationType): string {
    switch (type) {
        case 'STALE_QUOTES':
            return '📊';
        case 'STALE_EXCHANGE_RATES':
            return '💱';
        case 'UNCATEGORIZED_TRANSACTIONS':
            return '🏷️';
        case 'UNLINKED_PAYEE':
            return '👤';
        case 'LOW_BALANCE':
            return '⚠️';
        case 'BUDGET_ALERT':
            return '💰';
    }
}
