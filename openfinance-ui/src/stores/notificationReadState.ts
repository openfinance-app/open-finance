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
  markRead: (userId: string, notification: INotification) => void;
  syncActive: (userId: string, notifications: INotification[]) => void;
}

export const useNotificationReadState = create<NotificationReadState>()(
  persist(
    set => ({
      seen: {},
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
      syncActive: (userId, notifications) =>
        set(state => {
          const previous = state.seen[userId];
          if (!previous) return state;
          const active = new Set(notifications.map(notificationFingerprint));
          const retained = Object.fromEntries(
            Object.entries(previous).filter(([, value]) => active.has(value))
          );
          if (Object.keys(retained).length === Object.keys(previous).length) return state;
          return { seen: { ...state.seen, [userId]: retained } };
        }),
    }),
    { name: 'openfinance_notification_reads', partialize: state => ({ seen: state.seen }) }
  )
);
