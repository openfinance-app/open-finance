import type { HistoryFieldChange } from '@/types/history';

export function historyFieldChanges(json?: string): HistoryFieldChange[] {
  if (!json) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return [];
    return Object.entries(parsed).flatMap(([field, value]) =>
      value && typeof value === 'object' && ('before' in value || 'after' in value)
        ? [
            {
              field,
              before: 'before' in value ? value.before : null,
              after: 'after' in value ? value.after : null,
            },
          ]
        : []
    );
  } catch {
    return [];
  }
}
