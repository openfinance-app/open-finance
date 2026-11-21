import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { INotification } from '@/types/notification';

export function notificationFingerprint(notification: INotification): string {
  return JSON.stringify([
    notification.type,
    notification.count,
    notification.severity,
    notification.actionUrl,
    notification.metadata ?? null,
  ]);
}

interface NotificationReadState {
  seen: Record<string, Record<string, string>>;
  dismissed: Record<string, string[]>;
  markRead: (userId: string, notification: INotification) => void;
  dismiss: (userId: string, notifications: INotification[]) => void;
  syncActive: (userId: string, notifications: INotification[]) => void;
}

export const useNotificationReadState = create<NotificationReadState>()(
  persist(
    set => ({
      seen: {},
      dismissed: {},
      markRead: (userId, notification) =>
        set(state => ({
          seen: {
            ...state.seen,
            [userId]: {
              ...state.seen[userId],
              [notification.type]: notificationFingerprint(notification),
            },
          },
        })),
      dismiss: (userId, notifications) =>
        set(state => ({
          dismissed: {
            ...state.dismissed,
            [userId]: [
              ...new Set([
                ...(state.dismissed[userId] ?? []),
                ...notifications.map(notificationFingerprint),
              ]),
            ],
          },
        })),
      syncActive: (userId, notifications) =>
        set(state => {
          const previous = state.seen[userId] ?? {};
          const dismissed = state.dismissed[userId] ?? [];
          const active = new Set(notifications.map(notificationFingerprint));
          const retained = Object.fromEntries(
            Object.entries(previous).filter(([, value]) => active.has(value))
          );
          const stillDismissed = dismissed.filter(value => active.has(value));
          if (
            Object.keys(retained).length === Object.keys(previous).length &&
            stillDismissed.length === dismissed.length
          )
            return state;
          return {
            seen: { ...state.seen, [userId]: retained },
            dismissed: { ...state.dismissed, [userId]: stillDismissed },
          };
        }),
    }),
    {
      name: 'openfinance_notification_reads',
      partialize: ({ seen, dismissed }) => ({ seen, dismissed }),
    }
  )
);
