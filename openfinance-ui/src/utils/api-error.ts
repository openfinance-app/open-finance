import { isAxiosError } from 'axios';

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!isAxiosError<{ message?: string; validationErrors?: Record<string, string> }>(error))
    return fallback;
  const data = error.response?.data;
  const fieldMessage = Object.values(data?.validationErrors ?? {})
    .filter(Boolean)
    .join(' ');
  return fieldMessage || data?.message || fallback;
}
