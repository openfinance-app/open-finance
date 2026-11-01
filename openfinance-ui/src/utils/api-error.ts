import { isAxiosError } from 'axios';

export function apiErrorMessage(error: unknown, fallback: string): string {
  return isAxiosError<{ message?: string }>(error) && error.response?.data.message
    ? error.response.data.message
    : fallback;
}
