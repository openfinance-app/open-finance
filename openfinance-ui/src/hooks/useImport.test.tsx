import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useStartImport,
  useImportSession,
  useImportTransactions,
  useConfirmImport,
  useUpdateAccount,
  useUpdateTransactions,
  useCancelImport,
  useImportSessions,
  useImportProgress,
} from './useImport';

// Mock the importService module
vi.mock('@/services/importService', () => ({
  importService: {
    startImport: vi.fn(),
    getSession: vi.fn(),
    getProgress: vi.fn(),
    getTransactions: vi.fn(),
    confirmImport: vi.fn(),
    updateAccount: vi.fn(),
    updateTransactions: vi.fn(),
    cancelImport: vi.fn(),
    listSessions: vi.fn(),
  },
}));

import { importService } from '@/services/importService';
const mockedImportService = importService as any;

describe('useImport hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  const mockSession = {
    id: 1,
    status: 'PARSED',
    fileName: 'transactions.qif',
    totalTransactions: 10,
    duplicateCount: 2,
    accountId: 1,
    createdAt: '2024-01-01T00:00:00Z',
  };

  const mockTransactions = [
    { id: 1, date: '2024-01-01', amount: -50, description: 'Grocery', isDuplicate: false },
    { id: 2, date: '2024-01-02', amount: -30, description: 'Coffee', isDuplicate: true },
  ];

  describe('useImportProgress', () => {
    it('polls active work and stops when it is no longer active', async () => {
      vi.mocked(importService.getProgress).mockResolvedValue({
        phase: 'IMPORTING',
        processed: 500,
        total: 3083,
      });
      const { result, rerender } = renderHook(({ active }) => useImportProgress(1, active), {
        wrapper,
        initialProps: { active: true },
      });
      await waitFor(() => expect(result.current.data?.processed).toBe(500));
      await waitFor(() => expect(importService.getProgress).toHaveBeenCalledTimes(2), {
        timeout: 2000,
      });
      rerender({ active: false });
      await new Promise(resolve => setTimeout(resolve, 1100));
      expect(importService.getProgress).toHaveBeenCalledTimes(2);
    });

    it('does not fetch for an inactive import', () => {
      renderHook(() => useImportProgress(1, false), { wrapper });
      expect(importService.getProgress).not.toHaveBeenCalled();
    });
  });

  // ── useStartImport ─────────────────────────────────────────────────
  describe('useStartImport', () => {
    it('should start an import session', async () => {
      mockedImportService.startImport.mockResolvedValue(mockSession);

      const { result } = renderHook(() => useStartImport(), { wrapper });

      await act(async () => {
        result.current.mutate({ uploadId: 'abc123', accountId: 1 } as any);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockSession);
      expect(mockedImportService.startImport).toHaveBeenCalled();
    });

    it('should cache session data on success', async () => {
      mockedImportService.startImport.mockResolvedValue(mockSession);
      const setQueryDataSpy = vi.spyOn(queryClient, 'setQueryData');

      const { result } = renderHook(() => useStartImport(), { wrapper });

      await act(async () => {
        result.current.mutate({ uploadId: 'abc123', accountId: 1 } as any);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(setQueryDataSpy).toHaveBeenCalledWith(['import-sessions', 1, true], mockSession);
    });
  });

  // ── useImportSession ─────────────────────────────────────────────────
  describe('useImportSession', () => {
    it('should fetch import session by id', async () => {
      mockedImportService.getSession.mockResolvedValue(mockSession);

      const { result } = renderHook(() => useImportSession(1), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockSession);
      expect(mockedImportService.getSession).toHaveBeenCalledWith(1, true);
    });

    it('should be disabled when session id is null', () => {
      const { result } = renderHook(() => useImportSession(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useImportTransactions ─────────────────────────────────────────────────
  describe('useImportTransactions', () => {
    it('should fetch transactions for review', async () => {
      mockedImportService.getTransactions.mockResolvedValue(mockTransactions);

      const { result } = renderHook(() => useImportTransactions(1, 'PARSED'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockTransactions);
    });

    it('should be disabled when session id is null', () => {
      const { result } = renderHook(() => useImportTransactions(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });

    it('should be disabled when session is in terminal state', () => {
      const { result } = renderHook(() => useImportTransactions(1, 'COMPLETED'), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });

    it.each(['PENDING', 'PARSING', 'IMPORTING'] as const)(
      'does not fetch a review in %s',
      status => {
        const { result } = renderHook(() => useImportTransactions(1, status), { wrapper });
        expect(result.current.fetchStatus).toBe('idle');
        expect(mockedImportService.getTransactions).not.toHaveBeenCalled();
      }
    );

    it('should be disabled when session status is FAILED', () => {
      const { result } = renderHook(() => useImportTransactions(1, 'FAILED'), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });

    it('should be disabled when session status is CANCELLED', () => {
      const { result } = renderHook(() => useImportTransactions(1, 'CANCELLED'), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useConfirmImport ─────────────────────────────────────────────────
  describe('useConfirmImport', () => {
    it('should confirm import with mappings', async () => {
      const completedSession = { ...mockSession, status: 'COMPLETED' };
      mockedImportService.confirmImport.mockResolvedValue(completedSession);

      const { result } = renderHook(() => useConfirmImport(), { wrapper });

      await act(async () => {
        result.current.mutate({
          sessionId: 1,
          accountId: 1,
          categoryMappings: { Groceries: 5 },
          skipDuplicates: true,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(completedSession);
    });

    it('should invalidate import-sessions query on success', async () => {
      mockedImportService.confirmImport.mockResolvedValue({ ...mockSession, status: 'IMPORTING' });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useConfirmImport(), { wrapper });

      await act(async () => {
        result.current.mutate({
          sessionId: 1,
          accountId: 1,
          categoryMappings: {},
          skipDuplicates: false,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      // Confirmation is now asynchronous: only the import-sessions list is
      // invalidated here so polling takes over. The transaction/account lists
      // are refreshed by the wizard once the session poll reports COMPLETED,
      // because the imported data does not exist yet at confirm time.
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['import-sessions'] });
      expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['transactions'] });
      expect(invalidateSpy).not.toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useUpdateAccount ─────────────────────────────────────────────────
  describe('useUpdateAccount', () => {
    it('waits for review activation after account changes, including a cached review', async () => {
      mockedImportService.getTransactions.mockResolvedValue(mockTransactions);
      mockedImportService.updateAccount.mockResolvedValue({ ...mockSession, accountId: 2 });
      const { result, rerender } = renderHook(
        ({ reviewActive }) => ({
          review: useImportTransactions(1, 'PARSED', reviewActive),
          account: useUpdateAccount(),
        }),
        { wrapper, initialProps: { reviewActive: false } }
      );

      expect(mockedImportService.getTransactions).not.toHaveBeenCalled();
      await act(async () => {
        await result.current.account.mutateAsync({ sessionId: 1, accountId: 2 });
      });
      expect(mockedImportService.getTransactions).not.toHaveBeenCalled();

      rerender({ reviewActive: true });
      await waitFor(() => expect(result.current.review.data).toEqual(mockTransactions));
      expect(mockedImportService.getTransactions).toHaveBeenCalledTimes(1);

      rerender({ reviewActive: false });
      await act(async () => {
        await result.current.account.mutateAsync({ sessionId: 1, accountId: null });
      });
      expect(mockedImportService.getTransactions).toHaveBeenCalledTimes(1);

      rerender({ reviewActive: true });
      await waitFor(() => expect(mockedImportService.getTransactions).toHaveBeenCalledTimes(2));
    });

    it('replaces an initial in-flight review when account selection completes', async () => {
      let releaseOld!: (value: typeof mockTransactions) => void;
      mockedImportService.getTransactions
        .mockImplementationOnce(
          () =>
            new Promise(resolve => {
              releaseOld = resolve;
            })
        )
        .mockResolvedValue([{ ...mockTransactions[0], isDuplicate: true }]);
      mockedImportService.updateAccount.mockResolvedValue({ ...mockSession, accountId: 2 });
      const { result } = renderHook(
        () => ({
          review: useImportTransactions(1, 'PARSED'),
          account: useUpdateAccount(),
        }),
        { wrapper }
      );
      await waitFor(() => expect(mockedImportService.getTransactions).toHaveBeenCalledTimes(1));
      act(() => result.current.account.mutate({ sessionId: 1, accountId: 2 }));
      await waitFor(() => expect(mockedImportService.updateAccount).toHaveBeenCalled());
      await act(async () => {
        releaseOld(mockTransactions);
      });
      await waitFor(() => expect(result.current.account.isSuccess).toBe(true));
      expect(result.current.review.data).toEqual([{ ...mockTransactions[0], isDuplicate: true }]);
      expect(mockedImportService.getTransactions).toHaveBeenCalledTimes(2);
    });

    it('should update account for import session', async () => {
      const updatedSession = { ...mockSession, accountId: 2 };
      mockedImportService.updateAccount.mockResolvedValue(updatedSession);

      const { result } = renderHook(() => useUpdateAccount(), { wrapper });

      await act(async () => {
        result.current.mutate({ sessionId: 1, accountId: 2 });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedImportService.updateAccount).toHaveBeenCalledWith(1, 2, true);
    });
  });

  // ── useUpdateTransactions ─────────────────────────────────────────────────
  describe('useUpdateTransactions', () => {
    it('should update transactions for import session', async () => {
      mockedImportService.updateTransactions.mockResolvedValue(mockSession);

      const { result } = renderHook(() => useUpdateTransactions(), { wrapper });

      await act(async () => {
        result.current.mutate({
          sessionId: 1,
          transactions: mockTransactions as any,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedImportService.updateTransactions).toHaveBeenCalledWith(
        1,
        mockTransactions,
        true,
        undefined
      );
    });
  });

  // ── useCancelImport ─────────────────────────────────────────────────
  describe('useCancelImport', () => {
    it('should cancel import session', async () => {
      const cancelledSession = { ...mockSession, status: 'CANCELLED' };
      mockedImportService.cancelImport.mockResolvedValue(cancelledSession);

      const { result } = renderHook(() => useCancelImport(), { wrapper });

      await act(async () => {
        result.current.mutate(1);
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data?.status).toBe('CANCELLED');
      expect(mockedImportService.cancelImport).toHaveBeenCalledWith(1, expect.anything());
    });
  });

  // ── useImportSessions ─────────────────────────────────────────────────
  describe('useImportSessions', () => {
    it('should list all import sessions', async () => {
      mockedImportService.listSessions.mockResolvedValue([mockSession]);

      const { result } = renderHook(() => useImportSessions(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual([mockSession]);
      expect(mockedImportService.listSessions).toHaveBeenCalled();
    });
  });
});
