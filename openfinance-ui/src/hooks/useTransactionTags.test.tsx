import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useTransactionTags, usePopularTags } from './useTransactionTags';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useTransactionTags', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockTags = [
    { tag: 'groceries', count: 15 },
    { tag: 'rent', count: 12 },
    { tag: 'utilities', count: 8 },
  ];

  it('should fetch transaction tags successfully', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockTags });

    const { result } = renderHook(() => useTransactionTags(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockTags);
    expect(mockedApiClient.get).toHaveBeenCalledWith('/transactions/tags');
  });

  it('should handle API error', async () => {
    mockedApiClient.get.mockRejectedValue(new Error('Server error'));

    const { result } = renderHook(() => useTransactionTags(), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.message).toBe('Server error');
  });

  it('should return empty array when no tags exist', async () => {
    mockedApiClient.get.mockResolvedValue({ data: [] });

    const { result } = renderHook(() => useTransactionTags(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual([]);
  });
});

describe('usePopularTags', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockPopularTags = ['groceries', 'rent', 'utilities', 'transport', 'dining'];

  it('should fetch popular tags with default limit', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockPopularTags });

    const { result } = renderHook(() => usePopularTags(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockPopularTags);
    expect(mockedApiClient.get).toHaveBeenCalledWith('/transactions/tags/popular', {
      params: { limit: 20 },
    });
  });

  it('should fetch popular tags with custom limit', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockPopularTags.slice(0, 3) });

    const { result } = renderHook(() => usePopularTags(3), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockedApiClient.get).toHaveBeenCalledWith('/transactions/tags/popular', {
      params: { limit: 3 },
    });
  });

  it('should handle API error', async () => {
    mockedApiClient.get.mockRejectedValue(new Error('Failed to fetch'));

    const { result } = renderHook(() => usePopularTags(), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.message).toBe('Failed to fetch');
  });
});
