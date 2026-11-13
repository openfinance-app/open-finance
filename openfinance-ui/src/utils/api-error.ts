import { isAxiosError } from 'axios';

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (!isAxiosError<{ message?: string; validationErrors?: Record<string, string> }>(error))
    return fallback;
  const data = error.response?.data;
  return data?.validationErrors
    ? Object.values(data.validationErrors).join(' ')
    : data?.message || fallback;
}
