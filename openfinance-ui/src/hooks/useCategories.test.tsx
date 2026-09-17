import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import { useCategories } from './useCategories';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

describe('useCategories', () => {
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

  const mockCategories = [
    { id: 1, name: 'Salary', type: 'INCOME', isSystem: true, subcategoryCount: 0 },
    {
      id: 2,
      name: 'Groceries',
      type: 'EXPENSE',
      parentId: null,
      isSystem: true,
      subcategoryCount: 3,
    },
    {
      id: 3,
      name: 'Rent',
      type: 'EXPENSE',
      parentId: null,
      icon: '🏠',
      color: '#3498db',
      isSystem: true,
      subcategoryCount: 0,
    },
  ];

  it('should fetch categories successfully', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockCategories });

    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual(mockCategories);
    expect(mockedApiClient.get).toHaveBeenCalledWith('/categories');
  });

  it('should handle API error', async () => {
    mockedApiClient.get.mockRejectedValue(new Error('Network error'));

    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error?.message).toBe('Network error');
  });

  it('should return empty array data shape when API returns empty', async () => {
    mockedApiClient.get.mockResolvedValue({ data: [] });

    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data).toEqual([]);
  });

  it('should have a staleTime of 5 minutes', async () => {
    mockedApiClient.get.mockResolvedValue({ data: mockCategories });

    const { result } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    // Verify the data is cached (second call should not make another API call)
    const { result: result2 } = renderHook(() => useCategories(), { wrapper });

    await waitFor(() => {
      expect(result2.current.isSuccess).toBe(true);
    });

    // Only one API call should have been made due to staleTime
    expect(mockedApiClient.get).toHaveBeenCalledTimes(1);
  });
});
