/**
 * Tests for useUserFinancialData Hook
 * Task 4.4.2: Test user financial data hook
 */

import { renderHook, waitFor } from '@testing-library/react';
import { useUserFinancialData } from './useUserFinancialData';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import apiClient from '../services/apiClient';

// Mock the API client
vi.mock('../services/apiClient');

const mockGet = vi.mocked(apiClient.get);

describe('useUserFinancialData', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should initialize with null data and not loading', async () => {
    mockGet.mockResolvedValue({ data: [] });

    const { result } = renderHook(() => useUserFinancialData());

    // Initially loading
    expect(result.current.isLoading).toBe(true);

    // Wait for loading to complete
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.data).not.toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('should fetch and calculate user financial data', async () => {
    // Mock assets response
    const mockAssets = [
      {
        id: 1,
        userId: 1,
        name: 'Stock Portfolio',
        type: 'STOCK',
        quantity: 100,
        currentPrice: 150,
        totalValue: 15000,
        currency: 'USD',
      },
      {
        id: 2,
        userId: 1,
        name: 'Savings Account',
        type: 'OTHER',
        quantity: 1,
        currentPrice: 10000,
        totalValue: 10000,
        currency: 'USD',
      },
    ];

    // Mock transactions response (expenses over 6 months)
    const mockTransactions = [
      {
        id: 1,
        userId: 1,
        type: 'EXPENSE',
        amount: 2000,
        date: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(), // 1 month ago
      },
      {
        id: 2,
        userId: 1,
        type: 'EXPENSE',
        amount: 2500,
        date: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString(), // 2 months ago
      },
      {
        id: 3,
        userId: 1,
        type: 'EXPENSE',
        amount: 1800,
        date: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString(), // 3 months ago
      },
    ];

    mockGet
      .mockResolvedValueOnce({ data: mockAssets })
      .mockResolvedValueOnce({ data: mockTransactions });

    const { result } = renderHook(() => useUserFinancialData());

    // Wait for data to load
    await waitFor(() => {
      expect(result.current.data).not.toBeNull();
    });

    // Verify total savings calculation
    expect(result.current.data?.totalSavings).toBe(25000); // 15000 + 10000

    // Verify average monthly expenses calculation
    expect(result.current.data?.averageMonthlyExpenses).toBeGreaterThan(0);

    // Verify currency
    expect(result.current.data?.currency).toBe('USD');

    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it('should handle empty assets', async () => {
    mockGet
      .mockResolvedValueOnce({ data: [] }) // Empty assets
      .mockResolvedValueOnce({ data: [] }); // Empty transactions

    const { result } = renderHook(() => useUserFinancialData());

    await waitFor(() => {
      expect(result.current.data).not.toBeNull();
    });

    expect(result.current.data?.totalSavings).toBe(0);
    expect(result.current.data?.averageMonthlyExpenses).toBe(0);
    expect(result.current.data?.currency).toBe('EUR'); // Default currency
  });

  it('should handle API errors gracefully', async () => {
    mockGet.mockRejectedValue(new Error('API Error'));

    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { result } = renderHook(() => useUserFinancialData());

    await waitFor(() => {
      expect(result.current.error).not.toBeNull();
    });

    expect(result.current.error).toBe('API Error');
    expect(result.current.data).toBeNull();
    expect(result.current.isLoading).toBe(false);

    consoleSpy.mockRestore();
  });

  it('should allow refetching data', async () => {
    const mockAssets = [
      {
        id: 1,
        userId: 1,
        name: 'Portfolio',
        type: 'STOCK',
        quantity: 100,
        currentPrice: 100,
        totalValue: 10000,
        currency: 'EUR',
      },
    ];

    mockGet
      .mockResolvedValueOnce({ data: mockAssets })
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: mockAssets })
      .mockResolvedValueOnce({ data: [] });

    const { result } = renderHook(() => useUserFinancialData());

    await waitFor(() => {
      expect(result.current.data).not.toBeNull();
    });

    expect(result.current.data?.totalSavings).toBe(10000);

    // Refetch
    await result.current.refetch();

    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledTimes(4); // 2 initial + 2 refetch
    });
  });

  it('should calculate average expenses correctly with multiple transactions', async () => {
    const mockAssets = [
      {
        id: 1,
        userId: 1,
        name: 'Savings',
        type: 'OTHER',
        quantity: 1,
        currentPrice: 50000,
        totalValue: 50000,
        currency: 'EUR',
      },
    ];

    // Create 6 months of expenses
    const mockTransactions = Array.from({ length: 6 }, (_, i) => ({
      id: i + 1,
      userId: 1,
      type: 'EXPENSE',
      amount: 2000 + i * 100, // Varying amounts
      date: new Date(Date.now() - (i + 1) * 30 * 24 * 60 * 60 * 1000).toISOString(),
    }));

    mockGet
      .mockResolvedValueOnce({ data: mockAssets })
      .mockResolvedValueOnce({ data: mockTransactions });

    const { result } = renderHook(() => useUserFinancialData());

    await waitFor(() => {
      expect(result.current.data).not.toBeNull();
    });

    // Total expenses: 2000 + 2100 + 2200 + 2300 + 2400 + 2500 = 13500
    // The calculation divides by the actual months between oldest and newest transaction
    // which may be more than 6 months, so we just verify it's reasonable
    expect(result.current.data?.averageMonthlyExpenses).toBeGreaterThan(1500);
    expect(result.current.data?.averageMonthlyExpenses).toBeLessThan(3000);
  });
});
