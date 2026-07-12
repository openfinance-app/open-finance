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
} from './useImport';

// Mock the importService module
vi.mock('@/services/importService', () => ({
  importService: {
    startImport: vi.fn(),
    getSession: vi.fn(),
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
      expect(setQueryDataSpy).toHaveBeenCalledWith(['import-sessions', 1], mockSession);
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

    it('should invalidate transactions and accounts queries on success', async () => {
      mockedImportService.confirmImport.mockResolvedValue({ ...mockSession, status: 'COMPLETED' });
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
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['transactions'] });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['accounts'] });
    });
  });

  // ── useUpdateAccount ─────────────────────────────────────────────────
  describe('useUpdateAccount', () => {
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
        true
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
