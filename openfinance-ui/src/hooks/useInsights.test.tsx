import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useTopInsights,
  useInsights,
  useGenerateInsights,
  useDismissInsight,
} from './useInsights';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

describe('useInsights hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockInsight = {
    id: 1,
    type: 'SPENDING_ALERT',
    title: 'High spending detected',
    description: 'Your spending increased by 20% this month',
    priority: 'HIGH',
    createdAt: '2024-01-15T00:00:00Z',
  };

  // ── useTopInsights ─────────────────────────────────────────────────
  describe('useTopInsights', () => {
    it('should fetch top insights with default limit', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockInsight] });

      const { result } = renderHook(() => useTopInsights(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });
      expect(result.current.data).toEqual([mockInsight]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/insights/top/3', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should fetch top insights with custom limit', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockInsight] });

      const { result } = renderHook(() => useTopInsights(5), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });
      expect(mockedApiClient.get).toHaveBeenCalledWith('/insights/top/5', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── useInsights ─────────────────────────────────────────────────
  describe('useInsights', () => {
    it('should fetch all active insights', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockInsight] });

      const { result } = renderHook(() => useInsights(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });
      expect(result.current.data).toEqual([mockInsight]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/insights', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── useGenerateInsights ─────────────────────────────────────────────────
  describe('useGenerateInsights', () => {
    it('should generate new insights', async () => {
      const newInsights = [mockInsight, { ...mockInsight, id: 2, title: 'Budget alert' }];
      mockedApiClient.post.mockResolvedValue({ data: newInsights });

      const { result } = renderHook(() => useGenerateInsights(), { wrapper });

      await act(async () => {
        result.current.mutate();
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(newInsights);
    });

    it('should invalidate insight queries on success', async () => {
      mockedApiClient.post.mockResolvedValue({ data: [mockInsight] });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useGenerateInsights(), { wrapper });

      await act(async () => {
        result.current.mutate();
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['insights'] });
    });
  });

  // ── useDismissInsight ─────────────────────────────────────────────────
  describe('useDismissInsight', () => {
    it('should dismiss an insight', async () => {
      mockedApiClient.post.mockResolvedValue({});

      const { result } = renderHook(() => useDismissInsight(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.post).toHaveBeenCalledWith('/insights/1/dismiss');
    });

    it('should invalidate insight queries on success', async () => {
      mockedApiClient.post.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDismissInsight(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['insights'] });
    });
  });
});
