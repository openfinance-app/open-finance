/**
 * Tests for Transaction Rules Hooks
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  useTransactionRules,
  useTransactionRule,
  useCreateRule,
  useUpdateRule,
  useDeleteRule,
  useToggleRule,
} from './useTransactionRules';
import {
  getRules,
  getRule,
  createRule,
  updateRule,
  deleteRule,
  toggleRule,
} from '../services/transactionRulesService';
import type { TransactionRule, TransactionRuleRequest } from '@/types/transactionRules';

// Mock the service functions
vi.mock('../services/transactionRulesService', () => ({
  getRules: vi.fn(),
  getRule: vi.fn(),
  createRule: vi.fn(),
  updateRule: vi.fn(),
  deleteRule: vi.fn(),
  toggleRule: vi.fn(),
}));

const mockGetRules = vi.mocked(getRules);
const mockGetRule = vi.mocked(getRule);
const mockCreateRule = vi.mocked(createRule);
const mockUpdateRule = vi.mocked(updateRule);
const mockDeleteRule = vi.mocked(deleteRule);
const mockToggleRule = vi.mocked(toggleRule);

// Create a wrapper component for testing hooks
const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('useTransactionRules', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const mockRules: TransactionRule[] = [
    {
      id: 1,
      name: 'Test Rule 1',
      priority: 0,
      isEnabled: true,
      conditions: [],
      actions: [],
    },
    {
      id: 2,
      name: 'Test Rule 2',
      priority: 1,
      isEnabled: false,
      conditions: [],
      actions: [],
    },
  ];

  describe('useTransactionRules', () => {
    it('should fetch rules successfully', async () => {
      mockGetRules.mockResolvedValue(mockRules);

      const { result } = renderHook(() => useTransactionRules(), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockRules);
      expect(mockGetRules).toHaveBeenCalledTimes(1);
    });

    it('should handle fetch error', async () => {
      const error = new Error('Fetch failed');
      mockGetRules.mockRejectedValue(error);

      const { result } = renderHook(() => useTransactionRules(), {
        wrapper: createWrapper(),
      });

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useTransactionRule', () => {
    it('should fetch a single rule when id > 0', async () => {
      const mockRule = mockRules[0];
      mockGetRule.mockResolvedValue(mockRule);

      const { result } = renderHook(() => useTransactionRule(1), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(true);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(result.current.data).toEqual(mockRule);
      expect(mockGetRule).toHaveBeenCalledWith(1);
    });

    it('should not fetch when id <= 0', () => {
      const { result } = renderHook(() => useTransactionRule(0), {
        wrapper: createWrapper(),
      });

      expect(result.current.isLoading).toBe(false);
      expect(result.current.data).toBeUndefined();
      expect(mockGetRule).not.toHaveBeenCalled();
    });
  });

  describe('useCreateRule', () => {
    it('should create a rule and invalidate queries', async () => {
      const mockRuleRequest: TransactionRuleRequest = {
        name: 'New Rule',
        priority: 0,
        isEnabled: true,
        conditions: [],
        actions: [],
      };
      const mockCreatedRule = { ...mockRuleRequest, id: 3 };

      mockCreateRule.mockResolvedValue(mockCreatedRule);

      const { result } = renderHook(() => useCreateRule(), {
        wrapper: createWrapper(),
      });

      result.current.mutate(mockRuleRequest);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockCreateRule).toHaveBeenCalledWith(mockRuleRequest);
      expect(result.current.data).toEqual(mockCreatedRule);
    });

    it('should handle create error', async () => {
      const error = new Error('Create failed');
      mockCreateRule.mockRejectedValue(error);

      const { result } = renderHook(() => useCreateRule(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({} as TransactionRuleRequest);

      await waitFor(() => {
        expect(result.current.isError).toBe(true);
      });

      expect(result.current.error).toEqual(error);
    });
  });

  describe('useUpdateRule', () => {
    it('should update a rule and invalidate queries', async () => {
      const mockRuleRequest: TransactionRuleRequest = {
        name: 'Updated Rule',
        priority: 0,
        isEnabled: true,
        conditions: [],
        actions: [],
      };
      const mockUpdatedRule = { ...mockRuleRequest, id: 1 };

      mockUpdateRule.mockResolvedValue(mockUpdatedRule);

      const { result } = renderHook(() => useUpdateRule(), {
        wrapper: createWrapper(),
      });

      result.current.mutate({ id: 1, data: mockRuleRequest });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockUpdateRule).toHaveBeenCalledWith(1, mockRuleRequest);
      expect(result.current.data).toEqual(mockUpdatedRule);
    });
  });

  describe('useDeleteRule', () => {
    it('should delete a rule and invalidate queries', async () => {
      mockDeleteRule.mockResolvedValue(undefined);

      const { result } = renderHook(() => useDeleteRule(), {
        wrapper: createWrapper(),
      });

      result.current.mutate(1);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockDeleteRule).toHaveBeenCalledWith(1);
    });
  });

  describe('useToggleRule', () => {
    it('should toggle a rule and invalidate queries', async () => {
      const mockToggledRule = { ...mockRules[0], isEnabled: false };
      mockToggleRule.mockResolvedValue(mockToggledRule);

      const { result } = renderHook(() => useToggleRule(), {
        wrapper: createWrapper(),
      });

      result.current.mutate(1);

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      expect(mockToggleRule).toHaveBeenCalledWith(1);
      expect(result.current.data).toEqual(mockToggledRule);
    });
  });
});
