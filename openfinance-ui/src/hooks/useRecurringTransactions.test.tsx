import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useRecurringTransactions,
  useRecurringTransactionsPaged,
  useActiveRecurringTransactions,
  useDueRecurringTransactions,
  useRecurringTransaction,
  useCreateRecurringTransaction,
  useUpdateRecurringTransaction,
  useDeleteRecurringTransaction,
  usePauseRecurringTransaction,
  useResumeRecurringTransaction,
  useProcessRecurringTransactions,
  useRecurringTransactionOperations,
} from './useRecurringTransactions';
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

const mockRecurringTransaction = {
  id: 1,
  accountId: 1,
  accountName: 'Checking',
  toAccountId: null,
  toAccountName: null,
  type: 'EXPENSE',
  amount: 1500,
  currency: 'USD',
  categoryId: 5,
  categoryName: 'Housing',
  categoryIcon: 'Home',
  categoryColor: '#4CAF50',
  payee: 'Landlord',
  description: 'Monthly Rent',
  notes: null,
  frequency: 'MONTHLY',
  frequencyDisplayName: 'Monthly',
  nextOccurrence: '2024-02-01',
  endDate: null,
  isActive: true,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
  isDue: false,
  daysUntilNext: 15,
  isEnded: false,
};

describe('useRecurringTransactions hooks', () => {
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

  // ── useRecurringTransactions ─────────────────────────────────────────────────
  describe('useRecurringTransactions', () => {
    it('should fetch all recurring transactions', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockRecurringTransaction] });

      const { result } = renderHook(() => useRecurringTransactions(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockRecurringTransaction]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/recurring-transactions', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should fetch with account filter', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockRecurringTransaction] });

      const { result } = renderHook(
        () => useRecurringTransactions({ accountId: 1 }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('accountId=1'),
        expect.any(Object),
      );
    });

    it('should fetch active recurring transactions when isActive=true', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockRecurringTransaction] });

      const { result } = renderHook(
        () => useRecurringTransactions({ isActive: true }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('/recurring-transactions/active'),
        expect.any(Object),
      );
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useRecurringTransactions(), { wrapper });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });
  });

  // ── useRecurringTransactionsPaged ─────────────────────────────────────────────────
  describe('useRecurringTransactionsPaged', () => {
    it('should fetch paged recurring transactions', async () => {
      const pagedResponse = {
        content: [mockRecurringTransaction],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };
      mockedApiClient.get.mockResolvedValue({ data: pagedResponse });

      const { result } = renderHook(
        () => useRecurringTransactionsPaged({ page: 0, size: 20 }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(pagedResponse);
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('/recurring-transactions/paged'),
        expect.any(Object),
      );
    });

    it('should include filter parameters in query', async () => {
      const pagedResponse = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 };
      mockedApiClient.get.mockResolvedValue({ data: pagedResponse });

      const { result } = renderHook(
        () => useRecurringTransactionsPaged({ type: 'EXPENSE', frequency: 'MONTHLY', search: 'rent' }),
        { wrapper },
      );

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      const url = mockedApiClient.get.mock.calls[0][0] as string;
      expect(url).toContain('type=EXPENSE');
      expect(url).toContain('frequency=MONTHLY');
      expect(url).toContain('search=rent');
    });
  });

  // ── useActiveRecurringTransactions ─────────────────────────────────────────────────
  describe('useActiveRecurringTransactions', () => {
    it('should delegate to useRecurringTransactions with isActive=true', async () => {
      mockedApiClient.get.mockResolvedValue({ data: [mockRecurringTransaction] });

      const { result } = renderHook(() => useActiveRecurringTransactions(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.get).toHaveBeenCalledWith(
        expect.stringContaining('/recurring-transactions/active'),
        expect.any(Object),
      );
    });
  });

  // ── useDueRecurringTransactions ─────────────────────────────────────────────────
  describe('useDueRecurringTransactions', () => {
    it('should fetch due recurring transactions', async () => {
      const dueTransaction = { ...mockRecurringTransaction, isDue: true, daysUntilNext: 0 };
      mockedApiClient.get.mockResolvedValue({ data: [dueTransaction] });

      const { result } = renderHook(() => useDueRecurringTransactions(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([dueTransaction]);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/recurring-transactions/due', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });
  });

  // ── useRecurringTransaction ─────────────────────────────────────────────────
  describe('useRecurringTransaction', () => {
    it('should fetch a single recurring transaction by id', async () => {
      mockedApiClient.get.mockResolvedValue({ data: mockRecurringTransaction });

      const { result } = renderHook(() => useRecurringTransaction(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockRecurringTransaction);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/recurring-transactions/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should be disabled when id is null', () => {
      const { result } = renderHook(() => useRecurringTransaction(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
      expect(mockedApiClient.get).not.toHaveBeenCalled();
    });
  });

  // ── useCreateRecurringTransaction ─────────────────────────────────────────────────
  describe('useCreateRecurringTransaction', () => {
    it('should create a recurring transaction', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockRecurringTransaction });

      const { result } = renderHook(() => useCreateRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate({
          accountId: 1,
          type: 'EXPENSE',
          amount: 1500,
          currency: 'USD',
          description: 'Monthly Rent',
          frequency: 'MONTHLY',
          nextOccurrence: '2024-02-01',
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockRecurringTransaction);
    });

    it('should invalidate queries on success', async () => {
      mockedApiClient.post.mockResolvedValue({ data: mockRecurringTransaction });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useCreateRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate({
          accountId: 1,
          type: 'EXPENSE',
          amount: 1500,
          currency: 'USD',
          description: 'Monthly Rent',
          frequency: 'MONTHLY',
          nextOccurrence: '2024-02-01',
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recurringTransactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    });
  });

  // ── useUpdateRecurringTransaction ─────────────────────────────────────────────────
  describe('useUpdateRecurringTransaction', () => {
    it('should update a recurring transaction', async () => {
      const updated = { ...mockRecurringTransaction, amount: 1600 };
      mockedApiClient.put.mockResolvedValue({ data: updated });

      const { result } = renderHook(() => useUpdateRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate({
          id: 1,
          data: {
            accountId: 1,
            type: 'EXPENSE',
            amount: 1600,
            currency: 'USD',
            description: 'Monthly Rent',
            frequency: 'MONTHLY',
            nextOccurrence: '2024-02-01',
          },
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.amount).toBe(1600);
      expect(mockedApiClient.put).toHaveBeenCalledWith(
        '/recurring-transactions/1',
        expect.any(Object),
        expect.any(Object),
      );
    });
  });

  // ── useDeleteRecurringTransaction ─────────────────────────────────────────────────
  describe('useDeleteRecurringTransaction', () => {
    it('should delete a recurring transaction', async () => {
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.delete).toHaveBeenCalledWith('/recurring-transactions/1', {
        headers: { 'X-Encryption-Session': 'test-encryption-key' },
      });
    });

    it('should invalidate queries on success', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recurringTransactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
    });
  });

  // ── usePauseRecurringTransaction ─────────────────────────────────────────────────
  describe('usePauseRecurringTransaction', () => {
    it('should pause a recurring transaction', async () => {
      const paused = { ...mockRecurringTransaction, isActive: false };
      mockedApiClient.post.mockResolvedValue({ data: paused });

      const { result } = renderHook(() => usePauseRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.isActive).toBe(false);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/recurring-transactions/1/pause',
        {},
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } },
      );
    });
  });

  // ── useResumeRecurringTransaction ─────────────────────────────────────────────────
  describe('useResumeRecurringTransaction', () => {
    it('should resume a paused recurring transaction', async () => {
      const resumed = { ...mockRecurringTransaction, isActive: true };
      mockedApiClient.post.mockResolvedValue({ data: resumed });

      const { result } = renderHook(() => useResumeRecurringTransaction(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.isActive).toBe(true);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/recurring-transactions/1/resume',
        {},
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } },
      );
    });
  });

  // ── useProcessRecurringTransactions ─────────────────────────────────────────────────
  describe('useProcessRecurringTransactions', () => {
    it('should process recurring transactions', async () => {
      const processingResult = { processedCount: 3, failedCount: 0, errors: [] };
      mockedApiClient.post.mockResolvedValue({ data: processingResult });

      const { result } = renderHook(() => useProcessRecurringTransactions(), { wrapper });

      await act(async () => {
        result.current.mutate();
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(processingResult);
    });

    it('should invalidate multiple query keys on success', async () => {
      const processingResult = { processedCount: 1, failedCount: 0, errors: [] };
      mockedApiClient.post.mockResolvedValue({ data: processingResult });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useProcessRecurringTransactions(), { wrapper });

      await act(async () => {
        result.current.mutate();
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['transactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['recurringTransactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['dashboard'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useRecurringTransactionOperations ─────────────────────────────────────────────────
  describe('useRecurringTransactionOperations', () => {
    it('should expose all mutation operations', () => {
      const { result } = renderHook(() => useRecurringTransactionOperations(), { wrapper });

      expect(result.current.create).toBeDefined();
      expect(result.current.update).toBeDefined();
      expect(result.current.delete).toBeDefined();
      expect(result.current.pause).toBeDefined();
      expect(result.current.resume).toBeDefined();
      expect(result.current.process).toBeDefined();
      expect(result.current.isLoading).toBe(false);
    });

    it('should report isLoading when any mutation is pending', async () => {
      // Create a never-resolving promise to keep mutation pending
      mockedApiClient.post.mockImplementation(() => new Promise(() => {}));

      const { result } = renderHook(() => useRecurringTransactionOperations(), { wrapper });

      act(() => {
        result.current.create.mutate({
          accountId: 1,
          type: 'EXPENSE',
          amount: 100,
          currency: 'USD',
          description: 'Test',
          frequency: 'MONTHLY',
          nextOccurrence: '2024-02-01',
        });
      });

      await waitFor(() => expect(result.current.isLoading).toBe(true));
    });
  });
});
